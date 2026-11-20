package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class CompressedImageResult(
    val dataUrl: String,
    val sizeBytes: Int,
    val width: Int,
    val height: Int
)

object ImageCompressor {
    const val MAX_DIMENSION_PX = 600
    const val TARGET_MAX_BYTES = 80 * 1024 // 40–80 KB target
    const val HARD_MAX_BYTES = 120 * 1024 // 120 KB strict limit
    const val MAX_DATA_URL_CHARS = 175_000

    private val ALLOWED_MIME_TYPES = setOf(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/webp"
    )

    fun isSupportedImageMimeType(mimeType: String?): Boolean {
        if (mimeType.isNullOrBlank()) return false
        val normalized = mimeType.lowercase().substringBefore(';').trim()
        if (normalized.startsWith("video/")) return false
        return normalized in ALLOWED_MIME_TYPES
    }

    suspend fun compressUriToDataUrl(
        context: Context,
        uri: Uri
    ): Result<CompressedImageResult> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri)
            if (mimeType != null && !isSupportedImageMimeType(mimeType)) {
                return@withContext Result.failure(
                    IllegalArgumentException("Unsupported file format ($mimeType). Please select a JPEG, PNG, or WebP image.")
                )
            }

            val rawBytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(
                    IllegalStateException("Could not read the selected image file.")
                )

            if (rawBytes.isEmpty()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Selected image file is empty.")
                )
            }

            compressBytesToDataUrl(rawBytes)
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(e.localizedMessage ?: "Failed to process image.", e)
            )
        }
    }

    fun compressBytesToDataUrl(rawBytes: ByteArray): Result<CompressedImageResult> {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return Result.failure(
                    IllegalArgumentException("File is not a valid image. Video or corrupted files are not supported.")
                )
            }

            val detectedMime = bounds.outMimeType
            if (detectedMime != null && !isSupportedImageMimeType(detectedMime)) {
                return Result.failure(
                    IllegalArgumentException("Unsupported image format ($detectedMime). Use JPEG, PNG, or WebP.")
                )
            }

            val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION_PX, MAX_DIMENSION_PX)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOptions)
                ?: return Result.failure(IllegalArgumentException("Unable to decode the selected image."))

            val rotationDegrees = readExifRotation(rawBytes)
            val oriented = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                if (rotated != decoded) decoded.recycle()
                rotated
            } else {
                decoded
            }

            val result = compressBitmapToJpegDataUrl(oriented)
            oriented.recycle()
            result
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(e.localizedMessage ?: "Image compression failed.", e)
            )
        }
    }

    fun compressBitmapToJpegDataUrl(source: Bitmap): Result<CompressedImageResult> {
        var currentBitmap = scaleDownToCanvas(source, MAX_DIMENSION_PX)
        var jpegBytes = compressJpegWithinBudget(currentBitmap)

        // If still over hard limit, step down to 440px, then 320px
        if (jpegBytes.size > HARD_MAX_BYTES) {
            val medium = scaleDownToCanvas(currentBitmap, 440)
            if (medium != currentBitmap) currentBitmap.recycle()
            currentBitmap = medium
            jpegBytes = compressJpegWithinBudget(currentBitmap)
        }

        if (jpegBytes.size > HARD_MAX_BYTES) {
            val smaller = scaleDownToCanvas(currentBitmap, 320)
            if (smaller != currentBitmap) currentBitmap.recycle()
            currentBitmap = smaller
            jpegBytes = compressJpegWithinBudget(currentBitmap)
        }

        val finalWidth = currentBitmap.width
        val finalHeight = currentBitmap.height
        currentBitmap.recycle()

        val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        val dataUrl = "data:image/jpeg;base64,$base64"

        if (jpegBytes.size > HARD_MAX_BYTES || dataUrl.length > MAX_DATA_URL_CHARS) {
            return Result.failure(
                IllegalStateException("Compressed image exceeds 120 KB limit (${jpegBytes.size / 1024} KB). Please choose a simpler photo.")
            )
        }

        return Result.success(
            CompressedImageResult(
                dataUrl = dataUrl,
                sizeBytes = jpegBytes.size,
                width = finalWidth,
                height = finalHeight
            )
        )
    }

    private fun scaleDownToCanvas(source: Bitmap, maxDim: Int): Bitmap {
        val srcW = source.width
        val srcH = source.height
        val scale = min(1.0f, maxDim.toFloat() / max(srcW, srcH).toFloat())
        val targetW = (srcW * scale).roundToInt().coerceAtLeast(1)
        val targetH = (srcH * scale).roundToInt().coerceAtLeast(1)

        val output = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, null, RectF(0f, 0f, targetW.toFloat(), targetH.toFloat()), paint)
        return output
    }

    private fun compressJpegWithinBudget(bitmap: Bitmap): ByteArray {
        var quality = 84
        var bytes: ByteArray
        while (true) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            if (bytes.size <= TARGET_MAX_BYTES || quality <= 36) {
                break
            }
            quality -= 8
        }
        return bytes
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqW: Int, reqH: Int): Int {
        var inSampleSize = 1
        if (height > reqH * 2 || width > reqW * 2) {
            val halfH = height / 2
            val halfW = width / 2
            while ((halfH / inSampleSize) >= reqH && (halfW / inSampleSize) >= reqW) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun readExifRotation(rawBytes: ByteArray): Int {
        return try {
            rawBytes.inputStream().use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            }
        } catch (_: Exception) {
            0
        }
    }

    fun decodeDataUrlToImageBitmap(dataUrl: String): ImageBitmap? {
        if (dataUrl.isBlank()) return null
        return try {
            val base64Part = dataUrl.substringAfter("base64,", "")
            if (base64Part.isBlank()) return null
            val bytes = Base64.decode(base64Part, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            bmp.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    fun generateHardwareThumbnailDataUrl(
        bgColorHex: Int,
        accentColorHex: Int,
        codeBadge: String,
        shapeKind: String
    ): String {
        val size = 240
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(bgColorHex)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = accentColorHex
        paint.style = Paint.Style.FILL

        when (shapeKind) {
            "pipe" -> {
                canvas.drawRoundRect(RectF(34f, 82f, 206f, 134f), 14f, 14f, paint)
                paint.color = Color.argb(70, 255, 255, 255)
                canvas.drawRoundRect(RectF(34f, 92f, 206f, 104f), 6f, 6f, paint)
                paint.color = accentColorHex
                canvas.drawRoundRect(RectF(26f, 74f, 52f, 142f), 8f, 8f, paint)
            }
            "elbow" -> {
                canvas.drawRoundRect(RectF(62f, 44f, 122f, 152f), 16f, 16f, paint)
                canvas.drawRoundRect(RectF(62f, 98f, 184f, 158f), 16f, 16f, paint)
            }
            "rod" -> {
                paint.strokeWidth = 14f
                paint.style = Paint.Style.STROKE
                canvas.drawLine(36f, 166f, 176f, 46f, paint)
                canvas.drawLine(68f, 180f, 208f, 60f, paint)
                paint.style = Paint.Style.FILL
            }
            "nut" -> {
                canvas.drawCircle(120f, 112f, 56f, paint)
                paint.color = bgColorHex
                canvas.drawCircle(120f, 112f, 24f, paint)
            }
            "tap" -> {
                canvas.drawRoundRect(RectF(56f, 96f, 176f, 132f), 12f, 12f, paint)
                canvas.drawRoundRect(RectF(146f, 96f, 180f, 162f), 12f, 12f, paint)
                canvas.drawRoundRect(RectF(86f, 58f, 146f, 78f), 10f, 10f, paint)
                canvas.drawRect(RectF(108f, 76f, 124f, 98f), paint)
            }
            "tank" -> {
                canvas.drawRoundRect(RectF(60f, 48f, 180f, 172f), 20f, 20f, paint)
                paint.color = bgColorHex
                canvas.drawRect(60f, 86f, 180f, 96f, paint)
                canvas.drawRect(60f, 126f, 180f, 136f, paint)
            }
            "cement" -> {
                canvas.drawRoundRect(RectF(56f, 46f, 184f, 172f), 14f, 14f, paint)
                paint.color = bgColorHex
                canvas.drawRoundRect(RectF(76f, 76f, 164f, 140f), 8f, 8f, paint)
            }
            else -> {
                canvas.drawRoundRect(RectF(108f, 72f, 132f, 184f), 8f, 8f, paint)
                canvas.drawRoundRect(RectF(62f, 50f, 178f, 84f), 10f, 10f, paint)
            }
        }

        // Crisp bottom label bar
        paint.color = Color.argb(190, 17, 24, 39)
        canvas.drawRect(0f, 190f, size.toFloat(), size.toFloat(), paint)
        paint.color = Color.WHITE
        paint.textSize = 24f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(codeBadge, size / 2f, 223f, paint)

        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 78, out)
        bmp.recycle()
        val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }
}

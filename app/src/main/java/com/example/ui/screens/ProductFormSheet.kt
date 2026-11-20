package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.ProductInput
import com.example.domain.model.HardwareCategory
import com.example.domain.model.Product
import com.example.domain.model.ProductUnit
import com.example.ui.theme.BorderStone
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.ForestGreenContainer
import com.example.ui.theme.OnForestGreenContainer
import com.example.ui.theme.OutOfStockRed
import com.example.ui.theme.WarmStoneSurface
import com.example.util.CurrencyFormatter
import com.example.util.ImageCompressor
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductFormSheet(
    editingProduct: Product?,
    suggestedCode: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (ProductInput, onError: (String) -> Unit) -> Unit,
    onDeleteDemo: ((Product) -> Unit)? = null
) {
    BackHandler(onBack = onDismiss)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEditing = editingProduct != null

    var itemCode by remember(editingProduct) {
        mutableStateOf(editingProduct?.itemCode ?: suggestedCode)
    }
    var name by remember(editingProduct) { mutableStateOf(editingProduct?.name ?: "") }
    var brand by remember(editingProduct) { mutableStateOf(editingProduct?.brand ?: "") }
    var sizeSpec by remember(editingProduct) { mutableStateOf(editingProduct?.sizeSpec ?: "") }
    var category by remember(editingProduct) {
        mutableStateOf(editingProduct?.categoryEnum() ?: HardwareCategory.PIPES_PLUMBING)
    }
    var unit by remember(editingProduct) {
        mutableStateOf(editingProduct?.unitEnum() ?: ProductUnit.PIECE)
    }
    var purchasePriceText by remember(editingProduct) {
        mutableStateOf(editingProduct?.purchasePrice?.let { CurrencyFormatter.formatNumber(it) } ?: "")
    }
    var sellingPriceText by remember(editingProduct) {
        mutableStateOf(editingProduct?.sellingPrice?.let { CurrencyFormatter.formatNumber(it) } ?: "")
    }
    var openingStockQtyText by remember(editingProduct) {
        mutableStateOf(editingProduct?.stockQty?.let { CurrencyFormatter.formatNumber(it) } ?: "0")
    }
    var lowStockLimitText by remember(editingProduct) {
        mutableStateOf(editingProduct?.lowStockLimit?.let { CurrencyFormatter.formatNumber(it) } ?: "10")
    }
    var description by remember(editingProduct) { mutableStateOf(editingProduct?.description ?: "") }
    var imageDataUrl by remember(editingProduct) { mutableStateOf(editingProduct?.imageDataUrl ?: "") }
    var imageSizeLabel by remember(editingProduct) { mutableStateOf<String?>(null) }
    var isCompressingImage by remember { mutableStateOf(false) }
    var imageError by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    fun handleSelectedImageUri(uri: Uri) {
        imageError = null
        isCompressingImage = true
        scope.launch {
            val result = ImageCompressor.compressUriToDataUrl(context, uri)
            isCompressingImage = false
            result.fold(
                onSuccess = { compressed ->
                    imageDataUrl = compressed.dataUrl
                    imageSizeLabel = "${(compressed.sizeBytes / 1024.0).toInt().coerceAtLeast(1)} KB • ${compressed.width}×${compressed.height}px JPEG"
                },
                onFailure = { err ->
                    imageError = err.localizedMessage ?: "Failed to compress image."
                }
            )
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            handleSelectedImageUri(uri)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            handleSelectedImageUri(uri)
        }
    }

    fun launchCameraCapture() {
        try {
            val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
            val photoFile = File(cameraDir, "product_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            imageError = e.localizedMessage ?: "Unable to open camera on this device."
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    Text(
                        text = if (isEditing) "Edit Product Details" else "Add Hardware Product",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (isEditing && editingProduct != null && editingProduct.isDemo && onDeleteDemo != null) {
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete demo product",
                                tint = OutOfStockRed
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Photo Capture & Upload Card
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderStone, MaterialTheme.shapes.large)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Product Photo (Max 600×600px • ≤120 KB JPEG)",
                        style = MaterialTheme.typography.titleMedium
                    )

                    val previewBitmap = remember(imageDataUrl) {
                        ImageCompressor.decodeDataUrlToImageBitmap(imageDataUrl)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(WarmStoneSurface)
                                .border(1.dp, BorderStone, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                isCompressingImage -> CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = ForestGreen
                                )
                                previewBitmap != null -> Image(
                                    bitmap = previewBitmap,
                                    contentDescription = "Product preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                else -> Text(
                                    text = "No Photo",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { launchCameraCapture() },
                                    enabled = !isCompressingImage,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("take_photo_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CameraAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Camera", maxLines = 1)
                                }

                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    enabled = !isCompressingImage,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("upload_photo_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PhotoLibrary,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Gallery", maxLines = 1)
                                }
                            }

                            if (imageDataUrl.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = imageSizeLabel ?: "Compressed JPEG ready",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ForestGreen
                                    )
                                    TextButton(
                                        onClick = {
                                            imageDataUrl = ""
                                            imageSizeLabel = null
                                            imageError = null
                                        }
                                    ) {
                                        Text("Remove", color = OutOfStockRed)
                                    }
                                }
                            }
                        }
                    }

                    if (imageError != null) {
                        Text(
                            text = imageError.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = OutOfStockRed
                        )
                    }
                }
            }

            // Item Code, Name, Brand, Size
            OutlinedTextField(
                value = itemCode,
                onValueChange = { if (it.length <= 9) itemCode = it.uppercase() },
                label = { Text("Item Code (4–9 chars, e.g. PVC-101)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("product_code_input")
            )

            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 120) name = it },
                label = { Text("Product Name * (e.g. Astral PVC Pipe)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("product_name_input")
            )

            OutlinedTextField(
                value = brand,
                onValueChange = { if (it.length <= 80) brand = it },
                label = { Text("Brand (Optional, e.g. Astral, Taparia, Jaquar)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("product_brand_input")
            )

            OutlinedTextField(
                value = sizeSpec,
                onValueChange = { if (it.length <= 80) sizeSpec = it },
                label = { Text("Size / Specification (e.g. 1 inch • 10 ft)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Category Pills (7 categories)
            Text(
                text = "Category",
                style = MaterialTheme.typography.titleMedium
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HardwareCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ForestGreenContainer,
                            selectedLabelColor = OnForestGreenContainer
                        )
                    )
                }
            }

            // Unit Pills (8 units)
            Text(
                text = "Measurement Unit",
                style = MaterialTheme.typography.titleMedium
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ProductUnit.entries.forEach { u ->
                    FilterChip(
                        selected = unit == u,
                        onClick = { unit = u },
                        label = { Text(u.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ForestGreenContainer,
                            selectedLabelColor = OnForestGreenContainer
                        )
                    )
                }
            }

            // Pricing Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = purchasePriceText,
                    onValueChange = { purchasePriceText = sanitizeDecimalInput(it, true) },
                    label = { Text("Purchase ₹ *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("product_purchase_price_input")
                )
                OutlinedTextField(
                    value = sellingPriceText,
                    onValueChange = { sellingPriceText = sanitizeDecimalInput(it, true) },
                    label = { Text("Selling ₹ *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("product_selling_price_input")
                )
            }

            // Stock & Low-Stock Alert Limit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isEditing && editingProduct != null) {
                    // Read-only current stock during Edit Product; audited changes go via Adjust Stock
                    OutlinedTextField(
                        value = CurrencyFormatter.formatQty(editingProduct.stockQty, unit.label),
                        onValueChange = {},
                        enabled = false,
                        label = { Text("Current Stock") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("product_stock_readonly")
                    )
                } else {
                    OutlinedTextField(
                        value = openingStockQtyText,
                        onValueChange = { openingStockQtyText = sanitizeDecimalInput(it, unit.allowsDecimal) },
                        label = { Text("Opening Stock (${unit.label})") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (unit.allowsDecimal) KeyboardType.Decimal else KeyboardType.Number
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("product_stock_qty_input")
                    )
                }

                OutlinedTextField(
                    value = lowStockLimitText,
                    onValueChange = { lowStockLimitText = sanitizeDecimalInput(it, unit.allowsDecimal) },
                    label = { Text("Low Alert ≤") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (unit.allowsDecimal) KeyboardType.Decimal else KeyboardType.Number
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("product_low_limit_input")
                )
            }

            if (isEditing) {
                Text(
                    text = "To change stock quantity safely with an audit reason, use 'Adjust Stock' in Product Details or record a Purchase / Sale.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Verbatim Shopkeeper Notes / Description (max 1000 chars)
            OutlinedTextField(
                value = description,
                onValueChange = { if (it.length <= 1000) description = it },
                label = { Text("Description / Rack Location (Saved Exactly As Typed)") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("product_description_input")
            )

            if (formError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = formError.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Button(
                onClick = {
                    formError = null
                    val finalCode = itemCode.trim().ifBlank { suggestedCode }.uppercase()
                    val finalName = name.trim()
                    val buyPrice = purchasePriceText.toDoubleOrNull()
                    val sellPrice = sellingPriceText.toDoubleOrNull()
                    val stockQty = if (isEditing && editingProduct != null) {
                        editingProduct.stockQty
                    } else {
                        openingStockQtyText.toDoubleOrNull() ?: 0.0
                    }
                    val lowLimit = lowStockLimitText.toDoubleOrNull() ?: 10.0

                    when {
                        finalCode.length !in 4..9 -> {
                            formError = "Item code must be 4 to 9 characters."
                        }
                        finalName.isBlank() -> {
                            formError = "Please enter the product name."
                        }
                        buyPrice == null || buyPrice < 0.0 -> {
                            formError = "Enter a valid non-negative Purchase Price."
                        }
                        sellPrice == null || sellPrice < 0.0 -> {
                            formError = "Enter a valid non-negative Selling Price."
                        }
                        stockQty < 0.0 || lowLimit < 0.0 -> {
                            formError = "Stock quantity and alert limit cannot be negative."
                        }
                        isCompressingImage -> {
                            formError = "Please wait for image compression to finish."
                        }
                        else -> {
                            onSave(
                                ProductInput(
                                    id = editingProduct?.id,
                                    itemCode = finalCode,
                                    name = finalName,
                                    brand = brand.trim(),
                                    sizeSpec = sizeSpec.trim(),
                                    category = category,
                                    unit = unit,
                                    purchasePrice = buyPrice,
                                    sellingPrice = sellPrice,
                                    stockQty = stockQty,
                                    lowStockLimit = lowLimit,
                                    description = description, // Verbatim, never trimmed or modified
                                    imageDataUrl = imageDataUrl
                                )
                            ) { err ->
                                formError = err
                            }
                        }
                    }
                },
                enabled = !isSubmitting && !isCompressingImage,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ForestGreen,
                    contentColor = Color.White
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("save_product_button")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Saving...")
                } else {
                    Text(
                        text = if (isEditing) "Update Details" else "Save Product",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showDeleteConfirm && editingProduct != null && onDeleteDemo != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Demo Product?") },
            text = { Text("Remove demo item ${editingProduct.name} (${editingProduct.itemCode})?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteDemo(editingProduct)
                        onDismiss()
                    }
                ) {
                    Text("Delete", color = OutOfStockRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

fun sanitizeDecimalInput(raw: String, allowDecimal: Boolean): String {
    val sb = StringBuilder()
    var hasDot = false
    for (ch in raw) {
        if (ch.isDigit()) {
            sb.append(ch)
        } else if (allowDecimal && ch == '.' && !hasDot) {
            if (sb.isEmpty()) sb.append('0')
            sb.append('.')
            hasDot = true
        }
    }
    return sb.toString()
}

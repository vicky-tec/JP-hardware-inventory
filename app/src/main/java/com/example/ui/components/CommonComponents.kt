package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.Plumbing
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.HardwareCategory
import com.example.domain.model.StockStatus
import com.example.ui.DatabaseStatus
import com.example.ui.FeedbackMessage
import com.example.ui.theme.AvailableGreen
import com.example.ui.theme.AvailableGreenBg
import com.example.ui.theme.BorderStone
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.LowStockAmber
import com.example.ui.theme.LowStockAmberBg
import com.example.ui.theme.OutOfStockRed
import com.example.ui.theme.OutOfStockRedBg
import com.example.ui.theme.WarmStoneSurface
import com.example.util.ImageCompressor

@Composable
fun ProductThumbnail(
    imageDataUrl: String,
    category: HardwareCategory,
    productName: String,
    size: Dp = 64.dp,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(imageDataUrl) {
        ImageCompressor.decodeDataUrlToImageBitmap(imageDataUrl)
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(WarmStoneSurface)
            .border(1.dp, BorderStone, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "$productName photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = categoryIcon(category),
                contentDescription = category.label,
                tint = ForestGreen,
                modifier = Modifier.size(size * 0.45f)
            )
        }
    }
}

fun categoryIcon(category: HardwareCategory): ImageVector = when (category) {
    HardwareCategory.PIPES_PLUMBING -> Icons.Filled.Plumbing
    HardwareCategory.IRON_RODS_STEEL -> Icons.Filled.Construction
    HardwareCategory.NUTS_BOLTS_SCREWS -> Icons.Filled.Hardware
    HardwareCategory.TAPS_SANITARY -> Icons.Filled.WaterDrop
    HardwareCategory.BUILDING_MATERIALS -> Icons.Filled.Construction
    HardwareCategory.TOOLS_ACCESSORIES -> Icons.Filled.Build
    HardwareCategory.OTHER_HARDWARE -> Icons.Filled.Category
}

@Composable
fun StockStatusBadge(
    status: StockStatus,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = when (status) {
        StockStatus.AVAILABLE -> AvailableGreenBg to AvailableGreen
        StockStatus.LOW_STOCK -> LowStockAmberBg to LowStockAmber
        StockStatus.OUT_OF_STOCK -> OutOfStockRedBg to OutOfStockRed
    }
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun DatabaseStatusPill(
    status: DatabaseStatus,
    modifier: Modifier = Modifier
) {
    val (bg, fg, icon, text) = when (status) {
        is DatabaseStatus.Connected -> Quad(
            AvailableGreenBg,
            AvailableGreen,
            Icons.Filled.CloudDone,
            "Cloud Synced"
        )
        is DatabaseStatus.Syncing -> Quad(
            LowStockAmberBg,
            LowStockAmber,
            Icons.Filled.CloudSync,
            "Syncing..."
        )
        is DatabaseStatus.Error -> Quad(
            OutOfStockRedBg,
            OutOfStockRed,
            Icons.Filled.CloudOff,
            "DB Error"
        )
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(50),
        modifier = modifier.testTag("db_connection_status")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = fg,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun FeedbackBanner(
    message: FeedbackMessage?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = message != null, modifier = modifier) {
        if (message != null) {
            val bg = if (message.isError) OutOfStockRedBg else AvailableGreenBg
            val fg = if (message.isError) OutOfStockRed else AvailableGreen
            val icon = if (message.isError) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle
            Surface(
                color = bg,
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .border(1.dp, fg.copy(alpha = 0.3f), MaterialTheme.shapes.medium)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = fg,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = fg,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Dismiss notification",
                            tint = fg,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

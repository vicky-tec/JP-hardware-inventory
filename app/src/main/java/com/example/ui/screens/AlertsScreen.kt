package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.Product
import com.example.ui.components.ProductThumbnail
import com.example.ui.theme.AvailableGreen
import com.example.ui.theme.BorderStone
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.LowStockAmber
import com.example.ui.theme.LowStockAmberBg
import com.example.ui.theme.OutOfStockRed
import com.example.ui.theme.OutOfStockRedBg
import com.example.util.CurrencyFormatter

@Composable
fun AlertsScreen(
    outOfStockProducts: List<Product>,
    lowStockProducts: List<Product>,
    onRestockClick: (Product) -> Unit
) {
    if (outOfStockProducts.isEmpty() && lowStockProducts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "All items stocked",
                    tint = AvailableGreen,
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    text = "All Items Well Stocked",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "No out-of-stock or low-stock hardware items right now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (outOfStockProducts.isNotEmpty()) {
            item {
                Surface(
                    color = OutOfStockRedBg,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RemoveShoppingCart,
                            contentDescription = null,
                            tint = OutOfStockRed
                        )
                        Text(
                            text = "Not Available — 0 Stock (${outOfStockProducts.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = OutOfStockRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            items(outOfStockProducts, key = { "out_${it.id}" }) { product ->
                AlertProductCard(
                    product = product,
                    isOutOfStock = true,
                    onRestockClick = { onRestockClick(product) }
                )
            }
        }

        if (lowStockProducts.isNotEmpty()) {
            item {
                Surface(
                    color = LowStockAmberBg,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WarningAmber,
                            contentDescription = null,
                            tint = LowStockAmber
                        )
                        Text(
                            text = "Low Stock Warning (${lowStockProducts.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = LowStockAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            items(lowStockProducts, key = { "low_${it.id}" }) { product ->
                AlertProductCard(
                    product = product,
                    isOutOfStock = false,
                    onRestockClick = { onRestockClick(product) }
                )
            }
        }
    }
}

@Composable
private fun AlertProductCard(
    product: Product,
    isOutOfStock: Boolean,
    onRestockClick: () -> Unit
) {
    val accent = if (isOutOfStock) OutOfStockRed else LowStockAmber

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, accent.copy(alpha = 0.45f), MaterialTheme.shapes.large)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProductThumbnail(
                imageDataUrl = product.imageDataUrl,
                category = product.categoryEnum(),
                productName = product.name,
                size = 60.dp
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${product.itemCode} • Limit <= ${CurrencyFormatter.formatNumber(product.lowStockLimit)} ${product.unit}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isOutOfStock) {
                        "0 ${product.unit} left"
                    } else {
                        "Only ${CurrencyFormatter.formatQty(product.stockQty, product.unit)} left"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Button(
                onClick = onRestockClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ForestGreen,
                    contentColor = Color.White
                ),
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("restock_button_${product.itemCode}")
            ) {
                Icon(
                    imageVector = Icons.Filled.AddShoppingCart,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Restock", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

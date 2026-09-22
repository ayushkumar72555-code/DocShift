package com.ayush.docshift.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(onSelect: (Screen) -> Unit) {
    val tiles = listOf(
        Triple("DocSafe", "Keep important files ready on your device.", Screen.DocSafe),
        Triple("Reduce Image Size", "Compress images to a target file size.", Screen.Compress),
        Triple("Resize Image", "Set dimensions in pixels, cm or inches.", Screen.Resize),
        Triple("Reduce PDF Size", "Shrink PDFs to a precise target size.", Screen.PdfCompress),
        Triple("Image → PDF", "Combine images into one PDF.", Screen.ImageToPdf),
        Triple("PDF → Image", "Export PDF pages as images.", Screen.PdfToImage)
    )

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("DocShift", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(
                "Simple, private tools for images and PDFs.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            tiles.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { item ->
                        val title = item.first
                        val description = item.second
                        val screen = item.third
                        Card(
                            Modifier.weight(1f).height(148.dp).clickable { onSelect(screen) },
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            Column(
                                Modifier.fillMaxSize().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        Text(
            "All processing happens locally on your device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

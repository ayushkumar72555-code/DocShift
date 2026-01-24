package com.ayush.aimage.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onSelect: (Screen) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text("DocShift", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = { onSelect(Screen.Compress) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Size Reduce")
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { onSelect(Screen.ImageToPdf) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Image → PDF")
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { onSelect(Screen.PdfToImage) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("PDF → Image")
        }
    }
}

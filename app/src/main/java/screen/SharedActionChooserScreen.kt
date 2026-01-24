package com.ayush.aimage.ui.screen

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SharedActionChooserScreen(
    imageUris: List<Uri>,
    onCompress: () -> Unit,
    onImageToPdf: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            "What do you want to do?",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(24.dp))

        Text("${imageUris.size} image(s) received")

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onCompress,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reduce Image Size")
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onImageToPdf,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Convert Images to PDF")
        }

        Spacer(Modifier.height(24.dp))

        TextButton(onClick = onBack) {
            Text("Cancel")
        }
    }
}

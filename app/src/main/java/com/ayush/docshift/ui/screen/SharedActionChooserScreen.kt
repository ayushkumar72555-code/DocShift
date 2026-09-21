package com.ayush.docshift.ui.screen

import android.net.Uri
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

@Composable
fun SharedActionChooserScreen(
    imageUris: List<Uri>,
    onCompress: () -> Unit,
    onResize: () -> Unit,
    onImageToPdf: () -> Unit,
    onBack: () -> Unit
) {
    DocShiftScaffold(
        title = "Choose an action",
        subtitle = imageUris.size.toString() + " image" + if (imageUris.size == 1) "" else "s" + " received",
        onBack = onBack
    ) {
        SectionCard {
            Text("What would you like to do with these images?", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            PrimaryAction("Reduce image size", onClick = onCompress)
            SecondaryAction("Resize images", onClick = onResize)
            SecondaryAction("Convert images to PDF", onClick = onImageToPdf)
        }
    }
}

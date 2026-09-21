package com.ayush.docshift.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun TutorialScreen(onFinish: () -> Unit) {
    val pages = listOf(
        "Welcome to DocShift" to "A clean, offline workspace for everyday image and PDF tasks.",
        "Reduce file size" to "Compress images to a target size such as 20 KB, 50 KB or 100 KB.",
        "Resize images" to "Set dimensions in pixels, centimetres or inches with aspect-ratio control.",
        "Work with PDFs" to "Create PDFs from images, export PDF pages, or reduce PDF file size.",
        "Ready to go" to "Everything is processed locally on your device."
    )
    var page by remember { mutableStateOf(0) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(pages[page].first, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            Text(pages[page].second, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Text((page + 1).toString() + " / " + pages.size, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (page > 0) OutlinedButton(onClick = { page-- }, Modifier.weight(1f)) { Text("Back") }
            Button(onClick = { if (page == pages.lastIndex) onFinish() else page++ }, Modifier.weight(1f)) {
                Text(if (page == pages.lastIndex) "Get started" else "Next")
            }
        }
    }
}

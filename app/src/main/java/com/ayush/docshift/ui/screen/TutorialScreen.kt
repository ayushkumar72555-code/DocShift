package com.ayush.docshift.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TutorialScreen(
    onFinish: () -> Unit
) {
    val pages = listOf(
        "Hi! This me Ayush 👋\n\nWelcome to DocShift.\nThis app helps you work with images and PDFs easily.",
        "\uD83D\uDE0E Haan maine hi banaya hai!",
        "📉 Size Reduce\n\nCompress images to an exact size like 20 KB, 50 KB, or 100 KB.",
        "\uD83D\uDD28 Latest Update: Image Resize based on pixels, Centimters and Inches",
        "🖼️ Image → PDF\n\nConvert one or multiple images into a single PDF file.",
        "📄 PDF → Image\n\nExtract every page of a PDF as high-quality images.",
        "🎉 Enjoy!\n\nYou’re all set. Use DocShift and make file handling effortless."
    )

    var page by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {

        Spacer(Modifier.height(20.dp))

        Text(
            text = pages[page],
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            if (page > 0) {
                TextButton(onClick = { page-- }) {
                    Text("Back")
                }
            }

            Button(
                onClick = {
                    if (page == pages.lastIndex) {
                        onFinish()
                    } else {
                        page++
                    }
                }
            ) {
                Text(if (page == pages.lastIndex) "Enjoy" else "Next")
            }
        }
    }
}

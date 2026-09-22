package com.ayush.docshift.ui.screen

sealed class Screen {
    object Home : Screen()
    object Tutorial : Screen()
    object DocSafe : Screen()
    object Compress : Screen()
    object Resize : Screen()
    object PdfCompress : Screen()
    object ImageToPdf : Screen()
    object PdfToImage : Screen()
    object SharedChooser : Screen()
}

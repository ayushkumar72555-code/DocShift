package com.ayush.aimage.ui.screen

sealed class Screen {
    object Home : Screen()
    object Tutorial : Screen()
    object Compress : Screen()
    object ImageToPdf : Screen()
    object PdfToImage : Screen()
    object SharedChooser : Screen()

}

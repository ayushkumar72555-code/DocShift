package com.ayush.docshift.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

object PdfToImageConverter {
    private const val MAX_OUTPUT_DIMENSION = 4096

    enum class OutputFormat(val extension: String, val mimeType: String) {
        PNG("png", "image/png"),
        JPEG("jpg", "image/jpeg")
    }

    suspend fun convert(
        context: Context,
        pdfUri: Uri,
        outputDir: File,
        format: OutputFormat = OutputFormat.PNG,
        dpi: Int = 300,
        pageRange: IntRange? = null,
        onProgress: (current: Int, total: Int) -> Unit
    ): List<File> {
        if (!outputDir.exists() && !outputDir.mkdirs()) throw IllegalStateException("Unable to create output directory")
        val pfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
            ?: throw IllegalArgumentException("Cannot open PDF")
        val renderer = PdfRenderer(pfd)
        val outputFiles = mutableListOf<File>()
        try {
            val pageCount = renderer.pageCount
            val pageIndexes = (pageRange ?: (1..pageCount)).filter { it in 1..pageCount }.map { it - 1 }
            require(pageIndexes.isNotEmpty()) { "Choose at least one page to export" }
            val renderScale = (dpi.coerceIn(72, 300) / 100f)
            for ((progressIndex, i) in pageIndexes.withIndex()) {
                val page = renderer.openPage(i)
                try {
                    val requestedWidth = page.width * renderScale
                    val requestedHeight = page.height * renderScale
                    val dimensionScale = min(1f, MAX_OUTPUT_DIMENSION / maxOf(requestedWidth, requestedHeight))
                    val scale = renderScale * dimensionScale
                    val width = maxOf(1, (page.width * scale).toInt())
                    val height = maxOf(1, (page.height * scale).toInt())
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        val matrix = Matrix().apply { setScale(scale, scale) }
                        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val outFile = File(outputDir, "page_${i + 1}.${format.extension}")
                        FileOutputStream(outFile).use { output ->
                            val bitmapFormat = if (format == OutputFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                            bitmap.compress(bitmapFormat, if (format == OutputFormat.PNG) 100 else 95, output)
                        }
                        outputFiles.add(outFile)
                    } finally {
                        bitmap.recycle()
                    }
                } finally {
                    page.close()
                }
                onProgress(progressIndex + 1, pageIndexes.size)
            }
            return outputFiles
        } finally {
            renderer.close()
            pfd.close()
        }
    }
}

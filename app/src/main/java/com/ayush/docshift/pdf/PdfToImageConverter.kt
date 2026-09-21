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
    private const val RENDER_SCALE = 3f
    private const val MAX_OUTPUT_DIMENSION = 4096

    suspend fun convert(context: Context, pdfUri: Uri, outputDir: File, onProgress: (current: Int, total: Int) -> Unit): List<File> {
        if (!outputDir.exists() && !outputDir.mkdirs()) throw IllegalStateException("Unable to create output directory")
        val pfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
            ?: throw IllegalArgumentException("Cannot open PDF")
        val renderer = PdfRenderer(pfd)
        val outputFiles = mutableListOf<File>()
        try {
            val pageCount = renderer.pageCount
            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                try {
                    val requestedWidth = page.width * RENDER_SCALE
                    val requestedHeight = page.height * RENDER_SCALE
                    val dimensionScale = min(1f, MAX_OUTPUT_DIMENSION / maxOf(requestedWidth, requestedHeight))
                    val scale = RENDER_SCALE * dimensionScale
                    val width = maxOf(1, (page.width * scale).toInt())
                    val height = maxOf(1, (page.height * scale).toInt())
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        val matrix = Matrix().apply { setScale(scale, scale) }
                        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val outFile = File(outputDir, "page_${i + 1}.png")
                        FileOutputStream(outFile).use { output ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                        }
                        outputFiles.add(outFile)
                    } finally {
                        bitmap.recycle()
                    }
                } finally {
                    page.close()
                }
                onProgress(i + 1, pageCount)
            }
            return outputFiles
        } finally {
            renderer.close()
            pfd.close()
        }
    }
}

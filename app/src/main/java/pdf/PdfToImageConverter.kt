package com.ayush.aimage.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream

object PdfToImageConverter {

    suspend fun convert(
        context: Context,
        pdfUri: Uri,
        outputDir: File,
        onProgress: (current: Int, total: Int) -> Unit
    ): List<File> {

        if (!outputDir.exists()) outputDir.mkdirs()

        val pfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
            ?: throw IllegalArgumentException("Cannot open PDF")

        val renderer = PdfRenderer(pfd)

        val pageCount = renderer.pageCount
        val outputFiles = mutableListOf<File>()

        for (i in 0 until pageCount) {
            val page = renderer.openPage(i)

            // Scale factor (2 = good, 3 = excellent)
            val scale = 3f

            val width = (page.width * scale).toInt()
            val height = (page.height * scale).toInt()

            val bitmap = Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
            )

// White background (prevents black pages)
            bitmap.eraseColor(Color.WHITE)

            val matrix = Matrix().apply {
                setScale(scale, scale)
            }

            page.render(
                bitmap,
                null,
                matrix,
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
            )

            // IMPORTANT: avoid black background
            bitmap.eraseColor(Color.WHITE)

            page.render(
                bitmap,
                null,
                null,
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
            )

            val outFile = File(outputDir, "page_${i + 1}.jpg")
            FileOutputStream(outFile).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }

            page.close()
            outputFiles.add(outFile)

            // Progress callback
            onProgress(i + 1, pageCount)
        }

        renderer.close()
        pfd.close()

        return outputFiles
    }
}

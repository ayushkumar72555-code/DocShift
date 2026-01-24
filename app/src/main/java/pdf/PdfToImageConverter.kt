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

    fun convert(
        context: Context,
        pdfUri: Uri,
        outputDir: File
    ): List<File> {

        if (!outputDir.exists()) outputDir.mkdirs()

        val results = mutableListOf<File>()

        val fd: ParcelFileDescriptor =
            context.contentResolver.openFileDescriptor(pdfUri, "r")
                ?: throw IllegalStateException("Cannot open PDF")

        val renderer = PdfRenderer(fd)

        val dpi = 300
        val scale = dpi / 72f

        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)

            val width = (page.width * scale).toInt()
            val height = (page.height * scale).toInt()

            val bitmap = Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
            )

            // 🔴 THIS LINE FIXES BLACK BACKGROUNDS
            bitmap.eraseColor(Color.WHITE)

            val matrix = Matrix().apply {
                postScale(scale, scale)
            }

            page.render(
                bitmap,
                null,
                matrix,
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
            )

            val outFile = File(
                outputDir,
                "AImage_page_${i + 1}.jpg"
            )

            FileOutputStream(outFile).use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
            }

            bitmap.recycle()
            page.close()

            results.add(outFile)
        }

        renderer.close()
        fd.close()

        return results
    }
}

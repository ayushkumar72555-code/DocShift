package com.ayush.aimage.pdf

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object ImageToPdfConverter {

    fun convert(
        resolver: ContentResolver,
        imageUris: List<Uri>,
        outputDir: File,
        fileName: String
    ): File {

        require(imageUris.isNotEmpty()) {
            "No images provided"
        }

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val pdfDocument = PdfDocument()

        imageUris.forEachIndexed { index, uri ->
            val bitmap = decodeBitmapFullQuality(resolver, uri)

            val pageInfo = PdfDocument.PageInfo.Builder(
                bitmap.width,
                bitmap.height,
                index + 1
            ).create()

            val page = pdfDocument.startPage(pageInfo)

            // Draw bitmap exactly as-is (no scaling, no compression)
            page.canvas.drawBitmap(bitmap, 0f, 0f, null)

            pdfDocument.finishPage(page)
            bitmap.recycle()
        }

        val outputFile = File(outputDir, "$fileName.pdf")

        FileOutputStream(outputFile).use {
            pdfDocument.writeTo(it)
        }

        pdfDocument.close()
        return outputFile
    }

    /**
     * Decode image at full resolution without scaling or recompression.
     * This preserves original image quality inside the PDF.
     */
    private fun decodeBitmapFullQuality(
        resolver: ContentResolver,
        uri: Uri
    ): Bitmap {

        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
            inDither = false
        }

        resolver.openInputStream(uri)?.use { input ->
            return BitmapFactory.decodeStream(input, null, options)
                ?: throw IllegalStateException("Failed to decode bitmap")
        }

        throw IllegalStateException("Unable to open image input stream")
    }
}

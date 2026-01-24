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

        if (imageUris.isEmpty()) {
            throw IllegalArgumentException("No images provided")
        }

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val pdfDocument = PdfDocument()

        imageUris.forEachIndexed { index, uri ->
            val bitmap = decodeBitmapSafe(resolver, uri)

            val pageInfo = PdfDocument.PageInfo.Builder(
                bitmap.width,
                bitmap.height,
                index + 1
            ).create()

            val page = pdfDocument.startPage(pageInfo)
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

    private fun decodeBitmapSafe(
        resolver: ContentResolver,
        uri: Uri,
        maxDimension: Int = 4096
    ): Bitmap {

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        resolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, options)
        }

        val sampleSize = calculateInSampleSize(
            options.outWidth,
            options.outHeight,
            maxDimension
        )

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        resolver.openInputStream(uri)!!.use {
            return BitmapFactory.decodeStream(it, null, decodeOptions)
                ?: throw IllegalStateException("Failed to decode bitmap")
        }
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        maxDim: Int
    ): Int {
        var inSampleSize = 1
        var w = width
        var h = height

        while (w > maxDim || h > maxDim) {
            w /= 2
            h /= 2
            inSampleSize *= 2
        }
        return inSampleSize
    }
}

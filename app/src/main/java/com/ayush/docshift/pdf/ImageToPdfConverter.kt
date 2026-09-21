package com.ayush.docshift.pdf

import android.content.ContentResolver
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.ayush.docshift.util.BitmapUtils
import java.io.File
import java.io.FileOutputStream

object ImageToPdfConverter {
    private const val MAX_BITMAP_DIMENSION = 4096

    fun convert(resolver: ContentResolver, imageUris: List<Uri>, outputDir: File, fileName: String): File {
        require(imageUris.isNotEmpty()) { "No images provided" }
        if (!outputDir.exists() && !outputDir.mkdirs()) throw IllegalStateException("Unable to create output directory")
        val pdfDocument = PdfDocument()
        try {
            imageUris.forEachIndexed { index, uri ->
                val bitmap = BitmapUtils.decodeSafe(resolver, uri, MAX_BITMAP_DIMENSION)
                try {
                    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    try {
                        page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    } finally {
                        pdfDocument.finishPage(page)
                    }
                } finally {
                    bitmap.recycle()
                }
            }
            val outputFile = File(outputDir, "${fileName}.pdf")
            FileOutputStream(outputFile).use { output -> pdfDocument.writeTo(output) }
            return outputFile
        } finally {
            pdfDocument.close()
        }
    }
}

package com.ayush.docshift.pdf

import android.content.ContentResolver
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.ayush.docshift.util.BitmapUtils
import java.io.File
import java.io.FileOutputStream

object ImageToPdfConverter {
    private const val MAX_BITMAP_DIMENSION = 4096

    enum class PageFormat(val width: Int, val height: Int) {
        Original(0, 0), A4Portrait(595, 842), Letter(612, 792)
    }

    fun convert(
        resolver: ContentResolver,
        imageUris: List<Uri>,
        outputDir: File,
        fileName: String,
        pageFormat: PageFormat = PageFormat.Original,
        marginPx: Int = 0,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): File {
        require(imageUris.isNotEmpty()) { "No images provided" }
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw IllegalStateException("Unable to create output directory")
        }

        val pdfDocument = PdfDocument()
        try {
            imageUris.forEachIndexed { index, uri ->
                val bitmap = BitmapUtils.decodeSafe(resolver, uri, MAX_BITMAP_DIMENSION)
                try {
                    val pageWidth = if (pageFormat == PageFormat.Original) bitmap.width else pageFormat.width
                    val pageHeight = if (pageFormat == PageFormat.Original) bitmap.height else pageFormat.height
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    try {
                        val safeMargin = marginPx.coerceAtLeast(0).coerceAtMost(minOf(pageWidth, pageHeight) / 3)
                        val contentWidth = pageWidth - safeMargin * 2
                        val contentHeight = pageHeight - safeMargin * 2
                        val scale = minOf(contentWidth.toFloat() / bitmap.width, contentHeight.toFloat() / bitmap.height)
                        val drawWidth = bitmap.width * scale
                        val drawHeight = bitmap.height * scale
                        val left = (pageWidth - drawWidth) / 2f
                        val top = (pageHeight - drawHeight) / 2f
                        page.canvas.drawBitmap(bitmap, null, android.graphics.RectF(left, top, left + drawWidth, top + drawHeight), null)
                    } finally {
                        pdfDocument.finishPage(page)
                    }
                } finally {
                    bitmap.recycle()
                }
                onProgress(index + 1, imageUris.size)
            }

            val outputFile = File(outputDir, "$fileName.pdf")
            FileOutputStream(outputFile).use { output ->
                pdfDocument.writeTo(output)
            }
            return outputFile
        } finally {
            pdfDocument.close()
        }
    }
}

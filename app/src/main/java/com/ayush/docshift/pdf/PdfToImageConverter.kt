package com.ayush.docshift.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import com.ayush.docshift.util.PageRangeParser
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
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
        val renderer = try {
            PdfRenderer(pfd)
        } catch (error: Throwable) {
            pfd.close()
            throw error
        }
        var sessionDir: File? = null
        var completed = false
        try {
            val pageCount = renderer.pageCount
            val requestedPages = pageRange ?: (1..pageCount)
            if (pageRange != null) {
                require(PageRangeParser.isWithinPageCount(pageRange, pageCount)) {
                    "Page range must be between 1 and $pageCount"
                }
            }
            val pageIndexes = requestedPages.map { it - 1 }
            require(pageIndexes.isNotEmpty()) { "Choose at least one page to export" }

            // Keep each conversion isolated so retries cannot overwrite previous results.
            val workingDir = File(outputDir, "conversion_${UUID.randomUUID()}")
            check(workingDir.mkdirs()) { "Unable to create conversion output directory" }
            sessionDir = workingDir

            val renderScale = (dpi.coerceIn(72, 300) / 100f)
            for ((progressIndex, i) in pageIndexes.withIndex()) {
                coroutineContext.ensureActive()
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
                        val outFile = File(workingDir, "page_${i + 1}.${format.extension}")
                        FileOutputStream(outFile).use { output ->
                            val bitmapFormat = if (format == OutputFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                            check(bitmap.compress(bitmapFormat, if (format == OutputFormat.PNG) 100 else 95, output)) {
                                "Unable to encode page ${i + 1}"
                            }
                        }
                    } finally {
                        bitmap.recycle()
                    }
                } finally {
                    page.close()
                }
                onProgress(progressIndex + 1, pageIndexes.size)
            }
            completed = true
            return pageIndexes.map { File(workingDir, "page_${it + 1}.${format.extension}") }
        } finally {
            try {
                renderer.close()
            } finally {
                try {
                    pfd.close()
                } finally {
                    if (!completed) sessionDir?.deleteRecursively()
                }
            }
        }
    }
}

package com.ayush.docshift.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

object PdfCompressor {
    enum class CompressionMode(
        val label: String,
        val description: String,
        val minQuality: Int,
        val maxRenderDimension: Int
    ) {
        Maximum("Maximum compression", "Smallest file, lower image quality", 15, 1400),
        Balanced("Balanced", "Good size reduction with good image quality", 30, 1800),
        HighQuality("High quality", "Larger file with sharper images", 50, 2600),
        ImagesOnly("Image optimized", "Prioritize image quality", 65, 3200)
    }

    private const val MAX_JPEG_QUALITY = 95
    private const val MAX_COMPRESSION_PASSES = 28

    fun compressToTarget(
        context: Context,
        pdfUri: Uri,
        targetKb: Int,
        outputDir: File,
        mode: CompressionMode,
        onProgress: (Int, Int) -> Unit
    ): File {
        require(targetKb > 0) { "Target size must be greater than 0" }
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw IllegalStateException("Unable to create output directory")
        }

        val targetBytes = targetKb.toLong() * 1024L
        val source = renderPages(context, pdfUri, mode, onProgress)

        try {
            var quality = MAX_JPEG_QUALITY
            var scale = 1f

            for (pass in 0 until MAX_COMPRESSION_PASSES) {
                val data = buildPdf(source, quality, scale)

                if (data.size.toLong() <= targetBytes) {
                    val exact = padPdfToExactSize(data, targetBytes)
                    val output = File(
                        outputDir,
                        "DocShift_compressed_${System.currentTimeMillis()}.pdf"
                    )
                    FileOutputStream(output).use { it.write(exact) }
                    return output
                }

                if (quality > mode.minQuality) {
                    quality = max(mode.minQuality, quality - 5)
                } else {
                    scale *= 0.90f
                }
            }

            throw IllegalArgumentException(
                "This PDF cannot reach " + targetKb + " KB in " + mode.label + " mode."
            )
        } finally {
            source.forEach { it.bitmap.recycle() }
        }
    }

    private data class PageBitmap(
        val bitmap: Bitmap,
        val widthPoints: Int,
        val heightPoints: Int
    )

    private fun renderPages(
        context: Context,
        uri: Uri,
        mode: CompressionMode,
        onProgress: (Int, Int) -> Unit
    ): List<PageBitmap> {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalArgumentException("Cannot open PDF")
        val renderer = PdfRenderer(pfd)
        val pages = mutableListOf<PageBitmap>()

        try {
            val total = renderer.pageCount

            for (index in 0 until total) {
                val page = renderer.openPage(index)
                try {
                    val scale = min(
                        1f,
                        mode.maxRenderDimension.toFloat() / max(page.width, page.height)
                    )
                    val width = max(1, (page.width * scale).toInt())
                    val height = max(1, (page.height * scale).toInt())

                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)

                    page.render(
                        bitmap,
                        null,
                        Matrix().apply { setScale(scale, scale) },
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )

                    pages += PageBitmap(bitmap, page.width, page.height)
                } finally {
                    page.close()
                }
                onProgress(index + 1, total)
            }

            return pages
        } catch (e: Exception) {
            pages.forEach { it.bitmap.recycle() }
            throw e
        } finally {
            renderer.close()
            pfd.close()
        }
    }

    private fun buildPdf(pages: List<PageBitmap>, quality: Int, scale: Float): ByteArray {
        val document = PdfDocument()

        try {
            pages.forEachIndexed { index, page ->
                val width = max(1, (page.bitmap.width * scale).toInt())
                val height = max(1, (page.bitmap.height * scale).toInt())

                val scaled = if (scale == 1f) {
                    page.bitmap
                } else {
                    Bitmap.createScaledBitmap(page.bitmap, width, height, true)
                }

                val compressed = ByteArrayOutputStream().use { stream ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                    stream.toByteArray()
                }

                val compressedBitmap = android.graphics.BitmapFactory.decodeByteArray(
                    compressed, 0, compressed.size
                ) ?: throw IllegalStateException("Unable to compress PDF page")

                try {
                    val info = PdfDocument.PageInfo.Builder(
                        page.widthPoints, page.heightPoints, index + 1
                    ).create()
                    val outputPage = document.startPage(info)

                    try {
                        outputPage.canvas.drawColor(Color.WHITE)
                        outputPage.canvas.drawBitmap(
                            compressedBitmap,
                            null,
                            android.graphics.RectF(
                                0f, 0f,
                                page.widthPoints.toFloat(),
                                page.heightPoints.toFloat()
                            ),
                            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
                        )
                    } finally {
                        document.finishPage(outputPage)
                    }
                } finally {
                    compressedBitmap.recycle()
                    if (scaled !== page.bitmap) scaled.recycle()
                }
            }

            val output = ByteArrayOutputStream()
            document.writeTo(output)
            return output.toByteArray()
        } finally {
            document.close()
        }
    }

    private fun padPdfToExactSize(pdf: ByteArray, targetBytes: Long): ByteArray {
        require(pdf.size.toLong() <= targetBytes)
        if (pdf.size.toLong() == targetBytes) return pdf

        val eof = byteArrayOf(
            '%'.code.toByte(), '%'.code.toByte(), 'E'.code.toByte(),
            'O'.code.toByte(), 'F'.code.toByte()
        )

        var eofIndex = -1
        for (index in pdf.size - eof.size downTo 0) {
            if (eof.indices.all { offset -> pdf[index + offset] == eof[offset] }) {
                eofIndex = index
                break
            }
        }

        require(eofIndex >= 0) { "Generated PDF has no EOF marker" }

        val padding = (targetBytes - pdf.size).toInt()
        val result = ByteArray(targetBytes.toInt())
        System.arraycopy(pdf, 0, result, 0, eofIndex)
        for (index in eofIndex until eofIndex + padding) result[index] = 0x20
        System.arraycopy(pdf, eofIndex, result, eofIndex + padding, pdf.size - eofIndex)
        return result
    }
}

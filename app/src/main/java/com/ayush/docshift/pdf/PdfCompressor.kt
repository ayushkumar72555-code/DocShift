package com.ayush.docshift.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
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
    private const val SCALE_STEP = 0.90f
    private const val MAX_SCALE_PASSES = 12

    private data class PageInfo(
        val widthPoints: Int,
        val heightPoints: Int,
        val renderWidth: Int,
        val renderHeight: Int
    )

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
        val sourceSize = context.contentResolver.openAssetFileDescriptor(pdfUri, "r")?.use { it.length } ?: -1L

        if (sourceSize >= 0L && sourceSize <= targetBytes) {
            val output = File(
                outputDir,
                "DocShift_compressed_" + System.currentTimeMillis() + ".pdf"
            )
            val source = File.createTempFile("docshift_source_", ".pdf", context.cacheDir)
            try {
                context.contentResolver.openInputStream(pdfUri)?.use { input ->
                    FileOutputStream(source).use { outputStream -> input.copyTo(outputStream) }
                } ?: throw IllegalArgumentException("Cannot open PDF")
                writeExactSizePdf(source, output, targetBytes)
                onProgress(1, 1)
                return output
            } finally {
                source.delete()
            }
        }

        val pages = inspectPages(context, pdfUri, mode)
        if (pages.isEmpty()) throw IllegalArgumentException("PDF has no pages")

        var scale = 1f
        var lastCandidate: File? = null

        try {
            repeat(MAX_SCALE_PASSES) { pass ->
                val candidate = findBestQualityAtScale(
                    context,
                    pdfUri,
                    pages,
                    scale,
                    targetBytes,
                    mode,
                    if (pass == 0) onProgress else { _, _ -> }
                )

                if (candidate != null) {
                    lastCandidate?.delete()
                    lastCandidate = candidate

                    val output = File(
                        outputDir,
                        "DocShift_compressed_" + System.currentTimeMillis() + ".pdf"
                    )
                    writeExactSizePdf(candidate, output, targetBytes)
                    candidate.delete()
                    lastCandidate = null
                    return output
                }

                scale *= SCALE_STEP
            }

            throw IllegalArgumentException(
                "This PDF cannot reach " + targetKb + " KB in " + mode.label +
                    " mode. Try a larger target size or a stronger compression mode."
            )
        } finally {
            lastCandidate?.delete()
        }
    }

    private fun inspectPages(
        context: Context,
        uri: Uri,
        mode: CompressionMode
    ): List<PageInfo> {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalArgumentException("Cannot open PDF")
        val renderer = PdfRenderer(pfd)

        try {
            return buildList(renderer.pageCount) {
                for (index in 0 until renderer.pageCount) {
                    val page = renderer.openPage(index)
                    try {
                        val renderScale = min(
                            1f,
                            mode.maxRenderDimension.toFloat() / max(page.width, page.height)
                        )
                        add(
                            PageInfo(
                                page.width,
                                page.height,
                                max(1, (page.width * renderScale).toInt()),
                                max(1, (page.height * renderScale).toInt())
                            )
                        )
                    } finally {
                        page.close()
                    }
                }
            }
        } finally {
            renderer.close()
            pfd.close()
        }
    }

    private fun findBestQualityAtScale(
        context: Context,
        pdfUri: Uri,
        pages: List<PageInfo>,
        scale: Float,
        targetBytes: Long,
        mode: CompressionMode,
        onProgress: (Int, Int) -> Unit
    ): File? {
        var low = mode.minQuality
        var high = MAX_JPEG_QUALITY
        var best: File? = null

        while (low <= high) {
            val quality = (low + high) / 2
            val candidate = buildPdfFile(
                context, pdfUri, pages, scale, quality, onProgress
            )

            if (candidate.length() <= targetBytes) {
                best?.delete()
                best = candidate
                low = quality + 1
            } else {
                candidate.delete()
                high = quality - 1
            }
        }

        return best
    }

    private fun buildPdfFile(
        context: Context,
        pdfUri: Uri,
        pages: List<PageInfo>,
        scale: Float,
        quality: Int,
        onProgress: (Int, Int) -> Unit
    ): File {
        val temp = File.createTempFile("docshift_pdf_", ".pdf", context.cacheDir)
        val pfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
            ?: throw IllegalArgumentException("Cannot open PDF")
        val renderer = PdfRenderer(pfd)
        val document = PdfDocument()

        try {
            for (index in pages.indices) {
                val sourcePage = renderer.openPage(index)
                var bitmap: Bitmap? = null

                try {
                    val width = max(1, (pages[index].renderWidth * scale).toInt())
                    val height = max(1, (pages[index].renderHeight * scale).toInt())

                    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)

                    val scaleX = width.toFloat() / sourcePage.width.toFloat()
                    val scaleY = height.toFloat() / sourcePage.height.toFloat()

                    sourcePage.render(
                        bitmap,
                        null,
                        Matrix().apply { setScale(scaleX, scaleY) },
                        PdfRenderer.Page.RENDER_MODE_FOR_PRINT
                    )

                    val pageInfo = PdfDocument.PageInfo.Builder(
                        pages[index].widthPoints,
                        pages[index].heightPoints,
                        index + 1
                    ).create()

                    val outputPage = document.startPage(pageInfo)
                    try {
                        outputPage.canvas.drawColor(Color.WHITE)
                        outputPage.canvas.drawBitmap(
                            bitmap,
                            null,
                            RectF(
                                0f,
                                0f,
                                pages[index].widthPoints.toFloat(),
                                pages[index].heightPoints.toFloat()
                            ),
                            Paint(Paint.FILTER_BITMAP_FLAG).apply { isDither = true }
                        )
                    } finally {
                        document.finishPage(outputPage)
                    }
                } finally {
                    bitmap?.recycle()
                    sourcePage.close()
                }

                onProgress(index + 1, pages.size)
            }

            FileOutputStream(temp).use { output -> document.writeTo(output) }
            return temp
        } catch (e: Exception) {
            temp.delete()
            throw e
        } finally {
            document.close()
            renderer.close()
            pfd.close()
        }
    }

    private fun writeExactSizePdf(source: File, destination: File, targetBytes: Long) {
        require(source.length() <= targetBytes) {
            "Internal error: generated PDF is larger than the target"
        }

        if (source.length() == targetBytes) {
            source.copyTo(destination, overwrite = true)
            return
        }

        val eofOffset = findLastEofOffset(source)
        require(eofOffset >= 0) { "Generated PDF has no EOF marker" }

        val paddingBytes = targetBytes - source.length()

        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                copyExactly(input, output, eofOffset)

                val buffer = ByteArray(8192) { 0x20 }
                var remaining = paddingBytes
                while (remaining > 0) {
                    val count = min(remaining, buffer.size.toLong()).toInt()
                    output.write(buffer, 0, count)
                    remaining -= count
                }

                input.skipNBytes(5)
                input.copyTo(output)
            }
        }

        check(destination.length() == targetBytes) {
            "Unable to create an exact target-size PDF"
        }
    }

    private fun findLastEofOffset(file: File): Long {
        RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            val searchStart = max(0L, length - 4096L)
            raf.seek(searchStart)

            val data = ByteArray((length - searchStart).toInt())
            raf.readFully(data)

            val marker = byteArrayOf(
                '%'.code.toByte(), '%'.code.toByte(),
                'E'.code.toByte(), 'O'.code.toByte(), 'F'.code.toByte()
            )

            for (i in data.size - marker.size downTo 0) {
                if (marker.indices.all { data[i + it] == marker[it] }) {
                    return searchStart + i
                }
            }
        }
        return -1L
    }

    private fun copyExactly(input: FileInputStream, output: FileOutputStream, bytes: Long) {
        val buffer = ByteArray(8192)
        var remaining = bytes

        while (remaining > 0) {
            val count = min(remaining, buffer.size.toLong()).toInt()
            val read = input.read(buffer, 0, count)
            if (read < 0) throw IllegalStateException("Unexpected end of PDF")
            output.write(buffer, 0, read)
            remaining -= read
        }
    }
}

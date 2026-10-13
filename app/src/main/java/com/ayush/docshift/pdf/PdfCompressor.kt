package com.ayush.docshift.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlin.math.max
import kotlin.math.min

object PdfCompressor {
    private fun copyCancellable(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        checkCancellation: () -> Unit
    ) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            checkCancellation()
            val bytesRead = input.read(buffer)
            if (bytesRead < 0) break
            output.write(buffer, 0, bytesRead)
        }
        output.flush()
    }

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

    suspend fun compressToTarget(
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
        val sessionDir = File(outputDir, "compression_${UUID.randomUUID()}")
        check(sessionDir.mkdirs()) { "Unable to create temporary compression directory" }
        val sourceFile = File(sessionDir, "source.pdf")
        val outputFile = File(outputDir, "DocShift_compressed_${UUID.randomUUID()}.pdf")
        val job = coroutineContext[Job]
        val checkCancellation: () -> Unit = { job?.ensureActive() }
        var completed = false

        try {
            checkCancellation()
            val input = context.contentResolver.openInputStream(pdfUri)
                ?: throw IllegalArgumentException("Cannot open PDF")
            input.use { stream ->
                FileOutputStream(sourceFile).use { output ->
                    copyCancellable(stream, output, checkCancellation)
                }
            }

            // Never degrade a document that already satisfies the requested maximum.
            if (sourceFile.length() <= targetBytes) {
                sourceFile.inputStream().use { inputStream ->
                    outputFile.outputStream().use { outputStream ->
                        copyCancellable(inputStream, outputStream, checkCancellation)
                    }
                }
                onProgress(1, 1)
                completed = true
                return outputFile
            }

            var quality = MAX_JPEG_QUALITY
            var scale = 1f

            for (pass in 0 until MAX_COMPRESSION_PASSES) {
                buildPdf(
                    sourceFile = sourceFile,
                    outputFile = outputFile,
                    mode = mode,
                    quality = quality,
                    scale = scale,
                    onProgress = if (pass == 0) onProgress else null,
                    checkCancellation = checkCancellation
                )

                if (outputFile.length() <= targetBytes) {
                    completed = true
                    return outputFile
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
            sourceFile.delete()
            sessionDir.deleteRecursively()
            if (!completed) outputFile.delete()
        }
    }

    /**
     * Re-renders one page at a time on every compression pass. This avoids keeping
     * every page bitmap alive simultaneously and writes directly to a file rather
     * than materializing the complete PDF as a byte array.
     */
    private fun buildPdf(
        sourceFile: File,
        outputFile: File,
        mode: CompressionMode,
        quality: Int,
        scale: Float,
        onProgress: ((Int, Int) -> Unit)?,
        checkCancellation: () -> Unit
    ) {
        if (outputFile.exists() && !outputFile.delete()) {
            throw IOException("Unable to replace temporary compressed PDF")
        }

        val descriptor = ParcelFileDescriptor.open(
            sourceFile,
            ParcelFileDescriptor.MODE_READ_ONLY
        )
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (error: Exception) {
            descriptor.close()
            throw error
        }
        val document = PdfDocument()

        try {
            checkCancellation()
            val totalPages = renderer.pageCount
            require(totalPages > 0) { "The selected PDF has no pages" }

            for (index in 0 until totalPages) {
                checkCancellation()
                val page = renderer.openPage(index)
                try {
                    val baseScale = min(
                        1f,
                        mode.maxRenderDimension.toFloat() / max(page.width, page.height)
                    )
                    val renderScale = (baseScale * scale).coerceAtLeast(0.01f)
                    val bitmapWidth = max(1, (page.width * renderScale).toInt())
                    val bitmapHeight = max(1, (page.height * renderScale).toInt())
                    val bitmap = Bitmap.createBitmap(
                        bitmapWidth,
                        bitmapHeight,
                        Bitmap.Config.ARGB_8888
                    )

                    try {
                        bitmap.eraseColor(Color.WHITE)
                        page.render(
                            bitmap,
                            null,
                            Matrix().apply { setScale(renderScale, renderScale) },
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )

                        val jpegBytes = java.io.ByteArrayOutputStream().use { stream ->
                            check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)) {
                                "Unable to compress PDF page"
                            }
                            stream.toByteArray()
                        }
                        val compressedBitmap = BitmapFactory.decodeByteArray(
                            jpegBytes,
                            0,
                            jpegBytes.size
                        ) ?: throw IllegalStateException("Unable to decode compressed PDF page")

                        try {
                            val pageInfo = PdfDocument.PageInfo.Builder(
                                page.width,
                                page.height,
                                index + 1
                            ).create()
                            val outputPage = document.startPage(pageInfo)
                            try {
                                outputPage.canvas.drawColor(Color.WHITE)
                                outputPage.canvas.drawBitmap(
                                    compressedBitmap,
                                    null,
                                    RectF(
                                        0f,
                                        0f,
                                        page.width.toFloat(),
                                        page.height.toFloat()
                                    ),
                                    Paint(Paint.FILTER_BITMAP_FLAG)
                                )
                            } finally {
                                document.finishPage(outputPage)
                            }
                        } finally {
                            compressedBitmap.recycle()
                        }
                    } finally {
                        bitmap.recycle()
                    }
                } finally {
                    page.close()
                }
                onProgress?.invoke(index + 1, totalPages)
            }

            FileOutputStream(outputFile).use { output ->
                document.writeTo(output)
            }
        } finally {
            try {
                document.close()
            } finally {
                try {
                    renderer.close()
                } finally {
                    descriptor.close()
                }
            }
        }
    }
}

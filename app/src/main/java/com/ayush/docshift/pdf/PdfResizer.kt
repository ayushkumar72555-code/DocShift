package com.ayush.docshift.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object PdfResizer {
    private const val MAX_RENDER_DIMENSION = 4096

    data class PageDimensions(val widthPoints: Int, val heightPoints: Int)

    enum class ResizeUnit { Pixels, Centimeters, Inches }

    fun readFirstPageDimensions(context: Context, pdfUri: Uri): PageDimensions {
        val pfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
            ?: throw IllegalArgumentException("Cannot open PDF")
        val renderer = PdfRenderer(pfd)
        try {
            require(renderer.pageCount > 0) { "PDF has no pages" }
            val page = renderer.openPage(0)
            return try {
                PageDimensions(page.width, page.height)
            } finally {
                page.close()
            }
        } finally {
            renderer.close()
            pfd.close()
        }
    }

    fun toPoints(valueInput: String, unit: ResizeUnit, dpi: Int): Int? {
        val value = valueInput.toFloatOrNull()?.takeIf { it > 0f } ?: return null
        require(dpi > 0) { "DPI must be greater than 0" }

        val points = when (unit) {
            ResizeUnit.Pixels -> value / dpi * 72f
            ResizeUnit.Inches -> value * 72f
            ResizeUnit.Centimeters -> value / 2.54f * 72f
        }

        return points.roundToInt().coerceAtLeast(1)
    }

    fun calculateTargetSize(
        originalWidthPoints: Int,
        originalHeightPoints: Int,
        requestedWidthPoints: Int?,
        requestedHeightPoints: Int?,
        maintainAspectRatio: Boolean
    ): PageDimensions {
        require(requestedWidthPoints != null || requestedHeightPoints != null) {
            "Enter width or height"
        }

        if (!maintainAspectRatio) {
            return PageDimensions(
                requestedWidthPoints ?: originalWidthPoints,
                requestedHeightPoints ?: originalHeightPoints
            )
        }

        if (requestedWidthPoints == null) {
            return PageDimensions(
                max(1, (requestedHeightPoints!! * originalWidthPoints.toFloat() / originalHeightPoints).roundToInt()),
                requestedHeightPoints
            )
        }

        if (requestedHeightPoints == null) {
            return PageDimensions(
                requestedWidthPoints,
                max(1, (requestedWidthPoints * originalHeightPoints.toFloat() / originalWidthPoints).roundToInt())
            )
        }

        val scale = min(
            requestedWidthPoints.toFloat() / originalWidthPoints,
            requestedHeightPoints.toFloat() / originalHeightPoints
        )

        return PageDimensions(
            max(1, (originalWidthPoints * scale).roundToInt()),
            max(1, (originalHeightPoints * scale).roundToInt())
        )
    }

    fun resize(
        context: Context,
        pdfUri: Uri,
        outputDir: File,
        widthInput: String,
        heightInput: String,
        unit: ResizeUnit,
        dpi: Int,
        maintainAspectRatio: Boolean,
        onProgress: (current: Int, total: Int) -> Unit
    ): File {
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw IllegalStateException("Unable to create output directory")
        }

        val original = readFirstPageDimensions(context, pdfUri)
        val target = calculateTargetSize(
            original.widthPoints,
            original.heightPoints,
            toPoints(widthInput, unit, dpi),
            toPoints(heightInput, unit, dpi),
            maintainAspectRatio
        )

        val pfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
            ?: throw IllegalArgumentException("Cannot open PDF")
        val renderer = PdfRenderer(pfd)
        val document = PdfDocument()

        try {
            val totalPages = renderer.pageCount
            for (index in 0 until totalPages) {
                val sourcePage = renderer.openPage(index)
                try {
                    val pageBitmapSize = renderSize(target.widthPoints, target.heightPoints, dpi)
                    val bitmap = Bitmap.createBitmap(
                        pageBitmapSize.first,
                        pageBitmapSize.second,
                        Bitmap.Config.ARGB_8888
                    )

                    try {
                        bitmap.eraseColor(Color.WHITE)

                        val scale = min(
                            pageBitmapSize.first.toFloat() / sourcePage.width,
                            pageBitmapSize.second.toFloat() / sourcePage.height
                        )
                        val contentWidth = sourcePage.width * scale
                        val contentHeight = sourcePage.height * scale
                        val left = (pageBitmapSize.first - contentWidth) / 2f
                        val top = (pageBitmapSize.second - contentHeight) / 2f

                        val matrix = Matrix().apply {
                            setScale(scale, scale)
                            postTranslate(left, top)
                        }

                        sourcePage.render(
                            bitmap,
                            null,
                            matrix,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )

                        val pageInfo = PdfDocument.PageInfo.Builder(
                            target.widthPoints,
                            target.heightPoints,
                            index + 1
                        ).create()

                        val outputPage = document.startPage(pageInfo)
                        try {
                            outputPage.canvas.drawBitmap(
                                bitmap,
                                null,
                                android.graphics.RectF(
                                    0f,
                                    0f,
                                    target.widthPoints.toFloat(),
                                    target.heightPoints.toFloat()
                                ),
                                null
                            )
                        } finally {
                            document.finishPage(outputPage)
                        }
                    } finally {
                        bitmap.recycle()
                    }
                } finally {
                    sourcePage.close()
                }

                onProgress(index + 1, totalPages)
            }

            val outputFile = File(
                outputDir,
                "DocShift_resize_${System.currentTimeMillis()}.pdf"
            )
            FileOutputStream(outputFile).use { output ->
                document.writeTo(output)
            }
            return outputFile
        } finally {
            document.close()
            renderer.close()
            pfd.close()
        }
    }

    private fun renderSize(widthPoints: Int, heightPoints: Int, dpi: Int): Pair<Int, Int> {
        val width = max(1, (widthPoints / 72f * dpi).roundToInt())
        val height = max(1, (heightPoints / 72f * dpi).roundToInt())
        val scale = min(
            1f,
            MAX_RENDER_DIMENSION.toFloat() / max(width, height)
        )
        return Pair(
            max(1, (width * scale).roundToInt()),
            max(1, (height * scale).roundToInt())
        )
    }
}

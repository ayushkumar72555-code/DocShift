package com.ayush.docshift.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.ayush.docshift.util.BitmapUtils
import java.io.FileNotFoundException
import kotlin.math.max
import kotlin.math.roundToInt

object ImageResizer {
    data class ImageDimensions(val width: Int, val height: Int)

    enum class ResizeUnit { Pixels, Centimeters, Inches }

    fun readDimensions(resolver: ContentResolver, uri: Uri): ImageDimensions {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }

        try {
            resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
            if (options.outWidth > 0 && options.outHeight > 0) {
                return ImageDimensions(options.outWidth, options.outHeight)
            }
        } catch (_: FileNotFoundException) {
            // Fall through to file descriptor access.
        } catch (_: SecurityException) {
            // Fall through to file descriptor access.
        }

        options.outWidth = 0
        options.outHeight = 0

        try {
            resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                BitmapFactory.decodeFileDescriptor(
                    descriptor.fileDescriptor,
                    null,
                    options
                )
            }
            if (options.outWidth > 0 && options.outHeight > 0) {
                return ImageDimensions(options.outWidth, options.outHeight)
            }
        } catch (_: FileNotFoundException) {
            // Handled below.
        } catch (_: SecurityException) {
            // Handled below.
        }

        throw IllegalStateException(
            "Unable to read image. The selected file could not be decoded."
        )
    }

    fun resize(
        resolver: ContentResolver,
        uri: Uri,
        widthInput: String,
        heightInput: String,
        unit: ResizeUnit,
        dpi: Int,
        maintainAspectRatio: Boolean
    ): Bitmap {
        require(dpi > 0) { "DPI must be greater than 0" }

        val original = readDimensions(resolver, uri)
        val target = calculateTargetSize(
            original.width,
            original.height,
            toPixels(widthInput, unit, dpi),
            toPixels(heightInput, unit, dpi),
            maintainAspectRatio
        )

        val bitmap = BitmapUtils.decodeSafe(
            resolver,
            uri,
            max(target.width, target.height)
        )

        if (bitmap.width == target.width && bitmap.height == target.height) return bitmap

        return Bitmap.createScaledBitmap(bitmap, target.width, target.height, true).also {
            bitmap.recycle()
        }
    }

    fun toPixels(valueInput: String, unit: ResizeUnit, dpi: Int): Int? =
        valueInput.toFloatOrNull()
            ?.takeIf { it > 0f && dpi > 0 }
            ?.let { value ->
                when (unit) {
                    ResizeUnit.Pixels -> value.roundToInt()
                    ResizeUnit.Inches -> (value * dpi).roundToInt()
                    ResizeUnit.Centimeters -> (value / 2.54f * dpi).roundToInt()
                }
            }
            ?.let { max(1, it) }

    private fun calculateTargetSize(
        originalWidth: Int,
        originalHeight: Int,
        requestedWidth: Int?,
        requestedHeight: Int?,
        maintainAspectRatio: Boolean
    ): ImageDimensions {
        require(requestedWidth != null || requestedHeight != null) {
            "Enter width or height"
        }

        if (!maintainAspectRatio) {
            return ImageDimensions(
                requestedWidth ?: originalWidth,
                requestedHeight ?: originalHeight
            )
        }

        if (requestedWidth == null) {
            return ImageDimensions(
                max(1, (requestedHeight!! * originalWidth.toFloat() / originalHeight).roundToInt()),
                requestedHeight
            )
        }

        if (requestedHeight == null) {
            return ImageDimensions(
                requestedWidth,
                max(1, (requestedWidth * originalHeight.toFloat() / originalWidth).roundToInt())
            )
        }

        val scale = minOf(
            requestedWidth.toFloat() / originalWidth,
            requestedHeight.toFloat() / originalHeight
        )

        return ImageDimensions(
            max(1, (originalWidth * scale).roundToInt()),
            max(1, (originalHeight * scale).roundToInt())
        )
    }
}

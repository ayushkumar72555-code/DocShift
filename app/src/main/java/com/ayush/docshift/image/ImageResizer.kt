package com.ayush.docshift.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlin.math.max
import kotlin.math.roundToInt

object ImageResizer {

    data class ImageDimensions(
        val width: Int,
        val height: Int
    )

    enum class ResizeUnit {
        Pixels,
        Centimeters,
        Inches
    }

    fun readDimensions(
        resolver: ContentResolver,
        uri: Uri
    ): ImageDimensions {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }

        return ImageDimensions(
            width = options.outWidth,
            height = options.outHeight
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
        val originalBitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: error("Unable to read image")

        val requestedWidth = parseDimension(widthInput, unit, dpi)
        val requestedHeight = parseDimension(heightInput, unit, dpi)

        val target = calculateTargetSize(
            originalWidth = originalBitmap.width,
            originalHeight = originalBitmap.height,
            requestedWidth = requestedWidth,
            requestedHeight = requestedHeight,
            maintainAspectRatio = maintainAspectRatio
        )

        return Bitmap.createScaledBitmap(
            originalBitmap,
            target.width,
            target.height,
            true
        )
    }

    fun toPixels(valueInput: String, unit: ResizeUnit, dpi: Int): Int? =
        valueInput.toFloatOrNull()
            ?.takeIf { it > 0f }
            ?.let { value ->
                when (unit) {
                    ResizeUnit.Pixels -> value.roundToInt()
                    ResizeUnit.Inches -> (value * dpi).roundToInt()
                    ResizeUnit.Centimeters -> (value / 2.54f * dpi).roundToInt()
                }
            }
            ?.let { max(1, it) }

    private fun parseDimension(valueInput: String, unit: ResizeUnit, dpi: Int): Int? =
        toPixels(valueInput, unit, dpi)

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
                width = requestedWidth ?: originalWidth,
                height = requestedHeight ?: originalHeight
            )
        }

        val aspectRatio = originalWidth.toFloat() / originalHeight.toFloat()

        return when {
            requestedWidth != null -> ImageDimensions(
                width = requestedWidth,
                height = max(1, (requestedWidth / aspectRatio).roundToInt())
            )

            requestedHeight != null -> ImageDimensions(
                width = max(1, (requestedHeight * aspectRatio).roundToInt()),
                height = requestedHeight
            )

            else -> ImageDimensions(originalWidth, originalHeight)
        }
    }
}


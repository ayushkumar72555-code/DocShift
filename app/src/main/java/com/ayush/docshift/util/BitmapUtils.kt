package com.ayush.docshift.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlin.math.max

object BitmapUtils {
    fun decodeSafe(resolver: ContentResolver, uri: Uri, maxDimension: Int = 4096): Bitmap {
        require(maxDimension > 0) { "maxDimension must be greater than 0" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IllegalStateException("Unable to open image input stream")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalStateException("Unable to determine image dimensions")
        }
        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
            inDither = false
        }
        resolver.openInputStream(uri)?.use {
            return BitmapFactory.decodeStream(it, null, options)
                ?: throw IllegalStateException("Failed to decode bitmap")
        } ?: throw IllegalStateException("Unable to open image input stream")
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        val largestDimension = max(width, height)
        if (largestDimension <= maxDimension) return 1
        var sampleSize = 1
        while (largestDimension / sampleSize > maxDimension) sampleSize *= 2
        return max(1, sampleSize)
    }
}

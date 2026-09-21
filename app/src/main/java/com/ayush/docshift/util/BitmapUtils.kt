package com.ayush.docshift.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.FileNotFoundException
import kotlin.math.max

object BitmapUtils {
    fun decodeSafe(resolver: ContentResolver, uri: Uri, maxDimension: Int = 4096): Bitmap {
        require(maxDimension > 0) { "maxDimension must be greater than 0" }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsDecoded = tryDecodeBounds(resolver, uri, bounds)
        if (!boundsDecoded) {
            throw IllegalStateException("Unable to open image input stream")
        }
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

        try {
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)?.let { return it }
            }
        } catch (_: FileNotFoundException) {
            // Fall through to file descriptor access.
        } catch (_: SecurityException) {
            // Fall through to file descriptor access.
        }

        try {
            resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                BitmapFactory.decodeFileDescriptor(
                    descriptor.fileDescriptor,
                    null,
                    options
                )?.let { return it }
            }
        } catch (_: FileNotFoundException) {
            // Handled below with a stable error message.
        } catch (_: SecurityException) {
            // Handled below with a stable error message.
        }

        throw IllegalStateException("Unable to decode the selected image")
    }

    private fun tryDecodeBounds(
        resolver: ContentResolver,
        uri: Uri,
        options: BitmapFactory.Options
    ): Boolean {
        try {
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
                if (options.outWidth > 0 && options.outHeight > 0) return true
            }
        } catch (_: FileNotFoundException) {
            // Try the descriptor path.
        } catch (_: SecurityException) {
            // Try the descriptor path.
        }

        try {
            resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                BitmapFactory.decodeFileDescriptor(
                    descriptor.fileDescriptor,
                    null,
                    options
                )
                if (options.outWidth > 0 && options.outHeight > 0) return true
            }
        } catch (_: FileNotFoundException) {
            return false
        } catch (_: SecurityException) {
            return false
        }

        return false
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        val largestDimension = max(width, height)
        if (largestDimension <= maxDimension) return 1
        var sampleSize = 1
        while (largestDimension / sampleSize > maxDimension) sampleSize *= 2
        return max(1, sampleSize)
    }
}

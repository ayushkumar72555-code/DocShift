package com.ayush.docshift.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import com.ayush.docshift.util.BitmapUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object ImageCompressor {
    private const val MAX_DECODE_DIMENSION = 4096
    private const val MIN_QUALITY = 5
    private const val MAX_QUALITY = 100

    fun compressToTarget(
        resolver: ContentResolver,
        uri: Uri,
        targetKb: Int,
        cacheDir: File
    ): File {
        require(targetKb > 0) { "Target size must be greater than 0" }
        val bitmap = BitmapUtils.decodeSafe(resolver, uri, MAX_DECODE_DIMENSION)
        return compressBitmapToTarget(bitmap, targetKb, cacheDir, "DocShift")
    }

    fun compressBitmapToTarget(
        bitmap: Bitmap,
        targetKb: Int,
        cacheDir: File,
        filePrefix: String = "DocShift"
    ): File {
        require(targetKb > 0) { "Target size must be greater than 0" }

        var currentBitmap = resizeForTarget(bitmap, targetKb)
        var bestData: ByteArray? = null
        var bestDifference = Long.MAX_VALUE

        try {
            repeat(5) {
                val data = findClosestJpeg(currentBitmap, targetKb)
                val difference = abs(data.size - targetKb * 1024L)

                if (difference < bestDifference) {
                    bestData = data
                    bestDifference = difference
                }

                if (data.size <= targetKb * 1024L) return@repeat

                val scale = min(
                    0.85f,
                    kotlin.math.sqrt(targetKb.toDouble() / (data.size / 1024.0))
                        .coerceIn(0.45, 0.85)
                        .toFloat()
                )
                val newWidth = max(1, (currentBitmap.width * scale).toInt())
                val newHeight = max(1, (currentBitmap.height * scale).toInt())

                if (newWidth == currentBitmap.width && newHeight == currentBitmap.height) {
                    return@repeat
                }

                val nextBitmap = Bitmap.createScaledBitmap(
                    currentBitmap,
                    newWidth,
                    newHeight,
                    true
                )
                if (currentBitmap !== bitmap) currentBitmap.recycle()
                currentBitmap = nextBitmap
            }

            val outFile = File(
                cacheDir,
                "${filePrefix}_${targetKb}KB_${System.currentTimeMillis()}.jpg"
            )
            FileOutputStream(outFile).use { it.write(bestData!!) }
            return outFile
        } finally {
            if (currentBitmap !== bitmap) currentBitmap.recycle()
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun findClosestJpeg(bitmap: Bitmap, targetKb: Int): ByteArray {
        var low = MIN_QUALITY
        var high = MAX_QUALITY
        var best: ByteArray? = null
        var bestDifference = Long.MAX_VALUE

        while (low <= high) {
            val quality = (low + high) / 2
            val data = ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                stream.toByteArray()
            }
            val difference = abs(data.size - targetKb * 1024L)

            if (difference < bestDifference) {
                best = data
                bestDifference = difference
            }

            if (data.size > targetKb * 1024L) high = quality - 1
            else low = quality + 1
        }

        return best!!
    }

    private fun resizeForTarget(bitmap: Bitmap, targetKb: Int): Bitmap {
        val maxDimension = when {
            targetKb <= 20 -> 600
            targetKb <= 50 -> 900
            targetKb <= 100 -> 1400
            targetKb <= 200 -> 2000
            targetKb <= 500 -> 2800
            else -> MAX_DECODE_DIMENSION
        }

        val ratio = min(
            maxDimension.toFloat() / bitmap.width,
            maxDimension.toFloat() / bitmap.height
        )
        if (ratio >= 1f) return bitmap

        return Bitmap.createScaledBitmap(
            bitmap,
            max(1, (bitmap.width * ratio).toInt()),
            max(1, (bitmap.height * ratio).toInt()),
            true
        )
    }
}

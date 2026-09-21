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
import kotlin.math.sqrt

object ImageCompressor {
    private const val MAX_DECODE_DIMENSION = 4096
    private const val MIN_QUALITY = 5
    private const val MAX_QUALITY = 100
    private const val MAX_RESIZE_PASSES = 5

    fun compressToTarget(
        resolver: ContentResolver,
        uri: Uri,
        targetKb: Int,
        cacheDir: File
    ): File {
        require(targetKb > 0) { "Target size must be greater than 0" }
        val bitmap = BitmapUtils.decodeSafe(resolver, uri, MAX_DECODE_DIMENSION)
        return try {
            compressBitmapToTarget(bitmap, targetKb, cacheDir, "DocShift")
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    fun compressBitmapToTarget(
        bitmap: Bitmap,
        targetKb: Int,
        cacheDir: File,
        filePrefix: String = "DocShift"
    ): File {
        require(targetKb > 0) { "Target size must be greater than 0" }
        require(!bitmap.isRecycled) { "Bitmap is already recycled" }

        var currentBitmap = resizeForTarget(bitmap, targetKb)
        var bestData: ByteArray? = null
        var bestDifference = Long.MAX_VALUE
        val targetBytes = targetKb.toLong() * 1024L

        try {
            repeat(MAX_RESIZE_PASSES) {
                val data = findClosestJpeg(currentBitmap, targetBytes)
                val difference = abs(data.size.toLong() - targetBytes)

                if (difference < bestDifference) {
                    bestData = data
                    bestDifference = difference
                }

                if (data.size.toLong() <= targetBytes) {
                    return writeResult(cacheDir, filePrefix, data)
                }

                val scale = sqrt(targetBytes.toDouble() / data.size.toDouble())
                    .coerceIn(0.45, 0.85)
                    .toFloat()

                val newWidth = max(1, (currentBitmap.width * scale).toInt())
                val newHeight = max(1, (currentBitmap.height * scale).toInt())

                if (newWidth == currentBitmap.width && newHeight == currentBitmap.height) {
                    return writeResult(cacheDir, filePrefix, bestData!!)
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

            return writeResult(cacheDir, filePrefix, bestData!!)
        } finally {
            if (currentBitmap !== bitmap && !currentBitmap.isRecycled) currentBitmap.recycle()
        }
    }

    private fun findClosestJpeg(bitmap: Bitmap, targetBytes: Long): ByteArray {
        var low = MIN_QUALITY
        var high = MAX_QUALITY
        var best: ByteArray? = null
        var bestDifference = Long.MAX_VALUE

        while (low <= high) {
            val quality = (low + high) / 2
            val data = compressJpeg(bitmap, quality)
            val difference = abs(data.size.toLong() - targetBytes)

            if (difference < bestDifference) {
                best = data
                bestDifference = difference
            }

            if (data.size.toLong() > targetBytes) {
                high = quality - 1
            } else {
                low = quality + 1
            }
        }

        return best ?: throw IllegalStateException("Unable to compress image")
    }

    private fun compressJpeg(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)) {
                "Unable to encode image"
            }
            stream.toByteArray()
        }

    private fun writeResult(cacheDir: File, prefix: String, data: ByteArray): File {
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw IllegalStateException("Unable to create output directory")
        }
        val outFile = File(cacheDir, "${prefix}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(outFile).use { it.write(data) }
        return outFile
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

package com.ayush.docshift.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.min

object ImageCompressor {

    fun compressToTarget(
        resolver: ContentResolver,
        uri: Uri,
        targetKb: Int,
        cacheDir: File
    ): File {

        val originalBitmap = resolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it)
        }

        // 🔴 THIS WAS MISSING
        val workingBitmap = resizeForTarget(originalBitmap, targetKb)

        return compressBitmapToTarget(
            bitmap = workingBitmap,
            targetKb = targetKb,
            cacheDir = cacheDir,
            filePrefix = "DocShift"
        )
    }

    fun compressBitmapToTarget(
        bitmap: Bitmap,
        targetKb: Int,
        cacheDir: File,
        filePrefix: String = "DocShift"
    ): File {
        require(targetKb > 0) {
            "Target size must be greater than 0"
        }

        var low = 5
        var high = 100
        var best: ByteArray? = null
        var bestDifference = Int.MAX_VALUE

        while (low <= high) {
            val mid = (low + high) / 2
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, mid, stream)
            val data = stream.toByteArray()
            val sizeKb = data.size / 1024
            val difference = abs(sizeKb - targetKb)

            if (best == null || difference < bestDifference || sizeKb <= targetKb) {
                best = data
                bestDifference = difference
            }

            if (difference <= 2) {
                break
            }

            if (sizeKb > targetKb) {
                high = mid - 1
            } else {
                low = mid + 1
            }
        }

        val outFile = File(cacheDir, "${filePrefix}_${targetKb}KB_${System.currentTimeMillis()}.jpg")
        FileOutputStream(outFile).use { it.write(best!!) }

        return outFile
    }

    // 🔴 THIS FUNCTION IS THE KEY
    private fun resizeForTarget(bitmap: Bitmap, targetKb: Int): Bitmap {

        val maxDim = when {
            targetKb <= 20 -> 300
            targetKb <= 50 -> 600
            targetKb <= 100 -> 1024
            else -> return bitmap
        }

        val ratio = min(
            maxDim.toFloat() / bitmap.width,
            maxDim.toFloat() / bitmap.height
        )

        if (ratio >= 1f) return bitmap

        val newWidth = (bitmap.width * ratio).toInt()
        val newHeight = (bitmap.height * ratio).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}

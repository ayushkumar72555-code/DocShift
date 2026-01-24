package com.ayush.aimage.image

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

        var low = 5
        var high = 100
        var best: ByteArray? = null

        while (low <= high) {
            val mid = (low + high) / 2
            val stream = ByteArrayOutputStream()
            workingBitmap.compress(Bitmap.CompressFormat.JPEG, mid, stream)
            val data = stream.toByteArray()
            val sizeKb = data.size / 1024

            if (abs(sizeKb - targetKb) <= 2) {
                best = data
                break
            }

            if (sizeKb > targetKb) {
                high = mid - 1
            } else {
                best = data
                low = mid + 1
            }
        }

        val outFile = File(cacheDir, "AImage_${targetKb}KB_${System.currentTimeMillis()}.jpg")
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

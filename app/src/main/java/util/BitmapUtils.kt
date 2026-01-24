package com.ayush.aimage.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.InputStream

object BitmapUtils {

    fun decodeSafe(
        inputStream: InputStream,
        maxDimension: Int = 4096
    ): Bitmap {
        throw NotImplementedError("Safe bitmap decoding not implemented yet")
    }
}

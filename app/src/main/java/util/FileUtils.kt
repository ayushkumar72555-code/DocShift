package com.ayush.aimage.util

import java.io.File

object FileUtils {

    fun ensureDir(dir: File): File {
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun timestampedName(prefix: String, extension: String): String {
        return "${prefix}_${System.currentTimeMillis()}.$extension"
    }
}

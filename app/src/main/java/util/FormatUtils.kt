package com.ayush.aimage.util

object FormatUtils {

    fun formatSize(bytes: Long): String {
        val kb = bytes / 1024f
        val mb = kb / 1024f
        return if (mb >= 1) "%.2f MB".format(mb) else "%.0f KB".format(kb)
    }
}

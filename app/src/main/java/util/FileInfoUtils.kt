package com.ayush.aimage.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

object FileInfoUtils {

    fun getFileName(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, null, null, null, null)?.use {
            val i = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst()) it.getString(i) else null
        }

    fun getFileSize(resolver: ContentResolver, uri: Uri): Long? =
        resolver.query(uri, null, null, null, null)?.use {
            val i = it.getColumnIndex(OpenableColumns.SIZE)
            if (it.moveToFirst()) it.getLong(i) else null
        }
}

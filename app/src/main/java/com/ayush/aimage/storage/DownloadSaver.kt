package com.ayush.aimage.storage

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object DownloadSaver {

    fun save(context: Context, file: File): Uri? {

        val extension = file.extension.lowercase()

        val mimeType = when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }

        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {

            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name) // 🔴 KEEP ORIGINAL NAME
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/AImage"
                )
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
            )

            resolver.openOutputStream(uri!!)?.use { output ->
                file.inputStream().use { input ->
                    input.copyTo(output)
                }
            }

            uri
        } else {

            val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

            val appDir = File(downloadsDir, "AImage")
            if (!appDir.exists()) appDir.mkdirs()

            val outFile = File(appDir, file.name) // 🔴 KEEP ORIGINAL NAME
            file.copyTo(outFile, overwrite = true)

            Uri.fromFile(outFile)
        }
    }

    fun share(context: Context, uri: Uri) {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = context.contentResolver.getType(uri) ?: "*/*"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            android.content.Intent.createChooser(intent, "Share file")
        )
    }
    fun shareMultiple(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                ArrayList(uris)
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(intent, "Share images")
        )
    }

}

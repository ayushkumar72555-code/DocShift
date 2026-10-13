package com.ayush.docshift.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

data class SafeDocument(val file: File, val mimeType: String)

object DocSafeStore {
    private const val DIRECTORY_NAME = "docsafe"

    fun directory(context: Context): File {
        val dir = File(context.filesDir, DIRECTORY_NAME)
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("Unable to create DocSafe storage")
        }
        return dir
    }

    fun list(context: Context): List<SafeDocument> =
        directory(context).listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?.map { SafeDocument(it, mimeTypeFor(it)) }
            .orEmpty()

    fun import(context: Context, uri: Uri): SafeDocument {
        val resolver = context.contentResolver
        val originalName = queryDisplayName(resolver, uri)
            ?: ("Document_" + System.currentTimeMillis())
        val destination = uniqueFile(directory(context), sanitizeName(originalName))
        val temporary = File(
            destination.parentFile,
            ".${destination.name}.${System.currentTimeMillis()}.tmp"
        )

        try {
            val input = resolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to read the selected file")
            input.use { stream ->
                temporary.outputStream().use { output -> stream.copyTo(output) }
            }
            if (!temporary.renameTo(destination)) {
                throw IllegalStateException("Unable to finalize imported file")
            }
            return SafeDocument(destination, mimeTypeFor(destination, resolver.getType(uri)))
        } catch (error: Exception) {
            temporary.delete()
            throw error
        } finally {
            temporary.delete()
        }
    }

    fun delete(document: SafeDocument): Boolean = document.file.delete()

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getString(0)
            }
        return uri.lastPathSegment
    }

    private fun sanitizeName(name: String): String {
        val sanitized = name.replace(Regex("[\\\\/:*?\\\"<>|]"), "_")
            .trim()
            .take(180)
        return sanitized.takeIf { it.isNotBlank() && it != "." && it != ".." }
            ?: "Document_" + System.currentTimeMillis()
    }

    private fun uniqueFile(directory: File, name: String): File {
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "").let {
            if (it.isEmpty()) "" else "." + it
        }
        var candidate = File(directory, name)
        var index = 2
        while (candidate.exists()) {
            candidate = File(directory, base + " (" + index + ")" + extension)
            index++
        }
        return candidate
    }

    private fun mimeTypeFor(file: File, fallback: String? = null): String =
        fallback ?: when (file.extension.lowercase()) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic", "heif" -> "image/heic"
            else -> "application/octet-stream"
        }
}

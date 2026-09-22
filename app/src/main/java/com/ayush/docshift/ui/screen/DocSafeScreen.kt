package com.ayush.docshift.ui.screen

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.ayush.docshift.storage.DocSafeStore
import com.ayush.docshift.storage.SafeDocument
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocSafeScreen(context: Context, onBack: () -> Unit) {
    var documents by remember { mutableStateOf(DocSafeStore.list(context)) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        try {
            val imported = uris.map { DocSafeStore.import(context, it) }
            documents = (imported + documents).distinctBy { it.file.absolutePath }
        } catch (e: Exception) {
            errorMessage = e.message ?: "Unable to add the selected file"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DocSafe") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = { TextButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Add") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                "Your important files, stored on this device.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))

            if (documents.isEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Nothing here yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Add important PDFs, images and other files to keep them ready when you need them.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Add files") }
                    }
                }
            } else {
                Text(
                    documents.size.toString() + if (documents.size == 1) " file" else " files",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(documents, key = { it.file.absolutePath }) { document ->
                        DocSafeItem(
                            document = document,
                            onOpen = {
                                try { openDocument(context, document) }
                                catch (e: Exception) { errorMessage = e.message ?: "No app can open this file" }
                            },
                            onShare = {
                                try { shareDocument(context, document) }
                                catch (e: Exception) { errorMessage = e.message ?: "Unable to share this file" }
                            },
                            onDelete = {
                                if (DocSafeStore.delete(document)) {
                                    documents = documents.filterNot { it.file.absolutePath == document.file.absolutePath }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("DocSafe") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { errorMessage = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun DocSafeItem(
    document: SafeDocument,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                document.file.extension.uppercase(Locale.getDefault()).ifBlank { "FILE" },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Column(Modifier.weight(1f)) {
                Text(
                    document.file.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(formatSize(document.file.length()), style = MaterialTheme.typography.bodySmall)
            }
            Box {
                TextButton(onClick = { menuExpanded = true }) { Text("⋮") }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Open") }, onClick = { menuExpanded = false; onOpen() })
                    DropdownMenuItem(text = { Text("Share") }, onClick = { menuExpanded = false; onShare() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
    }
}

private fun openDocument(context: Context, document: SafeDocument) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", document.file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, document.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Open with"))
}

private fun shareDocument(context: Context, document: SafeDocument) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", document.file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = document.mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share file"))
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return bytes.toString() + " B"
    if (bytes < 1024 * 1024) return (bytes / 1024).toString() + " KB"
    return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))
}

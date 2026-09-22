package com.ayush.docshift.ui.screen

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.ayush.docshift.storage.DocSafeStore
import com.ayush.docshift.storage.SafeDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocSafeScreen(context: Context, onBack: () -> Unit) {
    var documents by remember { mutableStateOf<List<SafeDocument>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            isLoading = true
            documents = withContext(Dispatchers.IO) { DocSafeStore.list(context) }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult

        scope.launch {
            isLoading = true
            try {
                val imported = withContext(Dispatchers.IO) {
                    uris.map { DocSafeStore.import(context, it) }
                }
                documents = (imported + documents)
                    .distinctBy { it.file.absolutePath }
                    .sortedByDescending { it.file.lastModified() }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Unable to add the selected files"
            } finally {
                isLoading = false
            }
        }
    }

    fun launchPicker() {
        picker.launch(arrayOf("*/*"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DocSafe") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
                actions = {
                    FilledTonalButton(
                        onClick = ::launchPicker,
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(5.dp))
                        Text("Add")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = ::launchPicker,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add files") }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                "Your important files, stored on this device.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))

            when {
                isLoading && documents.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                documents.isEmpty() -> {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "Nothing here yet",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Add PDFs, images and other files. You can keep adding files whenever you want.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = ::launchPicker) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Add files")
                            }
                        }
                    }
                }

                else -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            documents.size.toString() + if (documents.size == 1) " file" else " files",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (isLoading) {
                            Spacer(Modifier.width(10.dp))
                            CircularProgressIndicator(
                                Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(documents, key = { it.file.absolutePath }) { document ->
                            DocSafeTile(
                                document = document,
                                onOpen = {
                                    try {
                                        openDocument(context, document)
                                    } catch (e: Exception) {
                                        errorMessage = e.message ?: "No app can open this file"
                                    }
                                },
                                onShare = {
                                    try {
                                        shareDocument(context, document)
                                    } catch (e: Exception) {
                                        errorMessage = e.message ?: "Unable to share this file"
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            DocSafeStore.delete(document)
                                        }
                                        documents = documents.filterNot {
                                            it.file.absolutePath == document.file.absolutePath
                                        }
                                    }
                                }
                            )
                        }
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
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun DocSafeTile(
    document: SafeDocument,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.78f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                DocSafeThumbnail(
                    document = document,
                    modifier = Modifier.fillMaxSize()
                )

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(42.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shadowElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "File options"
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open") },
                                onClick = { menuExpanded = false; onOpen() }
                            )
                            DropdownMenuItem(
                                text = { Text("Share") },
                                onClick = { menuExpanded = false; onShare() }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = { menuExpanded = false; onDelete() }
                            )
                        }
                    }
                )
            }

            Column(
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    document.file.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    formatSize(document.file.length()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DocSafeThumbnail(
    document: SafeDocument,
    modifier: Modifier = Modifier
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, document.file.absolutePath) {
        value = withContext(Dispatchers.IO) {
            createThumbnail(document.file, document.mimeType)
        }
        awaitDispose {
            value?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = document.file.name,
            modifier = modifier.clip(MaterialTheme.shapes.medium),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(54.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                document.file.extension.uppercase(Locale.getDefault()).ifBlank { "FILE" },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun createThumbnail(file: File, mimeType: String): Bitmap? {
    return try {
        when {
            mimeType == "application/pdf" || file.extension.equals("pdf", ignoreCase = true) ->
                createPdfThumbnail(file)

            mimeType.startsWith("image/") ->
                BitmapFactory.Options().run {
                    inSampleSize = calculateSampleSize(file)
                    BitmapFactory.decodeFile(file.absolutePath, this)
                }

            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

private fun createPdfThumbnail(file: File): Bitmap? {
    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    descriptor.use {
        PdfRenderer(it).use { renderer ->
            if (renderer.pageCount == 0) return null
            renderer.openPage(0).use { page ->
                val width = 600
                val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bitmap
            }
        }
    }
}

private fun calculateSampleSize(file: File): Int {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)
    var sample = 1
    while (options.outWidth / sample > 1000 || options.outHeight / sample > 1000) {
        sample *= 2
    }
    return sample
}

private fun openDocument(context: Context, document: SafeDocument) {
    val uri = FileProvider.getUriForFile(
        context,
        context.packageName + ".provider",
        document.file
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, document.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Open with"))
}

private fun shareDocument(context: Context, document: SafeDocument) {
    val uri = FileProvider.getUriForFile(
        context,
        context.packageName + ".provider",
        document.file
    )
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

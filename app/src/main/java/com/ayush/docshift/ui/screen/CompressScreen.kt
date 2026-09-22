package com.ayush.docshift.ui.screen

import android.content.ContentResolver
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.FileProvider
import com.ayush.docshift.image.ImageCompressor
import com.ayush.docshift.storage.DownloadSaver
import com.ayush.docshift.util.FileInfoUtils
import com.ayush.docshift.util.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressScreen(contentResolver: ContentResolver, cacheDir: File, initialUris: List<Uri> = emptyList(), onBack: () -> Unit) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var targetSize by remember { mutableStateOf("100") }
    var expanded by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var imageUris by remember { mutableStateOf(initialUris) }
    var originalSizes by remember { mutableStateOf<List<Long>>(emptyList()) }
    var resultFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    val presets = listOf("20", "50", "100", "200", "500")

    fun selectImages(uris: List<Uri>) {
        imageUris = uris
        originalSizes = uris.mapNotNull { FileInfoUtils.getFileSize(contentResolver, it) }
        resultFiles = emptyList()
        progress = 0
    }

    LaunchedEffect(initialUris) { if (initialUris.isNotEmpty()) selectImages(initialUris) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // Some providers grant temporary access only.
                }
            }
            selectImages(uris)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        DocShiftScaffold("Compress image", "Smart target compression", onBack) {
            ToolIntro("TARGET COMPRESSION", "Reduce image size", "Choose a file-size limit and let DocShift optimize the image locally.")
            PrivacyBadge()
            SectionCard("Files") {
                if (imageUris.isEmpty()) {
                    Text("Choose one or more images to compress.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(imageUris.size.toString() + " image" + if (imageUris.size == 1) " selected" else "s selected", fontWeight = FontWeight.Medium)
                    if (originalSizes.isNotEmpty()) Text("Original total · " + FormatUtils.formatSize(originalSizes.sum()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SecondaryAction("Select images", !isProcessing) { picker.launch(arrayOf("image/*")) }
            }
            SectionCard("Target size") {
                Text("QUICK PRESETS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("50", "100", "200", "500").forEach { preset ->
                        FilterChip(
                            selected = targetSize == preset,
                            onClick = { targetSize = preset },
                            label = { Text("< $preset KB") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
                    OutlinedTextField(
                        value = targetSize,
                        onValueChange = { targetSize = it.filter(Char::isDigit) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        label = { Text("Target size (KB)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
                    )
                    ExposedDropdownMenu(expanded, { expanded = false }) {
                        presets.forEach { preset -> DropdownMenuItem(text = { Text(preset + " KB") }, onClick = { targetSize = preset; expanded = false }) }
                    }
                }
                Text("Output format · JPEG (universal)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (imageUris.isNotEmpty() && originalSizes.isNotEmpty()) {
                val estimate = (targetSize.toLongOrNull() ?: 0L) * 1024L * imageUris.size
                SectionCard("Estimated result") {
                    Text("Estimated output · " + if (estimate > 0) FormatUtils.formatSize(estimate) else "Select a target")
                    Text("Target-based optimization avoids quality-slider guesswork.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (isProcessing) SectionCard("Progress") { ProgressBlock(progress, imageUris.size, "Processing " + progress + " of " + imageUris.size) }
            PrimaryAction(if (isProcessing) "Compressing…" else "Compress images", imageUris.isNotEmpty() && !isProcessing) {
                keyboard?.hide()
                val kb = targetSize.toIntOrNull()
                if (kb == null || kb <= 0) {
                    Toast.makeText(context, "Enter a valid target size", Toast.LENGTH_SHORT).show()
                    return@PrimaryAction
                }
                isProcessing = true
                progress = 0
                resultFiles = emptyList()
                scope.launch(Dispatchers.IO) {
                    try {
                        val output = mutableListOf<File>()
                        imageUris.forEachIndexed { index, uri ->
                            output += ImageCompressor.compressToTarget(contentResolver, uri, kb, cacheDir)
                            withContext(Dispatchers.Main) { progress = index + 1 }
                        }
                        withContext(Dispatchers.Main) { resultFiles = output }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "Compression failed: " + (e.message ?: "Unknown error"),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } finally {
                        withContext(Dispatchers.Main) { isProcessing = false }
                    }
                }
            }
            if (resultFiles.isNotEmpty()) {
                val originalTotal = originalSizes.sum()
                val finalTotal = resultFiles.sumOf { it.length() }
                val saved = originalTotal - finalTotal
                val percent = if (originalTotal > 0) saved * 100 / originalTotal else 0
                SectionCard("Result") {
                    Text("Final size · " + FormatUtils.formatSize(finalTotal))
                    Text("Saved · " + FormatUtils.formatSize(saved) + " (" + percent + "%)", fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { resultFiles.forEach { DownloadSaver.save(context, it) }; Toast.makeText(context, "Saved to Downloads", Toast.LENGTH_SHORT).show() }, Modifier.weight(1f)) { Text("Save") }
                        OutlinedButton(onClick = {
                            DownloadSaver.shareMultiple(context, resultFiles.map { FileProvider.getUriForFile(context, context.packageName + ".provider", it) })
                        }, Modifier.weight(1f)) { Text("Share") }
                    }
                }
            }
        }
    }
}

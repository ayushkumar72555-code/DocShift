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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.FileProvider
import com.ayush.docshift.image.ImageCompressor
import com.ayush.docshift.image.ImageResizer
import com.ayush.docshift.storage.DownloadSaver
import com.ayush.docshift.util.FileInfoUtils
import com.ayush.docshift.util.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResizeScreen(contentResolver: ContentResolver, cacheDir: File, initialUris: List<Uri> = emptyList(), onBack: () -> Unit) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var imageUris by remember { mutableStateOf(initialUris) }
    var originalSizes by remember { mutableStateOf<List<Long>>(emptyList()) }
    var firstDimensions by remember { mutableStateOf<ImageResizer.ImageDimensions?>(null) }
    var width by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var targetSize by remember { mutableStateOf("100") }
    var dpi by remember { mutableStateOf("300") }
    var unit by remember { mutableStateOf(ImageResizer.ResizeUnit.Pixels) }
    var unitExpanded by remember { mutableStateOf(false) }
    var maintainAspectRatio by remember { mutableStateOf(true) }
    var progress by remember { mutableStateOf(0) }
    var isProcessing by remember { mutableStateOf(false) }
    var resultFiles by remember { mutableStateOf<List<File>>(emptyList()) }

    fun loadSelected(uris: List<Uri>) {
        imageUris = uris
        resultFiles = emptyList()
        progress = 0
        originalSizes = uris.mapNotNull { FileInfoUtils.getFileSize(contentResolver, it) }
        scope.launch(Dispatchers.IO) {
            val dimensions = uris.firstOrNull()?.let { ImageResizer.readDimensions(contentResolver, it) }
            withContext(Dispatchers.Main) {
                firstDimensions = dimensions
                if (dimensions != null && width.isBlank() && height.isBlank()) {
                    width = dimensions.width.toString()
                    height = dimensions.height.toString()
                }
            }
        }
    }
    LaunchedEffect(initialUris) { if (initialUris.isNotEmpty()) loadSelected(initialUris) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { if (it.isNotEmpty()) loadSelected(it) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        DocShiftScaffold("Resize image", "Set dimensions and optionally compress", onBack) {
            SectionCard("Images") {
                if (imageUris.isEmpty()) Text("Choose images to resize.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                    Text(imageUris.size.toString() + " image" + if (imageUris.size == 1) " selected" else "s selected", fontWeight = FontWeight.Medium)
                    firstDimensions?.let { Text("Original · " + it.width + " × " + it.height + " px", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (originalSizes.isNotEmpty()) Text("Original size · " + FormatUtils.formatSize(originalSizes.sum()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SecondaryAction("Select images", !isProcessing) { picker.launch("image/*") }
            }
            SectionCard("Dimensions") {
                ExposedDropdownMenuBox(unitExpanded, { unitExpanded = !unitExpanded }) {
                    OutlinedTextField(
                        value = when (unit) {
                            ImageResizer.ResizeUnit.Pixels -> "Pixels"
                            ImageResizer.ResizeUnit.Centimeters -> "Centimeters"
                            ImageResizer.ResizeUnit.Inches -> "Inches"
                        },
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        label = { Text("Unit") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(unitExpanded) }
                    )
                    ExposedDropdownMenu(unitExpanded, { unitExpanded = false }) {
                        ImageResizer.ResizeUnit.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(when (option) {
                                ImageResizer.ResizeUnit.Pixels -> "Pixels"
                                ImageResizer.ResizeUnit.Centimeters -> "Centimeters"
                                ImageResizer.ResizeUnit.Inches -> "Inches"
                            }) }, onClick = { unit = option; unitExpanded = false })
                        }
                    }
                }
                if (unit != ImageResizer.ResizeUnit.Pixels) {
                    OutlinedTextField(value = dpi, onValueChange = { dpi = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, label = { Text("DPI") })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = width, onValueChange = { width = it.filter { c -> c.isDigit() || c == '.' } }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, label = { Text("Width") })
                    OutlinedTextField(value = height, onValueChange = { height = it.filter { c -> c.isDigit() || c == '.' } }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, label = { Text("Height") })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Maintain aspect ratio")
                    Switch(checked = maintainAspectRatio, onCheckedChange = { maintainAspectRatio = it })
                }
            }
            SectionCard("Output size") {
                OutlinedTextField(value = targetSize, onValueChange = { targetSize = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, label = { Text("Compress after resize (KB)") })
            }
            if (isProcessing) SectionCard("Progress") { ProgressBlock(progress, imageUris.size, "Processing " + progress + " of " + imageUris.size) }
            PrimaryAction(if (isProcessing) "Resizing…" else "Resize and compress", imageUris.isNotEmpty() && !isProcessing) {
                keyboard?.hide()
                val targetKb = targetSize.toIntOrNull()
                val dpiValue = dpi.toIntOrNull() ?: 300
                if (targetKb == null || targetKb <= 0) {
                    Toast.makeText(context, "Enter a valid target size", Toast.LENGTH_SHORT).show()
                    return@PrimaryAction
                }
                if (width.isBlank() && height.isBlank()) {
                    Toast.makeText(context, "Enter width or height", Toast.LENGTH_SHORT).show()
                    return@PrimaryAction
                }
                isProcessing = true
                progress = 0
                resultFiles = emptyList()
                scope.launch(Dispatchers.IO) {
                    try {
                        val output = mutableListOf<File>()
                        imageUris.forEachIndexed { index, uri ->
                            val bitmap = ImageResizer.resize(contentResolver, uri, width, height, unit, dpiValue, maintainAspectRatio)
                            try {
                                output += ImageCompressor.compressBitmapToTarget(bitmap, targetKb, cacheDir, "DocShift_resize")
                            } finally {
                                if (!bitmap.isRecycled) bitmap.recycle()
                            }
                            withContext(Dispatchers.Main) { progress = index + 1 }
                        }
                        withContext(Dispatchers.Main) { resultFiles = output }
                    } catch (_: Exception) {
                        withContext(Dispatchers.Main) { Toast.makeText(context, "Resize failed", Toast.LENGTH_SHORT).show() }
                    } finally { withContext(Dispatchers.Main) { isProcessing = false } }
                }
            }
            if (resultFiles.isNotEmpty()) SectionCard("Result") {
                Text(resultFiles.size.toString() + " file" + if (resultFiles.size == 1) " ready" else "s ready", fontWeight = FontWeight.SemiBold)
                Text("Final total · " + FormatUtils.formatSize(resultFiles.sumOf { it.length() }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { resultFiles.forEach { DownloadSaver.save(context, it) }; Toast.makeText(context, "Saved to Downloads", Toast.LENGTH_SHORT).show() }, Modifier.weight(1f)) { Text("Save") }
                    OutlinedButton(onClick = { DownloadSaver.shareMultiple(context, resultFiles.map { FileProvider.getUriForFile(context, context.packageName + ".provider", it) }) }, Modifier.weight(1f)) { Text("Share") }
                }
            }
        }
    }
}

package com.ayush.docshift.ui.screen

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
import com.ayush.docshift.pdf.PdfCompressor
import com.ayush.docshift.storage.DownloadSaver
import com.ayush.docshift.util.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfCompressScreen(cacheDir: File, initialPdf: Uri? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val presets = listOf("100", "200", "500", "1000", "2000")
    val modes = PdfCompressor.CompressionMode.entries
    var selectedPdf by remember { mutableStateOf(initialPdf) }
    var targetSize by remember { mutableStateOf("500") }
    var expanded by remember { mutableStateOf(false) }
    var modeExpanded by remember { mutableStateOf(false) }
    var compressionMode by remember { mutableStateOf(PdfCompressor.CompressionMode.Balanced) }
    var originalSize by remember { mutableStateOf(0L) }
    var progress by remember { mutableStateOf(0) }
    var totalPages by remember { mutableStateOf(0) }
    var isProcessing by remember { mutableStateOf(false) }
    var resultFile by remember { mutableStateOf<File?>(null) }

    fun selectPdf(uri: Uri) {
        selectedPdf = uri
        resultFile = null
        progress = 0
        totalPages = 0
        originalSize = try { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L } catch (_: Exception) { 0L }
    }
    LaunchedEffect(initialPdf) { initialPdf?.let(::selectPdf) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(::selectPdf) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        DocShiftScaffold("Reduce PDF size", "Precise target size with selectable quality", onBack) {
            SectionCard("Document") {
                if (selectedPdf == null) Text("Choose a PDF to reduce its file size.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                    Text("PDF selected", fontWeight = FontWeight.Medium)
                    if (originalSize > 0) Text("Original · " + FormatUtils.formatSize(originalSize), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SecondaryAction("Select PDF", !isProcessing) { picker.launch("application/pdf") }
            }
            SectionCard("Compression settings") {
                ExposedDropdownMenuBox(modeExpanded, { modeExpanded = !modeExpanded }) {
                    OutlinedTextField(
                        value = compressionMode.label,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        label = { Text("Compression type") },
                        supportingText = { Text(compressionMode.description) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modeExpanded) }
                    )
                    ExposedDropdownMenu(modeExpanded, { modeExpanded = false }) {
                        modes.forEach { mode ->
                            DropdownMenuItem(text = { Column {
                                Text(mode.label, fontWeight = FontWeight.Medium)
                                Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } }, onClick = { compressionMode = mode; modeExpanded = false })
                        }
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
            }
            if (isProcessing) SectionCard("Progress") { ProgressBlock(progress, totalPages, "Processing page " + progress + " of " + totalPages) }
            PrimaryAction(if (isProcessing) "Compressing…" else "Reduce PDF size", selectedPdf != null && !isProcessing) {
                keyboard?.hide()
                val kb = targetSize.toIntOrNull()
                val uri = selectedPdf ?: return@PrimaryAction
                if (kb == null || kb <= 0) {
                    Toast.makeText(context, "Enter a valid target size", Toast.LENGTH_SHORT).show()
                    return@PrimaryAction
                }
                isProcessing = true
                resultFile = null
                progress = 0
                totalPages = 0
                scope.launch {
                    try {
                        resultFile = withContext(Dispatchers.IO) {
                            PdfCompressor.compressToTarget(context, uri, kb, File(cacheDir, "compressed_pdfs"), compressionMode) { current, total ->
                                scope.launch(Dispatchers.Main.immediate) {
                                    progress = current
                                    totalPages = total
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message ?: "PDF compression failed", Toast.LENGTH_LONG).show()
                    } finally { isProcessing = false }
                }
            }
            resultFile?.let { file ->
                val saved = originalSize - file.length()
                val percent = if (originalSize > 0) saved * 100 / originalSize else 0
                SectionCard("Result") {
                    Text("Final size · " + FormatUtils.formatSize(file.length()))
                    Text("Saved · " + FormatUtils.formatSize(saved) + " (" + percent + "%)", fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { DownloadSaver.save(context, file); Toast.makeText(context, "Saved to Downloads", Toast.LENGTH_SHORT).show() }, Modifier.weight(1f)) { Text("Save") }
                        OutlinedButton(onClick = { DownloadSaver.share(context, FileProvider.getUriForFile(context, context.packageName + ".provider", file)) }, Modifier.weight(1f)) { Text("Share") }
                    }
                }
            }
        }
    }
}

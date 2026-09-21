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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
fun PdfCompressScreen(
    cacheDir: File,
    initialPdf: Uri? = null,
    onBack: () -> Unit
) {
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
        originalSize = try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    LaunchedEffect(initialPdf) {
        initialPdf?.let { selectPdf(it) }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(::selectPdf) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("← Back") }

        Spacer(Modifier.height(28.dp))
        Text("Reduce PDF Size", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { picker.launch("application/pdf") },
            enabled = !isProcessing,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Select PDF") }

        selectedPdf?.let {
            Spacer(Modifier.height(8.dp))
            Text("PDF selected")
        }

        if (originalSize > 0) {
            Spacer(Modifier.height(8.dp))
            Text("Original size: " + FormatUtils.formatSize(originalSize))
        }

        Spacer(Modifier.height(20.dp))

        ExposedDropdownMenuBox(
            expanded = modeExpanded,
            onExpandedChange = { modeExpanded = !modeExpanded }
        ) {
            OutlinedTextField(
                value = compressionMode.label,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                label = { Text("Compression type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modeExpanded) }
            )

            ExposedDropdownMenu(
                expanded = modeExpanded,
                onDismissRequest = { modeExpanded = false }
            ) {
                modes.forEach { mode ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(mode.label)
                                Text(mode.description, style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        onClick = {
                            compressionMode = mode
                            modeExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = targetSize,
                onValueChange = { targetSize = it },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                label = { Text("Target size (KB)") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                presets.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(preset + " KB") },
                        onClick = {
                            targetSize = preset
                            expanded = false
                        }
                    )
                }
            }
        }

        if (isProcessing && totalPages > 0) {
            Spacer(Modifier.height(18.dp))
            LinearProgressIndicator(
                progress = progress / totalPages.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text("Processing page " + progress + " of " + totalPages)
        }

        Spacer(Modifier.height(20.dp))

        Button(
            enabled = selectedPdf != null && !isProcessing,
            onClick = {
                keyboard?.hide()
                val kb = targetSize.toIntOrNull()
                val uri = selectedPdf ?: return@Button

                if (kb == null || kb <= 0) {
                    Toast.makeText(context, "Invalid target size", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                isProcessing = true
                resultFile = null
                progress = 0
                totalPages = 0

                scope.launch {
                    try {
                        resultFile = withContext(Dispatchers.IO) {
                            PdfCompressor.compressToTarget(
                                context = context,
                                pdfUri = uri,
                                targetKb = kb,
                                outputDir = File(cacheDir, "compressed_pdfs"),
                                mode = compressionMode
                            ) { current, total ->
                                progress = current
                                totalPages = total
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            e.message ?: "PDF compression failed",
                            Toast.LENGTH_LONG
                        ).show()
                    } finally {
                        isProcessing = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isProcessing) "Compressing..." else "Reduce PDF Size")
        }

        resultFile?.let { file ->
            Spacer(Modifier.height(24.dp))
            val saved = originalSize - file.length()
            val percent = if (originalSize > 0) saved * 100 / originalSize else 0

            Text("Final size: " + FormatUtils.formatSize(file.length()))
            Text(
                "Saved: " + FormatUtils.formatSize(saved) + " (" + percent + "%)",
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = {
                    DownloadSaver.save(context, file)
                    Toast.makeText(context, "Saved to Downloads", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save PDF") }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    DownloadSaver.share(
                        context,
                        FileProvider.getUriForFile(
                            context,
                            context.packageName + ".provider",
                            file
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Share PDF") }
        }
    }
}

package com.ayush.docshift.ui.screen

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.ayush.docshift.R
import com.ayush.docshift.pdf.PdfToImageConverter
import com.ayush.docshift.storage.DownloadSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PdfToImageScreen(
    contentResolver: android.content.ContentResolver,
    cacheDir: File,
    initialPdf: android.net.Uri? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedPdf by remember { mutableStateOf(initialPdf) }
    var resultFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var totalPages by remember { mutableStateOf(0) }
    var exportFormat by remember { mutableStateOf(PdfToImageConverter.OutputFormat.PNG) }
    var dpi by remember { mutableStateOf(300) }
    var rangeMode by remember { mutableStateOf("All pages") }
    var customRange by remember { mutableStateOf("") }

    fun selectedRange(): IntRange? {
        if (rangeMode != "Custom range") return null
        val parts = customRange.split("-").mapNotNull { it.trim().toIntOrNull() }
        return if (parts.size == 2 && parts[0] > 0 && parts[1] >= parts[0]) parts[0]..parts[1] else null
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        it?.let { uri ->
            selectedPdf = uri
            resultFiles = emptyList()
            progress = 0
            totalPages = 0
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        DocShiftScaffold("Extract PDF pages", "High-fidelity page extractor", onBack) {
            ToolIntro("MULTI-PAGE RASTERIZER", "Export PDF to images", "Choose your page range, image format and resolution before extracting.")
            PrivacyBadge()
            SectionCard("Document") {
                if (selectedPdf == null) {
                    Text("Choose a PDF to convert.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("PDF selected", fontWeight = FontWeight.Medium)
                }
                SecondaryAction("Select PDF", !isProcessing) {
                    picker.launch("application/pdf")
                }
            }

            SectionCard("Export range") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("All pages", "Custom range").forEach { option ->
                        FilterChip(selected = rangeMode == option, onClick = { rangeMode = option }, label = { Text(option) }, modifier = Modifier.weight(1f))
                    }
                }
                if (rangeMode == "Custom range") {
                    OutlinedTextField(value = customRange, onValueChange = { customRange = it.filter { char -> char.isDigit() || char == '-' } }, modifier = Modifier.fillMaxWidth(), label = { Text("Pages, e.g. 1-4") }, singleLine = true)
                }
            }
            SectionCard("Image settings") {
                Text("IMAGE FORMAT", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PdfToImageConverter.OutputFormat.entries.forEach { format ->
                        FilterChip(selected = exportFormat == format, onClick = { exportFormat = format }, label = { Text(format.name) }, modifier = Modifier.weight(1f))
                    }
                }
                Text("RESOLUTION", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(72, 150, 300).forEach { option ->
                        FilterChip(selected = dpi == option, onClick = { dpi = option }, label = { Text("$option DPI") }, modifier = Modifier.weight(1f))
                    }
                }
            }

            if (isProcessing) {
                SectionCard("Progress") {
                    ProgressBlock(progress, totalPages, "Processing page $progress of $totalPages")
                }
            }

            PrimaryAction("Convert to images", selectedPdf != null && !isProcessing) {
                val uri = selectedPdf ?: return@PrimaryAction
                isProcessing = true
                resultFiles = emptyList()
                progress = 0
                totalPages = 0

                scope.launch(Dispatchers.IO) {
                    try {
                        val files = PdfToImageConverter.convert(
                            context,
                            uri,
                            File(cacheDir, "pdf_images"),
                            format = exportFormat,
                            dpi = dpi,
                            pageRange = selectedRange()
                        ) { current, total ->
                            scope.launch(Dispatchers.Main.immediate) {
                                progress = current
                                totalPages = total
                            }
                        }

                        withContext(Dispatchers.Main) {
                            resultFiles = files
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "PDF to image failed: ${e.message ?: "Unknown error"}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } finally {
                        withContext(Dispatchers.Main) {
                            isProcessing = false
                        }
                    }
                }
            }

            if (resultFiles.isNotEmpty()) {
                SectionCard("Result") {
                    Text(
                        resultFiles.size.toString() +
                            " image" +
                            if (resultFiles.size == 1) " generated" else "s generated",
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                resultFiles.forEach { DownloadSaver.save(context, it) }
                                Toast.makeText(context, "Images saved to Downloads", Toast.LENGTH_SHORT).show()
                            },
                            Modifier.weight(1f)
                        ) { Text("Save all") }

                        OutlinedButton(
                            onClick = {
                                val uris = resultFiles.map {
                                    FileProvider.getUriForFile(
                                        context,
                                        context.packageName + ".provider",
                                        it
                                    )
                                }
                                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "image/*"
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share images"))
                            },
                            Modifier.weight(1f)
                        ) { Text("Share all") }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        resultFiles.forEachIndexed { index, file ->
                            ListItem(
                                headlineContent = { Text("Page " + (index + 1)) },
                                supportingContent = { Text(file.name) },
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = {
                                            runCatching { DownloadSaver.save(context, file) }
                                                .onSuccess {
                                                    Toast.makeText(context, "Page " + (index + 1) + " saved", Toast.LENGTH_SHORT).show()
                                                }
                                                .onFailure {
                                                    Toast.makeText(context, "Save failed: ${it.message ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                                                }
                                        }) {
                                            Image(painterResource(R.drawable.save), "Save page", Modifier.size(24.dp))
                                        }
                                        IconButton(onClick = {
                                            DownloadSaver.share(
                                                context,
                                                FileProvider.getUriForFile(
                                                    context,
                                                    context.packageName + ".provider",
                                                    file
                                                )
                                            )
                                        }) {
                                            Image(painterResource(R.drawable.share), "Share page", Modifier.size(24.dp))
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
}

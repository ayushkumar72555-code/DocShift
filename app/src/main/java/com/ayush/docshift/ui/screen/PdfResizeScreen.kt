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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.ayush.docshift.pdf.PdfResizer
import com.ayush.docshift.storage.DownloadSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfResizeScreen(
    cacheDir: File,
    initialPdf: Uri? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedPdf by remember { mutableStateOf(initialPdf) }
    var originalDimensions by remember { mutableStateOf<PdfResizer.PageDimensions?>(null) }

    var width by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var dpi by remember { mutableStateOf("300") }
    var unit by remember { mutableStateOf(PdfResizer.ResizeUnit.Inches) }
    var expanded by remember { mutableStateOf(false) }
    var maintainAspectRatio by remember { mutableStateOf(true) }

    var progress by remember { mutableStateOf(0) }
    var totalPages by remember { mutableStateOf(0) }
    var isProcessing by remember { mutableStateOf(false) }
    var resultFile by remember { mutableStateOf<File?>(null) }

    fun loadPdf(uri: Uri) {
        selectedPdf = uri
        resultFile = null
        progress = 0
        totalPages = 0

        scope.launch(Dispatchers.IO) {
            try {
                val dimensions = PdfResizer.readFirstPageDimensions(context, uri)
                withContext(Dispatchers.Main) {
                    originalDimensions = dimensions
                    if (width.isBlank() && height.isBlank()) {
                        width = "%.2f".format(dimensions.widthPoints / 72f)
                        height = "%.2f".format(dimensions.heightPoints / 72f)
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    originalDimensions = null
                    Toast.makeText(context, "Unable to read PDF", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(initialPdf) {
        initialPdf?.let { loadPdf(it) }
    }

    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { loadPdf(it) }
    }

    val selectedUnit = when (unit) {
        PdfResizer.ResizeUnit.Pixels -> "Pixels"
        PdfResizer.ResizeUnit.Centimeters -> "Centimeters"
        PdfResizer.ResizeUnit.Inches -> "Inches"
    }

    val targetPreview = originalDimensions?.let { original ->
        try {
            PdfResizer.calculateTargetSize(
                original.widthPoints,
                original.heightPoints,
                PdfResizer.toPoints(width, unit, dpi.toIntOrNull() ?: 300),
                PdfResizer.toPoints(height, unit, dpi.toIntOrNull() ?: 300),
                maintainAspectRatio
            )
        } catch (_: Exception) {
            null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("← Back")
        }

        Spacer(Modifier.height(28.dp))
        Text("PDF Resize", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(18.dp))

        Button(
            onClick = { pdfPicker.launch("application/pdf") },
            enabled = !isProcessing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Select PDF")
        }

        selectedPdf?.let {
            Spacer(Modifier.height(8.dp))
            Text("PDF selected")
        }

        originalDimensions?.let { dimensions ->
            Spacer(Modifier.height(8.dp))
            Text(
                "Original page: ${dimensions.widthPoints} × ${dimensions.heightPoints} pt",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(20.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedUnit,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                label = { Text("Resize unit") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                }
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                PdfResizer.ResizeUnit.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                when (option) {
                                    PdfResizer.ResizeUnit.Pixels -> "Pixels"
                                    PdfResizer.ResizeUnit.Centimeters -> "Centimeters"
                                    PdfResizer.ResizeUnit.Inches -> "Inches"
                                }
                            )
                        },
                        onClick = {
                            unit = option
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = dpi,
            onValueChange = { dpi = it },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            label = { Text("DPI") },
            supportingText = {
                Text("Used to convert pixel dimensions into physical PDF page size")
            }
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = width,
                onValueChange = { width = it },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                label = { Text("Width") }
            )

            OutlinedTextField(
                value = height,
                onValueChange = { height = it },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                label = { Text("Height") }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = maintainAspectRatio,
                onCheckedChange = { maintainAspectRatio = it }
            )
            Text("Maintain aspect ratio")
        }

        targetPreview?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                "Output page: ${it.widthPoints} × ${it.heightPoints} pt",
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (isProcessing && totalPages > 0) {
            Spacer(Modifier.height(18.dp))
            LinearProgressIndicator(
                progress = progress / totalPages.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text("Processing page $progress of $totalPages")
        }

        Spacer(Modifier.height(20.dp))

        Button(
            enabled = selectedPdf != null && !isProcessing,
            onClick = {
                val uri = selectedPdf ?: return@Button
                val dpiValue = dpi.toIntOrNull()

                if (dpiValue == null || dpiValue <= 0) {
                    Toast.makeText(context, "Invalid DPI", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                if (width.isBlank() && height.isBlank()) {
                    Toast.makeText(context, "Enter width or height", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                isProcessing = true
                progress = 0
                totalPages = 0
                resultFile = null

                scope.launch {
                    try {
                        val output = withContext(Dispatchers.IO) {
                            PdfResizer.resize(
                                context = context,
                                pdfUri = uri,
                                outputDir = File(cacheDir, "resized_pdfs"),
                                widthInput = width,
                                heightInput = height,
                                unit = unit,
                                dpi = dpiValue,
                                maintainAspectRatio = maintainAspectRatio
                            ) { current, total ->
                                progress = current
                                totalPages = total
                            }
                        }
                        resultFile = output
                    } catch (_: Exception) {
                        Toast.makeText(
                            context,
                            "PDF resize failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    } finally {
                        isProcessing = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isProcessing) "Resizing..." else "Resize PDF")
        }

        resultFile?.let { file ->
            Spacer(Modifier.height(24.dp))
            Text("PDF ready", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("Final size: ${file.length() / 1024} KB")

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    DownloadSaver.save(context, file)
                    Toast.makeText(context, "Saved to Downloads", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save PDF")
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )
                    DownloadSaver.share(context, uri)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Share PDF")
            }
        }
    }
}

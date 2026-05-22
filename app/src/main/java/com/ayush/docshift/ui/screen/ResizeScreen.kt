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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
fun ResizeScreen(
    contentResolver: ContentResolver,
    cacheDir: File,
    initialUris: List<Uri> = emptyList(),
    onBack: () -> Unit
) {
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
    var expanded by remember { mutableStateOf(false) }
    var maintainAspectRatio by remember { mutableStateOf(true) }

    var progress by remember { mutableStateOf(0) }
    var isProcessing by remember { mutableStateOf(false) }
    var resultFiles by remember { mutableStateOf<List<File>>(emptyList()) }

    fun loadSelectedInfo(uris: List<Uri>) {
        imageUris = uris
        resultFiles = emptyList()
        progress = 0
        originalSizes = uris.mapNotNull {
            FileInfoUtils.getFileSize(contentResolver, it)
        }

        scope.launch(Dispatchers.IO) {
            val dimensions = uris.firstOrNull()?.let {
                ImageResizer.readDimensions(contentResolver, it)
            }

            withContext(Dispatchers.Main) {
                firstDimensions = dimensions
                if (dimensions != null && width.isBlank() && height.isBlank()) {
                    width = dimensions.width.toString()
                    height = dimensions.height.toString()
                }
            }
        }
    }

    LaunchedEffect(initialUris) {
        if (initialUris.isNotEmpty()) {
            loadSelectedInfo(initialUris)
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            loadSelectedInfo(uris)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("<- Back")
        }

        Spacer(Modifier.height(32.dp))

        Text("Image Resize", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { imagePicker.launch("image/*") },
            enabled = !isProcessing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Select Images")
        }

        if (imageUris.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("${imageUris.size} selected")
        }

        firstDimensions?.let { dimensions ->
            Spacer(Modifier.height(8.dp))
            Text(
                "Original: ${dimensions.width} x ${dimensions.height} px",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (originalSizes.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Original size: ${FormatUtils.formatSize(originalSizes.sum())}")
        }

        Spacer(Modifier.height(20.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = when (unit) {
                    ImageResizer.ResizeUnit.Pixels -> "Pixels"
                    ImageResizer.ResizeUnit.Centimeters -> "Centimeters"
                    ImageResizer.ResizeUnit.Inches -> "Inches"
                },
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
                ImageResizer.ResizeUnit.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                when (option) {
                                    ImageResizer.ResizeUnit.Pixels -> "Pixels"
                                    ImageResizer.ResizeUnit.Centimeters -> "Centimeters"
                                    ImageResizer.ResizeUnit.Inches -> "Inches"
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

        if (unit != ImageResizer.ResizeUnit.Pixels) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = dpi,
                onValueChange = { dpi = it },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                label = { Text("DPI") }
            )
        }

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

        Spacer(Modifier.height(8.dp))

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

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = targetSize,
            onValueChange = { targetSize = it },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            label = { Text("Compress after resize (KB)") }
        )

        if (isProcessing) {
            Spacer(Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = progress / imageUris.size.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))
            Text("Processing $progress of ${imageUris.size}")
        }

        Spacer(Modifier.height(20.dp))

        Button(
            enabled = imageUris.isNotEmpty() && !isProcessing,
            onClick = {
                keyboard?.hide()
                val targetKb = targetSize.toIntOrNull()
                val dpiValue = dpi.toIntOrNull() ?: 300

                if (targetKb == null || targetKb <= 0) {
                    Toast.makeText(context, "Invalid target size", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                if (width.isBlank() && height.isBlank()) {
                    Toast.makeText(context, "Enter width or height", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                isProcessing = true
                progress = 0
                resultFiles = emptyList()

                scope.launch(Dispatchers.IO) {
                    try {
                        val output = mutableListOf<File>()

                        imageUris.forEachIndexed { index, uri ->
                            val resizedBitmap = ImageResizer.resize(
                                resolver = contentResolver,
                                uri = uri,
                                widthInput = width,
                                heightInput = height,
                                unit = unit,
                                dpi = dpiValue,
                                maintainAspectRatio = maintainAspectRatio
                            )

                            val file = ImageCompressor.compressBitmapToTarget(
                                bitmap = resizedBitmap,
                                targetKb = targetKb,
                                cacheDir = cacheDir,
                                filePrefix = "DocShift_resize"
                            )

                            output.add(file)

                            withContext(Dispatchers.Main) {
                                progress = index + 1
                            }
                        }

                        withContext(Dispatchers.Main) {
                            resultFiles = output
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Resize failed", Toast.LENGTH_SHORT).show()
                        }
                    } finally {
                        withContext(Dispatchers.Main) {
                            isProcessing = false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isProcessing) "Resizing..." else "Resize and Compress")
        }

        if (resultFiles.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))

            val finalTotal = resultFiles.sumOf { it.length() }

            Text("Final size: ${FormatUtils.formatSize(finalTotal)}")
            Text("${resultFiles.size} file(s) ready", fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    resultFiles.forEach { DownloadSaver.save(context, it) }
                    Toast.makeText(context, "Saved to Downloads", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val uris = resultFiles.map {
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            it
                        )
                    }
                    DownloadSaver.shareMultiple(context, uris)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Share")
            }
        }
    }
}

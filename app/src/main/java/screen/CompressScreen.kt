package com.ayush.aimage.ui.screen

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
import com.ayush.aimage.image.ImageCompressor
import com.ayush.aimage.storage.DownloadSaver
import com.ayush.aimage.util.FileInfoUtils
import com.ayush.aimage.util.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressScreen(
    contentResolver: ContentResolver,
    cacheDir: File,
    initialUris: List<Uri> = emptyList(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    val sizePresets = listOf("20", "50", "100", "200", "500")

    var targetSize by remember { mutableStateOf("100") }
    var expanded by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }

    var imageUris by remember { mutableStateOf(initialUris) }
    var originalSizes by remember { mutableStateOf<List<Long>>(emptyList()) }
    var resultFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        imageUris = uris
        originalSizes = uris.mapNotNull {
            FileInfoUtils.getFileSize(contentResolver, it)
        }
        resultFiles = emptyList()
        progress = 0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // Back
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("← Back")
        }

        Spacer(Modifier.height(40.dp))

        Text("Reduce Size", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(24.dp))

        // Target size selector
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = targetSize,
                onValueChange = { targetSize = it },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                label = { Text("Target size (KB)") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                }
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                sizePresets.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text("$preset KB") },
                        onClick = {
                            targetSize = preset
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Select images
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

        // Original size
        if (originalSizes.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Original size: ${FormatUtils.formatSize(originalSizes.sum())}",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // Progress UI
        if (isProcessing) {
            Spacer(Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = progress / imageUris.size.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Processing $progress of ${imageUris.size}",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(24.dp))

        // Compress button
        Button(
            enabled = imageUris.isNotEmpty() && !isProcessing,
            onClick = {
                keyboard?.hide()
                val kb = targetSize.toIntOrNull()

                if (kb == null) {
                    Toast.makeText(context, "Invalid target size", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                isProcessing = true
                progress = 0
                resultFiles = emptyList()

                scope.launch(Dispatchers.IO) {
                    try {
                        val output = mutableListOf<File>()

                        imageUris.forEachIndexed { index, uri ->
                            val file = ImageCompressor.compressToTarget(
                                contentResolver,
                                uri,
                                kb,
                                cacheDir
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
                            Toast.makeText(
                                context,
                                "Batch compression failed",
                                Toast.LENGTH_SHORT
                            ).show()
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
            Text(if (isProcessing) "Compressing…" else "Compress")
        }

        // Results
        if (resultFiles.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))

            val originalTotal = originalSizes.sum()
            val finalTotal = resultFiles.sumOf { it.length() }
            val savedBytes = originalTotal - finalTotal
            val savedPercent =
                if (originalTotal > 0) (savedBytes * 100 / originalTotal) else 0

            Text("Final size: ${FormatUtils.formatSize(finalTotal)}")
            Text(
                "Saved: ${FormatUtils.formatSize(savedBytes)} ($savedPercent%)",
                fontWeight = FontWeight.Bold
            )

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

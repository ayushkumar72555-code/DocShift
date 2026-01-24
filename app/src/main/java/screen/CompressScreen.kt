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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.ayush.aimage.image.ImageCompressor
import com.ayush.aimage.storage.DownloadSaver
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

    var imageUris by remember {
        mutableStateOf(initialUris)
    }

    var resultFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        imageUris = uris
        resultFiles = emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("← Back")
        }

        Spacer(Modifier.height(32.dp))

        Text("Reduce Size", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(24.dp))

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

        Button(
            onClick = { imagePicker.launch("image/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Select")
        }

        if (imageUris.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("${imageUris.size} selected")
        }

        Spacer(Modifier.height(24.dp))

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
                scope.launch(Dispatchers.IO) {
                    try {
                        val output = mutableListOf<File>()

                        for (uri in imageUris) {
                            val file = ImageCompressor.compressToTarget(
                                contentResolver,
                                uri,
                                kb,
                                cacheDir
                            )
                            output.add(file)
                        }

                        withContext(Dispatchers.Main) {
                            resultFiles = output
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Batch compression failed", Toast.LENGTH_SHORT).show()
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
            Text(
                if (isProcessing)
                    "Compressing ${imageUris.size} "
                else
                    "Compress"
            )
        }

        if (resultFiles.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))

            Text("Compressed ${resultFiles.size}")

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    resultFiles.forEach { DownloadSaver.save(context, it) }
                    Toast.makeText(context, "saved to Downloads", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Thank you Ayush!")
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

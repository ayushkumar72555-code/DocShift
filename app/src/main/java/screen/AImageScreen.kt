package com.ayush.aimage.ui.screen

import android.content.ContentResolver
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.ayush.aimage.image.ImageCompressor
import com.ayush.aimage.pdf.ImageToPdfConverter
import com.ayush.aimage.storage.DownloadSaver
import com.ayush.aimage.util.FileInfoUtils
import com.ayush.aimage.util.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AImageScreen(
    contentResolver: ContentResolver,
    cacheDir: File
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    var targetSize by remember { mutableStateOf("100") }

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imageName by remember { mutableStateOf<String?>(null) }
    var imageSize by remember { mutableStateOf<Long?>(null) }

    var resultFile by remember { mutableStateOf<File?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            imageUri = it
            imageName = FileInfoUtils.getFileName(contentResolver, it)
            imageSize = FileInfoUtils.getFileSize(contentResolver, it)
            resultFile = null
        }
    }

    val multiImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult

        isProcessing = true
        scope.launch(Dispatchers.IO) {
            try {
                val outputDir = File(context.cacheDir, "pdf")
                val pdfFile = ImageToPdfConverter.convert(
                    resolver = contentResolver,
                    imageUris = uris,
                    outputDir = outputDir,
                    fileName = "AImage_${System.currentTimeMillis()}"
                )

                withContext(Dispatchers.Main) {
                    resultFile = pdfFile
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Image → PDF failed", Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isProcessing = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text("Reduce Image Size", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(26.dp))

        Button(
            onClick = { imagePicker.launch("image/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Upload Image")
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { multiImagePicker.launch("image/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Image → PDF")
        }

        imageName?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, fontWeight = FontWeight.Bold)
            Text("Original: ${FormatUtils.formatSize(imageSize!!)}")
        }

        Spacer(Modifier.height(20.dp))

        Button(
            enabled = imageUri != null && !isProcessing,
            onClick = {
                keyboard?.hide()
                val kb = targetSize.toIntOrNull() ?: return@Button

                isProcessing = true
                scope.launch(Dispatchers.IO) {
                    try {
                        val file = ImageCompressor.compressToTarget(
                            contentResolver,
                            imageUri!!,
                            kb,
                            cacheDir
                        )
                        withContext(Dispatchers.Main) {
                            resultFile = file
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Compression failed", Toast.LENGTH_SHORT).show()
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
            Text(if (isProcessing) "Processing…" else "Reduce Size")
        }

        resultFile?.let { file ->
            Spacer(Modifier.height(20.dp))
            Text("Result: ${FormatUtils.formatSize(file.length())}")

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
                Text("Share")
            }

            Button(
                onClick = {
                    DownloadSaver.save(context, file)
                    Toast.makeText(context, "Saved to Downloads", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }
}

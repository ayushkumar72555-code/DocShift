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
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.ayush.aimage.pdf.ImageToPdfConverter
import com.ayush.aimage.storage.DownloadSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageToPdfScreen(
    contentResolver: ContentResolver,
    cacheDir: File,
    initialUris: List<Uri> = emptyList(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var imageUris by remember { mutableStateOf(initialUris) }
    var resultFile by remember { mutableStateOf<File?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    /* ---------- Conversion logic (single source of truth) ---------- */

    fun convertImagesToPdf(uris: List<Uri>) {
        if (uris.isEmpty()) return

        isProcessing = true
        resultFile = null

        scope.launch(Dispatchers.IO) {
            try {
                val pdfFile = ImageToPdfConverter.convert(
                    resolver = contentResolver,
                    imageUris = uris,
                    outputDir = cacheDir,
                    fileName = "AImage_${System.currentTimeMillis()}"
                )

                withContext(Dispatchers.Main) {
                    resultFile = pdfFile
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Image → PDF failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isProcessing = false
                }
            }
        }
    }

    /* ---------- Auto-start when launched via Share ---------- */

    LaunchedEffect(initialUris) {
        if (initialUris.isNotEmpty()) {
            convertImagesToPdf(initialUris)
        }
    }

    /* ---------- Picker ---------- */

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        imageUris = uris
        convertImagesToPdf(uris)
    }

    /* ---------- UI ---------- */

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

        Spacer(Modifier.height(40.dp))

        Text("Image → PDF", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { imagePicker.launch("image/*") },
            enabled = !isProcessing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (isProcessing) "Processing…" else "Select"
            )
        }

        if (imageUris.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Select: ${imageUris.size}")
        }

        resultFile?.let { file ->
            Spacer(Modifier.height(24.dp))

            Text(
                "PDF created successfully",
                fontWeight = MaterialTheme.typography.bodyLarge.fontWeight
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    DownloadSaver.save(context, file)
                    Toast.makeText(
                        context,
                        "Saved to Downloads",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Thank you Ayush!")
            }

            Spacer(Modifier.height(12.dp))

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
        }
    }
}

package com.ayush.aimage.ui.screen

import android.content.ContentResolver
import android.content.Intent
import com.ayush.aimage.R
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
import com.ayush.aimage.pdf.PdfToImageConverter
import com.ayush.aimage.storage.DownloadSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToImageScreen(
    contentResolver: ContentResolver,
    cacheDir: File,
    initialPdf: Uri? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedPdf by remember { mutableStateOf(initialPdf) }
    var resultFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var totalPages by remember { mutableStateOf(0) }

    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedPdf = it
            resultFiles = emptyList()
            progress = 0
            totalPages = 0
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // Back
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("← Back")
        }

        Spacer(Modifier.height(40.dp))

        Text("PDF → Image", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(20.dp))

        // Select PDF
        Button(
            onClick = { pdfPicker.launch("application/pdf") },
            enabled = !isProcessing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Select PDF")
        }

        selectedPdf?.let {
            Spacer(Modifier.height(12.dp))
            Text("PDF selected")
        }

        // Progress UI
        if (isProcessing && totalPages > 0) {
            Spacer(Modifier.height(20.dp))

            LinearProgressIndicator(
                progress = progress / totalPages.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Processing page $progress of $totalPages",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(20.dp))

        // Convert
        Button(
            enabled = selectedPdf != null && !isProcessing,
            onClick = {
                val uri = selectedPdf ?: return@Button

                isProcessing = true
                resultFiles = emptyList()
                progress = 0
                totalPages = 0

                scope.launch {
                    try {
                        val outputDir = File(cacheDir, "pdf_images")

                        val images = withContext(Dispatchers.IO) {
                            PdfToImageConverter.convert(
                                context = context,
                                pdfUri = uri,
                                outputDir = outputDir
                            ) { current, total ->
                                progress = current
                                totalPages = total
                            }
                        }

                        resultFiles = images

                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "PDF to Image failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    } finally {
                        isProcessing = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isProcessing) "Processing…" else "Convert to Images")
        }

        // Results
        if (resultFiles.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))

            Text(
                "Generated ${resultFiles.size} images",
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    resultFiles.forEach { DownloadSaver.save(context, it) }
                    Toast.makeText(
                        context,
                        "Images saved to Downloads",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save All Images")
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

                    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "image/*"
                        putParcelableArrayListExtra(
                            Intent.EXTRA_STREAM,
                            ArrayList(uris)
                        )
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    context.startActivity(
                        Intent.createChooser(intent, "Share images")
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Share All Images")
            }

            Spacer(Modifier.height(16.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                resultFiles.forEachIndexed { index, file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Page ${index + 1}",
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = {
                                DownloadSaver.save(context, file)
                                Toast.makeText(
                                    context,
                                    "Page ${index + 1} saved",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Image(
                                painter = painterResource(R.drawable.save),
                                contentDescription = "Save page ${index + 1}",
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        IconButton(
                            onClick = {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    file
                                )
                                DownloadSaver.share(context, uri)
                            }
                        ) {
                            Image(
                                painter = painterResource(R.drawable.share),
                                contentDescription = "Share page ${index + 1}",
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


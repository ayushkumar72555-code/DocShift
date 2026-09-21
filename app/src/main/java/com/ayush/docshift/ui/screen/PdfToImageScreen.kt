package com.ayush.docshift.ui.screen

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import com.ayush.docshift.R
import com.ayush.docshift.pdf.PdfToImageConverter
import com.ayush.docshift.storage.DownloadSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PdfToImageScreen(contentResolver: android.content.ContentResolver, cacheDir: File, initialPdf: Uri? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedPdf by remember { mutableStateOf(initialPdf) }
    var resultFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var totalPages by remember { mutableStateOf(0) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        it?.let {
            selectedPdf = it
            resultFiles = emptyList()
            progress = 0
            totalPages = 0
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        DocShiftScaffold("PDF to image", "Export each page as a high quality image", onBack) {
            SectionCard("Document") {
                if (selectedPdf == null) Text("Choose a PDF to convert.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Text("PDF selected", fontWeight = FontWeight.Medium)
                SecondaryAction("Select PDF", !isProcessing) { picker.launch("application/pdf") }
            }
            if (isProcessing) SectionCard("Progress") { ProgressBlock(progress, totalPages, "Processing page " + progress + " of " + totalPages) }
            PrimaryAction("Convert to images", selectedPdf != null && !isProcessing) {
                val uri = selectedPdf ?: return@PrimaryAction
                isProcessing = true
                resultFiles = emptyList()
                progress = 0
                totalPages = 0
                scope.launch {
                    try {
                        resultFiles = withContext(Dispatchers.IO) {
                            PdfToImageConverter.convert(context, uri, File(cacheDir, "pdf_images")) { current, total ->
                                progress = current
                                totalPages = total
                            }
                        }
                    } catch (_: Exception) {
                        Toast.makeText(context, "PDF to image failed", Toast.LENGTH_SHORT).show()
                    } finally { isProcessing = false }
                }
            }
            if (resultFiles.isNotEmpty()) SectionCard("Result") {
                Text(resultFiles.size.toString() + " image" + if (resultFiles.size == 1) " generated" else "s generated", fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { resultFiles.forEach { DownloadSaver.save(context, it) }; Toast.makeText(context, "Images saved to Downloads", Toast.LENGTH_SHORT).show() }, Modifier.weight(1f)) { Text("Save all") }
                    OutlinedButton(onClick = {
                        val uris = resultFiles.map { FileProvider.getUriForFile(context, context.packageName + ".provider", it) }
                        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "image/*"
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share images"))
                    }, Modifier.weight(1f)) { Text("Share all") }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    resultFiles.forEachIndexed { index, file ->
                        ListItem(
                            headlineContent = { Text("Page " + (index + 1)) },
                            supportingContent = { Text(file.name) },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = {
                                        DownloadSaver.save(context, file)
                                        Toast.makeText(context, "Page " + (index + 1) + " saved", Toast.LENGTH_SHORT).show()
                                    }) { Image(painterResource(R.drawable.save), "Save page", Modifier.size(24.dp)) }
                                    IconButton(onClick = {
                                        DownloadSaver.share(context, FileProvider.getUriForFile(context, context.packageName + ".provider", file))
                                    }) { Image(painterResource(R.drawable.share), "Share page", Modifier.size(24.dp)) }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

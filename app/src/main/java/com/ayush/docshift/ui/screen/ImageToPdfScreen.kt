package com.ayush.docshift.ui.screen

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.FileProvider
import com.ayush.docshift.pdf.ImageToPdfConverter
import com.ayush.docshift.storage.DownloadSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ImageToPdfScreen(contentResolver: ContentResolver, cacheDir: File, initialUris: List<Uri> = emptyList(), onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var imageUris by remember { mutableStateOf(initialUris) }
    var resultFile by remember { mutableStateOf<File?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var fileNameInput by remember { mutableStateOf("") }

    fun convert(uris: List<Uri>) {
        if (uris.isEmpty()) return
        isProcessing = true
        progress = 0
        resultFile = null
        scope.launch(Dispatchers.IO) {
            try {
                val pdf = ImageToPdfConverter.convert(
                    resolver = contentResolver,
                    imageUris = uris,
                    outputDir = cacheDir,
                    fileName = "DocShift_" + System.currentTimeMillis()
                ) { current, _ ->
                    scope.launch(Dispatchers.Main.immediate) { progress = current }
                }
                withContext(Dispatchers.Main) { resultFile = pdf }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Image to PDF failed: " + (e.message ?: "Unknown error"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally { withContext(Dispatchers.Main) { isProcessing = false } }
        }
    }

    LaunchedEffect(initialUris) { if (initialUris.isNotEmpty()) convert(initialUris) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) {
        if (it.isNotEmpty()) { imageUris = it; convert(it) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        DocShiftScaffold("Image to PDF", "Combine multiple images into one PDF", onBack) {
            SectionCard("Images") {
                if (imageUris.isEmpty()) Text("Select one or more images to create a PDF.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Text(imageUris.size.toString() + " image" + if (imageUris.size == 1) " selected" else "s selected", fontWeight = FontWeight.Medium)
                SecondaryAction("Select images", !isProcessing) { picker.launch("image/*") }
            }
            if (isProcessing) SectionCard("Progress") { ProgressBlock(progress, imageUris.size, "Processing " + progress + " of " + imageUris.size) }
            if (resultFile == null && !isProcessing) PrimaryAction("Create PDF", imageUris.isNotEmpty()) { convert(imageUris) }
            resultFile?.let { file ->
                SectionCard("Result") {
                    Text("PDF created successfully", fontWeight = FontWeight.SemiBold)
                    Text(file.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { pendingFile = file; fileNameInput = file.nameWithoutExtension; showRenameDialog = true }, Modifier.weight(1f)) { Text("Save") }
                        OutlinedButton(onClick = { DownloadSaver.share(context, FileProvider.getUriForFile(context, context.packageName + ".provider", file)) }, Modifier.weight(1f)) { Text("Share") }
                    }
                }
            }
        }
    }

    if (showRenameDialog && pendingFile != null) {
        val original = pendingFile!!
        val extension = original.extension
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Save PDF") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose a file name before saving.")
                    OutlinedTextField(
                        value = fileNameInput,
                        onValueChange = { fileNameInput = it.replace(Regex("""[\\/:*?"<>|]"""), "") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("File name") }
                    )
                    Text("." + extension, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val safeName = fileNameInput.trim()
                    if (safeName.isNotEmpty()) {
                        val renamed = File(original.parent, safeName + "." + extension)
                        val savedFile = if (renamed.absolutePath == original.absolutePath) {
                            original
                        } else {
                            runCatching {
                                original.copyTo(renamed, overwrite = true)
                                renamed
                            }.getOrElse { original }
                        }
                        runCatching {
                            DownloadSaver.save(context, savedFile)
                        }.onSuccess {
                            Toast.makeText(context, "Saved as " + safeName + "." + extension, Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(
                                context,
                                "Save failed: " + (it.message ?: "Unknown error"),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        if (savedFile != original) savedFile.delete()
                    }
                    showRenameDialog = false
                    pendingFile = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false; pendingFile = null }) { Text("Cancel") } }
        )
    }
}

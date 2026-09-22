package com.ayush.docshift.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ayush.docshift.ui.theme.BrandDeep
import com.ayush.docshift.ui.theme.MutedSlate
import com.ayush.docshift.ui.theme.PrimaryAction
import com.ayush.docshift.ui.theme.SoftAccent
import com.ayush.docshift.ui.theme.SurfaceTint

private data class HomeTool(val title: String, val subtitle: String, val badge: String, val screen: Screen)

@Composable
fun HomeScreen(onSelect: (Screen) -> Unit) {
    val tools = listOf(
        HomeTool("DocSafe Vault", "Keep private files on this device.", "SECURE", Screen.DocSafe),
        HomeTool("Reduce image size", "Meet portal limits without guesswork.", "IMAGE", Screen.Compress),
        HomeTool("Resize image", "Precise dimensions, DPI and aspect control.", "RESIZE", Screen.Resize),
        HomeTool("Reduce PDF size", "Optimize documents for sharing and forms.", "PDF", Screen.PdfCompress),
        HomeTool("Combine images to PDF", "Arrange scans into one polished document.", "MERGE", Screen.ImageToPdf),
        HomeTool("Export PDF to images", "Extract high-fidelity page images.", "EXPORT", Screen.PdfToImage)
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HomeHero()
        Text("YOUR ON-DEVICE TOOLKIT", style = MaterialTheme.typography.labelLarge, color = BrandDeep)
        tools.forEach { tool -> HomeToolCard(tool, onSelect) }
        PrivacyPromise()
    }
}

@Composable
private fun HomeHero() {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = BrandDeep)) {
        Row(Modifier.fillMaxWidth().padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("DocShift", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Text("Private, precise tools for every document.", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFDCE5FF))
            }
            Icon(Icons.Default.Description, null, tint = Color.White, modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
private fun HomeToolCard(tool: HomeTool, onSelect: (Screen) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onSelect(tool.screen) },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tool.badge.take(1), modifier = Modifier.size(44.dp).background(SoftAccent, RoundedCornerShape(14.dp)).padding(top = 11.dp), color = PrimaryAction, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(tool.title, style = MaterialTheme.typography.titleMedium, color = BrandDeep, fontWeight = FontWeight.SemiBold)
                Text(tool.subtitle, style = MaterialTheme.typography.bodySmall, color = MutedSlate)
            }
            Icon(Icons.Default.ArrowForward, null, tint = PrimaryAction)
        }
    }
}

@Composable
private fun PrivacyPromise() {
    Row(Modifier.fillMaxWidth().background(SurfaceTint, RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Lock, null, tint = BrandDeep, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text("Private by design - every file stays on your device.", style = MaterialTheme.typography.bodySmall, color = BrandDeep, fontWeight = FontWeight.Medium)
    }
}

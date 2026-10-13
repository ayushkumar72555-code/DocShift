package com.ayush.docshift.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Paper = Color(0xFFF0E6D5)
private val PaperLight = Color(0xFFFFF9ED)
private val PaperGrey = Color(0xFFE5D8C0)
private val Ink = Color(0xFF30271F)
private val InkMuted = Color(0xFF756652)
private val CardBlue = Color(0xFF284B78)
private val CardBlueLight = Color(0xFFD8E2F0)
private val PaperLine = Color(0xFFB8A17E)
private val TicketShape = GenericShape { size, _ ->
    // Shape paths use pixel coordinates; proportions keep the notches consistent at any density.
    val notch = minOf(size.height * 0.09f, size.width * 0.025f)
    val corner = minOf(size.height * 0.045f, size.width * 0.035f)
    moveTo(corner, 0f)
    lineTo(size.width - corner, 0f)
    lineTo(size.width, corner)
    lineTo(size.width, size.height * 0.5f - notch)
    quadraticTo(size.width - notch, size.height * 0.5f, size.width, size.height * 0.5f + notch)
    lineTo(size.width, size.height - corner)
    lineTo(size.width - corner, size.height)
    lineTo(corner, size.height)
    lineTo(0f, size.height - corner)
    lineTo(0f, size.height * 0.5f + notch)
    quadraticTo(notch, size.height * 0.5f, 0f, size.height * 0.5f - notch)
    lineTo(0f, corner)
    close()
}

private data class HomeTool(
    val title: String,
    val subtitle: String,
    val screen: Screen,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onSelect: (Screen) -> Unit) {
    val tools = listOf(
        HomeTool("DocSafe Vault", "Keep private files on this device.", Screen.DocSafe, Icons.Default.Lock),
        HomeTool("Reduce image size", "Smaller files for forms and sharing.", Screen.Compress, Icons.Default.Image),
        HomeTool("Resize image", "Adjust dimensions and aspect ratio.", Screen.Resize, Icons.Default.Straighten),
        HomeTool("Reduce PDF size", "Make documents easier to share.", Screen.PdfCompress, Icons.Default.PictureAsPdf),
        HomeTool("Images to PDF", "Combine photos into one document.", Screen.ImageToPdf, Icons.Default.PhotoLibrary),
        HomeTool("PDF to images", "Export pages as image files.", Screen.PdfToImage, Icons.Default.PictureAsPdf)
    )
    Scaffold(
        containerColor = Paper,
        topBar = {
            // A custom paper header avoids Material top-bar defaults and keeps the brand aligned
            // with the same parchment surface as the rest of the screen.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Paper)
                    .statusBarsPadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .padding(horizontal = 22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HeaderRule(Modifier.weight(1f))
                        Text(
                            text = "DocShift",
                            color = Ink,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 27.sp,
                            letterSpacing = 0.2.sp
                        )
                        HeaderRule(Modifier.weight(1f))
                    }
                }
                Canvas(Modifier.fillMaxWidth().height(1.dp)) {
                    drawLine(PaperLine.copy(alpha = 0.65f), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                }
            }
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(Paper)
                .padding(innerPadding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 16.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("THE PAPER TOOLKIT", color = InkMuted, fontSize = 10.sp, letterSpacing = 2.4.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "What would you like to do?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Ink,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                )
                Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                    val y = size.height / 2f
                    drawLine(PaperLine.copy(alpha = 0.8f), Offset(0f, y), Offset(size.width * 0.78f, y), 1.dp.toPx())
                    drawCircle(Color(0xFF9B7650), radius = 2.2.dp.toPx(), center = Offset(size.width * 0.78f, y))
                }
            }

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Preserve the original two-column mobile grid; wider screens can use three.
                val columns = if (maxWidth >= 600.dp) 3 else 2
                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    tools.chunked(columns).forEach { rowTools ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowTools.forEach { tool ->
                                HomeToolCard(
                                    tool = tool,
                                    onSelect = onSelect,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(columns - rowTools.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            PrivacyPromise()
        }
    }
}

@Composable
private fun HeaderRule(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.weight(1f).height(12.dp)) {
            val y = size.height / 2f
            drawLine(PaperLine, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            drawLine(PaperLine, Offset(size.width * 0.42f, y - 3.dp.toPx()), Offset(size.width * 0.46f, y), strokeWidth = 1.dp.toPx())
            drawLine(PaperLine, Offset(size.width * 0.46f, y), Offset(size.width * 0.42f, y + 3.dp.toPx()), strokeWidth = 1.dp.toPx())
        }
    }
}

@Composable
private fun HomeToolCard(
    tool: HomeTool,
    onSelect: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val featured = tool.screen == Screen.DocSafe
    val cardColor = if (featured) CardBlue else if (tool.screen == Screen.Resize) PaperGrey else PaperLight
    val foreground = if (featured) Color(0xFFF6F0E5) else Ink

    Box(
        modifier = modifier
            .heightIn(min = 126.dp)
            .padding(end = 2.dp, bottom = 3.dp)
            .clickable(role = Role.Button) { onSelect(tool.screen) }
    ) {
        // Offset backing sheet makes the tile read as layered cardstock, not a flat Material card.
        Box(Modifier.fillMaxSize().offset(x = 2.dp, y = 3.dp).clip(TicketShape)
            .background(if (featured) Color(0xFF1D385F) else Color(0xFFD0BFA2)))
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = TicketShape,
            colors = CardDefaults.cardColors(containerColor = cardColor),
            border = BorderStroke(1.dp, if (featured) Color(0xFF6E88A9) else PaperLine),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 126.dp)) {
            PaperTexture(
                modifier = Modifier.matchParentSize(),
                dark = featured
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box {
                        Canvas(Modifier.size(40.dp)) {
                            val stamp = if (featured) Color(0xFF1E3A61) else Color(0xFFE8D9BC)
                            drawRoundRect(stamp, cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
                            drawRoundRect(
                                if (featured) CardBlueLight.copy(alpha = 0.7f) else PaperLine,
                                topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                                size = androidx.compose.ui.geometry.Size(size.width - 6.dp.toPx(), size.height - 6.dp.toPx()),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                                style = Stroke(1.dp.toPx())
                            )
                        }
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = null,
                            tint = if (featured) Color(0xFFF1E7D2) else Ink,
                            modifier = Modifier.align(Alignment.Center).size(23.dp)
                        )
                    }
                    Text(
                        text = when (tool.screen) {
                            Screen.DocSafe -> "VAULT"
                            Screen.Compress -> "01"
                            Screen.Resize -> "02"
                            Screen.PdfCompress -> "03"
                            Screen.ImageToPdf -> "04"
                            Screen.PdfToImage -> "05"
                            else -> "•"
                        },
                        color = if (featured) Color(0xFFD7E0EC).copy(alpha = 0.75f) else InkMuted.copy(alpha = 0.8f),
                        fontSize = 9.sp,
                        letterSpacing = 1.3.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = tool.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = foreground,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        }
    }
}

@Composable
private fun PaperTexture(modifier: Modifier = Modifier, dark: Boolean) {
    val lineColor = if (dark) Color(0xFFB8CDE6).copy(alpha = 0.24f) else PaperLine.copy(alpha = 0.42f)
    val grainColor = if (dark) Color(0xFFE7D7B9).copy(alpha = 0.16f) else Color(0xFF8B785B).copy(alpha = 0.14f)
    Canvas(modifier) {
        // A restrained graph-paper detail in the upper-right corner.
        val grid = 8.dp.toPx()
        val cornerWidth = size.width * 0.25f
        val cornerHeight = size.height * 0.22f
        val startX = size.width - cornerWidth
        for (x in startX.toInt()..size.width.toInt() step grid.toInt().coerceAtLeast(1)) {
            drawLine(lineColor, Offset(x.toFloat(), 0f), Offset(x.toFloat(), cornerHeight), strokeWidth = 0.6.dp.toPx())
        }
        for (y in 0..cornerHeight.toInt() step grid.toInt().coerceAtLeast(1)) {
            drawLine(lineColor, Offset(startX, y.toFloat()), Offset(size.width, y.toFloat()), strokeWidth = 0.6.dp.toPx())
        }
        // Deterministic fine grain; avoids runtime randomness and keeps rendering lightweight.
        for (i in 0 until 110) {
            val x = ((i * 47) % 997) / 997f * size.width
            val y = ((i * 83 + 17) % 991) / 991f * size.height
            drawCircle(grainColor, radius = if (i % 7 == 0) 1.1.dp.toPx() else 0.55.dp.toPx(), center = Offset(x, y))
        }
    }
}

@Composable
private fun PrivacyPromise() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TicketShape)
            .background(Color(0xFFE2D3BA))
    ) {
        Canvas(Modifier.matchParentSize()) {
            val inset = 4.dp.toPx()
            val dash = 4.dp.toPx()
            val gap = 3.dp.toPx()
            val color = Color(0xFF9D8566)
            var x = inset
            while (x < size.width - inset) {
                drawLine(color, Offset(x, inset), Offset((x + dash).coerceAtMost(size.width - inset), inset), strokeWidth = 1.dp.toPx())
                drawLine(color, Offset(x, size.height - inset), Offset((x + dash).coerceAtMost(size.width - inset), size.height - inset), strokeWidth = 1.dp.toPx())
                x += dash + gap
            }
            var y = inset
            while (y < size.height - inset) {
                drawLine(color, Offset(inset, y), Offset(inset, (y + dash).coerceAtMost(size.height - inset)), strokeWidth = 1.dp.toPx())
                drawLine(color, Offset(size.width - inset, y), Offset(size.width - inset, (y + dash).coerceAtMost(size.height - inset)), strokeWidth = 1.dp.toPx())
                y += dash + gap
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF344B73), modifier = Modifier.size(20.dp))
            Text(
                text = "Private • Files stay on your device.",
                color = Ink,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

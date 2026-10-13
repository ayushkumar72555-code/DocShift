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

private val Paper = Color(0xFFF3EBDD)
private val PaperLight = Color(0xFFFBF7EF)
private val PaperGrey = Color(0xFFE7E3DA)
private val Ink = Color(0xFF252B31)
private val InkMuted = Color(0xFF6E6A61)
private val CardBlue = Color(0xFF243F70)
private val CardBlueLight = Color(0xFFB8CBEA)
private val PaperLine = Color(0xFFB9AC96)
private val TicketShape = GenericShape { size, _ ->
    // Shape paths use pixel coordinates; proportions keep the notches consistent at any density.
    val notch = size.width * 0.035f
    val corner = size.height * 0.035f
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
            TopAppBar(
                title = {
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
                            fontSize = 27.sp
                        )
                        HeaderRule(Modifier.weight(1f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Paper,
                    scrolledContainerColor = Paper
                )
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .background(Paper)
                .padding(horizontal = 18.dp)
                .padding(top = 12.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "What would you like to do?",
                style = MaterialTheme.typography.headlineSmall,
                color = Ink,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold
            )

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Preserve the original two-column mobile grid; wider screens can use three.
                val columns = if (maxWidth >= 600.dp) 3 else 2
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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

    Card(
        modifier = modifier
            .heightIn(min = 150.dp)
            .clickable(role = Role.Button) { onSelect(tool.screen) },
        shape = TicketShape,
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = BorderStroke(1.dp, if (featured) Color(0xFF46618D) else PaperLine.copy(alpha = 0.8f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 150.dp)) {
            PaperTexture(
                modifier = Modifier.matchParentSize(),
                dark = featured
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box {
                        Canvas(Modifier.size(44.dp)) {
                            drawCircle(
                                color = if (featured) Color(0xFF1A3158) else PaperLine.copy(alpha = 0.22f),
                                radius = size.minDimension / 2f
                            )
                            val c = if (featured) CardBlueLight else InkMuted
                            drawCircle(c.copy(alpha = 0.35f), radius = size.minDimension / 2f - 1.dp.toPx(), style = Stroke(1.dp.toPx()))
                        }
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = null,
                            tint = if (featured) Color(0xFFE0D2B4) else Ink,
                            modifier = Modifier.align(Alignment.Center).size(23.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = if (featured) Color(0xFFE0D2B4) else InkMuted,
                        modifier = Modifier.size(18.dp)
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

@Composable
private fun PaperTexture(modifier: Modifier = Modifier, dark: Boolean) {
    val lineColor = if (dark) Color(0xFF7890B6).copy(alpha = 0.18f) else PaperLine.copy(alpha = 0.28f)
    val grainColor = if (dark) Color(0xFFE7D7B9).copy(alpha = 0.11f) else Color(0xFF8B785B).copy(alpha = 0.10f)
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

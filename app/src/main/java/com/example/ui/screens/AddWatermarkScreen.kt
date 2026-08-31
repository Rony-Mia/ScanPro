package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.SquareFoot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ScanProViewModel
import com.example.model.DocumentItem
import com.example.model.WatermarkPosition
import com.example.ui.components.ScanLineDivider
import com.example.ui.theme.ScanProGreenContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWatermarkScreen(
    viewModel: ScanProViewModel,
    onBack: () -> Unit,
    onWatermarkApplied: (DocumentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedDoc by viewModel.selectedDocument.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val doc = selectedDoc ?: return

    var watermarkText by remember { mutableStateOf("CONFIDENTIAL") }
    var selectedPosition by remember { mutableStateOf(WatermarkPosition.DIAGONAL) }
    var opacity by remember { mutableStateOf(0.35f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Add Watermark",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        enabled = !isProcessing,
                        modifier = Modifier.testTag("watermark_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            viewModel.watermarkDocument(doc, watermarkText, selectedPosition, opacity) { watermarked ->
                                onWatermarkApplied(watermarked)
                            }
                        },
                        enabled = !isProcessing && watermarkText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ScanProGreenContainer,
                            contentColor = Color.White,
                            disabledContainerColor = ScanProGreenContainer.copy(alpha = 0.6f),
                            disabledContentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("apply_watermark_button")
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Applying Watermark...",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = "Apply Watermark",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Live Interactive Preview Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(0.72f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White)
                        .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp)),
                    contentAlignment = when (selectedPosition) {
                        WatermarkPosition.CENTER -> Alignment.Center
                        WatermarkPosition.DIAGONAL -> Alignment.Center
                        WatermarkPosition.CORNER -> Alignment.BottomEnd
                    }
                ) {
                    Image(
                        painter = painterResource(id = doc.thumbnailRes),
                        contentDescription = "Document preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Dynamic Live Watermark Overlay
                    if (watermarkText.isNotBlank()) {
                        Text(
                            text = watermarkText,
                            color = Color.Red.copy(alpha = opacity),
                            fontSize = if (selectedPosition == WatermarkPosition.CORNER) 14.sp else 22.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            modifier = Modifier
                                .padding(if (selectedPosition == WatermarkPosition.CORNER) 10.dp else 0.dp)
                                .rotate(if (selectedPosition == WatermarkPosition.DIAGONAL) -35f else 0f)
                        )
                    }
                }
            }

            ScanLineDivider(opacity = 0.3f)

            // Watermark Text Input
            OutlinedTextField(
                value = watermarkText,
                onValueChange = { watermarkText = it },
                label = { Text("Watermark Text") },
                placeholder = { Text("e.g. CONFIDENTIAL, DRAFT, COPY") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("watermark_text_input")
            )

            // Position Selector
            Column {
                Text(
                    text = "Position",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PositionChip(
                        title = "Diagonal",
                        icon = Icons.Default.Rotate90DegreesCw,
                        isSelected = selectedPosition == WatermarkPosition.DIAGONAL,
                        onClick = { selectedPosition = WatermarkPosition.DIAGONAL },
                        modifier = Modifier.weight(1f)
                    )
                    PositionChip(
                        title = "Center",
                        icon = Icons.Default.FormatAlignCenter,
                        isSelected = selectedPosition == WatermarkPosition.CENTER,
                        onClick = { selectedPosition = WatermarkPosition.CENTER },
                        modifier = Modifier.weight(1f)
                    )
                    PositionChip(
                        title = "Corner",
                        icon = Icons.Default.SquareFoot,
                        isSelected = selectedPosition == WatermarkPosition.CORNER,
                        onClick = { selectedPosition = WatermarkPosition.CORNER },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Opacity Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Opacity",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(opacity * 100).toInt()}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ScanProGreenContainer
                    )
                }
                Slider(
                    value = opacity,
                    onValueChange = { opacity = it },
                    valueRange = 0.05f..0.95f,
                    colors = SliderDefaults.colors(
                        thumbColor = ScanProGreenContainer,
                        activeTrackColor = ScanProGreenContainer
                    ),
                    modifier = Modifier.testTag("watermark_opacity_slider")
                )
            }
        }
    }
}

@Composable
private fun PositionChip(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) ScanProGreenContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = modifier.height(44.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

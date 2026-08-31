package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ScanProViewModel
import com.example.ui.theme.ScanProGreenContainer

@Composable
fun CameraCaptureScreen(
    viewModel: ScanProViewModel,
    onClose: () -> Unit,
    onNavigateToReview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val draftPages by viewModel.activeDraftPages.collectAsState()
    var isFlashOn by remember { mutableStateOf(false) }
    var isAutoCapture by remember { mutableStateOf(true) }

    // Laser scan animation
    val infiniteTransition = rememberInfiniteTransition(label = "laser_transition")
    val laserPosition by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_pos"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F1418))
    ) {
        // Document viewfinder simulation canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            val rectWidth = canvasWidth * 0.78f
            val rectHeight = rectWidth * 1.35f
            val rectLeft = (canvasWidth - rectWidth) / 2f
            val rectTop = (canvasHeight - rectHeight) / 2.3f

            // Document bounding box guide
            drawRoundRect(
                color = Color(0x332D6A4F),
                topLeft = Offset(rectLeft, rectTop),
                size = Size(rectWidth, rectHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f)
            )

            drawRoundRect(
                color = ScanProGreenContainer,
                topLeft = Offset(rectLeft, rectTop),
                size = Size(rectWidth, rectHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f),
                style = Stroke(width = 3f)
            )

            // Scanning laser line
            val lineY = rectTop + rectHeight * laserPosition
            drawLine(
                color = Color(0xFF4EE1A0),
                start = Offset(rectLeft + 8f, lineY),
                end = Offset(rectLeft + rectWidth - 8f, lineY),
                strokeWidth = 4f
            )

            // Center reticle
            val centerX = canvasWidth / 2f
            val centerY = rectTop + rectHeight / 2f
            drawCircle(
                color = Color.White.copy(alpha = 0.6f),
                radius = 3f,
                center = Offset(centerX, centerY)
            )
            drawLine(
                color = Color.White.copy(alpha = 0.4f),
                start = Offset(centerX - 16f, centerY),
                end = Offset(centerX + 16f, centerY),
                strokeWidth = 1.5f
            )
            drawLine(
                color = Color.White.copy(alpha = 0.4f),
                start = Offset(centerX, centerY - 16f),
                end = Offset(centerX, centerY + 16f),
                strokeWidth = 1.5f
            )
        }

        // Top Overlay Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Close button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0x661C2B33))
                    .clickable { onClose() }
                    .testTag("camera_close_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Auto toggle badge
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isAutoCapture) ScanProGreenContainer else Color(0x661C2B33))
                    .clickable { isAutoCapture = !isAutoCapture }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("camera_auto_toggle"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (isAutoCapture) "Auto" else "Manual",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Page count badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(ScanProGreenContainer)
                    .clickable { onNavigateToReview() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("camera_page_count_badge"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${draftPages.size} ${if (draftPages.size == 1) "page" else "pages"}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Bottom Overlay Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flash toggle
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color(0x661C2B33))
                    .clickable {
                        isFlashOn = !isFlashOn
                        viewModel.showToast(if (isFlashOn) "Flash turned ON" else "Flash turned OFF")
                    }
                    .testTag("camera_flash_toggle"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Flash",
                    tint = if (isFlashOn) Color(0xFFFFD54F) else Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Shutter Button
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(4.dp, Color(0x66FFFFFF), CircleShape)
                    .clickable {
                        // Capture next page
                        val sampleImages = listOf(
                            R.drawable.sample_invoice,
                            R.drawable.sample_blueprint,
                            R.drawable.sample_spreadsheet,
                            R.drawable.sample_contract
                        )
                        val nextRes = sampleImages[draftPages.size % sampleImages.size]
                        viewModel.addPageToDraft(nextRes)
                    }
                    .padding(6.dp)
                    .testTag("camera_shutter_button"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(2.dp, Color(0xFFECEEEA), CircleShape)
                )
            }

            // Thumbnail Preview & Review Entry
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(2.dp, Color.White, RoundedCornerShape(10.dp))
                    .clickable { onNavigateToReview() }
                    .testTag("camera_preview_thumbnail_button")
            ) {
                if (draftPages.isNotEmpty()) {
                    val lastPage = draftPages.last()
                    Image(
                        painter = painterResource(id = lastPage.drawableRes),
                        contentDescription = "Recent scan preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(ScanProGreenContainer)
                            .border(1.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = draftPages.size.toString(),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x661C2B33)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoLibrary,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

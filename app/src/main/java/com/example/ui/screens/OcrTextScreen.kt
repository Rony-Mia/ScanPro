package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ScanProViewModel
import com.example.ui.components.ChangeDocumentButton
import com.example.ui.components.ScanLineDivider
import com.example.ui.theme.ScanProGreenContainer
import com.example.ui.theme.ScanProGreenPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrTextScreen(
    viewModel: ScanProViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedDoc by viewModel.selectedDocument.collectAsState()
    val doc = selectedDoc ?: return
    val clipboardManager = LocalClipboardManager.current
    val isProcessing by viewModel.isProcessing.collectAsState()
    val ocrProgress by viewModel.ocrProgress.collectAsState()

    // Extracted text now comes from the ViewModel's real OCR result (doc.ocrText),
    // not a hardcoded sample string.
    var extractedText by remember(doc.ocrText) {
        mutableStateOf(doc.ocrText)
    }

    // Run real on-device OCR extraction the first time this screen opens for a document
    // that doesn't already have extracted text cached.
    LaunchedEffect(doc.id) {
        if (doc.ocrText.isBlank()) {
            viewModel.extractTextFromDocument(doc) { result ->
                extractedText = result
            }
        }
    }

    // Laser scan animation (only animates while extraction is actually running)
    val infiniteTransition = rememberInfiniteTransition(label = "ocr_laser")
    val laserY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "laser_anim"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Extract Text (OCR)",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("ocr_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    ChangeDocumentButton(
                        viewModel = viewModel,
                        currentDocId = doc.id,
                        enabled = !isProcessing,
                        testTagPrefix = "ocr"
                    ) { selected ->
                        viewModel.selectDocument(selected)
                    }
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(extractedText))
                            viewModel.showToast("Copied to clipboard")
                        },
                        modifier = Modifier.testTag("ocr_copy_all_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = ScanProGreenContainer
                        )
                    }
                    IconButton(
                        onClick = { viewModel.showToast("Sharing extracted text...") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.showToast("Exported as ${doc.title.substringBeforeLast(".")}.txt")
                        },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("ocr_export_txt_button")
                    ) {
                        Icon(Icons.Outlined.TextSnippet, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export .txt")
                    }

                    Button(
                        onClick = {
                            viewModel.showToast("Exported as ${doc.title.substringBeforeLast(".")}.docx")
                        },
                        enabled = !isProcessing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ScanProGreenContainer,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("ocr_export_docx_button")
                    ) {
                        Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export .docx")
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Scanner preview thumbnail with laser animation + real OCR status
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Document thumbnail with animated scan beam (only while processing)
                    Box(
                        modifier = Modifier
                            .size(width = 54.dp, height = 72.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White)
                    ) {
                        Image(
                            painter = painterResource(id = doc.thumbnailRes),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        if (isProcessing) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val y = size.height * laserY
                                drawLine(
                                    color = Color(0xFF4EE1A0),
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 3f
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        if (isProcessing) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Extracting text...",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ScanProGreenContainer
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = "${(ocrProgress * 100).toInt()}%",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { ocrProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = ScanProGreenContainer,
                                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = ScanProGreenContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Text Extraction Complete",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ScanProGreenContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "${extractedText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size} words detected on device",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            ScanLineDivider(opacity = 0.3f)

            // Editable extracted text
            OutlinedTextField(
                value = extractedText,
                onValueChange = { extractedText = it },
                label = { Text(if (isProcessing) "Extracting..." else "Extracted Text") },
                enabled = !isProcessing,
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("ocr_editable_text_area")
            )
        }
    }
}

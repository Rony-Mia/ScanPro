package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.OcrEngine
import com.example.data.ScanProViewModel
import com.example.model.DocumentItem
import com.example.ui.components.ChangeDocumentButton
import com.example.ui.components.DocThumbnailImage
import com.example.ui.components.ManageOcrLanguagesDialog
import com.example.ui.components.NoDocSelectedScaffold
import com.example.ui.components.ScanLineDivider
import com.example.ui.theme.ScanProGreenContainer
import com.example.util.ShareUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrTextScreen(
    viewModel: ScanProViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedDoc by viewModel.selectedDocument.collectAsState()
    val allDocs by viewModel.documents.collectAsState()
    val selectedOcrLang by viewModel.ocrLanguage.collectAsState()
    val installedLangs by viewModel.installedOcrLanguages.collectAsState()

    var showManageLanguagesDialog by remember { mutableStateOf(false) }
    var showLanguagePickerMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshInstalledOcrLanguages()
    }

    // If no doc is currently selected, auto-select the first doc from library if present
    LaunchedEffect(selectedDoc, allDocs) {
        if (selectedDoc == null && allDocs.isNotEmpty()) {
            viewModel.selectDocument(allDocs.first())
        }
    }

    val doc = selectedDoc
    if (doc == null) {
        NoDocSelectedScaffold(
            toolTitle = "Extract Text (OCR)",
            toolIcon = Icons.Outlined.TextSnippet,
            viewModel = viewModel,
            onBack = onBack,
            onDocumentSelected = { viewModel.selectDocument(it) },
            modifier = modifier
        )
        return
    }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val isProcessing by viewModel.isProcessing.collectAsState()
    val ocrProgress by viewModel.ocrProgress.collectAsState()

    var extractedText by remember(doc.id, doc.ocrText) {
        mutableStateOf(doc.ocrText)
    }

    // Run real on-device Tesseract OCR extraction the first time this screen opens for a document
    // that doesn't already have extracted text cached.
    LaunchedEffect(doc.id, selectedOcrLang) {
        if (doc.ocrText.isBlank()) {
            viewModel.extractTextFromDocument(doc, selectedOcrLang) { result ->
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

    if (showManageLanguagesDialog) {
        ManageOcrLanguagesDialog(
            viewModel = viewModel,
            onDismiss = {
                showManageLanguagesDialog = false
                viewModel.refreshInstalledOcrLanguages()
            }
        )
    }

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
                        onClick = {
                            ShareUtil.shareText(
                                context = context,
                                text = extractedText,
                                subject = "Extracted text - ${doc.title}"
                            )
                        },
                        modifier = Modifier.testTag("ocr_share_button")
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
                            viewModel.exportOcrText(doc.copy(ocrText = extractedText), "txt")
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
                            viewModel.exportOcrText(doc.copy(ocrText = extractedText), "docx")
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
                        DocThumbnailImage(
                            document = doc,
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
                            val wordCount = extractedText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
                            Text(
                                text = "$wordCount words detected on device (Tesseract offline)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Language Selector Row
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !isProcessing) { showLanguagePickerMenu = true }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .testTag("ocr_language_selector_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = ScanProGreenContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val displayLang = when (selectedOcrLang) {
                                "eng+ben" -> "English + Bengali (eng+ben)"
                                "eng" -> "English (eng)"
                                "ben" -> "Bengali (ben)"
                                else -> OcrEngine.AVAILABLE_LANGUAGES.find { it.code == selectedOcrLang }?.name ?: selectedOcrLang
                            }
                            Text(
                                text = displayLang,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        DropdownMenu(
                            expanded = showLanguagePickerMenu,
                            onDismissRequest = { showLanguagePickerMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("English + Bengali (Default)") },
                                onClick = {
                                    viewModel.setOcrLanguage("eng+ben")
                                    showLanguagePickerMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("English (eng)") },
                                onClick = {
                                    viewModel.setOcrLanguage("eng")
                                    showLanguagePickerMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Bengali (বাংলা - ben)") },
                                onClick = {
                                    viewModel.setOcrLanguage("ben")
                                    showLanguagePickerMenu = false
                                }
                            )
                            // Other installed languages
                            val extraInstalled = installedLangs.filter { it != "eng" && it != "ben" }
                            if (extraInstalled.isNotEmpty()) {
                                HorizontalDivider()
                                extraInstalled.forEach { code ->
                                    val lang = OcrEngine.AVAILABLE_LANGUAGES.find { it.code == code }
                                    DropdownMenuItem(
                                        text = { Text(lang?.name ?: code) },
                                        onClick = {
                                            viewModel.setOcrLanguage(code)
                                            showLanguagePickerMenu = false
                                        }
                                    )
                                }
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Default.Download, contentDescription = null, tint = ScanProGreenContainer, modifier = Modifier.size(18.dp))
                                },
                                text = { Text("Manage OCR Languages...", color = ScanProGreenContainer, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    showLanguagePickerMenu = false
                                    showManageLanguagesDialog = true
                                }
                            )
                        }
                    }

                    // Re-run button
                    FilledTonalButton(
                        onClick = {
                            viewModel.extractTextFromDocument(doc, selectedOcrLang) { result ->
                                extractedText = result
                            }
                        },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("ocr_rerun_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Re-run OCR",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Re-run", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            ScanLineDivider(opacity = 0.3f)

            // Editable extracted text
            OutlinedTextField(
                value = extractedText,
                onValueChange = { extractedText = it },
                label = { Text(if (isProcessing) "Extracting text..." else "Extracted Text (Editable)") },
                placeholder = { Text("No text detected yet. Tap 'Re-run' or edit text manually here.") },
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

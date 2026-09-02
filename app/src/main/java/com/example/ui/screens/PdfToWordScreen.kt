package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.DocxEngine
import com.example.data.ScanProViewModel
import com.example.model.DocumentItem
import com.example.ui.components.ChangeDocumentButton
import com.example.ui.components.DocThumbnailImage
import com.example.ui.components.NoDocSelectedScaffold
import com.example.ui.theme.*
import com.example.util.ShareUtil
import java.io.File

/**
 * PDF to Word (.docx) Converter screen.
 *
 * Supports:
 * - Two-stage extraction: Born-digital PDF direct Unicode extraction (100% accurate) + Scanned PDF offline Tesseract OCR fallback.
 * - Font styling with Bengali Unicode fonts (SolaimanLipi, Kalpurush, Nikosh), English standards, and Legacy compatibility (Sutonny MJ).
 * - Real OOXML (.docx) output compliant with Word and Google Docs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToWordScreen(
    viewModel: ScanProViewModel,
    onBack: () -> Unit,
    onConverted: (DocumentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedDoc by viewModel.selectedDocument.collectAsState()
    val allDocs by viewModel.documents.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    LaunchedEffect(selectedDoc, allDocs) {
        if (selectedDoc == null && allDocs.isNotEmpty()) {
            viewModel.selectDocument(allDocs.first())
        }
    }

    val doc = selectedDoc
    if (doc == null) {
        NoDocSelectedScaffold(
            toolTitle = "PDF to Word (.docx)",
            toolIcon = Icons.Default.Description,
            viewModel = viewModel,
            onBack = onBack,
            onDocumentSelected = { viewModel.selectDocument(it) },
            modifier = modifier
        )
        return
    }

    var selectedFontIndex by remember { mutableIntStateOf(0) }
    var fontDropdownExpanded by remember { mutableStateOf(false) }
    var conversionResult by remember { mutableStateOf<DocxEngine.DocxConversionResult?>(null) }
    var convertedDoc by remember { mutableStateOf<DocumentItem?>(null) }

    val currentFont = DocxEngine.SUPPORTED_FONTS.getOrElse(selectedFontIndex) { DocxEngine.SUPPORTED_FONTS.first() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "PDF to Word (.docx)",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        enabled = !isProcessing,
                        modifier = Modifier.testTag("pdf_to_word_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    ChangeDocumentButton(
                        viewModel = viewModel,
                        currentDocId = doc.id,
                        testTagPrefix = "pdf_to_word_doc",
                        onDocumentSelected = {
                            viewModel.selectDocument(it)
                            conversionResult = null
                            convertedDoc = null
                        }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Selected PDF Document Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DocThumbnailImage(
                        document = doc,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = doc.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "${doc.pageCount} ${if (doc.pageCount == 1) "Page" else "Pages"}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "•",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = doc.fileSize,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 2. Font Selection Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FontDownload,
                            contentDescription = null,
                            tint = ScanProGreenContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Target Font (for .docx)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Dropdown selector box
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedCard(
                            onClick = { if (!isProcessing) fontDropdownExpanded = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pdf_to_word_font_selector")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = currentFont.displayName,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = currentFont.category,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select font",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = fontDropdownExpanded,
                            onDismissRequest = { fontDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            DocxEngine.SUPPORTED_FONTS.forEachIndexed { index, fontOption ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = fontOption.displayName,
                                                    fontWeight = if (index == selectedFontIndex) FontWeight.Bold else FontWeight.Normal
                                                )
                                                if (fontOption.isLegacy) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Legacy",
                                                        fontSize = 10.sp,
                                                        color = ScanProAccentRed,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            Text(
                                                text = fontOption.category,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedFontIndex = index
                                        fontDropdownExpanded = false
                                    },
                                    leadingIcon = {
                                        if (index == selectedFontIndex) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = ScanProGreenContainer
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Warning banner if legacy font is selected
                    AnimatedVisibility(visible = currentFont.warning != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ScanProErrorContainer.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = ScanProError,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .padding(top = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = currentFont.warning ?: "",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }

            // 3. Conversion Info / Strategy Explanation
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ToolCategoryScan.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = ScanProGreenContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Smart Two-Stage Conversion",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = ScanProGreenContainer
                        )
                    }

                    Text(
                        text = "• Born-digital PDF: Direct Unicode text extraction via PDFBox (100% accurate Bengali & English).\n" +
                               "• Scanned / Image PDF: Automatic fallback to offline Tesseract OCR.\n" +
                               "• Pure OOXML: Produces genuine, editable .docx files compatible with MS Word and Google Docs.\n" +
                               "• Note: Text content and paragraphs are preserved. Complex visual tables or column layouts are formatted simply.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }

            // 4. Action Button
            Button(
                onClick = {
                    viewModel.convertPdfToDocx(
                        doc = doc,
                        fontFamily = currentFont.name,
                        isComplexScript = currentFont.isComplexScript
                    ) { resultDoc, details ->
                        convertedDoc = resultDoc
                        conversionResult = details
                        onConverted(resultDoc)
                    }
                },
                enabled = !isProcessing,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ScanProGreenContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("pdf_to_word_convert_button")
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Converting PDF to Word...", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Default.Transform, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Convert to Word (.docx)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // 5. Conversion Result Card (if completed)
            AnimatedVisibility(visible = conversionResult != null && convertedDoc != null) {
                val res = conversionResult
                val cDoc = convertedDoc
                if (res != null && cDoc != null) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ScanProGreenContainer.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(ScanProGreenLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = ScanProGreenPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Conversion Successful!",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = ScanProGreenPrimary
                                    )
                                    Text(
                                        text = cDoc.title,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // Extraction method badge
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (res.extractionMethod == DocxEngine.ExtractionMethod.TEXT_LAYER) {
                                    ScanProGreenLight.copy(alpha = 0.4f)
                                } else {
                                    ScanProSecondaryContainer.copy(alpha = 0.6f)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (res.extractionMethod == DocxEngine.ExtractionMethod.TEXT_LAYER) {
                                            Icons.Default.CheckCircle
                                        } else {
                                            Icons.Default.FindInPage
                                        },
                                        contentDescription = null,
                                        tint = if (res.extractionMethod == DocxEngine.ExtractionMethod.TEXT_LAYER) {
                                            ScanProGreenPrimary
                                        } else {
                                            ScanProSecondary
                                        },
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = res.extractionMethod.displayName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // Stats row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${res.paragraphCount}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Paragraphs",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${res.characterCount}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Characters",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = cDoc.fileSize,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "File Size",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Warning note if any
                            if (res.warnings.isNotEmpty()) {
                                res.warnings.forEach { warning ->
                                    Text(
                                        text = "• $warning",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Font reference notice
                            Text(
                                text = "Font Note: \"${currentFont.displayName}\" is referenced in the .docx. Devices with this font installed will render with full native typography.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp
                            )

                            // Action buttons (Open & Share)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        ShareUtil.shareDocument(context, cDoc)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("pdf_to_word_share_button")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Share .docx", fontSize = 13.sp)
                                }

                                Button(
                                    onClick = {
                                        val filePath = cDoc.filePath
                                        if (!filePath.isNullOrBlank()) {
                                            val file = File(filePath)
                                            if (file.exists()) {
                                                try {
                                                    val uri = FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.fileprovider",
                                                        file
                                                    )
                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(uri, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(Intent.createChooser(intent, "Open Word Document"))
                                                } catch (e: Exception) {
                                                    ShareUtil.shareDocument(context, cDoc)
                                                }
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ScanProGreenContainer),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("pdf_to_word_open_button")
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open File", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

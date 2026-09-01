package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.ScanProViewModel
import com.example.model.DocFormat
import com.example.model.DocumentItem
import com.example.ui.components.ChangeDocumentButton
import com.example.ui.components.NoDocSelectedScaffold
import com.example.ui.components.ScanLineDivider
import com.example.ui.theme.ScanProAccentRed
import com.example.ui.theme.ScanProGreenContainer
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitPdfScreen(
    viewModel: ScanProViewModel,
    onBack: () -> Unit,
    onSplitCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            toolTitle = "Split PDF",
            toolIcon = Icons.AutoMirrored.Filled.CallSplit,
            viewModel = viewModel,
            onBack = onBack,
            onDocumentSelected = { viewModel.selectDocument(it) },
            modifier = modifier
        )
        return
    }

    val totalPages = doc.pageCount.coerceAtLeast(1)
    var splitCuts by remember(doc.id) { mutableStateOf<Set<Int>>(if (totalPages > 1) setOf(1) else emptySet()) }
    var pageBitmaps by remember(doc.id) { mutableStateOf<List<Bitmap>>(emptyList()) }

    LaunchedEffect(doc.id) {
        viewModel.loadPageThumbnails(doc) { bitmaps ->
            pageBitmaps = bitmaps
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Split PDF",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        enabled = !isProcessing,
                        modifier = Modifier.testTag("split_back_button")
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
                        testTagPrefix = "split"
                    ) { selected ->
                        viewModel.selectDocument(selected)
                        splitCuts = emptySet()
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
                    val partsCount = splitCuts.size + 1
                    Button(
                        onClick = {
                            viewModel.splitDocument(doc, splitCuts.toList().sorted()) {
                                onSplitCompleted()
                            }
                        },
                        enabled = !isProcessing && splitCuts.isNotEmpty(),
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
                            .testTag("split_confirm_button")
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Splitting PDF...",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = if (splitCuts.isEmpty()) "Select Split Points" else "Split into $partsCount Files",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Resulting in: ${doc.title.substringBeforeLast(".")}_part1.pdf ... part$partsCount.pdf",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
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
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            // Document summary card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ScanProAccentRed.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = ScanProAccentRed)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(doc.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("${doc.pageCount} pages • ${doc.fileSize}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            ScanLineDivider(opacity = 0.3f)
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Tap scissor icons between pages to place split cuts:",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Horizontal Pages Strip with Scissors Cut Points
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(totalPages) { pageIdx ->
                    val pageNum = pageIdx + 1
                    val pageBitmap = pageBitmaps.getOrNull(pageIdx)
                    val draftPage = doc.pages.getOrNull(pageIdx)

                    // Page Card
                    Box(
                        modifier = Modifier
                            .size(width = 110.dp, height = 160.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            pageBitmap != null -> {
                                Image(
                                    bitmap = pageBitmap.asImageBitmap(),
                                    contentDescription = "Page $pageNum",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            draftPage != null && !draftPage.imageUri.isNullOrEmpty() -> {
                                AsyncImage(
                                    model = draftPage.imageUri,
                                    contentDescription = "Page $pageNum",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            doc.format == DocFormat.JPG && !doc.filePath.isNullOrEmpty() -> {
                                AsyncImage(
                                    model = File(doc.filePath),
                                    contentDescription = "Page $pageNum",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            !doc.thumbnailUri.isNullOrEmpty() -> {
                                AsyncImage(
                                    model = doc.thumbnailUri,
                                    contentDescription = "Page $pageNum",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = ScanProGreenContainer,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Page $pageNum",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Scissor Cut Point between pages
                    if (pageIdx < totalPages - 1) {
                        val isCut = splitCuts.contains(pageNum)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .clickable {
                                    val newCuts = splitCuts.toMutableSet()
                                    if (isCut) newCuts.remove(pageNum) else newCuts.add(pageNum)
                                    splitCuts = newCuts
                                }
                                .testTag("scissor_cut_$pageNum")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(if (isCut) ScanProAccentRed else MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, if (isCut) ScanProAccentRed else Color.LightGray, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCut,
                                    contentDescription = "Cut",
                                    tint = if (isCut) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isCut) "Split here" else "Cut",
                                fontSize = 10.sp,
                                fontWeight = if (isCut) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCut) ScanProAccentRed else Color.Gray
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Split summary banner
            Surface(
                color = ScanProGreenContainer.copy(alpha = 0.1f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (splitCuts.isEmpty()) "Tap on any scissors icon above to set split points" else "${splitCuts.size} splits selected (${splitCuts.size + 1} independent PDF files will be generated)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ScanProGreenContainer
                    )
                }
            }
        }
    }
}

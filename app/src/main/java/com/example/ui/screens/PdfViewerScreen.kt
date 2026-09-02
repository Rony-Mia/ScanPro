package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.CallMerge
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.ScanProViewModel
import com.example.model.DocFormat
import com.example.model.DocumentItem
import com.example.ui.theme.ScanProGreenContainer
import com.example.ui.theme.ScanProInk
import com.example.util.ShareUtil
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    document: DocumentItem,
    viewModel: ScanProViewModel,
    onBack: () -> Unit,
    onNavigateToMerge: () -> Unit,
    onNavigateToCompress: () -> Unit,
    onNavigateToOcr: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentPageIndex by remember { mutableStateOf(0) }
    var isThumbnailStripVisible by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember(document.title) { mutableStateOf(document.title) }

    val totalPages = document.pageCount.coerceAtLeast(1)

    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingPage by remember { mutableStateOf(true) }
    var pageRenderError by remember { mutableStateOf(false) }
    var thumbnailBitmaps by remember(document.id) { mutableStateOf<Map<Int, Bitmap>>(emptyMap()) }
    var reloadTrigger by remember { mutableStateOf(0) }

    // Page rendering effect for PDF / converted document files
    LaunchedEffect(document.id, currentPageIndex, reloadTrigger) {
        isLoadingPage = true
        pageRenderError = false
        val activeDraftPage = document.pages.getOrNull(currentPageIndex)

        if (activeDraftPage != null && !activeDraftPage.imageUri.isNullOrEmpty()) {
            // Fresh scan page with local image uri
            pageBitmap = null
            isLoadingPage = false
        } else {
            // Render real PDF page directly from file via native PdfRenderer
            val rendered = viewModel.renderPdfPage(document, currentPageIndex)
            if (rendered != null) {
                pageBitmap = rendered
                pageRenderError = false
            } else {
                // If direct render returned null, check if single JPG file exists
                if (document.format == DocFormat.JPG && !document.filePath.isNullOrEmpty() && File(document.filePath).exists()) {
                    pageBitmap = null
                    pageRenderError = false
                } else if (!document.thumbnailUri.isNullOrEmpty()) {
                    pageBitmap = null
                    pageRenderError = false
                } else {
                    pageBitmap = null
                    pageRenderError = true
                }
            }
            isLoadingPage = false
        }
    }

    // Lazy load thumbnails for carousel
    LaunchedEffect(document.id, isThumbnailStripVisible) {
        if (isThumbnailStripVisible && thumbnailBitmaps.isEmpty()) {
            viewModel.loadPageThumbnails(document) { bitmaps ->
                thumbnailBitmaps = bitmaps.mapIndexed { idx, bmp -> idx to bmp }.toMap()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clickable { showRenameDialog = true }
                            .testTag("viewer_title_rename_trigger")
                    ) {
                        Text(
                            text = document.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 220.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Rename",
                            tint = ScanProGreenContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("viewer_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showToast("Document size: ${document.fileSize}, ${document.date}") }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More",
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
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ViewerActionButton(
                        icon = Icons.Outlined.Share,
                        label = "Share",
                        onClick = { ShareUtil.shareDocument(context, document) },
                        testTag = "viewer_share_button"
                    )
                    ViewerActionButton(
                        icon = Icons.AutoMirrored.Outlined.CallMerge,
                        label = "Merge",
                        onClick = onNavigateToMerge,
                        testTag = "viewer_merge_button"
                    )
                    ViewerActionButton(
                        icon = Icons.Outlined.Compress,
                        label = "Compress",
                        onClick = onNavigateToCompress,
                        testTag = "viewer_compress_button"
                    )
                    ViewerActionButton(
                        icon = Icons.Outlined.DocumentScanner,
                        label = "OCR",
                        onClick = onNavigateToOcr,
                        testTag = "viewer_ocr_button"
                    )
                    ViewerActionButton(
                        icon = Icons.Outlined.Print,
                        label = "Print",
                        onClick = { viewModel.printDocument(context, document) },
                        testTag = "viewer_print_button"
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            // Document Page Viewport
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.92f)
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                val activeDraftPage = document.pages.getOrNull(currentPageIndex)

                when {
                    isLoadingPage -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            CircularProgressIndicator(
                                color = ScanProGreenContainer,
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Rendering page ${currentPageIndex + 1}...",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    pageRenderError -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(42.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Unable to render page ${currentPageIndex + 1}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "The PDF file could not be loaded or is corrupted.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            OutlinedButton(
                                onClick = { reloadTrigger++ },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry", fontSize = 12.sp)
                            }
                        }
                    }

                    pageBitmap != null -> {
                        Image(
                            bitmap = pageBitmap!!.asImageBitmap(),
                            contentDescription = "Document Page ${currentPageIndex + 1}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }

                    activeDraftPage != null && !activeDraftPage.imageUri.isNullOrEmpty() -> {
                        AsyncImage(
                            model = activeDraftPage.imageUri,
                            contentDescription = "Document Page ${currentPageIndex + 1}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }

                    document.format == DocFormat.JPG && !document.filePath.isNullOrEmpty() -> {
                        AsyncImage(
                            model = File(document.filePath),
                            contentDescription = "Document Page ${currentPageIndex + 1}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }

                    !document.thumbnailUri.isNullOrEmpty() -> {
                        AsyncImage(
                            model = document.thumbnailUri,
                            contentDescription = "Document Page ${currentPageIndex + 1}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }

                    else -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No page content available", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Page Navigation Arrows (Overlay left/right)
                if (currentPageIndex > 0) {
                    IconButton(
                        onClick = { currentPageIndex = (currentPageIndex - 1).coerceAtLeast(0) },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 4.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.3f))
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Page", tint = Color.White)
                    }
                }

                if (currentPageIndex < totalPages - 1) {
                    IconButton(
                        onClick = { currentPageIndex = (currentPageIndex + 1).coerceAtMost(totalPages - 1) },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 4.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.3f))
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next Page", tint = Color.White)
                    }
                }

                // Page Indicator Pill
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xCC1C2B33))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${currentPageIndex + 1} / $totalPages",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Thumbnail Toggle Floating Button
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 12.dp)
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.9f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .clickable { isThumbnailStripVisible = !isThumbnailStripVisible }
                        .testTag("viewer_carousel_toggle"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ViewCarousel,
                        contentDescription = "Thumbnails",
                        tint = if (isThumbnailStripVisible) ScanProGreenContainer else ScanProInk,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Expandable Thumbnail Strip Overlay
            AnimatedVisibility(
                visible = isThumbnailStripVisible,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    LazyRow(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(totalPages) { pageIdx ->
                            val isCurrent = pageIdx == currentPageIndex
                            val pageItem = document.pages.getOrNull(pageIdx)
                            val cachedThumbnailBitmap = thumbnailBitmaps[pageIdx]

                            Box(
                                modifier = Modifier
                                    .size(width = 54.dp, height = 72.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(
                                        width = if (isCurrent) 2.dp else 1.dp,
                                        color = if (isCurrent) ScanProGreenContainer else Color.LightGray,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        currentPageIndex = pageIdx
                                    }
                            ) {
                                when {
                                    cachedThumbnailBitmap != null -> {
                                        Image(
                                            bitmap = cachedThumbnailBitmap.asImageBitmap(),
                                            contentDescription = "Thumb ${pageIdx + 1}",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    pageItem != null && !pageItem.imageUri.isNullOrEmpty() -> {
                                        AsyncImage(
                                            model = pageItem.imageUri,
                                            contentDescription = "Thumb ${pageIdx + 1}",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    document.format == DocFormat.JPG && !document.filePath.isNullOrEmpty() -> {
                                        AsyncImage(
                                            model = File(document.filePath),
                                            contentDescription = "Thumb ${pageIdx + 1}",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    !document.thumbnailUri.isNullOrEmpty() -> {
                                        AsyncImage(
                                            model = document.thumbnailUri,
                                            contentDescription = "Thumb ${pageIdx + 1}",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    else -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color(0xFFEEEEEE)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("${pageIdx + 1}", fontSize = 12.sp, color = Color.Gray)
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .fillMaxWidth()
                                        .padding(vertical = 1.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${pageIdx + 1}",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename PDF") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("File Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            viewModel.renameDocument(document.id, renameText)
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text("Save", color = ScanProGreenContainer, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ViewerActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = ScanProInk,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = ScanProInk
        )
    }
}

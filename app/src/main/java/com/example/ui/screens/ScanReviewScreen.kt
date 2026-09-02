package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.ScanProViewModel
import com.example.model.DocumentItem
import com.example.model.PageFilter
import com.example.ui.components.ScanLineDivider
import com.example.ui.theme.ScanProAccentRed
import com.example.ui.theme.ScanProGreenContainer
import com.example.ui.theme.ScanProGreenPrimary
import com.example.util.rememberDocumentScannerLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanReviewScreen(
    viewModel: ScanProViewModel,
    onBack: () -> Unit,
    onDone: (DocumentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    // Real scanner (camera + gallery import) instead of the old fake camera mock.
    // Appends newly captured/imported pages to the current draft.
    val launchAddPageScan = rememberDocumentScannerLauncher(viewModel = viewModel) { uris ->
        viewModel.addScannedPagesFromUris(uris)
    }

    val draftPages by viewModel.activeDraftPages.collectAsState()
    val selectedIndex by viewModel.selectedDraftIndex.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    val activePage = draftPages.getOrNull(selectedIndex) ?: draftPages.firstOrNull()
    var isCropModeActive by remember { mutableStateOf(true) }

    // Corner handle offsets for crop overlay
    var cropTopLeftX by remember { mutableStateOf(0f) }
    var cropTopLeftY by remember { mutableStateOf(0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Review Scans (${draftPages.size})",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        enabled = !isProcessing,
                        modifier = Modifier.testTag("scan_review_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.finishScanAndSave { savedDoc ->
                                onDone(savedDoc)
                            }
                        },
                        enabled = !isProcessing && draftPages.isNotEmpty(),
                        modifier = Modifier.testTag("scan_review_done_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    color = ScanProGreenContainer,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Saving...",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ScanProGreenContainer
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = ScanProGreenContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Done",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ScanProGreenContainer
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Filmstrip of Thumbnails
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(draftPages) { index, page ->
                    val isSelected = index == selectedIndex

                    Box(
                        modifier = Modifier
                            .size(width = 84.dp, height = 112.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) ScanProGreenContainer else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { viewModel.selectDraftPageIndex(index) }
                            .testTag("filmstrip_thumb_$index")
                    ) {
                        AsyncImage(
                            model = page.imageUri ?: page.drawableRes,
                            contentDescription = "Page ${page.pageNumber}",
                            modifier = Modifier
                                .fillMaxSize()
                                .rotate(page.rotationAngle),
                            contentScale = ContentScale.Crop,
                            colorFilter = if (page.filter == PageFilter.GRAYSCALE || page.filter == PageFilter.BW) {
                                ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                            } else null
                        )

                        // Page number badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                                .background(
                                    Color.White.copy(alpha = 0.85f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = page.pageNumber.toString(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Drag indicator
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 3.dp)
                                .background(
                                    Color.White.copy(alpha = 0.8f),
                                    RoundedCornerShape(3.dp)
                                )
                                .padding(horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragIndicator,
                                contentDescription = "Drag",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            ScanLineDivider(opacity = 0.35f)

            // Central Preview Area with Interactive Crop Overlay
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                if (activePage != null) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxHeight(0.92f)
                            .aspectRatio(0.72f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                    ) {
                        val containerWidthPx = constraints.maxWidth.toFloat()
                        val containerHeightPx = constraints.maxHeight.toFloat()

                        AsyncImage(
                            model = activePage.imageUri ?: activePage.drawableRes,
                            contentDescription = "Active Document Preview",
                            modifier = Modifier
                                .fillMaxSize()
                                .rotate(activePage.rotationAngle),
                            contentScale = ContentScale.Fit,
                            colorFilter = if (activePage.filter == PageFilter.GRAYSCALE || activePage.filter == PageFilter.BW) {
                                ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                            } else null
                        )

                        // Interactive Crop Overlay with Draggable Corner Handles
                        if (isCropModeActive) {
                            val leftPx = (activePage.cropLeft * containerWidthPx).coerceIn(0f, containerWidthPx)
                            val topPx = (activePage.cropTop * containerHeightPx).coerceIn(0f, containerHeightPx)
                            val rightPx = (activePage.cropRight * containerWidthPx).coerceIn(leftPx + 20f, containerWidthPx)
                            val bottomPx = (activePage.cropBottom * containerHeightPx).coerceIn(topPx + 20f, containerHeightPx)

                            val cropWidthDp = (maxWidth * (activePage.cropRight - activePage.cropLeft)).coerceAtLeast(24.dp)
                            val cropHeightDp = (maxHeight * (activePage.cropBottom - activePage.cropTop)).coerceAtLeast(24.dp)
                            val cropOffsetXDp = (maxWidth * activePage.cropLeft)
                            val cropOffsetYDp = (maxHeight * activePage.cropTop)

                            // Semi-transparent dimmed outer area
                            Box(
                                modifier = Modifier
                                    .offset(x = cropOffsetXDp, y = cropOffsetYDp)
                                    .size(width = cropWidthDp, height = cropHeightDp)
                                    .border(2.dp, ScanProGreenContainer, RoundedCornerShape(2.dp))
                                    .background(ScanProGreenContainer.copy(alpha = 0.08f))
                            )

                            // Top-Left Corner Handle
                            CropHandle(
                                modifier = Modifier
                                    .offset(
                                        x = cropOffsetXDp - 12.dp,
                                        y = cropOffsetYDp - 12.dp
                                    )
                                    .pointerInput(activePage.id, containerWidthPx, containerHeightPx) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val dLeft = dragAmount.x / containerWidthPx
                                            val dTop = dragAmount.y / containerHeightPx
                                            val newLeft = (activePage.cropLeft + dLeft).coerceIn(0f, activePage.cropRight - 0.1f)
                                            val newTop = (activePage.cropTop + dTop).coerceIn(0f, activePage.cropBottom - 0.1f)
                                            viewModel.updateActivePageCrop(newLeft, newTop, activePage.cropRight, activePage.cropBottom)
                                        }
                                    }
                            )

                            // Top-Right Corner Handle
                            CropHandle(
                                modifier = Modifier
                                    .offset(
                                        x = cropOffsetXDp + cropWidthDp - 12.dp,
                                        y = cropOffsetYDp - 12.dp
                                    )
                                    .pointerInput(activePage.id, containerWidthPx, containerHeightPx) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val dRight = dragAmount.x / containerWidthPx
                                            val dTop = dragAmount.y / containerHeightPx
                                            val newRight = (activePage.cropRight + dRight).coerceIn(activePage.cropLeft + 0.1f, 1f)
                                            val newTop = (activePage.cropTop + dTop).coerceIn(0f, activePage.cropBottom - 0.1f)
                                            viewModel.updateActivePageCrop(activePage.cropLeft, newTop, newRight, activePage.cropBottom)
                                        }
                                    }
                            )

                            // Bottom-Left Corner Handle
                            CropHandle(
                                modifier = Modifier
                                    .offset(
                                        x = cropOffsetXDp - 12.dp,
                                        y = cropOffsetYDp + cropHeightDp - 12.dp
                                    )
                                    .pointerInput(activePage.id, containerWidthPx, containerHeightPx) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val dLeft = dragAmount.x / containerWidthPx
                                            val dBottom = dragAmount.y / containerHeightPx
                                            val newLeft = (activePage.cropLeft + dLeft).coerceIn(0f, activePage.cropRight - 0.1f)
                                            val newBottom = (activePage.cropBottom + dBottom).coerceIn(activePage.cropTop + 0.1f, 1f)
                                            viewModel.updateActivePageCrop(newLeft, activePage.cropTop, activePage.cropRight, newBottom)
                                        }
                                    }
                            )

                            // Bottom-Right Corner Handle
                            CropHandle(
                                modifier = Modifier
                                    .offset(
                                        x = cropOffsetXDp + cropWidthDp - 12.dp,
                                        y = cropOffsetYDp + cropHeightDp - 12.dp
                                    )
                                    .pointerInput(activePage.id, containerWidthPx, containerHeightPx) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val dRight = dragAmount.x / containerWidthPx
                                            val dBottom = dragAmount.y / containerHeightPx
                                            val newRight = (activePage.cropRight + dRight).coerceIn(activePage.cropLeft + 0.1f, 1f)
                                            val newBottom = (activePage.cropBottom + dBottom).coerceIn(activePage.cropTop + 0.1f, 1f)
                                            viewModel.updateActivePageCrop(activePage.cropLeft, activePage.cropTop, newRight, newBottom)
                                        }
                                    }
                            )
                        }
                    }
                }
            }

            ScanLineDivider(opacity = 0.35f)

            // Bottom Toolbar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ReviewToolbarButton(
                        icon = Icons.Default.Crop,
                        label = if (isCropModeActive) "Done" else "Crop",
                        isActive = isCropModeActive,
                        onClick = {
                            isCropModeActive = !isCropModeActive
                            viewModel.showToast(if (isCropModeActive) "Drag corners to crop" else "Crop saved")
                        },
                        testTag = "review_crop_button"
                    )
                    if (isCropModeActive) {
                        ReviewToolbarButton(
                            icon = Icons.Default.Close,
                            label = "Reset Crop",
                            onClick = { viewModel.resetActivePageCrop() },
                            testTag = "review_reset_crop_button"
                        )
                    }
                    ReviewToolbarButton(
                        icon = Icons.Default.RotateRight,
                        label = "Rotate",
                        onClick = { viewModel.rotateActivePage() },
                        testTag = "review_rotate_button"
                    )
                    ReviewToolbarButton(
                        icon = Icons.Default.FilterVintage,
                        label = activePage?.filter?.displayName ?: "Filter",
                        onClick = { viewModel.cycleFilterActivePage() },
                        testTag = "review_filter_button"
                    )
                    ReviewToolbarButton(
                        icon = Icons.Default.AddAPhoto,
                        label = "Add Page",
                        onClick = launchAddPageScan,
                        testTag = "review_add_page_button"
                    )
                    ReviewToolbarButton(
                        icon = Icons.Default.Delete,
                        label = "Delete",
                        tint = ScanProAccentRed,
                        onClick = { viewModel.deleteActivePage() },
                        testTag = "review_delete_button"
                    )
                }
            }
        }
    }
}

@Composable
private fun CropHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(ScanProGreenContainer)
            .border(2.dp, Color.White, CircleShape)
    )
}

@Composable
private fun ReviewToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) ScanProGreenContainer else tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = if (isActive) ScanProGreenContainer else tint
        )
    }
}

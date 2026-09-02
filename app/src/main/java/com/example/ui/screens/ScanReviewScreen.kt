package com.example.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PictureAsPdf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.ScanProViewModel
import com.example.model.DocFormat
import com.example.model.DocumentItem
import com.example.model.PageFilter
import com.example.ui.components.ScanLineDivider
import com.example.ui.theme.ScanProAccentRed
import com.example.ui.theme.ScanProGreenContainer
import com.example.ui.theme.ScanProGreenPrimary
import com.example.util.rememberDocumentScannerLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanReviewScreen(
    viewModel: ScanProViewModel,
    onBack: () -> Unit,
    onDone: (DocumentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val launchAddPageScan = rememberDocumentScannerLauncher(viewModel = viewModel) { uris ->
        viewModel.addScannedPagesFromUris(uris)
    }

    val draftPages by viewModel.activeDraftPages.collectAsState()
    val selectedIndex by viewModel.selectedDraftIndex.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    val activePage = draftPages.getOrNull(selectedIndex) ?: draftPages.firstOrNull()
    var isCropModeActive by remember { mutableStateOf(true) }
    var showSaveFormatDialog by remember { mutableStateOf(false) }
    var chosenSaveFormat by remember { mutableStateOf(DocFormat.PDF) }

    // Decode intrinsic bitmap dimensions to ensure exact letterbox coordinate calculations
    var imageIntrinsicSize by remember(activePage?.id, activePage?.imageUri, activePage?.drawableRes) {
        mutableStateOf<Pair<Int, Int>?>(null)
    }

    LaunchedEffect(activePage?.id, activePage?.imageUri, activePage?.drawableRes) {
        if (activePage == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                if (!activePage.imageUri.isNullOrEmpty()) {
                    val uri = Uri.parse(activePage.imageUri)
                    if (uri.scheme == "file") {
                        BitmapFactory.decodeFile(uri.path, options)
                    } else {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream, null, options)
                        }
                    }
                } else if (activePage.drawableRes != 0) {
                    BitmapFactory.decodeResource(context.resources, activePage.drawableRes, options)
                }
                if (options.outWidth > 0 && options.outHeight > 0) {
                    imageIntrinsicSize = Pair(options.outWidth, options.outHeight)
                }
            } catch (_: Exception) {}
        }
    }

    if (showSaveFormatDialog) {
        SaveFormatPickerDialog(
            pageCount = draftPages.size,
            selectedFormat = chosenSaveFormat,
            onFormatSelected = { chosenSaveFormat = it },
            onDismiss = { showSaveFormatDialog = false },
            onConfirm = { format ->
                showSaveFormatDialog = false
                viewModel.finishScanAndSave(format) { savedDoc ->
                    onDone(savedDoc)
                }
            }
        )
    }

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
                            if (draftPages.size > 1) {
                                chosenSaveFormat = DocFormat.PDF
                            }
                            showSaveFormatDialog = true
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

                        // Calculate actual letterboxed/pillarboxed image rectangle under ContentScale.Fit
                        val (rawW, rawH) = imageIntrinsicSize ?: Pair(containerWidthPx.toInt(), containerHeightPx.toInt())
                        val isRotated90or270 = (activePage.rotationAngle.toInt() % 180 != 0)
                        val effectiveW = if (isRotated90or270) rawH else rawW
                        val effectiveH = if (isRotated90or270) rawW else rawH
                        val imageAspect = if (effectiveH > 0) effectiveW.toFloat() / effectiveH.toFloat() else (containerWidthPx / containerHeightPx)
                        val containerAspect = containerWidthPx / containerHeightPx

                        val displayedWidthPx: Float
                        val displayedHeightPx: Float
                        val displayedOffsetXPx: Float
                        val displayedOffsetYPx: Float

                        if (imageAspect > containerAspect) {
                            // Letterboxed top and bottom
                            displayedWidthPx = containerWidthPx
                            displayedHeightPx = containerWidthPx / imageAspect
                            displayedOffsetXPx = 0f
                            displayedOffsetYPx = (containerHeightPx - displayedHeightPx) / 2f
                        } else {
                            // Pillarboxed left and right
                            displayedHeightPx = containerHeightPx
                            displayedWidthPx = containerHeightPx * imageAspect
                            displayedOffsetXPx = (containerWidthPx - displayedWidthPx) / 2f
                            displayedOffsetYPx = 0f
                        }

                        val density = LocalDensity.current

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

                        // Interactive Crop Overlay mapped 1:1 to image displayed pixels
                        if (isCropModeActive && displayedWidthPx > 0f && displayedHeightPx > 0f) {
                            val cropOffsetXDp = with(density) { (displayedOffsetXPx + displayedWidthPx * activePage.cropLeft).toDp() }
                            val cropOffsetYDp = with(density) { (displayedOffsetYPx + displayedHeightPx * activePage.cropTop).toDp() }
                            val cropWidthDp = with(density) { (displayedWidthPx * (activePage.cropRight - activePage.cropLeft)).toDp().coerceAtLeast(24.dp) }
                            val cropHeightDp = with(density) { (displayedHeightPx * (activePage.cropBottom - activePage.cropTop)).toDp().coerceAtLeast(24.dp) }

                            // Semi-transparent dimmed crop area box
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
                                    .pointerInput(activePage.id, displayedWidthPx, displayedHeightPx) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val dLeft = dragAmount.x / displayedWidthPx
                                            val dTop = dragAmount.y / displayedHeightPx
                                            val newLeft = (activePage.cropLeft + dLeft).coerceIn(0f, activePage.cropRight - 0.05f)
                                            val newTop = (activePage.cropTop + dTop).coerceIn(0f, activePage.cropBottom - 0.05f)
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
                                    .pointerInput(activePage.id, displayedWidthPx, displayedHeightPx) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val dRight = dragAmount.x / displayedWidthPx
                                            val dTop = dragAmount.y / displayedHeightPx
                                            val newRight = (activePage.cropRight + dRight).coerceIn(activePage.cropLeft + 0.05f, 1f)
                                            val newTop = (activePage.cropTop + dTop).coerceIn(0f, activePage.cropBottom - 0.05f)
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
                                    .pointerInput(activePage.id, displayedWidthPx, displayedHeightPx) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val dLeft = dragAmount.x / displayedWidthPx
                                            val dBottom = dragAmount.y / displayedHeightPx
                                            val newLeft = (activePage.cropLeft + dLeft).coerceIn(0f, activePage.cropRight - 0.05f)
                                            val newBottom = (activePage.cropBottom + dBottom).coerceIn(activePage.cropTop + 0.05f, 1f)
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
                                    .pointerInput(activePage.id, displayedWidthPx, displayedHeightPx) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val dRight = dragAmount.x / displayedWidthPx
                                            val dBottom = dragAmount.y / displayedHeightPx
                                            val newRight = (activePage.cropRight + dRight).coerceIn(activePage.cropLeft + 0.05f, 1f)
                                            val newBottom = (activePage.cropBottom + dBottom).coerceIn(activePage.cropTop + 0.05f, 1f)
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
private fun SaveFormatPickerDialog(
    pageCount: Int,
    selectedFormat: DocFormat,
    onFormatSelected: (DocFormat) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (DocFormat) -> Unit
) {
    var format by remember { mutableStateOf(selectedFormat) }
    val isMultiPage = pageCount > 1

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Dialog Title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = ScanProGreenContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Save Document As",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (isMultiPage) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Image formats only support single-page documents — choose PDF or delete extra pages.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Format Options
                FormatOptionRow(
                    title = "PDF Document (.pdf)",
                    subtitle = "Multi-page or single-page document (Recommended)",
                    icon = Icons.Outlined.PictureAsPdf,
                    isSelected = format == DocFormat.PDF,
                    isEnabled = true,
                    onClick = {
                        format = DocFormat.PDF
                        onFormatSelected(DocFormat.PDF)
                    },
                    testTag = "format_option_pdf"
                )

                FormatOptionRow(
                    title = "JPEG Image (.jpg)",
                    subtitle = if (isMultiPage) "Single page only ($pageCount pages in draft)" else "Standard compressed image file",
                    icon = Icons.Outlined.Image,
                    isSelected = format == DocFormat.JPG,
                    isEnabled = !isMultiPage,
                    onClick = {
                        format = DocFormat.JPG
                        onFormatSelected(DocFormat.JPG)
                    },
                    testTag = "format_option_jpg"
                )

                FormatOptionRow(
                    title = "PNG Image (.png)",
                    subtitle = if (isMultiPage) "Single page only ($pageCount pages in draft)" else "Lossless high quality image file",
                    icon = Icons.Outlined.Image,
                    isSelected = format == DocFormat.PNG,
                    isEnabled = !isMultiPage,
                    onClick = {
                        format = DocFormat.PNG
                        onFormatSelected(DocFormat.PNG)
                    },
                    testTag = "format_option_png"
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(format) },
                        colors = ButtonDefaults.buttonColors(containerColor = ScanProGreenContainer),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("confirm_save_format_button")
                    ) {
                        Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatOptionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        enabled = isEnabled,
        shape = RoundedCornerShape(12.dp),
        color = when {
            !isEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
            isSelected -> ScanProGreenContainer.copy(alpha = 0.12f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = when {
                !isEnabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                isSelected -> ScanProGreenContainer
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                enabled = isEnabled,
                colors = RadioButtonDefaults.colors(selectedColor = ScanProGreenContainer)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isEnabled) {
                    if (isSelected) ScanProGreenContainer else MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
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

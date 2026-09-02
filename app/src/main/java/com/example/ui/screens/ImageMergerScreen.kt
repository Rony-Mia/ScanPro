package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.ScanProViewModel
import com.example.model.*
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

enum class MergerTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    LAYOUT("Layout", Icons.Outlined.GridView),
    SPACING("Spacing", Icons.Outlined.SpaceDashboard),
    STYLE("Style", Icons.Outlined.Palette),
    ADVANCED("Advanced", Icons.Outlined.Tune)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageMergerScreen(
    viewModel: ScanProViewModel,
    onBack: () -> Unit,
    onMerged: (DocumentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isProcessing by viewModel.isProcessing.collectAsState()

    var images by remember { mutableStateOf<List<MergerImageItem>>(emptyList()) }
    var config by remember { mutableStateOf(ImageMergerConfig()) }
    var currentTab by remember { mutableStateOf(MergerTab.LAYOUT) }
    var currentPreviewPage by remember { mutableStateOf(0) }
    var zoomScale by remember { mutableFloatStateOf(1f) }

    // Dialog state for custom caption editing
    var editingCaptionImageId by remember { mutableStateOf<String?>(null) }
    var tempCaptionText by remember { mutableStateOf("") }

    // Export Title Dialog
    var showExportDialog by remember { mutableStateOf(false) }
    var exportTitle by remember { mutableStateOf("Merged_Collage_${System.currentTimeMillis() % 10000}") }

    // Image picker launcher (OpenMultipleDocuments / PickMultipleVisualMedia)
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val newItems = uris.map { uri ->
                var name = "Image_${System.currentTimeMillis() % 10000}"
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx)?.let { name = it }
                    }
                }
                MergerImageItem(
                    id = UUID.randomUUID().toString(),
                    uri = uri,
                    fileName = name
                )
            }
            images = images + newItems
        }
    }

    val totalPages = remember(images.size, config) {
        viewModel.imageMergerEngine.calculateTotalPages(images.size, config)
    }

    // Ensure preview page stays valid
    LaunchedEffect(totalPages) {
        if (currentPreviewPage >= totalPages) {
            currentPreviewPage = max(0, totalPages - 1)
        }
    }

    // Live Rendered Bitmap Preview
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRenderingPreview by remember { mutableStateOf(false) }

    LaunchedEffect(images, config, currentPreviewPage) {
        isRenderingPreview = true
        withContext(Dispatchers.IO) {
            try {
                val bmp = viewModel.imageMergerEngine.renderPageToBitmap(
                    pageIndex = currentPreviewPage,
                    allImages = images,
                    config = config,
                    scaleFactor = 1.25f // Balanced crisp preview rendering
                )
                withContext(Dispatchers.Main) {
                    previewBitmap?.recycle()
                    previewBitmap = bmp
                    isRenderingPreview = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isRenderingPreview = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Image Merger",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (images.isNotEmpty()) {
                            Text(
                                text = "${images.size} images • $totalPages ${if (totalPages == 1) "page" else "pages"}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        enabled = !isProcessing,
                        modifier = Modifier.testTag("merger_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { pickerLauncher.launch(arrayOf("image/*")) },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("merger_add_photos_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Add", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = {
                            if (images.isEmpty()) {
                                pickerLauncher.launch(arrayOf("image/*"))
                            } else {
                                showExportDialog = true
                            }
                        },
                        enabled = !isProcessing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ScanProGreenContainer,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("merger_export_button")
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Merging & Exporting...", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(
                                imageVector = if (config.exportFormat == MergerExportFormat.PDF) Icons.Outlined.PictureAsPdf else Icons.Outlined.Image,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            val btnLabel = if (images.isEmpty()) {
                                "Select Images to Merge"
                            } else {
                                "Merge & Export (${config.exportFormat.label})"
                            }
                            Text(btnLabel, fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
        ) {
            // 1. TOP SECTION: Thumbnail Strip (Horizontal Scrollable)
            if (images.isNotEmpty()) {
                ThumbnailStrip(
                    images = images,
                    onAddMore = { pickerLauncher.launch(arrayOf("image/*")) },
                    onRemove = { id -> images = images.filter { it.id != id } },
                    onRotate = { id ->
                        images = images.map {
                            if (it.id == id) it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360) else it
                        }
                    },
                    onMoveLeft = { index ->
                        if (index > 0) {
                            val mutable = images.toMutableList()
                            val temp = mutable[index]
                            mutable[index] = mutable[index - 1]
                            mutable[index - 1] = temp
                            images = mutable
                        }
                    },
                    onMoveRight = { index ->
                        if (index < images.size - 1) {
                            val mutable = images.toMutableList()
                            val temp = mutable[index]
                            mutable[index] = mutable[index + 1]
                            mutable[index + 1] = temp
                            images = mutable
                        }
                    },
                    onEditCaption = { item ->
                        editingCaptionImageId = item.id
                        tempCaptionText = item.customCaption.ifBlank { item.fileName }
                    }
                )
            } else {
                // Empty state prompt
                EmptyImagesPrompt(
                    onPick = { pickerLauncher.launch(arrayOf("image/*")) }
                )
            }

            // 2. MIDDLE SECTION: Real-Time WYSIWYG Preview Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (images.isNotEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Interactive Zoomable Preview Area
                        val transformableState = rememberTransformableState { zoomChange, _, _ ->
                            zoomScale = (zoomScale * zoomChange).coerceIn(0.6f, 3.0f)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .transformable(state = transformableState),
                            contentAlignment = Alignment.Center
                        ) {
                            if (previewBitmap != null) {
                                Image(
                                    bitmap = previewBitmap!!.asImageBitmap(),
                                    contentDescription = "Live Layout Preview",
                                    modifier = Modifier
                                        .graphicsLayer(
                                            scaleX = zoomScale,
                                            scaleY = zoomScale
                                        )
                                        .shadow(8.dp, RoundedCornerShape(4.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                                        .clip(RoundedCornerShape(4.dp))
                                        .testTag("merger_live_preview")
                                )
                            }
                            if (isRenderingPreview) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = ScanProGreenPrimary,
                                    strokeWidth = 2.dp
                                )
                            }
                        }

                        // Page Navigation & Zoom Slider Bar
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .wrapContentWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                IconButton(
                                    onClick = { currentPreviewPage = max(0, currentPreviewPage - 1) },
                                    enabled = currentPreviewPage > 0,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronLeft,
                                        contentDescription = "Previous Page",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Text(
                                    text = "Page ${currentPreviewPage + 1} of $totalPages",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                IconButton(
                                    onClick = { currentPreviewPage = min(totalPages - 1, currentPreviewPage + 1) },
                                    enabled = currentPreviewPage < totalPages - 1,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Next Page",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                VerticalDivider(
                                    modifier = Modifier
                                        .height(16.dp)
                                        .padding(horizontal = 4.dp)
                                )

                                IconButton(
                                    onClick = { zoomScale = 1f },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FitScreen,
                                        contentDescription = "Reset Zoom",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Add photos to preview collage",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 3. BOTTOM SECTION: Control Settings Tabs & Panels
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 210.dp, max = 270.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Segmented Tabs
                    TabRow(
                        selectedTabIndex = currentTab.ordinal,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = ScanProGreenPrimary,
                        divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) }
                    ) {
                        MergerTab.values().forEach { tab ->
                            Tab(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                text = {
                                    Text(
                                        text = tab.title,
                                        fontSize = 12.sp,
                                        fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }

                    // Tab Contents (Scrollable inside)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp)
                    ) {
                        when (currentTab) {
                            MergerTab.LAYOUT -> LayoutTabContent(
                                config = config,
                                onConfigChange = { config = it }
                            )
                            MergerTab.SPACING -> SpacingTabContent(
                                config = config,
                                onConfigChange = { config = it }
                            )
                            MergerTab.STYLE -> StyleTabContent(
                                config = config,
                                onConfigChange = { config = it }
                            )
                            MergerTab.ADVANCED -> AdvancedTabContent(
                                config = config,
                                onConfigChange = { config = it }
                            )
                        }
                    }
                }
            }
        }
    }

    // Custom Caption Edit Dialog
    if (editingCaptionImageId != null) {
        val targetImage = images.find { it.id == editingCaptionImageId }
        AlertDialog(
            onDismissRequest = { editingCaptionImageId = null },
            title = { Text("Edit Caption", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter custom caption for ${targetImage?.fileName ?: "this image"}:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = tempCaptionText,
                        onValueChange = { tempCaptionText = it },
                        singleLine = true,
                        placeholder = { Text("e.g. Vacation Day 1") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        images = images.map {
                            if (it.id == editingCaptionImageId) it.copy(customCaption = tempCaptionText) else it
                        }
                        editingCaptionImageId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ScanProGreenContainer)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCaptionImageId = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Export Confirmation & Filename Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (config.exportFormat == MergerExportFormat.PDF) Icons.Outlined.PictureAsPdf else Icons.Outlined.Image,
                        contentDescription = null,
                        tint = ScanProGreenPrimary
                    )
                    Text("Export Merged Collage", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "File will be exported as ${config.exportFormat.label} with ${config.exportQuality.label} quality.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = exportTitle,
                        onValueChange = { exportTitle = it },
                        label = { Text("File Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Summary:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("• Images: ${images.size}", fontSize = 12.sp)
                            Text("• Total Pages: $totalPages", fontSize = 12.sp)
                            Text("• Layout: ${config.activeCols} × ${config.activeRows} (${config.fitMode.label})", fontSize = 12.sp)
                            Text("• Page Size: ${config.pageSize.displayName}", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExportDialog = false
                        viewModel.mergeAndExportImages(
                            images = images,
                            config = config,
                            customTitle = exportTitle
                        ) { newDoc ->
                            onMerged(newDoc)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ScanProGreenContainer),
                    modifier = Modifier.testTag("merger_confirm_export_button")
                ) {
                    Text("Export Now", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// -----------------------------------------------------------------------------------------
// THUMBNAIL STRIP COMPONENT
// -----------------------------------------------------------------------------------------
@Composable
private fun ThumbnailStrip(
    images: List<MergerImageItem>,
    onAddMore: () -> Unit,
    onRemove: (String) -> Unit,
    onRotate: (String) -> Unit,
    onMoveLeft: (Int) -> Unit,
    onMoveRight: (Int) -> Unit,
    onEditCaption: (MergerImageItem) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(images, key = { _, item -> item.id }) { index, item ->
                    ThumbnailCard(
                        item = item,
                        index = index,
                        totalCount = images.size,
                        onRemove = { onRemove(item.id) },
                        onRotate = { onRotate(item.id) },
                        onMoveLeft = { onMoveLeft(index) },
                        onMoveRight = { onMoveRight(index) },
                        onEditCaption = { onEditCaption(item) }
                    )
                }

                // Add More Card
                item {
                    Surface(
                        onClick = onAddMore,
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, ScanProGreenPrimary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .size(width = 80.dp, height = 100.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add More",
                                tint = ScanProGreenPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Add More",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ScanProGreenPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThumbnailCard(
    item: MergerImageItem,
    index: Int,
    totalCount: Int,
    onRemove: () -> Unit,
    onRotate: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onEditCaption: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
        modifier = Modifier
            .width(90.dp)
            .height(115.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Rotated Thumbnail Image
            AsyncImage(
                model = item.uri,
                contentDescription = item.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(rotationZ = item.rotationDegrees.toFloat())
            )

            // Gradient scrim at bottom for text
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(Color.Black.copy(alpha = 0.55f))
            )

            // Index badge top-left
            Surface(
                color = ScanProGreenPrimary,
                shape = CircleShape,
                modifier = Modifier
                    .padding(4.dp)
                    .size(18.dp)
                    .align(Alignment.TopStart)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${index + 1}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Remove 'X' button top-right
            Surface(
                onClick = onRemove,
                color = Color.Black.copy(alpha = 0.65f),
                shape = CircleShape,
                modifier = Modifier
                    .padding(4.dp)
                    .size(20.dp)
                    .align(Alignment.TopEnd)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Remove",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            // Action buttons row at bottom
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Move Left
                IconButton(
                    onClick = onMoveLeft,
                    enabled = index > 0,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Move Left",
                        tint = if (index > 0) Color.White else Color.Gray,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Rotate Button (Cycles 0 -> 90 -> 180 -> 270)
                IconButton(
                    onClick = onRotate,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RotateRight,
                        contentDescription = "Rotate",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Edit Caption
                IconButton(
                    onClick = onEditCaption,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Caption",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }

                // Move Right
                IconButton(
                    onClick = onMoveRight,
                    enabled = index < totalCount - 1,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Move Right",
                        tint = if (index < totalCount - 1) Color.White else Color.Gray,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyImagesPrompt(onPick: () -> Unit) {
    Surface(
        onClick = onPick,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, ScanProGreenPrimary.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                color = ScanProGreenContainer.copy(alpha = 0.15f),
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.AddPhotoAlternate,
                        contentDescription = null,
                        tint = ScanProGreenPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Select Images to Merge",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Pick as many photos as you want to arrange in custom grid collage",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = ScanProGreenPrimary
            )
        }
    }
}

// -----------------------------------------------------------------------------------------
// 1. LAYOUT TAB CONTENT
// -----------------------------------------------------------------------------------------
@Composable
private fun LayoutTabContent(
    config: ImageMergerConfig,
    onConfigChange: (ImageMergerConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // A. Per-Page Preset Chips (1, 2, 4, 6, 9, Custom)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Images per page: ${if (config.preset == MergerPagePreset.CUSTOM) "${config.customCols} × ${config.customRows} (${config.customCols * config.customRows})" else config.preset.label}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MergerPagePreset.values().forEach { preset ->
                    val isSelected = config.preset == preset
                    FilterChip(
                        selected = isSelected,
                        onClick = { onConfigChange(config.copy(preset = preset)) },
                        label = { Text(preset.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ScanProGreenContainer,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        // B. Custom Grid Steppers (if preset == Custom)
        if (config.preset == MergerPagePreset.CUSTOM) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Columns
                Column(modifier = Modifier.weight(1f)) {
                    Text("Columns: ${config.customCols}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Slider(
                        value = config.customCols.toFloat(),
                        onValueChange = { onConfigChange(config.copy(customCols = it.toInt())) },
                        valueRange = 1f..6f,
                        steps = 4,
                        colors = SliderDefaults.colors(thumbColor = ScanProGreenPrimary, activeTrackColor = ScanProGreenPrimary)
                    )
                }
                // Rows
                Column(modifier = Modifier.weight(1f)) {
                    Text("Rows: ${config.customRows}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Slider(
                        value = config.customRows.toFloat(),
                        onValueChange = { onConfigChange(config.copy(customRows = it.toInt())) },
                        valueRange = 1f..6f,
                        steps = 4,
                        colors = SliderDefaults.colors(thumbColor = ScanProGreenPrimary, activeTrackColor = ScanProGreenPrimary)
                    )
                }
            }
        }

        // C. Page Orientation & Auto-Orientation
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Orientation", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto-detect", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Switch(
                        checked = config.autoOrientation,
                        onCheckedChange = { onConfigChange(config.copy(autoOrientation = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = ScanProGreenPrimary, checkedTrackColor = ScanProGreenContainer.copy(alpha = 0.5f))
                    )
                }
            }

            if (!config.autoOrientation) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(MergerOrientation.PORTRAIT, MergerOrientation.LANDSCAPE).forEach { orient ->
                        val isSel = config.orientation == orient
                        OutlinedButton(
                            onClick = { onConfigChange(config.copy(orientation = orient)) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSel) ScanProGreenContainer else Color.Transparent,
                                contentColor = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface
                            ),
                            border = BorderStroke(1.dp, if (isSel) ScanProGreenPrimary else MaterialTheme.colorScheme.outlineVariant),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (orient == MergerOrientation.PORTRAIT) Icons.Outlined.StayCurrentPortrait else Icons.Outlined.StayCurrentLandscape,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(orient.label, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // D. Page Size Dropdown / Chips
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Page Size", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MergerPageSize.values().forEach { size ->
                    val isSel = config.pageSize == size
                    FilterChip(
                        selected = isSel,
                        onClick = { onConfigChange(config.copy(pageSize = size)) },
                        label = { Text(size.name, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ScanProGreenContainer,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// 2. SPACING TAB CONTENT
// -----------------------------------------------------------------------------------------
@Composable
private fun SpacingTabContent(
    config: ImageMergerConfig,
    onConfigChange: (ImageMergerConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // A. Image Gap
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Image-to-Image Gap (${config.horizontalGapDp.toInt()} pt)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Uniform", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Switch(
                        checked = config.uniformGap,
                        onCheckedChange = { onConfigChange(config.copy(uniformGap = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = ScanProGreenPrimary)
                    )
                }
            }

            if (config.uniformGap) {
                Slider(
                    value = config.horizontalGapDp,
                    onValueChange = { onConfigChange(config.copy(horizontalGapDp = it, verticalGapDp = it)) },
                    valueRange = 0f..32f,
                    colors = SliderDefaults.colors(thumbColor = ScanProGreenPrimary, activeTrackColor = ScanProGreenPrimary)
                )
            } else {
                Text("Horizontal Gap: ${config.horizontalGapDp.toInt()} pt", fontSize = 12.sp)
                Slider(
                    value = config.horizontalGapDp,
                    onValueChange = { onConfigChange(config.copy(horizontalGapDp = it)) },
                    valueRange = 0f..32f,
                    colors = SliderDefaults.colors(thumbColor = ScanProGreenPrimary, activeTrackColor = ScanProGreenPrimary)
                )
                Text("Vertical Gap: ${config.verticalGapDp.toInt()} pt", fontSize = 12.sp)
                Slider(
                    value = config.verticalGapDp,
                    onValueChange = { onConfigChange(config.copy(verticalGapDp = it)) },
                    valueRange = 0f..32f,
                    colors = SliderDefaults.colors(thumbColor = ScanProGreenPrimary, activeTrackColor = ScanProGreenPrimary)
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // B. Page Margin
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Page Margin (${config.marginTopDp.toInt()} pt)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Uniform", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Switch(
                        checked = config.uniformMargin,
                        onCheckedChange = { onConfigChange(config.copy(uniformMargin = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = ScanProGreenPrimary)
                    )
                }
            }

            if (config.uniformMargin) {
                Slider(
                    value = config.marginTopDp,
                    onValueChange = {
                        onConfigChange(
                            config.copy(
                                marginTopDp = it,
                                marginBottomDp = it,
                                marginLeftDp = it,
                                marginRightDp = it
                            )
                        )
                    },
                    valueRange = 0f..48f,
                    colors = SliderDefaults.colors(thumbColor = ScanProGreenPrimary, activeTrackColor = ScanProGreenPrimary)
                )
            } else {
                Text("Top / Bottom: ${config.marginTopDp.toInt()} pt", fontSize = 12.sp)
                Slider(
                    value = config.marginTopDp,
                    onValueChange = { onConfigChange(config.copy(marginTopDp = it, marginBottomDp = it)) },
                    valueRange = 0f..48f,
                    colors = SliderDefaults.colors(thumbColor = ScanProGreenPrimary, activeTrackColor = ScanProGreenPrimary)
                )
                Text("Left / Right: ${config.marginLeftDp.toInt()} pt", fontSize = 12.sp)
                Slider(
                    value = config.marginLeftDp,
                    onValueChange = { onConfigChange(config.copy(marginLeftDp = it, marginRightDp = it)) },
                    valueRange = 0f..48f,
                    colors = SliderDefaults.colors(thumbColor = ScanProGreenPrimary, activeTrackColor = ScanProGreenPrimary)
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// 3. STYLE TAB CONTENT
// -----------------------------------------------------------------------------------------
@Composable
private fun StyleTabContent(
    config: ImageMergerConfig,
    onConfigChange: (ImageMergerConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // A. Image Fit Mode (Fit vs Fill)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Image Scaling & Fit", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MergerFitMode.values().forEach { mode ->
                    val isSel = config.fitMode == mode
                    OutlinedButton(
                        onClick = { onConfigChange(config.copy(fitMode = mode)) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSel) ScanProGreenContainer else Color.Transparent,
                            contentColor = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(1.dp, if (isSel) ScanProGreenPrimary else MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(mode.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // B. Background Color Picker
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Page Background Color", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            val colorOptions = listOf(
                Pair("White", 0xFFFFFFFF),
                Pair("Warm Cream", 0xFFFDFBF7),
                Pair("Soft Gray", 0xFFF1F5F9),
                Pair("Dark Slate", 0xFF1E293B),
                Pair("Pure Black", 0xFF000000),
                Pair("Scan Green", 0xFFE8F5E9)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                colorOptions.forEach { (name, argb) ->
                    val isSel = config.backgroundColorArgb == argb
                    Surface(
                        onClick = { onConfigChange(config.copy(backgroundColorArgb = argb)) },
                        shape = CircleShape,
                        color = Color(argb),
                        border = BorderStroke(
                            if (isSel) 2.5.dp else 1.dp,
                            if (isSel) ScanProGreenPrimary else Color.LightGray
                        ),
                        modifier = Modifier.size(32.dp)
                    ) {
                        if (isSel) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = name,
                                    tint = if (argb == 0xFFFFFFFF || argb == 0xFFFDFBF7 || argb == 0xFFF1F5F9) Color.Black else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // C. Border Toggle & Thickness
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Image Border", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Switch(
                    checked = config.hasBorder,
                    onCheckedChange = { onConfigChange(config.copy(hasBorder = it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = ScanProGreenPrimary)
                )
            }
            if (config.hasBorder) {
                Text("Thickness: ${String.format("%.1f", config.borderThicknessDp)} pt", fontSize = 12.sp)
                Slider(
                    value = config.borderThicknessDp,
                    onValueChange = { onConfigChange(config.copy(borderThicknessDp = it)) },
                    valueRange = 0.5f..8f,
                    colors = SliderDefaults.colors(thumbColor = ScanProGreenPrimary, activeTrackColor = ScanProGreenPrimary)
                )
            }
        }

        // D. Border Radius (Rounded Corners)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Corner Rounding (${config.borderRadiusDp.toInt()} pt)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Slider(
                value = config.borderRadiusDp,
                onValueChange = { onConfigChange(config.copy(borderRadiusDp = it)) },
                valueRange = 0f..24f,
                colors = SliderDefaults.colors(thumbColor = ScanProGreenPrimary, activeTrackColor = ScanProGreenPrimary)
            )
        }

        // E. Drop Shadow Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Drop Shadow", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Adds subtle elevation behind images", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = config.hasShadow,
                onCheckedChange = { onConfigChange(config.copy(hasShadow = it)) },
                colors = SwitchDefaults.colors(checkedThumbColor = ScanProGreenPrimary)
            )
        }
    }
}

// -----------------------------------------------------------------------------------------
// 4. ADVANCED TAB CONTENT
// -----------------------------------------------------------------------------------------
@Composable
private fun AdvancedTabContent(
    config: ImageMergerConfig,
    onConfigChange: (ImageMergerConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // A. Export Format
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Export Format", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MergerExportFormat.values().forEach { fmt ->
                    val isSel = config.exportFormat == fmt
                    OutlinedButton(
                        onClick = { onConfigChange(config.copy(exportFormat = fmt)) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSel) ScanProGreenContainer else Color.Transparent,
                            contentColor = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(1.dp, if (isSel) ScanProGreenPrimary else MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(fmt.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // B. Resolution / Quality
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Export Resolution Quality", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MergerExportQuality.values().forEach { q ->
                    val isSel = config.exportQuality == q
                    FilterChip(
                        selected = isSel,
                        onClick = { onConfigChange(config.copy(exportQuality = q)) },
                        label = { Text(q.label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ScanProGreenContainer,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // C. Captions / Labels Mode
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Captions & Labels", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MergerCaptionMode.values().forEach { mode ->
                    val isSel = config.captionMode == mode
                    FilterChip(
                        selected = isSel,
                        onClick = { onConfigChange(config.copy(captionMode = mode)) },
                        label = { Text(mode.label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ScanProGreenContainer,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        // D. Numbering Toggles (Page Number & Image Index)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Page Numbering", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Shows 'Page X of Y' in footer", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = config.showPageNumber,
                onCheckedChange = { onConfigChange(config.copy(showPageNumber = it)) },
                colors = SwitchDefaults.colors(checkedThumbColor = ScanProGreenPrimary)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Image Index Badges", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Shows 1, 2, 3... badge on each cell", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = config.showImageIndex,
                onCheckedChange = { onConfigChange(config.copy(showImageIndex = it)) },
                colors = SwitchDefaults.colors(checkedThumbColor = ScanProGreenPrimary)
            )
        }
    }
}

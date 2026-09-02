package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.ScanProViewModel
import com.example.model.DocCategory
import com.example.model.DocFormat
import com.example.model.DocSortOrder
import com.example.model.DocumentItem
import com.example.ui.components.DocCard
import com.example.ui.components.ScanLineDivider
import com.example.ui.theme.ScanProGreenContainer
import com.example.ui.theme.ScanProGreenPrimary
import com.example.util.ShareUtil
import com.example.util.rememberDocumentScannerLauncher
import com.example.util.rememberFilePickerLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsLibraryScreen(
    viewModel: ScanProViewModel,
    onNavigateToScanReview: () -> Unit,
    onNavigateToViewer: (DocumentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Real scanner (camera + gallery import) instead of the old fake camera mock.
    val launchScannerFlow = rememberDocumentScannerLauncher(viewModel = viewModel) { uris ->
        viewModel.setScannedPagesFromUris(uris)
        onNavigateToScanReview()
    }

    // Real "import from phone storage" picker (Storage Access Framework).
    val launchFilePicker = rememberFilePickerLauncher { uris ->
        viewModel.importDocumentsFromUris(uris)
    }

    val documents by viewModel.documents.collectAsState()
    val filterTab by viewModel.filterTab.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()

    var isSearchExpanded by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val filteredDocuments = remember(documents, filterTab, searchQuery, sortOrder) {
        val filtered = documents.filter { doc ->
            val matchesFilter = when (filterTab) {
                "PDFs" -> doc.format == DocFormat.PDF
                "Images" -> doc.format == DocFormat.JPG || doc.format == DocFormat.PNG
                "Recent" -> doc.category == DocCategory.TODAY
                else -> true
            }
            val matchesSearch = if (searchQuery.isBlank()) true else {
                doc.title.contains(searchQuery, ignoreCase = true) ||
                doc.ocrText.contains(searchQuery, ignoreCase = true)
            }
            matchesFilter && matchesSearch
        }
        when (sortOrder) {
            DocSortOrder.DATE_DESC -> filtered
            DocSortOrder.DATE_ASC -> filtered.reversed()
            DocSortOrder.NAME_ASC -> filtered.sortedBy { it.title.lowercase() }
            DocSortOrder.NAME_DESC -> filtered.sortedByDescending { it.title.lowercase() }
            DocSortOrder.SIZE_DESC -> filtered.sortedByDescending { it.fileSize }
            DocSortOrder.SIZE_ASC -> filtered.sortedBy { it.fileSize }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchExpanded) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search documents...") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    viewModel.setSearchQuery("")
                                    isSearchExpanded = false
                                }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Clear")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("documents_search_input")
                        )
                    } else {
                        Text(
                            text = "Documents",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                },
                navigationIcon = {
                    if (!isSearchExpanded) {
                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.testTag("documents_sort_menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Sort Documents",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                Text(
                                    text = "Sort by",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                                DocSortOrder.values().forEach { order ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (sortOrder == order) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = ScanProGreenContainer,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                } else {
                                                    Spacer(modifier = Modifier.width(26.dp))
                                                }
                                                Text(
                                                    text = order.displayName,
                                                    fontWeight = if (sortOrder == order) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (sortOrder == order) ScanProGreenContainer else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        },
                                        onClick = {
                                            viewModel.setSortOrder(order)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    if (!isSearchExpanded) {
                        IconButton(
                            onClick = launchFilePicker,
                            modifier = Modifier.testTag("documents_import_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.UploadFile,
                                contentDescription = "Import from device",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = { isSearchExpanded = true },
                            modifier = Modifier.testTag("documents_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = {
                                val nextFilter = when (filterTab) {
                                    "All" -> "PDFs"
                                    "PDFs" -> "Images"
                                    "Images" -> "Recent"
                                    else -> "All"
                                }
                                viewModel.setFilterTab(nextFilter)
                            },
                            modifier = Modifier.testTag("documents_filter_toggle")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FilterList,
                                contentDescription = "Filter",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = launchScannerFlow,
                containerColor = ScanProGreenContainer,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .testTag("library_scan_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Scan Document",
                    modifier = Modifier.size(28.dp)
                )
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
            // Filter Chips Section
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filterOptions = listOf("All", "PDFs", "Images", "Recent")
                items(filterOptions) { filter ->
                    val isSelected = filter == filterTab
                    Surface(
                        onClick = { viewModel.setFilterTab(filter) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) ScanProGreenContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.testTag("filter_chip_$filter")
                    ) {
                        Text(
                            text = filter,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // Stats & View Switcher Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredDocuments.size} ITEMS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // List / Grid toggle switch
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (!isGridView) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable { if (isGridView) viewModel.toggleGridView() }
                            .padding(6.dp)
                            .testTag("list_view_toggle"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ViewList,
                            contentDescription = "List View",
                            tint = if (!isGridView) ScanProGreenContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isGridView) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable { if (!isGridView) viewModel.toggleGridView() }
                            .padding(6.dp)
                            .testTag("grid_view_toggle"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = "Grid View",
                            tint = if (isGridView) ScanProGreenContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Main Content Area (Empty state vs List vs Grid)
            if (filteredDocuments.isEmpty()) {
                // Empty State Screen (Screen 9)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = "Empty folder",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Your library is empty",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your scanned documents will appear here.",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = launchScannerFlow,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ScanProGreenContainer,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("empty_library_scan_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan Document", fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = launchFilePicker,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("empty_library_import_button")
                        ) {
                            Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import File", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else if (isGridView) {
                // Grid View Mode
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 90.dp, top = 8.dp)
                ) {
                    items(filteredDocuments, key = { it.id }) { doc ->
                        Surface(
                            onClick = { onNavigateToViewer(doc) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("doc_grid_${doc.id}")
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White)
                                ) {
                                    val thumbModel: Any? = when {
                                        !doc.thumbnailUri.isNullOrEmpty() -> doc.thumbnailUri
                                        !doc.filePath.isNullOrEmpty() && (doc.format == DocFormat.JPG || doc.format == DocFormat.PNG) -> java.io.File(doc.filePath)
                                        doc.thumbnailRes != 0 -> doc.thumbnailRes
                                        else -> null
                                    }
                                    if (thumbModel != null) {
                                        AsyncImage(
                                            model = thumbModel,
                                            contentDescription = doc.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = doc.title,
                                            tint = ScanProGreenContainer,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .align(Alignment.Center)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = doc.format.name,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ScanProGreenContainer
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = doc.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${doc.pageCount} ${if (doc.pageCount == 1) "page" else "pages"} • ${doc.fileSize}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                // Grouped List View Mode (Today, Yesterday, Earlier)
                val todayDocs = filteredDocuments.filter { it.category == DocCategory.TODAY }
                val yesterdayDocs = filteredDocuments.filter { it.category == DocCategory.YESTERDAY }
                val earlierDocs = filteredDocuments.filter { it.category == DocCategory.EARLIER }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 90.dp, top = 4.dp)
                ) {
                    if (todayDocs.isNotEmpty()) {
                        item {
                            Column {
                                Text(
                                    text = "TODAY",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                                ScanLineDivider(opacity = 0.3f)
                            }
                        }
                        items(todayDocs, key = { it.id }) { doc ->
                            DocCard(
                                document = doc,
                                onClick = { onNavigateToViewer(doc) },
                                onDelete = { viewModel.deleteDocument(doc.id) },
                                onRename = { newName -> viewModel.renameDocument(doc.id, newName) },
                                onShare = { ShareUtil.shareDocument(context, doc) }
                            )
                        }
                    }

                    if (yesterdayDocs.isNotEmpty()) {
                        item {
                            Column {
                                Text(
                                    text = "YESTERDAY",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                )
                                ScanLineDivider(opacity = 0.3f)
                            }
                        }
                        items(yesterdayDocs, key = { it.id }) { doc ->
                            DocCard(
                                document = doc,
                                onClick = { onNavigateToViewer(doc) },
                                onDelete = { viewModel.deleteDocument(doc.id) },
                                onRename = { newName -> viewModel.renameDocument(doc.id, newName) },
                                onShare = { ShareUtil.shareDocument(context, doc) }
                            )
                        }
                    }

                    if (earlierDocs.isNotEmpty()) {
                        item {
                            Column {
                                Text(
                                    text = "EARLIER",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                )
                                ScanLineDivider(opacity = 0.3f)
                            }
                        }
                        items(earlierDocs, key = { it.id }) { doc ->
                            DocCard(
                                document = doc,
                                onClick = { onNavigateToViewer(doc) },
                                onDelete = { viewModel.deleteDocument(doc.id) },
                                onRename = { newName -> viewModel.renameDocument(doc.id, newName) },
                                onShare = { ShareUtil.shareDocument(context, doc) }
                            )
                        }
                    }
                }
            }
        }
    }
}

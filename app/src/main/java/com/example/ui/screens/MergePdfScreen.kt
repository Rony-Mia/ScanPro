package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ScanProViewModel
import com.example.model.DocumentItem
import com.example.ui.components.ScanLineDivider
import com.example.ui.theme.ScanProAccentRed
import com.example.ui.theme.ScanProGreenContainer
import com.example.ui.theme.ScanProGreenPrimary
import com.example.util.rememberFilePickerLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergePdfScreen(
    viewModel: ScanProViewModel,
    onBack: () -> Unit,
    onMergeCompleted: (DocumentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val allDocs by viewModel.documents.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    var selectedDocIds by remember(allDocs) {
        mutableStateOf(allDocs.take(3).map { it.id }.toMutableList())
    }
    var showAddDialog by remember { mutableStateOf(false) }

    // Real "import from phone storage" picker (Storage Access Framework) —
    // this is what was missing. Newly imported files are added straight into
    // the merge list.
    val launchFilePicker = rememberFilePickerLauncher { uris ->
        viewModel.importDocumentsFromUris(uris) { imported ->
            if (imported.isNotEmpty()) {
                selectedDocIds = (selectedDocIds + imported.map { it.id }).toMutableList()
            }
        }
        showAddDialog = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Merge PDFs",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        enabled = !isProcessing,
                        modifier = Modifier.testTag("merge_back_button")
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
                        onClick = { showAddDialog = true },
                        enabled = !isProcessing,
                        modifier = Modifier.testTag("merge_add_files_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = ScanProGreenContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Add Files",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = ScanProGreenContainer
                            )
                        }
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
                    Button(
                        onClick = {
                            if (selectedDocIds.size >= 2) {
                                viewModel.mergeDocuments(selectedDocIds) { merged ->
                                    onMergeCompleted(merged)
                                }
                            } else {
                                viewModel.showToast("Select at least 2 files to merge")
                            }
                        },
                        enabled = !isProcessing,
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
                            .testTag("merge_now_button")
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Merging PDFs...",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = "Merge Now",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = ScanProGreenPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "100% on your device — files never leave your phone.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
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
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Order of documents (${selectedDocIds.size} files):",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(selectedDocIds) { index, docId ->
                    val doc = allDocs.find { it.id == docId }
                    if (doc != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // PDF Icon Badge
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ScanProAccentRed.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = "PDF",
                                        tint = ScanProAccentRed,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = doc.title,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${doc.pageCount} pages • ${doc.fileSize}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Move up / down arrows
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (index > 0) {
                                        IconButton(
                                            onClick = {
                                                val list = selectedDocIds.toMutableList()
                                                val item = list.removeAt(index)
                                                list.add(index - 1, item)
                                                selectedDocIds = list
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowUp,
                                                contentDescription = "Move Up",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (index < selectedDocIds.size - 1) {
                                        IconButton(
                                            onClick = {
                                                val list = selectedDocIds.toMutableList()
                                                val item = list.removeAt(index)
                                                list.add(index + 1, item)
                                                selectedDocIds = list
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Move Down",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Remove button
                                    IconButton(
                                        onClick = {
                                            val list = selectedDocIds.toMutableList()
                                            list.removeAt(index)
                                            selectedDocIds = list
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add PDF to Merge") },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                ) {
                    // Real "import from phone storage" option — opens the
                    // system file picker instead of only offering documents
                    // already inside the app.
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { launchFilePicker() }
                                .padding(vertical = 10.dp, horizontal = 4.dp)
                                .testTag("merge_import_from_device"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = ScanProGreenContainer)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Import from device",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = ScanProGreenContainer
                            )
                        }
                        ScanLineDivider(opacity = 0.25f)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "FROM LIBRARY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                        )
                    }

                    val available = allDocs.filterNot { it.id in selectedDocIds }
                    if (available.isEmpty()) {
                        item {
                            Text("All library documents already added", modifier = Modifier.padding(vertical = 8.dp))
                        }
                    } else {
                        itemsIndexed(available) { _, doc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val list = selectedDocIds.toMutableList()
                                        list.add(doc.id)
                                        selectedDocIds = list
                                        showAddDialog = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = ScanProAccentRed)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(doc.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("${doc.pageCount} pages", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

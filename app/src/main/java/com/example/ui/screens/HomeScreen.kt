package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ScanProViewModel
import com.example.model.DocumentItem
import com.example.ui.components.DocCard
import com.example.ui.components.ScanLineDivider
import com.example.ui.theme.ScanProGreenContainer
import com.example.ui.theme.ScanProGreenPrimary
import com.example.util.rememberDocumentScannerLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ScanProViewModel,
    onNavigateToScan: () -> Unit,
    onNavigateToDocuments: () -> Unit,
    onNavigateToViewer: (DocumentItem) -> Unit,
    onNavigateToMerge: () -> Unit,
    onNavigateToCompress: () -> Unit,
    onNavigateToOcr: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val documents by viewModel.documents.collectAsState()

    val launchScannerFlow = rememberDocumentScannerLauncher(viewModel = viewModel) { uris ->
        viewModel.setScannedPagesFromUris(uris)
        onNavigateToScan()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DocumentScanner,
                            contentDescription = "ScanPro Logo",
                            tint = ScanProGreenPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "ScanPro",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = ScanProGreenPrimary,
                            letterSpacing = (-0.5).sp
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("home_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Hero Action: Scan Document
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    onClick = launchScannerFlow,
                    color = ScanProGreenContainer,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_scan_hero_button")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 24.dp, horizontal = 16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Camera",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Scan Document",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Quick Actions 4-Grid
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    QuickActionItem(
                        icon = Icons.Outlined.MergeType,
                        label = "Merge PDF",
                        onClick = onNavigateToMerge,
                        testTag = "quick_merge"
                    )
                    QuickActionItem(
                        icon = Icons.Outlined.Compress,
                        label = "Compress",
                        onClick = onNavigateToCompress,
                        testTag = "quick_compress"
                    )
                    QuickActionItem(
                        icon = Icons.Outlined.UploadFile,
                        label = "Import File",
                        onClick = launchScannerFlow,
                        testTag = "quick_import"
                    )
                    QuickActionItem(
                        icon = Icons.Outlined.DocumentScanner,
                        label = "OCR Text",
                        onClick = onNavigateToOcr,
                        testTag = "quick_ocr"
                    )
                }
            }

            // Gradient Scan Line Divider
            item {
                ScanLineDivider(opacity = 0.4f)
            }

            // Recent Documents Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Documents",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-0.3).sp
                    )
                    if (documents.isNotEmpty()) {
                        TextButton(
                            onClick = onNavigateToDocuments,
                            modifier = Modifier.testTag("see_all_documents_button")
                        ) {
                            Text(
                                text = "See All",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ScanProGreenPrimary
                            )
                        }
                    }
                }
            }

            // Documents List or Empty State
            if (documents.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No documents yet",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap Scan Document to create your first PDF",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { viewModel.resetDefaultDocuments() }
                        ) {
                            Text("Load Sample Documents")
                        }
                    }
                }
            } else {
                items(documents.take(4), key = { it.id }) { doc ->
                    DocCard(
                        document = doc,
                        onClick = { onNavigateToViewer(doc) },
                        onDelete = { viewModel.deleteDocument(doc.id) },
                        onRename = { newName -> viewModel.renameDocument(doc.id, newName) },
                        onShare = { viewModel.showToast("Sharing ${doc.title}...") }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun QuickActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

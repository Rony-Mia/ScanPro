package com.example.ui.screens

import android.net.Uri
import androidx.compose.animation.*
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.ScanProViewModel
import com.example.model.DocumentItem
import com.example.model.PageFilter
import com.example.ui.components.ScanLineDivider
import com.example.ui.theme.ScanProGreenContainer
import com.example.ui.theme.ScanProGreenPrimary
import com.example.util.rememberDocumentScannerLauncher
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhiteboardScanScreen(
    viewModel: ScanProViewModel,
    onBack: () -> Unit,
    onDone: (DocumentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var whiteboardUri by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf(PageFilter.WHITEBOARD) }
    val defaultTitle = remember {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.getDefault())
        "Whiteboard_${dateFormat.format(Date())}"
    }
    var documentTitle by remember { mutableStateOf(defaultTitle) }
    val isProcessing by viewModel.isProcessing.collectAsState()

    val launchScanner = rememberDocumentScannerLauncher(
        viewModel = viewModel,
        onPagesScanned = { uris ->
            if (uris.isNotEmpty()) {
                whiteboardUri = uris.first().toString()
                viewModel.showToast("Whiteboard captured! Glare reduction applied.")
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Whiteboard Scanner",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("whiteboard_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (whiteboardUri != null) {
                        IconButton(
                            onClick = { launchScanner() },
                            modifier = Modifier.testTag("whiteboard_rescan_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Cameraswitch,
                                contentDescription = "Rescan",
                                tint = ScanProGreenPrimary
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
            if (whiteboardUri != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp)
                    ) {
                        Button(
                            onClick = {
                                val uri = whiteboardUri?.let { Uri.parse(it) }
                                if (uri != null) {
                                    viewModel.saveWhiteboardDocument(
                                        imageUri = uri,
                                        title = documentTitle.trim(),
                                        onComplete = onDone
                                    )
                                }
                            },
                            enabled = !isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("save_whiteboard_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ScanProGreenContainer,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Processing Whiteboard PDF...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save Whiteboard PDF", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (whiteboardUri == null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clickable { launchScanner() }
                        .testTag("whiteboard_scan_placeholder")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(ScanProGreenContainer.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CoPresent,
                                contentDescription = null,
                                tint = ScanProGreenContainer,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Scan Whiteboard or Blackboard",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Automatically eliminates glare, enhances marker strokes, and optimizes readability",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Button(
                    onClick = { launchScanner() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("whiteboard_start_scan_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ScanProGreenContainer,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.DocumentScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Capture Whiteboard", fontWeight = FontWeight.Bold)
                }

                // Features Info Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Lightbulb,
                                contentDescription = null,
                                tint = ScanProGreenPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Whiteboard Scan Features",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "• Glare & Reflection Reduction\n• Marker Color & Ink Contrast Boost\n• Automatic Keystoning & Perspective Correction\n• Searchable PDF text layer embedded",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                }
            } else {
                // Whiteboard Image Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(12.dp)
                        )
                        .background(Color.White)
                ) {
                    val colorFilter = when (selectedFilter) {
                        PageFilter.WHITEBOARD, PageFilter.ENHANCED -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.4f) })
                        PageFilter.MAGIC -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.25f) })
                        PageFilter.GRAYSCALE, PageFilter.BW -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                        PageFilter.COLOR -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.2f) })
                        PageFilter.ORIGINAL -> null
                    }

                    AsyncImage(
                        model = whiteboardUri,
                        contentDescription = "Whiteboard Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        colorFilter = colorFilter
                    )
                }

                ScanLineDivider(opacity = 0.35f)

                // Document Title
                OutlinedTextField(
                    value = documentTitle,
                    onValueChange = { documentTitle = it },
                    label = { Text("Document Title") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Edit, contentDescription = null, tint = ScanProGreenContainer)
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("whiteboard_title_input")
                )

                // Filter Choices
                Text(
                    text = "ENHANCEMENT FILTER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filterOptions = listOf(
                        PageFilter.WHITEBOARD to "Whiteboard",
                        PageFilter.ENHANCED to "Enhanced",
                        PageFilter.MAGIC to "Magic Color",
                        PageFilter.ORIGINAL to "Original"
                    )

                    filterOptions.forEach { (filter, label) ->
                        val isSelected = selectedFilter == filter
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) ScanProGreenContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) ScanProGreenContainer else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedFilter = filter }
                                .padding(vertical = 2.dp)
                                .testTag("whiteboard_filter_${filter.name.lowercase()}")
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

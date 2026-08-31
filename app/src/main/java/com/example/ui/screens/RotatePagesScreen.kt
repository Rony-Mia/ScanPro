package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ScanProViewModel
import com.example.model.DocumentItem
import com.example.ui.components.ChangeDocumentButton
import com.example.ui.theme.ScanProGreenContainer

/**
 * Real "Rotate Pages" tool. Previously this tile in the Tools grid only showed a
 * "Rotate Pages ready" toast and did nothing. This shows real rendered page thumbnails,
 * lets the user tap any page to rotate it 90° at a time, and applies the real rotation to
 * the PDF via [ScanProViewModel.rotateDocumentPages] / PDFBox page rotation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RotatePagesScreen(
    viewModel: ScanProViewModel,
    onBack: () -> Unit,
    onRotated: (DocumentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedDoc by viewModel.selectedDocument.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val doc = selectedDoc ?: return

    var thumbnails by remember(doc.id) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoadingThumbs by remember(doc.id) { mutableStateOf(true) }
    val rotations = remember(doc.id) { mutableStateMapOf<Int, Int>() }

    LaunchedEffect(doc.id) {
        isLoadingThumbs = true
        rotations.clear()
        viewModel.loadPageThumbnails(doc) { bitmaps ->
            thumbnails = bitmaps
            isLoadingThumbs = false
        }
    }

    val hasChanges = rotations.values.any { it != 0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Rotate Pages", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isProcessing, modifier = Modifier.testTag("rotate_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    ChangeDocumentButton(
                        viewModel = viewModel,
                        currentDocId = doc.id,
                        enabled = !isProcessing,
                        testTagPrefix = "rotate"
                    ) { selected -> viewModel.selectDocument(selected) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            viewModel.rotateDocumentPages(doc, rotations.toMap()) { rotated -> onRotated(rotated) }
                        },
                        enabled = !isProcessing && hasChanges,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ScanProGreenContainer,
                            contentColor = Color.White,
                            disabledContainerColor = ScanProGreenContainer.copy(alpha = 0.5f),
                            disabledContentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("rotate_apply_button")
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Saving...", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text(
                                if (hasChanges) "Save Rotated Pages" else "Tap a page to rotate it",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
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
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Text(doc.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "Tap any page to rotate it 90° clockwise",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))

            if (isLoadingThumbs) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ScanProGreenContainer)
                }
            } else if (thumbnails.isEmpty()) {
                Text(
                    "Couldn't render page previews for this document.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(thumbnails.size) { index ->
                        val currentRotation = rotations[index] ?: 0
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .aspectRatio(0.75f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        rotations[index] = ((currentRotation + 90) % 360)
                                    }
                                    .testTag("rotate_page_$index"),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = thumbnails[index].asImageBitmap(),
                                    contentDescription = "Page ${index + 1}",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .rotate(currentRotation.toFloat()),
                                    contentScale = ContentScale.Fit
                                )
                                if (currentRotation != 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(ScanProGreenContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RotateRight,
                                            contentDescription = "Rotated $currentRotation°",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("Page ${index + 1}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

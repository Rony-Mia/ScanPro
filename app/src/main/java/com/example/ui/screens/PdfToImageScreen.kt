package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.ScanProViewModel
import com.example.model.DocumentItem
import com.example.ui.components.ChangeDocumentButton
import com.example.ui.theme.ScanProAccentRed
import com.example.ui.theme.ScanProGreenContainer

/**
 * Real "PDF to Image" tool. Previously this tile in the Tools grid only showed a
 * "PDF to Image ready" toast and did nothing. This renders every page of the selected
 * PDF with Android's native PdfRenderer via [ScanProViewModel.convertPdfToImages] and
 * saves each as its own real JPEG document in the Library.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToImageScreen(
    viewModel: ScanProViewModel,
    onBack: () -> Unit,
    onConverted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedDoc by viewModel.selectedDocument.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val doc = selectedDoc ?: return

    var resultImages by remember(doc.id) { mutableStateOf<List<DocumentItem>>(emptyList()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("PDF to Image", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isProcessing, modifier = Modifier.testTag("pdf2img_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    ChangeDocumentButton(
                        viewModel = viewModel,
                        currentDocId = doc.id,
                        enabled = !isProcessing,
                        testTagPrefix = "pdf2img"
                    ) { selected ->
                        viewModel.selectDocument(selected)
                        resultImages = emptyList()
                    }
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
                            viewModel.convertPdfToImages(doc) { images ->
                                resultImages = images
                                onConverted()
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
                            .testTag("pdf2img_convert_button")
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Converting...", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text(
                                "Convert ${doc.pageCount} ${if (doc.pageCount == 1) "Page" else "Pages"} to Images",
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
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ScanProAccentRed.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = ScanProAccentRed)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(doc.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("${doc.pageCount} pages • ${doc.fileSize}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            if (resultImages.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ScanProGreenContainer, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${resultImages.size} images created and added to your Library",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ScanProGreenContainer
                    )
                }
                Spacer(Modifier.height(14.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(resultImages, key = { it.id }) { img ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(0.75f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                        ) {
                            AsyncImage(
                                model = img.thumbnailUri,
                                contentDescription = img.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            } else {
                Text(
                    "Each page of this PDF will be rendered and saved as its own JPEG image in your Library.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

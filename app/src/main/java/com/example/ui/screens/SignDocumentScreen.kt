package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ScanProViewModel
import com.example.model.DocumentItem
import com.example.ui.components.ChangeDocumentButton
import com.example.ui.theme.ScanProGreenContainer

/**
 * Real "Sign Document" tool. Previously this tile in the Tools grid only showed a
 * "Sign Document ready" toast and did nothing. This is a real touch signature pad —
 * every finger stroke is captured as points and embedded as real vector line art onto
 * the PDF page via [ScanProViewModel.signDocument] / PDFBox content streams (not a
 * screenshot or a bitmap overlay).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignDocumentScreen(
    viewModel: ScanProViewModel,
    onBack: () -> Unit,
    onSigned: (DocumentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedDoc by viewModel.selectedDocument.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val doc = selectedDoc ?: return

    var strokes by remember(doc.id) { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var currentStroke by remember(doc.id) { mutableStateOf<List<Offset>>(emptyList()) }
    var padSize by remember { mutableStateOf(Size.Zero) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Sign Document", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isProcessing, modifier = Modifier.testTag("sign_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    ChangeDocumentButton(
                        viewModel = viewModel,
                        currentDocId = doc.id,
                        enabled = !isProcessing,
                        testTagPrefix = "sign"
                    ) { selected ->
                        viewModel.selectDocument(selected)
                        strokes = emptyList()
                        currentStroke = emptyList()
                    }
                    IconButton(
                        onClick = {
                            strokes = emptyList()
                            currentStroke = emptyList()
                        },
                        enabled = !isProcessing && strokes.isNotEmpty(),
                        modifier = Modifier.testTag("sign_clear_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear signature", tint = MaterialTheme.colorScheme.onSurface)
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
                            val strokePairs = strokes.map { stroke -> stroke.map { it.x to it.y } }
                            viewModel.signDocument(doc, strokePairs, padSize.width, padSize.height) { signed ->
                                onSigned(signed)
                            }
                        },
                        enabled = !isProcessing && strokes.isNotEmpty(),
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
                            .testTag("sign_apply_button")
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Signing...", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text(
                                if (strokes.isEmpty()) "Draw your signature above" else "Apply Signature to Last Page",
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
                "Sign with your finger — it's embedded as real ink on page ${doc.pageCount} of the PDF, not a screenshot.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .onSizeChanged { padSize = Size(it.width.toFloat(), it.height.toFloat()) }
                    .pointerInput(doc.id) {
                        detectDragGestures(
                            onDragStart = { offset -> currentStroke = listOf(offset) },
                            onDrag = { change, _ ->
                                change.consume()
                                currentStroke = currentStroke + change.position
                            },
                            onDragEnd = {
                                if (currentStroke.size > 1) {
                                    strokes = strokes + listOf(currentStroke)
                                }
                                currentStroke = emptyList()
                            }
                        )
                    }
                    .testTag("sign_pad")
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val allStrokes = if (currentStroke.size > 1) strokes + listOf(currentStroke) else strokes
                    for (stroke in allStrokes) {
                        for (i in 0 until stroke.size - 1) {
                            drawLine(
                                color = Color(0xFF191919),
                                start = stroke[i],
                                end = stroke[i + 1],
                                strokeWidth = 5f
                            )
                        }
                    }
                }
                if (strokes.isEmpty() && currentStroke.isEmpty()) {
                    Text(
                        "Sign here",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

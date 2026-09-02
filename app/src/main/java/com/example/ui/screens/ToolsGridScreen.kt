package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ScanProViewModel
import com.example.model.ToolCategory
import com.example.model.ToolType
import com.example.ui.theme.*
import com.example.util.rememberDocumentScannerLauncher

data class ToolGridItem(
    val tool: ToolType,
    val icon: ImageVector,
    val categoryColor: Color,
    val testTag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsGridScreen(
    viewModel: ScanProViewModel,
    onNavigateToScanReview: () -> Unit,
    onNavigateToOcr: () -> Unit,
    onNavigateToMerge: () -> Unit,
    onNavigateToSplit: () -> Unit,
    onNavigateToCompress: () -> Unit,
    onNavigateToWatermark: () -> Unit,
    onNavigateToPassword: () -> Unit,
    onNavigateToImageToPdf: () -> Unit,
    onNavigateToImageMerger: () -> Unit,
    onNavigateToPdfToImage: () -> Unit,
    onNavigateToRotate: () -> Unit,
    onNavigateToDeletePages: () -> Unit,
    onNavigateToSign: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Real scanner (camera + gallery import) instead of the old fake camera mock.
    val launchScannerFlow = rememberDocumentScannerLauncher(viewModel = viewModel) { uris ->
        viewModel.setScannedPagesFromUris(uris)
        onNavigateToScanReview()
    }

    val toolItems = listOf(
        // Scanning (Soft Green Tint)
        ToolGridItem(ToolType.SCAN, Icons.Outlined.DocumentScanner, ToolCategoryScan, "tool_tile_scan"),
        ToolGridItem(ToolType.OCR, Icons.Outlined.TextSnippet, ToolCategoryScan, "tool_tile_ocr"),

        // Editing (Warm Paper Tint)
        ToolGridItem(ToolType.MERGE, Icons.Outlined.MergeType, ToolCategoryEdit, "tool_tile_merge"),
        ToolGridItem(ToolType.SPLIT, Icons.Outlined.Splitscreen, ToolCategoryEdit, "tool_tile_split"),
        ToolGridItem(ToolType.COMPRESS, Icons.Outlined.Compress, ToolCategoryEdit, "tool_tile_compress"),
        ToolGridItem(ToolType.IMAGE_TO_PDF, Icons.Outlined.Image, ToolCategoryEdit, "tool_tile_img_to_pdf"),
        ToolGridItem(ToolType.IMAGE_MERGER, Icons.Outlined.AutoAwesomeMosaic, ToolCategoryEdit, "tool_tile_img_merger"),
        ToolGridItem(ToolType.PDF_TO_IMAGE, Icons.Outlined.PictureAsPdf, ToolCategoryEdit, "tool_tile_pdf_to_img"),
        ToolGridItem(ToolType.WATERMARK, Icons.Outlined.WaterDrop, ToolCategoryEdit, "tool_tile_watermark"),
        ToolGridItem(ToolType.ROTATE, Icons.Outlined.RotateRight, ToolCategoryEdit, "tool_tile_rotate"),
        ToolGridItem(ToolType.DELETE_PAGES, Icons.Outlined.Delete, ToolCategoryEdit, "tool_tile_delete_pages"),

        // Security (Slate Tint)
        ToolGridItem(ToolType.PASSWORD, Icons.Outlined.Lock, ToolCategorySecurity, "tool_tile_password"),
        ToolGridItem(ToolType.SIGN, Icons.Outlined.Draw, ToolCategorySecurity, "tool_tile_sign")
    )

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
                            contentDescription = null,
                            tint = ScanProGreenPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                        Text(
                            text = "Tools",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = ScanProGreenPrimary
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            items(toolItems) { item ->
                ToolTile(
                    item = item,
                    onClick = {
                        when (item.tool) {
                            ToolType.SCAN -> launchScannerFlow()
                            ToolType.OCR -> onNavigateToOcr()
                            ToolType.MERGE -> onNavigateToMerge()
                            ToolType.SPLIT -> onNavigateToSplit()
                            ToolType.COMPRESS -> onNavigateToCompress()
                            ToolType.WATERMARK -> onNavigateToWatermark()
                            ToolType.PASSWORD -> onNavigateToPassword()
                            ToolType.IMAGE_TO_PDF -> onNavigateToImageToPdf()
                            ToolType.IMAGE_MERGER -> onNavigateToImageMerger()
                            ToolType.PDF_TO_IMAGE -> onNavigateToPdfToImage()
                            ToolType.ROTATE -> onNavigateToRotate()
                            ToolType.DELETE_PAGES -> onNavigateToDeletePages()
                            ToolType.SIGN -> onNavigateToSign()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ToolTile(
    item: ToolGridItem,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = item.categoryColor,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        ),
        shadowElevation = 0.5.dp,
        modifier = Modifier
            .height(115.dp)
            .fillMaxWidth()
            .testTag(item.testTag)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(8.dp)
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.tool.title,
                tint = ScanProInk,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.tool.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = ScanProInk,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}

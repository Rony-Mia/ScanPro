package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.DocFormat
import com.example.model.DocumentItem
import com.example.ui.theme.ScanProAccentRed
import com.example.ui.theme.ScanProGreenContainer
import java.io.File

/**
 * Robust, crash-safe thumbnail rendering component for DocumentItem.
 * Safely handles:
 * 1) thumbnailUri (SAF content:// or file://)
 * 2) filePath (JPG images or converted files)
 * 3) thumbnailRes ONLY when non-zero (so it never throws Resources$NotFoundException)
 * 4) Fallback vector icon
 */
@Composable
fun DocThumbnailImage(
    document: DocumentItem?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    if (document == null) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PictureAsPdf,
                contentDescription = null,
                tint = ScanProAccentRed,
                modifier = Modifier.size(28.dp)
            )
        }
        return
    }

    val model: Any? = when {
        !document.thumbnailUri.isNullOrEmpty() -> document.thumbnailUri
        !document.filePath.isNullOrEmpty() && (document.format == DocFormat.JPG || File(document.filePath).name.endsWith(".jpg", ignoreCase = true)) -> File(document.filePath)
        document.thumbnailRes != 0 -> document.thumbnailRes
        else -> null
    }

    if (model != null) {
        AsyncImage(
            model = model,
            contentDescription = document.title,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (document.format == DocFormat.PDF) Icons.Default.PictureAsPdf else Icons.Default.Description,
                contentDescription = document.title,
                tint = if (document.format == DocFormat.PDF) ScanProAccentRed else ScanProGreenContainer,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

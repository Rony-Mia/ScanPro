package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ScanProViewModel
import com.example.model.DocFormat
import com.example.model.DocumentItem
import com.example.ui.theme.ScanProAccentRed
import com.example.ui.theme.ScanProGreenContainer
import com.example.util.rememberFilePickerLauncher

/**
 * Top-bar "Change" action + dialog that lets the user pick which document a tool
 * (Split / Compress / Password / Watermark / OCR / Rotate / Delete Pages / Sign / PDF to
 * Image, etc.) operates on — either from the library or freshly imported from device
 * storage. Previously these screens silently operated on whatever [ScanProViewModel]'s
 * `selectedDocument` happened to be, with no way to change it from inside the tool.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeDocumentButton(
    viewModel: ScanProViewModel,
    currentDocId: String?,
    enabled: Boolean = true,
    testTagPrefix: String = "doc_picker",
    onDocumentSelected: (DocumentItem) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    TextButton(
        onClick = { showDialog = true },
        enabled = enabled,
        modifier = Modifier.testTag("${testTagPrefix}_change_button")
    ) {
        Text(
            text = "Change",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = ScanProGreenContainer
        )
    }

    if (showDialog) {
        DocumentPickerDialog(
            viewModel = viewModel,
            currentDocId = currentDocId,
            testTagPrefix = testTagPrefix,
            onDismiss = { showDialog = false },
            onSelected = { doc ->
                onDocumentSelected(doc)
                showDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentPickerDialog(
    viewModel: ScanProViewModel,
    currentDocId: String?,
    testTagPrefix: String = "doc_picker",
    onDismiss: () -> Unit,
    onSelected: (DocumentItem) -> Unit
) {
    val allDocs by viewModel.documents.collectAsState()

    // Real "import from phone storage" option, same picker used by Merge — the file the
    // user picks is copied into the library and immediately becomes the active document.
    val launchFilePicker = rememberFilePickerLauncher { uris ->
        viewModel.importDocumentsFromUris(uris) { imported ->
            imported.firstOrNull()?.let { onSelected(it) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Document") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { launchFilePicker() }
                            .padding(vertical = 10.dp, horizontal = 4.dp)
                            .testTag("${testTagPrefix}_import_from_device"),
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

                if (allDocs.isEmpty()) {
                    item {
                        Text(
                            "No documents in your library yet",
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }

                items(allDocs, key = { it.id }) { doc ->
                    val isSelected = doc.id == currentDocId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(doc) }
                            .padding(vertical = 10.dp, horizontal = 4.dp)
                            .testTag("${testTagPrefix}_doc_${doc.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (doc.format == DocFormat.JPG) Icons.Default.Image else Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = ScanProAccentRed
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                doc.title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text("${doc.pageCount} pages • ${doc.fileSize}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = "Selected", tint = ScanProGreenContainer)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

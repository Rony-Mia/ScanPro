package com.example.util

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

/**
 * Real "import from device storage" picker using Android's Storage Access
 * Framework (SAF) — the actual system file browser (Files app, Google Drive,
 * Downloads, etc.), not the app's own in-memory document list.
 *
 * This is what was missing everywhere: Merge/Split/Compress/Watermark/
 * Password/OCR and the Documents library only ever let the user choose
 * among the 5 hardcoded sample documents because nothing opened a real
 * file picker.
 *
 * @param onFilesPicked called with the URIs the user selected (PDF and/or
 * image files). Empty if the user cancelled.
 * @return a lambda that opens the system file picker. Call it from a button.
 */
@Composable
fun rememberFilePickerLauncher(
    onFilesPicked: (List<Uri>) -> Unit
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onFilesPicked(uris)
        }
    }

    return {
        launcher.launch(arrayOf("application/pdf", "image/*"))
    }
}

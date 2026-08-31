package com.example.util

import android.Manifest
import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.data.ScanProViewModel
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * Single source of truth for launching the real device scanner (camera capture +
 * gallery import) anywhere in the app. Wraps Google ML Kit's Document Scanner,
 * which handles camera preview, edge detection, cropping and permissions itself.
 *
 * Replaces the old fake CameraCaptureScreen mock UI, which never used the real
 * camera and only cycled through 4 hardcoded sample images.
 *
 * @param onPagesScanned called with the resulting image URIs once the user
 * finishes scanning/importing. Caller decides whether to start a new draft
 * (setScannedPagesFromUris) or append to an existing one (addScannedPagesFromUris),
 * and whether to navigate afterwards.
 * @return a lambda that starts the scan flow (checks/requests camera permission,
 * then launches the scanner). Call this from a button's onClick.
 */
@Composable
fun rememberDocumentScannerLauncher(
    viewModel: ScanProViewModel,
    onPagesScanned: (List<Uri>) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val activity = remember(context) {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return@remember ctx
            ctx = ctx.baseContext
        }
        null
    }

    val scannerOptions = remember {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }

    val scanner = remember(scannerOptions) {
        GmsDocumentScanning.getClient(scannerOptions)
    }

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val gmsResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pages = gmsResult?.pages
            if (!pages.isNullOrEmpty()) {
                val uris = pages.mapNotNull { it.imageUri }
                if (uris.isNotEmpty()) {
                    onPagesScanned(uris)
                }
            }
        }
    }

    fun startScan() {
        if (activity != null) {
            scanner.getStartScanIntent(activity)
                .addOnSuccessListener { intentSender ->
                    scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
                .addOnFailureListener { error ->
                    viewModel.showToast("Scanner error: ${error.localizedMessage}")
                }
        } else {
            viewModel.showToast("Scanner requires an active activity")
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startScan()
        } else {
            viewModel.showToast("Camera permission is required to scan documents")
        }
    }

    return {
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasCameraPermission) {
            startScan()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}

package com.example.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.ScanProViewModel
import com.example.ui.components.BottomTab
import com.example.ui.components.ScanProBottomBar
import com.example.ui.screens.*

object ScanProRoutes {
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val CAMERA = "camera"
    const val SCAN_REVIEW = "scan_review"
    const val PDF_VIEWER = "pdf_viewer"
    const val MERGE = "merge"
    const val SPLIT = "split"
    const val COMPRESS = "compress"
    const val PASSWORD = "password"
    const val WATERMARK = "watermark"
    const val OCR = "ocr"
}

@Composable
fun ScanProNavGraph(
    viewModel: ScanProViewModel,
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    val toastMessage by viewModel.toastMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    var currentBottomTab by remember { mutableStateOf(BottomTab.HOME) }

    NavHost(
        navController = navController,
        startDestination = ScanProRoutes.ONBOARDING,
        modifier = modifier
    ) {
        // 1. Onboarding Screen
        composable(ScanProRoutes.ONBOARDING) {
            OnboardingScreen(
                onAllowPermission = {
                    navController.navigate(ScanProRoutes.MAIN) {
                        popUpTo(ScanProRoutes.ONBOARDING) { inclusive = true }
                    }
                },
                onSkip = {
                    navController.navigate(ScanProRoutes.MAIN) {
                        popUpTo(ScanProRoutes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        // 2. Main Tab Shell (Home, Documents, Tools, Settings)
        composable(ScanProRoutes.MAIN) {
            Scaffold(
                bottomBar = {
                    ScanProBottomBar(
                        currentTab = currentBottomTab,
                        onTabSelected = { tab -> currentBottomTab = tab }
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (currentBottomTab) {
                        BottomTab.HOME -> {
                            HomeScreen(
                                viewModel = viewModel,
                                onNavigateToScan = { navController.navigate(ScanProRoutes.SCAN_REVIEW) },
                                onNavigateToDocuments = { currentBottomTab = BottomTab.DOCUMENTS },
                                onNavigateToViewer = { doc ->
                                    viewModel.selectDocument(doc)
                                    navController.navigate(ScanProRoutes.PDF_VIEWER)
                                },
                                onNavigateToMerge = { navController.navigate(ScanProRoutes.MERGE) },
                                onNavigateToCompress = { navController.navigate(ScanProRoutes.COMPRESS) },
                                onNavigateToOcr = { navController.navigate(ScanProRoutes.OCR) },
                                onNavigateToSettings = { currentBottomTab = BottomTab.SETTINGS }
                            )
                        }

                        BottomTab.DOCUMENTS -> {
                            DocumentsLibraryScreen(
                                viewModel = viewModel,
                                onNavigateToScan = { navController.navigate(ScanProRoutes.CAMERA) },
                                onNavigateToViewer = { doc ->
                                    viewModel.selectDocument(doc)
                                    navController.navigate(ScanProRoutes.PDF_VIEWER)
                                }
                            )
                        }

                        BottomTab.TOOLS -> {
                            ToolsGridScreen(
                                onNavigateToScan = { navController.navigate(ScanProRoutes.CAMERA) },
                                onNavigateToOcr = { navController.navigate(ScanProRoutes.OCR) },
                                onNavigateToMerge = { navController.navigate(ScanProRoutes.MERGE) },
                                onNavigateToSplit = { navController.navigate(ScanProRoutes.SPLIT) },
                                onNavigateToCompress = { navController.navigate(ScanProRoutes.COMPRESS) },
                                onNavigateToWatermark = { navController.navigate(ScanProRoutes.WATERMARK) },
                                onNavigateToPassword = { navController.navigate(ScanProRoutes.PASSWORD) },
                                onGenericToolSelected = { toolName ->
                                    viewModel.showToast("$toolName ready")
                                }
                            )
                        }

                        BottomTab.SETTINGS -> {
                            SettingsScreen(
                                viewModel = viewModel,
                                onBack = { currentBottomTab = BottomTab.HOME }
                            )
                        }
                    }
                }
            }
        }

        // 3. Camera Capture Screen
        composable(ScanProRoutes.CAMERA) {
            CameraCaptureScreen(
                viewModel = viewModel,
                onClose = { navController.popBackStack() },
                onNavigateToReview = { navController.navigate(ScanProRoutes.SCAN_REVIEW) }
            )
        }

        // 4. Scan Review Screen
        composable(ScanProRoutes.SCAN_REVIEW) {
            ScanReviewScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onDone = { savedDoc ->
                    viewModel.selectDocument(savedDoc)
                    navController.navigate(ScanProRoutes.PDF_VIEWER) {
                        popUpTo(ScanProRoutes.MAIN)
                    }
                },
                onAddPage = {
                    navController.navigate(ScanProRoutes.CAMERA)
                }
            )
        }

        // 5. PDF Viewer Screen
        composable(ScanProRoutes.PDF_VIEWER) {
            val selectedDoc by viewModel.selectedDocument.collectAsState()
            selectedDoc?.let { doc ->
                PdfViewerScreen(
                    document = doc,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToMerge = { navController.navigate(ScanProRoutes.MERGE) },
                    onNavigateToCompress = { navController.navigate(ScanProRoutes.COMPRESS) },
                    onNavigateToOcr = { navController.navigate(ScanProRoutes.OCR) }
                )
            } ?: run {
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            }
        }

        // 6. Merge PDF Screen
        composable(ScanProRoutes.MERGE) {
            MergePdfScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onMergeCompleted = { mergedDoc ->
                    viewModel.selectDocument(mergedDoc)
                    navController.navigate(ScanProRoutes.PDF_VIEWER) {
                        popUpTo(ScanProRoutes.MAIN)
                    }
                }
            )
        }

        // 7. Split PDF Screen
        composable(ScanProRoutes.SPLIT) {
            SplitPdfScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onSplitCompleted = {
                    currentBottomTab = BottomTab.DOCUMENTS
                    navController.navigate(ScanProRoutes.MAIN) {
                        popUpTo(ScanProRoutes.MAIN) { inclusive = true }
                    }
                }
            )
        }

        // 8. Compress PDF Screen
        composable(ScanProRoutes.COMPRESS) {
            CompressPdfScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onCompressCompleted = { compressedDoc ->
                    viewModel.selectDocument(compressedDoc)
                    navController.navigate(ScanProRoutes.PDF_VIEWER) {
                        popUpTo(ScanProRoutes.MAIN)
                    }
                }
            )
        }

        // 9. Password Protect Screen
        composable(ScanProRoutes.PASSWORD) {
            PasswordProtectScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onProtected = { protectedDoc ->
                    viewModel.selectDocument(protectedDoc)
                    navController.navigate(ScanProRoutes.PDF_VIEWER) {
                        popUpTo(ScanProRoutes.MAIN)
                    }
                }
            )
        }

        // 10. Add Watermark Screen
        composable(ScanProRoutes.WATERMARK) {
            AddWatermarkScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onWatermarkApplied = { watermarkedDoc ->
                    viewModel.selectDocument(watermarkedDoc)
                    navController.navigate(ScanProRoutes.PDF_VIEWER) {
                        popUpTo(ScanProRoutes.MAIN)
                    }
                }
            )
        }

        // 11. OCR Text Screen
        composable(ScanProRoutes.OCR) {
            OcrTextScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

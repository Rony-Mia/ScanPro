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
    const val SCAN_REVIEW = "scan_review"
    const val PDF_VIEWER = "pdf_viewer"
    const val MERGE = "merge"
    const val SPLIT = "split"
    const val COMPRESS = "compress"
    const val PASSWORD = "password"
    const val WATERMARK = "watermark"
    const val OCR = "ocr"
    const val IMAGE_TO_PDF = "image_to_pdf"
    const val PDF_TO_IMAGE = "pdf_to_image"
    const val ROTATE = "rotate"
    const val DELETE_PAGES = "delete_pages"
    const val SIGN = "sign"
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
                                onNavigateToScanReview = { navController.navigate(ScanProRoutes.SCAN_REVIEW) },
                                onNavigateToViewer = { doc ->
                                    viewModel.selectDocument(doc)
                                    navController.navigate(ScanProRoutes.PDF_VIEWER)
                                }
                            )
                        }

                        BottomTab.TOOLS -> {
                            ToolsGridScreen(
                                viewModel = viewModel,
                                onNavigateToScanReview = { navController.navigate(ScanProRoutes.SCAN_REVIEW) },
                                onNavigateToOcr = { navController.navigate(ScanProRoutes.OCR) },
                                onNavigateToMerge = { navController.navigate(ScanProRoutes.MERGE) },
                                onNavigateToSplit = { navController.navigate(ScanProRoutes.SPLIT) },
                                onNavigateToCompress = { navController.navigate(ScanProRoutes.COMPRESS) },
                                onNavigateToWatermark = { navController.navigate(ScanProRoutes.WATERMARK) },
                                onNavigateToPassword = { navController.navigate(ScanProRoutes.PASSWORD) },
                                onNavigateToImageToPdf = { navController.navigate(ScanProRoutes.IMAGE_TO_PDF) },
                                onNavigateToPdfToImage = { navController.navigate(ScanProRoutes.PDF_TO_IMAGE) },
                                onNavigateToRotate = { navController.navigate(ScanProRoutes.ROTATE) },
                                onNavigateToDeletePages = { navController.navigate(ScanProRoutes.DELETE_PAGES) },
                                onNavigateToSign = { navController.navigate(ScanProRoutes.SIGN) }
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

        // 3. Scan Review Screen
        // Reached directly from Home / Documents / Tools once the real ML Kit
        // scanner (camera + gallery import) has produced page images — there is
        // no separate camera route anymore.
        composable(ScanProRoutes.SCAN_REVIEW) {
            ScanReviewScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onDone = { savedDoc ->
                    viewModel.selectDocument(savedDoc)
                    navController.navigate(ScanProRoutes.PDF_VIEWER) {
                        popUpTo(ScanProRoutes.MAIN)
                    }
                }
            )
        }

        // 4. PDF Viewer Screen
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

        // 5. Merge PDF Screen
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

        // 6. Split PDF Screen
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

        // 7. Compress PDF Screen
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

        // 8. Password Protect Screen
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

        // 9. Add Watermark Screen
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

        // 10. OCR Text Screen
        composable(ScanProRoutes.OCR) {
            OcrTextScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // 11. Image to PDF Screen
        composable(ScanProRoutes.IMAGE_TO_PDF) {
            ImageToPdfScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onConverted = { newDoc ->
                    viewModel.selectDocument(newDoc)
                    navController.navigate(ScanProRoutes.PDF_VIEWER) {
                        popUpTo(ScanProRoutes.MAIN)
                    }
                }
            )
        }

        // 12. PDF to Image Screen
        composable(ScanProRoutes.PDF_TO_IMAGE) {
            PdfToImageScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onConverted = {
                    currentBottomTab = BottomTab.DOCUMENTS
                    navController.navigate(ScanProRoutes.MAIN) {
                        popUpTo(ScanProRoutes.MAIN) { inclusive = true }
                    }
                }
            )
        }

        // 13. Rotate Pages Screen
        composable(ScanProRoutes.ROTATE) {
            RotatePagesScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onRotated = { rotatedDoc ->
                    viewModel.selectDocument(rotatedDoc)
                    navController.navigate(ScanProRoutes.PDF_VIEWER) {
                        popUpTo(ScanProRoutes.MAIN)
                    }
                }
            )
        }

        // 14. Delete Pages Screen
        composable(ScanProRoutes.DELETE_PAGES) {
            DeletePagesScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onDeleted = { editedDoc ->
                    viewModel.selectDocument(editedDoc)
                    navController.navigate(ScanProRoutes.PDF_VIEWER) {
                        popUpTo(ScanProRoutes.MAIN)
                    }
                }
            )
        }

        // 15. Sign Document Screen
        composable(ScanProRoutes.SIGN) {
            SignDocumentScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onSigned = { signedDoc ->
                    viewModel.selectDocument(signedDoc)
                    navController.navigate(ScanProRoutes.PDF_VIEWER) {
                        popUpTo(ScanProRoutes.MAIN)
                    }
                }
            )
        }
    }
}

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
import com.example.model.ToolType
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
    const val IMAGE_MERGER = "image_merger"
    const val PDF_TO_IMAGE = "pdf_to_image"
    const val ROTATE = "rotate"
    const val DELETE_PAGES = "delete_pages"
    const val SIGN = "sign"
    const val ID_CARD = "id_card"
    const val BUSINESS_CARD = "business_card"
    const val WHITEBOARD = "whiteboard"
    const val QR_BARCODE = "qr_barcode"
    const val PDF_TO_WORD = "pdf_to_word"
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
    val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsState()
    val initialRoute = remember {
        if (viewModel.isOnboardingCompleted.value) ScanProRoutes.MAIN else ScanProRoutes.ONBOARDING
    }

    NavHost(
        navController = navController,
        startDestination = initialRoute,
        modifier = modifier
    ) {
        // 1. Onboarding Screen
        composable(ScanProRoutes.ONBOARDING) {
            OnboardingScreen(
                onFinishOnboarding = {
                    viewModel.completeOnboarding()
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
                            val navigateToTool: (ToolType) -> Unit = { tool ->
                                when (tool) {
                                    ToolType.SCAN -> navController.navigate(ScanProRoutes.SCAN_REVIEW)
                                    ToolType.ID_CARD -> navController.navigate(ScanProRoutes.ID_CARD)
                                    ToolType.BUSINESS_CARD -> navController.navigate(ScanProRoutes.BUSINESS_CARD)
                                    ToolType.WHITEBOARD -> navController.navigate(ScanProRoutes.WHITEBOARD)
                                    ToolType.QR_BARCODE -> navController.navigate(ScanProRoutes.QR_BARCODE)
                                    ToolType.OCR -> navController.navigate(ScanProRoutes.OCR)
                                    ToolType.MERGE -> navController.navigate(ScanProRoutes.MERGE)
                                    ToolType.SPLIT -> navController.navigate(ScanProRoutes.SPLIT)
                                    ToolType.COMPRESS -> navController.navigate(ScanProRoutes.COMPRESS)
                                    ToolType.PDF_TO_WORD -> navController.navigate(ScanProRoutes.PDF_TO_WORD)
                                    ToolType.IMAGE_TO_PDF -> navController.navigate(ScanProRoutes.IMAGE_TO_PDF)
                                    ToolType.IMAGE_MERGER -> navController.navigate(ScanProRoutes.IMAGE_MERGER)
                                    ToolType.PDF_TO_IMAGE -> navController.navigate(ScanProRoutes.PDF_TO_IMAGE)
                                    ToolType.WATERMARK -> navController.navigate(ScanProRoutes.WATERMARK)
                                    ToolType.ROTATE -> navController.navigate(ScanProRoutes.ROTATE)
                                    ToolType.DELETE_PAGES -> navController.navigate(ScanProRoutes.DELETE_PAGES)
                                    ToolType.PASSWORD -> navController.navigate(ScanProRoutes.PASSWORD)
                                    ToolType.SIGN -> navController.navigate(ScanProRoutes.SIGN)
                                }
                            }
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
                                onNavigateToSettings = { currentBottomTab = BottomTab.SETTINGS },
                                onNavigateToTool = navigateToTool
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
                                onNavigateToIdCard = { navController.navigate(ScanProRoutes.ID_CARD) },
                                onNavigateToBusinessCard = { navController.navigate(ScanProRoutes.BUSINESS_CARD) },
                                onNavigateToWhiteboard = { navController.navigate(ScanProRoutes.WHITEBOARD) },
                                onNavigateToQrBarcode = { navController.navigate(ScanProRoutes.QR_BARCODE) },
                                onNavigateToOcr = { navController.navigate(ScanProRoutes.OCR) },
                                onNavigateToMerge = { navController.navigate(ScanProRoutes.MERGE) },
                                onNavigateToSplit = { navController.navigate(ScanProRoutes.SPLIT) },
                                onNavigateToCompress = { navController.navigate(ScanProRoutes.COMPRESS) },
                                onNavigateToPdfToWord = { navController.navigate(ScanProRoutes.PDF_TO_WORD) },
                                onNavigateToWatermark = { navController.navigate(ScanProRoutes.WATERMARK) },
                                onNavigateToPassword = { navController.navigate(ScanProRoutes.PASSWORD) },
                                onNavigateToImageToPdf = { navController.navigate(ScanProRoutes.IMAGE_TO_PDF) },
                                onNavigateToImageMerger = { navController.navigate(ScanProRoutes.IMAGE_MERGER) },
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

        // 12. Image Merger Screen (Collage / Multi-Image Merger)
        composable(ScanProRoutes.IMAGE_MERGER) {
            ImageMergerScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onMerged = { newDoc ->
                    if (newDoc.format == com.example.model.DocFormat.PDF) {
                        viewModel.selectDocument(newDoc)
                        navController.navigate(ScanProRoutes.PDF_VIEWER) {
                            popUpTo(ScanProRoutes.MAIN)
                        }
                    } else {
                        currentBottomTab = BottomTab.DOCUMENTS
                        navController.navigate(ScanProRoutes.MAIN) {
                            popUpTo(ScanProRoutes.MAIN) { inclusive = true }
                        }
                    }
                }
            )
        }

        // 13. PDF to Image Screen
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

        // 16. ID Card Scanner Screen
        composable(ScanProRoutes.ID_CARD) {
            IdCardScannerScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onGenerated = { idCardDoc ->
                    viewModel.selectDocument(idCardDoc)
                    navController.navigate(ScanProRoutes.PDF_VIEWER) {
                        popUpTo(ScanProRoutes.MAIN)
                    }
                }
            )
        }

        // 17. QR / Barcode Scanner Screen
        composable(ScanProRoutes.QR_BARCODE) {
            QrBarcodeScannerScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // 18. PDF to Word (.docx) Screen
        composable(ScanProRoutes.PDF_TO_WORD) {
            PdfToWordScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onConverted = { _ ->
                    // Keep on screen to allow viewing result details, sharing or opening the converted Word file
                }
            )
        }

        // 19. Business Card Scanner Screen
        composable(ScanProRoutes.BUSINESS_CARD) {
            BusinessCardScannerScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onDone = { cardDoc ->
                    viewModel.selectDocument(cardDoc)
                    navController.navigate(ScanProRoutes.PDF_VIEWER) {
                        popUpTo(ScanProRoutes.MAIN)
                    }
                }
            )
        }

        // 20. Whiteboard / Blackboard Scanner Screen
        composable(ScanProRoutes.WHITEBOARD) {
            WhiteboardScanScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onDone = { wbDoc ->
                    viewModel.selectDocument(wbDoc)
                    navController.navigate(ScanProRoutes.PDF_VIEWER) {
                        popUpTo(ScanProRoutes.MAIN)
                    }
                }
            )
        }
    }
}

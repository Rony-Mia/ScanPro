package com.example.data

import android.app.Application
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.*
import com.example.util.Constants
import com.example.util.StorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ScanProViewModel(application: Application) : AndroidViewModel(application) {

    private val pdfEngine = PdfEngine(application)
    private val ocrEngine = OcrEngine(application)
    val imageMergerEngine = ImageMergerEngine(application)
    private val documentsDir = File(application.filesDir, "documents").apply { mkdirs() }
    private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE)

    private val _isOnboardingCompleted = MutableStateFlow(prefs.getBoolean(Constants.PREF_KEY_ONBOARDING_COMPLETED, false))
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()

    fun completeOnboarding() {
        prefs.edit().putBoolean(Constants.PREF_KEY_ONBOARDING_COMPLETED, true).apply()
        _isOnboardingCompleted.value = true
    }

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _ocrProgress = MutableStateFlow(0f)
    val ocrProgress: StateFlow<Float> = _ocrProgress.asStateFlow()

    override fun onCleared() {
        super.onCleared()
        ocrEngine.close()
    }

    private val _documents = MutableStateFlow<List<DocumentItem>>(emptyList())
    val documents: StateFlow<List<DocumentItem>> = _documents.asStateFlow()

    private val _activeDraftPages = MutableStateFlow<List<ScannedPage>>(emptyList())
    val activeDraftPages: StateFlow<List<ScannedPage>> = _activeDraftPages.asStateFlow()

    private val _selectedDraftIndex = MutableStateFlow(0)
    val selectedDraftIndex: StateFlow<Int> = _selectedDraftIndex.asStateFlow()

    private val _selectedDocument = MutableStateFlow<DocumentItem?>(null)
    val selectedDocument: StateFlow<DocumentItem?> = _selectedDocument.asStateFlow()

    // Document persistence: the library used to live only in memory (MutableStateFlow),
    // so every real scan/import/merge/etc. was lost the moment the app process died.
    // On startup we load whatever was last saved to disk (if any), and from then on every
    // change to _documents is written back to a JSON file in app storage.
    private var isRestoringPersistedLibrary = true

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val persisted = DocumentStore.load(documentsDir)
            withContext(Dispatchers.Main) {
                if (persisted != null) {
                    _documents.value = persisted
                    _selectedDocument.value = persisted.firstOrNull()
                }
                isRestoringPersistedLibrary = false
            }
            documents.collect { docs ->
                if (!isRestoringPersistedLibrary) {
                    DocumentStore.save(documentsDir, docs)
                }
            }
        }
    }

    private val _filterTab = MutableStateFlow("All")
    val filterTab: StateFlow<String> = _filterTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isGridView = MutableStateFlow(false)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    private val _sortOrder = MutableStateFlow(DocSortOrder.DATE_DESC)
    val sortOrder: StateFlow<DocSortOrder> = _sortOrder.asStateFlow()

    fun setSortOrder(order: DocSortOrder) {
        _sortOrder.value = order
        showToast("Sorted by ${order.displayName}")
    }

    // Settings
    private val _darkMode = MutableStateFlow(false)
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()

    private val _defaultSaveLocation = MutableStateFlow(StorageHelper.getCurrentSaveLocationDisplay(application))
    val defaultSaveLocation: StateFlow<String> = _defaultSaveLocation.asStateFlow()

    fun setDefaultSaveLocation() {
        StorageHelper.setDefaultSaveLocation(getApplication())
        _defaultSaveLocation.value = Constants.DEFAULT_SAVE_LOCATION_NAME
        showToast("Save location set to ${Constants.DEFAULT_SAVE_LOCATION_NAME}")
    }

    fun setCustomSaveLocation(uri: Uri) {
        val folderName = StorageHelper.setCustomSaveLocation(getApplication(), uri)
        _defaultSaveLocation.value = folderName
        showToast("Save location set to $folderName")
    }

    fun isDefaultSaveLocation(): Boolean {
        return StorageHelper.isDefaultLocation(getApplication())
    }

    private val _defaultQuality = MutableStateFlow(prefs.getString("pref_image_quality", "High (300 dpi)") ?: "High (300 dpi)")
    val defaultQuality: StateFlow<String> = _defaultQuality.asStateFlow()

    fun setDefaultQuality(quality: String) {
        _defaultQuality.value = quality
        prefs.edit().putString("pref_image_quality", quality).apply()
        showToast("Quality set to $quality")
    }

    private val _language = MutableStateFlow(prefs.getString("pref_app_language", "English (US)") ?: "English (US)")
    val language: StateFlow<String> = _language.asStateFlow()

    fun setLanguage(langName: String, localeTag: String) {
        _language.value = langName
        prefs.edit().putString("pref_app_language", langName).apply()
        try {
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                androidx.core.os.LocaleListCompat.forLanguageTags(localeTag)
            )
        } catch (_: Exception) {}
        showToast("Language set to $langName")
    }

    // OCR Multi-Language Support
    private val _ocrLanguage = MutableStateFlow(prefs.getString("pref_ocr_language", OcrEngine.DEFAULT_OCR_LANG) ?: OcrEngine.DEFAULT_OCR_LANG)
    val ocrLanguage: StateFlow<String> = _ocrLanguage.asStateFlow()

    private val _installedOcrLanguages = MutableStateFlow<List<String>>(emptyList())
    val installedOcrLanguages: StateFlow<List<String>> = _installedOcrLanguages.asStateFlow()

    private val _ocrDownloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val ocrDownloadProgress: StateFlow<Map<String, Float>> = _ocrDownloadProgress.asStateFlow()

    fun refreshInstalledOcrLanguages() {
        viewModelScope.launch(Dispatchers.IO) {
            val installed = ocrEngine.getInstalledLanguageCodes()
            _installedOcrLanguages.value = installed
        }
    }

    fun setOcrLanguage(langCode: String) {
        _ocrLanguage.value = langCode
        prefs.edit().putString("pref_ocr_language", langCode).apply()
    }

    fun downloadOcrLanguage(code: String, onComplete: ((Boolean) -> Unit)? = null) {
        if (_ocrDownloadProgress.value.containsKey(code)) return
        viewModelScope.launch(Dispatchers.IO) {
            _ocrDownloadProgress.update { it + (code to 0.01f) }
            val success = ocrEngine.downloadLanguage(code) { progress ->
                _ocrDownloadProgress.update { it + (code to progress) }
            }
            _ocrDownloadProgress.update { it - code }
            refreshInstalledOcrLanguages()
            withContext(Dispatchers.Main) {
                if (success) {
                    val langName = OcrEngine.AVAILABLE_LANGUAGES.find { it.code == code }?.name ?: code
                    showToast("$langName downloaded. Available offline!")
                } else {
                    showToast("Download failed for $code")
                }
                onComplete?.invoke(success)
            }
        }
    }

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    fun showToast(message: String) {
        _toastMessage.value = message
        viewModelScope.launch {
            delay(2500)
            if (_toastMessage.value == message) {
                _toastMessage.value = null
            }
        }
    }

    fun setFilterTab(tab: String) {
        _filterTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleGridView() {
        _isGridView.value = !_isGridView.value
    }

    fun toggleDarkMode(enabled: Boolean) {
        _darkMode.value = enabled
    }

    fun selectDocument(doc: DocumentItem) {
        _selectedDocument.value = doc
    }

    fun selectDocumentById(id: String) {
        _selectedDocument.value = _documents.value.find { it.id == id } ?: _documents.value.firstOrNull()
    }

    fun renameDocument(id: String, newTitle: String) {
        _documents.update { list ->
            list.map { if (it.id == id) it.copy(title = newTitle) else it }
        }
        if (_selectedDocument.value?.id == id) {
            _selectedDocument.value = _selectedDocument.value?.copy(title = newTitle)
        }
        showToast("Renamed to $newTitle")
    }

    fun deleteDocument(id: String) {
        _documents.update { list ->
            list.filterNot { it.id == id }
        }
        if (_selectedDocument.value?.id == id) {
            _selectedDocument.value = _documents.value.firstOrNull()
        }
        showToast("Document deleted")
    }

    fun clearAllDocumentsForEmptyState() {
        _documents.value = emptyList()
        _selectedDocument.value = null
        showToast("Library cleared")
    }

    /**
     * Imports real files the user picked from device storage (via the system
     * file picker) into the library — copies each into app storage, reads its
     * real name/size/page count, and adds it as a genuine DocumentItem.
     * This is what actually powers "Import File" / "Add Files from device"
     * everywhere in the app, instead of only offering the 5 sample documents.
     */
    fun importDocumentsFromUris(uris: List<Uri>, onComplete: ((List<DocumentItem>) -> Unit)? = null) {
        if (uris.isEmpty()) return
        if (_isProcessing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            try {
                val importedDocs = mutableListOf<DocumentItem>()
                var failedCount = 0
                uris.forEachIndexed { index, uri ->
                    val info = pdfEngine.importExternalFile(uri, documentsDir)
                    if (info == null) {
                        failedCount++
                        return@forEachIndexed
                    }
                    val docId = "doc-${System.currentTimeMillis()}-$index"
                    val thumbnailUri: String? = when (info.format) {
                        DocFormat.PDF -> {
                            val thumbFile = File(documentsDir, "${docId}_thumb.jpg")
                            pdfEngine.generateThumbnailForPdf(info.file, thumbFile)?.toString()
                        }
                        else -> Uri.fromFile(info.file).toString()
                    }
                    importedDocs.add(
                        DocumentItem(
                            id = docId,
                            title = info.displayName,
                            date = "Today",
                            time = "Just now",
                            pageCount = info.pageCount,
                            format = info.format,
                            fileSize = PdfEngine.formatFileSize(info.file.length()),
                            thumbnailRes = 0,
                            thumbnailUri = thumbnailUri,
                            category = DocCategory.TODAY,
                            pages = emptyList(),
                            filePath = info.file.absolutePath
                        )
                    )
                }
                if (importedDocs.isNotEmpty()) {
                    _documents.update { importedDocs + it }
                }
                withContext(Dispatchers.Main) {
                    when {
                        importedDocs.isNotEmpty() && failedCount == 0 ->
                            showToast("${importedDocs.size} ${if (importedDocs.size == 1) "file" else "files"} imported")
                        importedDocs.isNotEmpty() && failedCount > 0 ->
                            showToast("${importedDocs.size} imported, $failedCount failed")
                        else ->
                            showToast("Import failed")
                    }
                    onComplete?.invoke(importedDocs)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Import failed: ${e.localizedMessage ?: "Unknown error"}")
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // Active Draft Scanning
    fun selectDraftPageIndex(index: Int) {
        if (index in 0 until _activeDraftPages.value.size) {
            _selectedDraftIndex.value = index
        }
    }

    fun rotateActivePage() {
        val index = _selectedDraftIndex.value
        val pages = _activeDraftPages.value.toMutableList()
        if (index in pages.indices) {
            val p = pages[index]
            pages[index] = p.copy(rotationAngle = (p.rotationAngle + 90f) % 360f)
            _activeDraftPages.value = pages
            showToast("Rotated 90°")
        }
    }

    fun cycleFilterActivePage() {
        val index = _selectedDraftIndex.value
        val pages = _activeDraftPages.value.toMutableList()
        if (index in pages.indices) {
            val p = pages[index]
            val filters = PageFilter.values()
            val nextFilter = filters[(p.filter.ordinal + 1) % filters.size]
            pages[index] = p.copy(filter = nextFilter)
            _activeDraftPages.value = pages
            showToast("Filter: ${nextFilter.displayName}")
        }
    }

    fun updateActivePageCrop(cropLeft: Float, cropTop: Float, cropRight: Float, cropBottom: Float) {
        val index = _selectedDraftIndex.value
        val pages = _activeDraftPages.value.toMutableList()
        if (index in pages.indices) {
            val p = pages[index]
            val safeLeft = cropLeft.coerceIn(0f, 0.85f)
            val safeTop = cropTop.coerceIn(0f, 0.85f)
            val safeRight = cropRight.coerceIn(safeLeft + 0.1f, 1f)
            val safeBottom = cropBottom.coerceIn(safeTop + 0.1f, 1f)
            pages[index] = p.copy(
                cropLeft = safeLeft,
                cropTop = safeTop,
                cropRight = safeRight,
                cropBottom = safeBottom
            )
            _activeDraftPages.value = pages
        }
    }

    fun resetActivePageCrop() {
        val index = _selectedDraftIndex.value
        val pages = _activeDraftPages.value.toMutableList()
        if (index in pages.indices) {
            val p = pages[index]
            pages[index] = p.copy(
                cropLeft = 0f,
                cropTop = 0f,
                cropRight = 1f,
                cropBottom = 1f
            )
            _activeDraftPages.value = pages
            showToast("Crop reset")
        }
    }

    fun addPageToDraft(
        @DrawableRes res: Int = 0,
        imageUri: String? = null
    ) {
        val pages = _activeDraftPages.value.toMutableList()
        val newPage = ScannedPage(
            id = UUID.randomUUID().toString(),
            pageNumber = pages.size + 1,
            drawableRes = res,
            imageUri = imageUri
        )
        pages.add(newPage)
        _activeDraftPages.value = pages
        _selectedDraftIndex.value = pages.size - 1
        showToast("Page ${pages.size} added")
    }

    fun setScannedPagesFromUris(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        val newPages = uris.mapIndexed { index, uri ->
            ScannedPage(
                id = UUID.randomUUID().toString(),
                pageNumber = index + 1,
                imageUri = uri.toString(),
                drawableRes = 0
            )
        }
        _activeDraftPages.value = newPages
        _selectedDraftIndex.value = 0
        showToast("${newPages.size} ${if (newPages.size == 1) "page" else "pages"} scanned")
    }

    fun addScannedPagesFromUris(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        val pages = _activeDraftPages.value.toMutableList()
        val startIndex = pages.size
        uris.forEachIndexed { index, uri ->
            pages.add(
                ScannedPage(
                    id = UUID.randomUUID().toString(),
                    pageNumber = startIndex + index + 1,
                    imageUri = uri.toString(),
                    drawableRes = 0
                )
            )
        }
        _activeDraftPages.value = pages
        _selectedDraftIndex.value = pages.size - 1
        showToast("${uris.size} ${if (uris.size == 1) "page" else "pages"} added")
    }

    fun deleteActivePage() {
        val index = _selectedDraftIndex.value
        val pages = _activeDraftPages.value.toMutableList()
        if (pages.size > 1 && index in pages.indices) {
            pages.removeAt(index)
            // Re-number pages
            val reindexed = pages.mapIndexed { i, p -> p.copy(pageNumber = i + 1) }
            _activeDraftPages.value = reindexed
            _selectedDraftIndex.value = (index - 1).coerceAtLeast(0)
            showToast("Page deleted")
        } else {
            showToast("Scan must have at least 1 page")
        }
    }

    private suspend fun ensurePdfFileInternal(doc: DocumentItem): File = withContext(Dispatchers.IO) {
        // If doc is a JPG or PNG image, convert it to a real single-page PDF and cache it
        if (doc.format == DocFormat.JPG || doc.format == DocFormat.PNG) {
            val convertedFile = File(documentsDir, "${doc.id}_converted.pdf")
            if (convertedFile.exists() && convertedFile.length() > 0L) {
                return@withContext convertedFile
            }
            val imageUri: Uri? = when {
                !doc.filePath.isNullOrEmpty() -> Uri.fromFile(File(doc.filePath))
                !doc.thumbnailUri.isNullOrEmpty() -> Uri.parse(doc.thumbnailUri)
                doc.pages.isNotEmpty() && !doc.pages.first().imageUri.isNullOrEmpty() -> Uri.parse(doc.pages.first().imageUri)
                else -> null
            }
            if (imageUri != null) {
                return@withContext pdfEngine.imagesToPdf(listOf(imageUri), convertedFile)
            }
        }

        // If doc is a PDF with a valid existing file path, return it directly
        if (!doc.filePath.isNullOrEmpty()) {
            val file = File(doc.filePath)
            if (file.exists() && file.length() > 0L) {
                return@withContext file
            }
        }

        // Otherwise build from pages or thumbnail
        val file = File(documentsDir, "${doc.id}.pdf")
        if (!file.exists() || file.length() == 0L) {
            if (doc.pages.isNotEmpty()) {
                pdfEngine.createPdfFromPages(doc.pages, file)
            } else if (!doc.thumbnailUri.isNullOrEmpty()) {
                pdfEngine.imagesToPdf(listOf(Uri.parse(doc.thumbnailUri)), file)
            } else {
                pdfEngine.createEmptyPdf(file)
            }
        }
        file
    }

    /**
     * Renders a specific page of [doc] directly from its file to a Bitmap for the PDF Viewer.
     */
    suspend fun renderPdfPage(doc: DocumentItem, pageIndex: Int): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        try {
            val file = if (doc.format == DocFormat.PDF && !doc.filePath.isNullOrEmpty() && File(doc.filePath).exists()) {
                File(doc.filePath)
            } else {
                ensurePdfFileInternal(doc)
            }
            pdfEngine.renderPdfPageToBitmap(file, pageIndex)
        } catch (e: Exception) {
            null
        }
    }

    fun finishScanAndSave(
        format: DocFormat = DocFormat.PDF,
        onComplete: ((DocumentItem) -> Unit)? = null
    ) {
        if (_isProcessing.value) return
        val pages = _activeDraftPages.value
        if (pages.isEmpty()) {
            showToast("No pages to save")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            try {
                val firstPage = pages.firstOrNull()
                val docId = "doc-${System.currentTimeMillis()}"

                val (qualityVal, maxDim) = when {
                    _defaultQuality.value.contains("Low", true) -> Pair(0.70f, 1200)
                    _defaultQuality.value.contains("Medium", true) -> Pair(0.85f, 1800)
                    else -> Pair(0.95f, 2400)
                }
                val qualityInt = (qualityVal * 100).toInt().coerceIn(10, 100)

                val generatedFile: File
                val title: String
                val mimeType: String

                if (format == DocFormat.JPG || format == DocFormat.PNG) {
                    val pageToSave = firstPage ?: pages.first()
                    val bitmap = pdfEngine.loadPageBitmap(pageToSave)
                        ?: throw IllegalStateException("Could not load scanned image")

                    val ext = if (format == DocFormat.PNG) "png" else "jpg"
                    mimeType = if (format == DocFormat.PNG) "image/png" else "image/jpeg"
                    title = "Scan_${System.currentTimeMillis() % 10000}.$ext"
                    generatedFile = File(documentsDir, "$docId.$ext")

                    FileOutputStream(generatedFile).use { out ->
                        if (format == DocFormat.PNG) {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        } else {
                            bitmap.compress(Bitmap.CompressFormat.JPEG, qualityInt, out)
                        }
                    }
                    bitmap.recycle()
                } else {
                    title = "Scan_${System.currentTimeMillis() % 10000}.pdf"
                    mimeType = "application/pdf"
                    val outputFile = File(documentsDir, "$docId.pdf")
                    generatedFile = pdfEngine.createPdfFromPages(pages, outputFile, qualityVal, maxDim)
                }

                val realSize = PdfEngine.formatFileSize(generatedFile.length())

                // Export to user-chosen public storage (Documents/ScanPro or SAF custom folder)
                val saveResult = StorageHelper.saveFileToUserStorage(getApplication(), generatedFile, title, mimeType)
                val destinationDisplay = if (saveResult.success) saveResult.displayPath else "${Constants.DEFAULT_SAVE_LOCATION_NAME}/$title"

                val newDoc = DocumentItem(
                    id = docId,
                    title = title,
                    date = "Today",
                    time = "Just now",
                    pageCount = if (format == DocFormat.PDF) pages.size else 1,
                    format = format,
                    fileSize = realSize,
                    thumbnailRes = firstPage?.drawableRes ?: 0,
                    thumbnailUri = if (format == DocFormat.JPG || format == DocFormat.PNG) Uri.fromFile(generatedFile).toString() else firstPage?.imageUri,
                    category = DocCategory.TODAY,
                    pages = pages,
                    ocrText = "",
                    filePath = generatedFile.absolutePath
                )
                _documents.update { listOf(newDoc) + it }
                _selectedDocument.value = newDoc
                withContext(Dispatchers.Main) {
                    showToast("Saved to $destinationDisplay ($realSize)")
                    onComplete?.invoke(newDoc)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Failed to save document: ${e.localizedMessage ?: "Unknown error"}")
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun printDocument(context: android.content.Context, doc: DocumentItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = ensurePdfFileInternal(doc)
                withContext(Dispatchers.Main) {
                    com.example.util.PrintUtil.printPdfFile(context, doc, file)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Print error: ${e.localizedMessage ?: "Unknown error"}")
                }
            }
        }
    }

    // PDF Utilities Operations
    fun mergeDocuments(
        docIds: List<String>,
        outputTitle: String = "Merged_Document.pdf",
        onComplete: ((DocumentItem) -> Unit)? = null
    ) {
        if (_isProcessing.value) return
        if (docIds.size < 2) {
            showToast("Please select at least 2 files to merge")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            try {
                // Preserve exact order from docIds
                val selected = docIds.mapNotNull { id -> _documents.value.find { it.id == id } }
                if (selected.size < 2) {
                    withContext(Dispatchers.Main) {
                        showToast("Could not locate at least 2 documents to merge")
                    }
                    return@launch
                }
                val allPages = selected.flatMap { it.pages }
                val docId = "doc-${System.currentTimeMillis()}"
                val outputFile = File(documentsDir, "$docId.pdf")

                val inputUris = selected.map { doc ->
                    val file = ensurePdfFileInternal(doc)
                    Uri.fromFile(file)
                }

                val mergedFile = pdfEngine.mergePdfs(inputUris, outputFile)
                val realSize = PdfEngine.formatFileSize(mergedFile.length())
                val totalPages = selected.sumOf { it.pageCount }.coerceAtLeast(allPages.size).coerceAtLeast(1)

                val safeTitle = if (outputTitle.isNotBlank()) {
                    if (outputTitle.endsWith(".pdf", ignoreCase = true)) outputTitle else "$outputTitle.pdf"
                } else "Merged_${System.currentTimeMillis() % 10000}.pdf"

                // Export to user storage
                val saveResult = StorageHelper.saveFileToUserStorage(getApplication(), mergedFile, safeTitle)
                val destinationDisplay = if (saveResult.success) saveResult.displayPath else "${Constants.DEFAULT_SAVE_LOCATION_NAME}/$safeTitle"

                val newDoc = DocumentItem(
                    id = docId,
                    title = safeTitle,
                    date = "Today",
                    time = "Just now",
                    pageCount = totalPages,
                    format = DocFormat.PDF,
                    fileSize = realSize,
                    thumbnailRes = selected.firstOrNull()?.thumbnailRes ?: 0,
                    thumbnailUri = selected.firstOrNull()?.thumbnailUri,
                    category = DocCategory.TODAY,
                    pages = allPages,
                    filePath = mergedFile.absolutePath
                )
                _documents.update { listOf(newDoc) + it }
                _selectedDocument.value = newDoc
                withContext(Dispatchers.Main) {
                    showToast("Saved to $destinationDisplay ($realSize)")
                    onComplete?.invoke(newDoc)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Merge failed: ${e.localizedMessage ?: "Unknown error"}")
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun splitDocument(
        doc: DocumentItem,
        splitCuts: List<Int>,
        onComplete: ((List<DocumentItem>) -> Unit)? = null
    ) {
        if (_isProcessing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            try {
                val sourceFile = ensurePdfFileInternal(doc)
                val outputDir = File(documentsDir, "split_${System.currentTimeMillis()}").apply { mkdirs() }

                val splitFiles = pdfEngine.splitPdf(Uri.fromFile(sourceFile), splitCuts, outputDir)
                val locationName = StorageHelper.getCurrentSaveLocationDisplay(getApplication())
                val result = splitFiles.mapIndexed { index, file ->
                    val partNum = index + 1
                    val realSize = PdfEngine.formatFileSize(file.length())
                    StorageHelper.saveFileToUserStorage(getApplication(), file, file.name)
                    DocumentItem(
                        id = "doc-${System.currentTimeMillis()}-$partNum",
                        title = file.name,
                        date = "Today",
                        time = "Just now",
                        pageCount = (doc.pageCount / splitFiles.size.coerceAtLeast(1)).coerceAtLeast(1),
                        format = DocFormat.PDF,
                        fileSize = realSize,
                        thumbnailRes = doc.thumbnailRes,
                        thumbnailUri = doc.thumbnailUri,
                        category = DocCategory.TODAY,
                        pages = emptyList(),
                        filePath = file.absolutePath
                    )
                }
                _documents.update { result + it }
                withContext(Dispatchers.Main) {
                    showToast("Saved ${result.size} split files to $locationName")
                    onComplete?.invoke(result)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Split failed: ${e.localizedMessage ?: "Unknown error"}")
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun compressDocument(
        doc: DocumentItem,
        level: CompressionLevel,
        onComplete: ((DocumentItem) -> Unit)? = null
    ) {
        if (_isProcessing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            try {
                val sourceFile = ensurePdfFileInternal(doc)
                val docId = "doc-${System.currentTimeMillis()}"
                val targetFileName = "${doc.title.substringBeforeLast(".")}_compressed.pdf"
                val outputFile = File(documentsDir, targetFileName)

                val compressedFile = pdfEngine.compressPdf(Uri.fromFile(sourceFile), level, outputFile)
                val realSize = PdfEngine.formatFileSize(compressedFile.length())

                val saveResult = StorageHelper.saveFileToUserStorage(getApplication(), compressedFile, targetFileName)
                val destinationDisplay = if (saveResult.success) saveResult.displayPath else "${Constants.DEFAULT_SAVE_LOCATION_NAME}/$targetFileName"

                val compressed = doc.copy(
                    id = docId,
                    title = targetFileName,
                    fileSize = realSize,
                    isCompressed = true,
                    filePath = compressedFile.absolutePath
                )
                _documents.update { listOf(compressed) + it }
                _selectedDocument.value = compressed
                withContext(Dispatchers.Main) {
                    showToast("Saved to $destinationDisplay ($realSize, ${level.reductionPercent})")
                    onComplete?.invoke(compressed)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Compression failed: ${e.localizedMessage ?: "Unknown error"}")
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun passwordProtectDocument(
        doc: DocumentItem,
        pass: String,
        onComplete: ((DocumentItem) -> Unit)? = null
    ) {
        if (_isProcessing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            try {
                val sourceFile = ensurePdfFileInternal(doc)
                val docId = "doc-${System.currentTimeMillis()}"
                val targetFileName = "${doc.title.substringBeforeLast(".")}_protected.pdf"
                val outputFile = File(documentsDir, targetFileName)

                val protectedFile = pdfEngine.setPassword(Uri.fromFile(sourceFile), pass, outputFile)
                val realSize = PdfEngine.formatFileSize(protectedFile.length())

                val saveResult = StorageHelper.saveFileToUserStorage(getApplication(), protectedFile, targetFileName)
                val destinationDisplay = if (saveResult.success) saveResult.displayPath else "${Constants.DEFAULT_SAVE_LOCATION_NAME}/$targetFileName"

                val protected = doc.copy(
                    id = docId,
                    title = targetFileName,
                    isProtected = true,
                    password = pass,
                    fileSize = realSize,
                    filePath = protectedFile.absolutePath
                )
                _documents.update { list ->
                    list.map { if (it.id == doc.id) protected else it }
                }
                _selectedDocument.value = protected
                withContext(Dispatchers.Main) {
                    showToast("Saved to $destinationDisplay ($realSize)")
                    onComplete?.invoke(protected)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Password protection failed: ${e.localizedMessage ?: "Unknown error"}")
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun watermarkDocument(
        doc: DocumentItem,
        watermarkText: String,
        pos: WatermarkPosition,
        opacity: Float,
        onComplete: ((DocumentItem) -> Unit)? = null
    ) {
        if (_isProcessing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            try {
                val sourceFile = ensurePdfFileInternal(doc)
                val docId = "doc-${System.currentTimeMillis()}"
                val targetFileName = "${doc.title.substringBeforeLast(".")}_watermarked.pdf"
                val outputFile = File(documentsDir, targetFileName)

                val watermarkedFile = pdfEngine.addWatermark(Uri.fromFile(sourceFile), watermarkText, pos, opacity, outputFile)
                val realSize = PdfEngine.formatFileSize(watermarkedFile.length())

                val saveResult = StorageHelper.saveFileToUserStorage(getApplication(), watermarkedFile, targetFileName)
                val destinationDisplay = if (saveResult.success) saveResult.displayPath else "${Constants.DEFAULT_SAVE_LOCATION_NAME}/$targetFileName"

                val watermarked = doc.copy(
                    id = docId,
                    title = targetFileName,
                    watermark = watermarkText,
                    fileSize = realSize,
                    filePath = watermarkedFile.absolutePath
                )
                _documents.update { listOf(watermarked) + it }
                _selectedDocument.value = watermarked
                withContext(Dispatchers.Main) {
                    showToast("Saved to $destinationDisplay ($realSize)")
                    onComplete?.invoke(watermarked)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Watermark failed: ${e.localizedMessage ?: "Unknown error"}")
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /**
     * Renders every page of [doc]'s real PDF file to a Bitmap (via Android's native
     * PdfRenderer) so screens like Rotate Pages / Delete Pages can show real page
     * thumbnails instead of the old hardcoded sample images.
     */
    fun loadPageThumbnails(doc: DocumentItem, onResult: (List<android.graphics.Bitmap>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val bitmaps = try {
                val file = ensurePdfFileInternal(doc)
                (0 until doc.pageCount).mapNotNull { pageIndex ->
                    pdfEngine.renderPdfPageToBitmap(file, pageIndex)
                }
            } catch (e: Exception) {
                emptyList()
            }
            withContext(Dispatchers.Main) { onResult(bitmaps) }
        }
    }

    /** Real "Image to PDF": converts one or more picked images into a single new PDF document. */
    fun convertImagesToPdf(uris: List<Uri>, onComplete: ((DocumentItem) -> Unit)? = null) {
        if (uris.isEmpty()) {
            showToast("Choose at least 1 image")
            return
        }
        if (_isProcessing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            try {
                val docId = "doc-${System.currentTimeMillis()}"
                val title = "Image_to_PDF_${System.currentTimeMillis() % 10000}.pdf"
                val outputFile = File(documentsDir, "$docId.pdf")

                val generatedFile = pdfEngine.imagesToPdf(uris, outputFile)
                val realSize = PdfEngine.formatFileSize(generatedFile.length())
                val thumbFile = File(documentsDir, "${docId}_thumb.jpg")
                val thumbnailUri = pdfEngine.generateThumbnailForPdf(generatedFile, thumbFile)?.toString()

                val saveResult = StorageHelper.saveFileToUserStorage(getApplication(), generatedFile, title)
                val destinationDisplay = if (saveResult.success) saveResult.displayPath else "${Constants.DEFAULT_SAVE_LOCATION_NAME}/$title"

                val newDoc = DocumentItem(
                    id = docId,
                    title = title,
                    date = "Today",
                    time = "Just now",
                    pageCount = uris.size,
                    format = DocFormat.PDF,
                    fileSize = realSize,
                    thumbnailUri = thumbnailUri,
                    category = DocCategory.TODAY,
                    filePath = generatedFile.absolutePath
                )
                _documents.update { listOf(newDoc) + it }
                _selectedDocument.value = newDoc
                withContext(Dispatchers.Main) {
                    showToast("Saved to $destinationDisplay ($realSize)")
                    onComplete?.invoke(newDoc)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Image to PDF failed: ${e.localizedMessage ?: "Unknown error"}")
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /** Real "PDF to Image": renders every page of a PDF document into its own JPEG document. */
    fun convertPdfToImages(doc: DocumentItem, onComplete: ((List<DocumentItem>) -> Unit)? = null) {
        if (_isProcessing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            try {
                val sourceFile = ensurePdfFileInternal(doc)
                val outputDir = File(documentsDir, "pdf_to_img_${System.currentTimeMillis()}").apply { mkdirs() }
                val imageFiles = pdfEngine.pdfToImages(sourceFile, outputDir)
                val locationName = StorageHelper.getCurrentSaveLocationDisplay(getApplication())

                val baseName = doc.title.substringBeforeLast(".")
                val results = imageFiles.mapIndexed { index, file ->
                    val fileName = "${baseName}_page${index + 1}.jpg"
                    StorageHelper.saveFileToUserStorage(getApplication(), file, fileName, "image/jpeg")
                    DocumentItem(
                        id = "doc-${System.currentTimeMillis()}-$index",
                        title = fileName,
                        date = "Today",
                        time = "Just now",
                        pageCount = 1,
                        format = DocFormat.JPG,
                        fileSize = PdfEngine.formatFileSize(file.length()),
                        thumbnailUri = Uri.fromFile(file).toString(),
                        category = DocCategory.TODAY,
                        filePath = file.absolutePath
                    )
                }
                _documents.update { results + it }
                withContext(Dispatchers.Main) {
                    showToast("Saved ${results.size} images to $locationName")
                    onComplete?.invoke(results)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("PDF to Image failed: ${e.localizedMessage ?: "Unknown error"}")
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /** Real "Rotate Pages": rotates the given 0-based page indices by [degrees] and saves as a new document. */
    fun rotateDocumentPages(doc: DocumentItem, rotations: Map<Int, Int>, onComplete: ((DocumentItem) -> Unit)? = null) {
        if (rotations.values.all { it == 0 }) {
            showToast("Rotate at least one page first")
            return
        }
        if (_isProcessing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            try {
                val sourceFile = ensurePdfFileInternal(doc)
                val docId = "doc-${System.currentTimeMillis()}"
                val targetFileName = "${doc.title.substringBeforeLast(".")}_rotated.pdf"
                val outputFile = File(documentsDir, targetFileName)

                val rotatedFile = pdfEngine.rotatePages(Uri.fromFile(sourceFile), rotations, outputFile)
                val realSize = PdfEngine.formatFileSize(rotatedFile.length())

                val saveResult = StorageHelper.saveFileToUserStorage(getApplication(), rotatedFile, targetFileName)
                val destinationDisplay = if (saveResult.success) saveResult.displayPath else "${Constants.DEFAULT_SAVE_LOCATION_NAME}/$targetFileName"

                val rotated = doc.copy(
                    id = docId,
                    title = targetFileName,
                    fileSize = realSize,
                    filePath = rotatedFile.absolutePath
                )
                _documents.update { listOf(rotated) + it }
                _selectedDocument.value = rotated
                withContext(Dispatchers.Main) {
                    showToast("Saved to $destinationDisplay ($realSize)")
                    onComplete?.invoke(rotated)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Rotate failed: ${e.localizedMessage ?: "Unknown error"}")
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /** Real "Delete Pages": removes the given 0-based page indices and saves as a new document. */
    fun deleteDocumentPages(doc: DocumentItem, pageIndices: Set<Int>, onComplete: ((DocumentItem) -> Unit)? = null) {
        if (pageIndices.isEmpty()) {
            showToast("Select at least one page to delete")
            return
        }
        if (pageIndices.size >= doc.pageCount) {
            showToast("Can't delete every page")
            return
        }
        if (_isProcessing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            try {
                val sourceFile = ensurePdfFileInternal(doc)
                val docId = "doc-${System.currentTimeMillis()}"
                val targetFileName = "${doc.title.substringBeforeLast(".")}_edited.pdf"
                val outputFile = File(documentsDir, targetFileName)

                val resultFile = pdfEngine.deletePages(Uri.fromFile(sourceFile), pageIndices, outputFile)
                val realSize = PdfEngine.formatFileSize(resultFile.length())
                val newPageCount = (doc.pageCount - pageIndices.size).coerceAtLeast(1)

                val saveResult = StorageHelper.saveFileToUserStorage(getApplication(), resultFile, targetFileName)
                val destinationDisplay = if (saveResult.success) saveResult.displayPath else "${Constants.DEFAULT_SAVE_LOCATION_NAME}/$targetFileName"

                val edited = doc.copy(
                    id = docId,
                    title = targetFileName,
                    fileSize = realSize,
                    pageCount = newPageCount,
                    filePath = resultFile.absolutePath
                )
                _documents.update { listOf(edited) + it }
                _selectedDocument.value = edited
                withContext(Dispatchers.Main) {
                    showToast("Saved to $destinationDisplay ($realSize)")
                    onComplete?.invoke(edited)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Delete pages failed: ${e.localizedMessage ?: "Unknown error"}")
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /** Real "Sign Document": embeds a hand-drawn signature (vector strokes) onto one page. */
    fun signDocument(
        doc: DocumentItem,
        strokes: List<List<Pair<Float, Float>>>,
        padWidth: Float,
        padHeight: Float,
        pageIndex: Int = doc.pageCount - 1,
        onComplete: ((DocumentItem) -> Unit)? = null
    ) {
        if (strokes.isEmpty()) {
            showToast("Draw a signature first")
            return
        }
        if (_isProcessing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            try {
                val sourceFile = ensurePdfFileInternal(doc)
                val docId = "doc-${System.currentTimeMillis()}"
                val targetFileName = "${doc.title.substringBeforeLast(".")}_signed.pdf"
                val outputFile = File(documentsDir, targetFileName)

                val signedFile = pdfEngine.addSignature(
                    Uri.fromFile(sourceFile), pageIndex, strokes, padWidth, padHeight, outputFile
                )
                val realSize = PdfEngine.formatFileSize(signedFile.length())

                val saveResult = StorageHelper.saveFileToUserStorage(getApplication(), signedFile, targetFileName)
                val destinationDisplay = if (saveResult.success) saveResult.displayPath else "${Constants.DEFAULT_SAVE_LOCATION_NAME}/$targetFileName"

                val signed = doc.copy(
                    id = docId,
                    title = targetFileName,
                    fileSize = realSize,
                    filePath = signedFile.absolutePath
                )
                _documents.update { listOf(signed) + it }
                _selectedDocument.value = signed
                withContext(Dispatchers.Main) {
                    showToast("Saved to $destinationDisplay ($realSize)")
                    onComplete?.invoke(signed)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Signing failed: ${e.localizedMessage ?: "Unknown error"}")
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /** Real OCR export to user storage (.txt or .docx). */
    fun exportOcrText(doc: DocumentItem, format: String) {
        val baseName = doc.title.substringBeforeLast(".")
        val text = doc.ocrText.ifBlank { "No text extracted from document." }
        val targetFileName = "$baseName.$format"
        val mimeType = if (format == "docx") "application/vnd.openxmlformats-officedocument.wordprocessingml.document" else "text/plain"

        viewModelScope.launch(Dispatchers.IO) {
            val saveResult = StorageHelper.saveTextToUserStorage(getApplication(), text, targetFileName, mimeType)
            val destinationDisplay = if (saveResult.success) saveResult.displayPath else "${Constants.DEFAULT_SAVE_LOCATION_NAME}/$targetFileName"
            withContext(Dispatchers.Main) {
                showToast("Saved to $destinationDisplay")
            }
        }
    }

    fun extractTextFromDocument(
        doc: DocumentItem,
        language: String? = null,
        onComplete: ((String) -> Unit)? = null
    ) {
        if (_isProcessing.value) return
        val effectiveLang = language ?: _ocrLanguage.value
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            _ocrProgress.value = 0f
            try {
                val sourceFile = ensurePdfFileInternal(doc)
                val extractedText = ocrEngine.extractTextFromPdf(sourceFile, effectiveLang) { progress ->
                    _ocrProgress.value = progress
                }

                _documents.update { list ->
                    list.map { if (it.id == doc.id) it.copy(ocrText = extractedText) else it }
                }
                if (_selectedDocument.value?.id == doc.id) {
                    _selectedDocument.value = _selectedDocument.value?.copy(ocrText = extractedText)
                }

                withContext(Dispatchers.Main) {
                    onComplete?.invoke(extractedText)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Text extraction failed: ${e.localizedMessage ?: "Unknown error"}")
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /**
     * Real "Image Merger": Merges multiple selected images into a custom styled collage layout
     * and exports as either a multi-page PDF or high-resolution images according to [config].
     */
    fun mergeAndExportImages(
        images: List<MergerImageItem>,
        config: ImageMergerConfig,
        customTitle: String = "",
        onComplete: ((DocumentItem) -> Unit)? = null
    ) {
        if (images.isEmpty()) {
            showToast("Please add at least 1 image")
            return
        }
        if (_isProcessing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            try {
                val totalPages = imageMergerEngine.calculateTotalPages(images.size, config)
                val baseTitle = if (customTitle.isNotBlank()) customTitle.trim() else "Merged_Collage_${System.currentTimeMillis() % 10000}"
                val docId = "doc-${System.currentTimeMillis()}"

                when (config.exportFormat) {
                    MergerExportFormat.PDF -> {
                        val pdfTitle = if (baseTitle.endsWith(".pdf", ignoreCase = true)) baseTitle else "$baseTitle.pdf"
                        val outputFile = File(documentsDir, "$docId.pdf")

                        val generatedFile = imageMergerEngine.exportToPdf(images, config, outputFile)
                        val realSize = PdfEngine.formatFileSize(generatedFile.length())
                        val thumbFile = File(documentsDir, "${docId}_thumb.jpg")
                        val thumbnailUri = pdfEngine.generateThumbnailForPdf(generatedFile, thumbFile)?.toString()

                        val saveResult = StorageHelper.saveFileToUserStorage(getApplication(), generatedFile, pdfTitle, "application/pdf")
                        val destinationDisplay = if (saveResult.success) saveResult.displayPath else "${Constants.DEFAULT_SAVE_LOCATION_NAME}/$pdfTitle"

                        val newDoc = DocumentItem(
                            id = docId,
                            title = pdfTitle,
                            date = "Today",
                            time = "Just now",
                            pageCount = totalPages,
                            format = DocFormat.PDF,
                            fileSize = realSize,
                            thumbnailUri = thumbnailUri,
                            category = DocCategory.TODAY,
                            filePath = generatedFile.absolutePath
                        )

                        _documents.update { listOf(newDoc) + it }
                        _selectedDocument.value = newDoc
                        withContext(Dispatchers.Main) {
                            showToast("Saved to $destinationDisplay ($realSize)")
                            onComplete?.invoke(newDoc)
                        }
                    }
                    MergerExportFormat.IMAGE_PNG, MergerExportFormat.IMAGE_JPG -> {
                        val isPng = config.exportFormat == MergerExportFormat.IMAGE_PNG
                        val ext = if (isPng) "png" else "jpg"
                        val mimeType = if (isPng) "image/png" else "image/jpeg"
                        val outputDir = File(documentsDir, "merger_img_${System.currentTimeMillis()}").apply { mkdirs() }

                        val exportedFiles = imageMergerEngine.exportToImages(images, config, outputDir, baseTitle)
                        if (exportedFiles.isEmpty()) throw IllegalStateException("Failed to export merged images")

                        val firstFile = exportedFiles.first()
                        val totalSize = PdfEngine.formatFileSize(exportedFiles.sumOf { it.length() })
                        var primarySaveDisplay = ""

                        for (f in exportedFiles) {
                            val res = StorageHelper.saveFileToUserStorage(getApplication(), f, f.name, mimeType)
                            if (primarySaveDisplay.isEmpty() && res.success) {
                                primarySaveDisplay = res.displayPath
                            }
                        }

                        if (primarySaveDisplay.isEmpty()) {
                            primarySaveDisplay = "${Constants.DEFAULT_SAVE_LOCATION_NAME}/${firstFile.name}"
                        }

                        val newDoc = DocumentItem(
                            id = docId,
                            title = if (exportedFiles.size == 1) firstFile.name else "$baseTitle ($totalPages pages)",
                            date = "Today",
                            time = "Just now",
                            pageCount = exportedFiles.size,
                            format = DocFormat.JPG,
                            fileSize = totalSize,
                            thumbnailUri = Uri.fromFile(firstFile).toString(),
                            category = DocCategory.TODAY,
                            filePath = firstFile.absolutePath
                        )

                        _documents.update { listOf(newDoc) + it }
                        _selectedDocument.value = newDoc
                        withContext(Dispatchers.Main) {
                            showToast("Saved to $primarySaveDisplay ($totalSize)")
                            onComplete?.invoke(newDoc)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Merge failed: ${e.localizedMessage ?: "Unknown error"}")
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private fun Double.formatSize(): String = String.format("%.1f", this)
}

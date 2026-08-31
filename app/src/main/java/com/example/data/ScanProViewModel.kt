package com.example.data

import android.app.Application
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID

class ScanProViewModel(application: Application) : AndroidViewModel(application) {

    private val pdfEngine = PdfEngine(application)
    private val documentsDir = File(application.filesDir, "documents").apply { mkdirs() }

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val initialSamplePages = listOf(
        ScannedPage(
            id = "p1",
            pageNumber = 1,
            drawableRes = R.drawable.sample_invoice
        ),
        ScannedPage(
            id = "p2",
            pageNumber = 2,
            drawableRes = R.drawable.sample_blueprint,
            filter = PageFilter.GRAYSCALE
        ),
        ScannedPage(
            id = "p3",
            pageNumber = 3,
            drawableRes = R.drawable.sample_spreadsheet
        ),
        ScannedPage(
            id = "p4",
            pageNumber = 4,
            drawableRes = R.drawable.sample_contract
        )
    )

    private val defaultOcrInvoiceText = """
GLOBE DYNAMICS SOLUTIONS
INVOICE

120 Business Park Drive, Suite 300, London,
UK EC2A 4AB | +44 20 7123 4567

Invoice Date: October 15, 2023
Invoice #: GD-98765

BILL TO:
Client: Apex Marketing Group
45 Innovation Ave
Manchester, UK M1 6BP

SHIP TO:
Same as Bill To

Item Code | Description | Quantity | Unit Price | Total
-------------------------------------------------------
1. GD-WD-01 | Website Development | 60 | £75.00 | £4,500.00
2. GD-SMM-03 | Social Media Mgmt - 1 Month | 1 | £1,500.00 | £1,500.00
3. GD-CW-05 | Content Writing - 20 hrs | 20 | £65.00 | £1,300.00
4. GD-GD-02 | Graphic Design - 15 hrs | 15 | £70.00 | £1,050.00
5. GD-MH-04 | Web Hosting - 1 Year | 1 | £300.00 | £300.00

Subtotal: £8,650.00
VAT (20%): £1,730.00
Total Due: £10,380.00

Payment Terms: Net 30
Due Date: November 14, 2023

Bank Details | Barclays Bank | Sort Code: 20-45-67 | Account: 12345678

THANK YOU FOR YOUR BUSINESS! | www.globedynamics.co.uk
    """.trimIndent()

    private val defaultDocuments = listOf(
        DocumentItem(
            id = "doc-1",
            title = "Invoice_2023_10.pdf",
            date = "Oct 24",
            time = "10:42 AM",
            pageCount = 3,
            format = DocFormat.PDF,
            fileSize = "2.4 MB",
            thumbnailRes = R.drawable.sample_invoice,
            category = DocCategory.TODAY,
            pages = initialSamplePages.take(3),
            ocrText = defaultOcrInvoiceText
        ),
        DocumentItem(
            id = "doc-2",
            title = "Blueprint_V2_Final.jpg",
            date = "Oct 22",
            time = "09:15 AM",
            pageCount = 1,
            format = DocFormat.JPG,
            fileSize = "1.8 MB",
            thumbnailRes = R.drawable.sample_blueprint,
            category = DocCategory.TODAY,
            pages = listOf(initialSamplePages[1])
        ),
        DocumentItem(
            id = "doc-3",
            title = "Expense_Report_Q3.pdf",
            date = "Oct 15",
            time = "04:30 PM",
            pageCount = 4,
            format = DocFormat.OCR,
            fileSize = "4.1 MB",
            thumbnailRes = R.drawable.sample_spreadsheet,
            category = DocCategory.TODAY,
            pages = initialSamplePages,
            ocrText = "Quarterly Expense Summary\nTotal Expenses: $42,500.00\nDepartment: Operations & Engineering"
        ),
        DocumentItem(
            id = "doc-4",
            title = "NDA_TechCorp_Final.pdf",
            date = "Yesterday",
            time = "02:10 PM",
            pageCount = 4,
            format = DocFormat.PDF,
            fileSize = "3.2 MB",
            thumbnailRes = R.drawable.sample_contract,
            category = DocCategory.YESTERDAY,
            pages = initialSamplePages
        ),
        DocumentItem(
            id = "doc-5",
            title = "Q3_Financial_Report_Final.pdf",
            date = "Oct 10",
            time = "11:20 AM",
            pageCount = 4,
            format = DocFormat.PDF,
            fileSize = "2.4 MB",
            thumbnailRes = R.drawable.sample_invoice,
            category = DocCategory.EARLIER,
            pages = initialSamplePages
        )
    )

    private val _documents = MutableStateFlow<List<DocumentItem>>(defaultDocuments)
    val documents: StateFlow<List<DocumentItem>> = _documents.asStateFlow()

    private val _activeDraftPages = MutableStateFlow<List<ScannedPage>>(initialSamplePages)
    val activeDraftPages: StateFlow<List<ScannedPage>> = _activeDraftPages.asStateFlow()

    private val _selectedDraftIndex = MutableStateFlow(0)
    val selectedDraftIndex: StateFlow<Int> = _selectedDraftIndex.asStateFlow()

    private val _selectedDocument = MutableStateFlow<DocumentItem?>(defaultDocuments.first())
    val selectedDocument: StateFlow<DocumentItem?> = _selectedDocument.asStateFlow()

    private val _filterTab = MutableStateFlow("All")
    val filterTab: StateFlow<String> = _filterTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isGridView = MutableStateFlow(false)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    // Settings
    private val _darkMode = MutableStateFlow(false)
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()

    private val _autoCapture = MutableStateFlow(true)
    val autoCapture: StateFlow<Boolean> = _autoCapture.asStateFlow()

    private val _defaultSaveLocation = MutableStateFlow("/Documents/Scans")
    val defaultSaveLocation: StateFlow<String> = _defaultSaveLocation.asStateFlow()

    private val _defaultQuality = MutableStateFlow("High (300 dpi)")
    val defaultQuality: StateFlow<String> = _defaultQuality.asStateFlow()

    private val _language = MutableStateFlow("English (US)")
    val language: StateFlow<String> = _language.asStateFlow()

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

    fun toggleAutoCapture(enabled: Boolean) {
        _autoCapture.value = enabled
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

    fun resetDefaultDocuments() {
        _documents.value = defaultDocuments
        _selectedDocument.value = defaultDocuments.first()
        showToast("Sample documents restored")
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

    fun addPageToDraft(
        @DrawableRes res: Int = R.drawable.sample_invoice,
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
                drawableRes = R.drawable.sample_invoice
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
                    drawableRes = R.drawable.sample_invoice
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

    private fun ensurePdfFile(doc: DocumentItem): File {
        if (!doc.filePath.isNullOrEmpty()) {
            val file = File(doc.filePath)
            if (file.exists() && file.length() > 0L) {
                return file
            }
        }
        val file = File(documentsDir, "${doc.id}.pdf")
        if (!file.exists() || file.length() == 0L) {
            runBlocking(Dispatchers.IO) {
                val pagesToUse = if (doc.pages.isNotEmpty()) doc.pages else initialSamplePages
                pdfEngine.createPdfFromPages(pagesToUse, file)
            }
        }
        return file
    }

    fun finishScanAndSave(): DocumentItem {
        val pages = _activeDraftPages.value
        val firstPage = pages.firstOrNull()
        val docId = "doc-${System.currentTimeMillis()}"
        val title = "Scan_${System.currentTimeMillis() % 10000}.pdf"
        val outputFile = File(documentsDir, "$docId.pdf")

        _isProcessing.value = true
        val generatedFile = runBlocking(Dispatchers.IO) {
            pdfEngine.createPdfFromPages(pages, outputFile)
        }
        _isProcessing.value = false

        val realSize = PdfEngine.formatFileSize(generatedFile.length())
        val newDoc = DocumentItem(
            id = docId,
            title = title,
            date = "Today",
            time = "Just now",
            pageCount = pages.size,
            format = DocFormat.PDF,
            fileSize = realSize,
            thumbnailRes = firstPage?.drawableRes ?: R.drawable.sample_invoice,
            thumbnailUri = firstPage?.imageUri,
            category = DocCategory.TODAY,
            pages = pages,
            ocrText = defaultOcrInvoiceText,
            filePath = generatedFile.absolutePath
        )
        _documents.update { listOf(newDoc) + it }
        _selectedDocument.value = newDoc
        showToast("Document saved to Library ($realSize)")
        return newDoc
    }

    // PDF Utilities Operations
    fun mergeDocuments(docIds: List<String>, outputTitle: String = "Merged_Document.pdf"): DocumentItem {
        val selected = _documents.value.filter { it.id in docIds }
        val allPages = selected.flatMap { it.pages }
        val docId = "doc-${System.currentTimeMillis()}"
        val outputFile = File(documentsDir, "$docId.pdf")

        _isProcessing.value = true
        val inputUris = selected.map { doc ->
            val file = ensurePdfFile(doc)
            Uri.fromFile(file)
        }

        val mergedFile = runBlocking(Dispatchers.IO) {
            pdfEngine.mergePdfs(inputUris, outputFile)
        }
        _isProcessing.value = false

        val realSize = PdfEngine.formatFileSize(mergedFile.length())
        val totalPages = selected.sumOf { it.pageCount }.coerceAtLeast(allPages.size).coerceAtLeast(1)

        val newDoc = DocumentItem(
            id = docId,
            title = outputTitle,
            date = "Today",
            time = "Just now",
            pageCount = totalPages,
            format = DocFormat.PDF,
            fileSize = realSize,
            thumbnailRes = selected.firstOrNull()?.thumbnailRes ?: R.drawable.sample_invoice,
            thumbnailUri = selected.firstOrNull()?.thumbnailUri,
            category = DocCategory.TODAY,
            pages = allPages.ifEmpty { initialSamplePages },
            filePath = mergedFile.absolutePath
        )
        _documents.update { listOf(newDoc) + it }
        _selectedDocument.value = newDoc
        showToast("Merged into $outputTitle ($realSize)")
        return newDoc
    }

    fun splitDocument(doc: DocumentItem, splitCuts: List<Int>): List<DocumentItem> {
        val sourceFile = ensurePdfFile(doc)
        val outputDir = File(documentsDir, "split_${System.currentTimeMillis()}").apply { mkdirs() }

        _isProcessing.value = true
        val splitFiles = runBlocking(Dispatchers.IO) {
            pdfEngine.splitPdf(Uri.fromFile(sourceFile), splitCuts, outputDir)
        }
        _isProcessing.value = false

        val result = splitFiles.mapIndexed { index, file ->
            val partNum = index + 1
            val realSize = PdfEngine.formatFileSize(file.length())
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
                pages = doc.pages.take(2).ifEmpty { initialSamplePages.take(2) },
                filePath = file.absolutePath
            )
        }
        _documents.update { result + it }
        showToast("Split into ${result.size} files successfully")
        return result
    }

    fun compressDocument(doc: DocumentItem, level: CompressionLevel): DocumentItem {
        val sourceFile = ensurePdfFile(doc)
        val docId = "doc-${System.currentTimeMillis()}"
        val outputFile = File(documentsDir, "${doc.title.substringBeforeLast(".")}_compressed.pdf")

        _isProcessing.value = true
        val compressedFile = runBlocking(Dispatchers.IO) {
            pdfEngine.compressPdf(Uri.fromFile(sourceFile), level, outputFile)
        }
        _isProcessing.value = false

        val realSize = PdfEngine.formatFileSize(compressedFile.length())
        val compressed = doc.copy(
            id = docId,
            title = outputFile.name,
            fileSize = realSize,
            isCompressed = true,
            filePath = compressedFile.absolutePath
        )
        _documents.update { listOf(compressed) + it }
        _selectedDocument.value = compressed
        showToast("Compressed to $realSize (${level.reductionPercent})")
        return compressed
    }

    fun passwordProtectDocument(doc: DocumentItem, pass: String): DocumentItem {
        val sourceFile = ensurePdfFile(doc)
        val docId = "doc-${System.currentTimeMillis()}"
        val outputFile = File(documentsDir, "${doc.title.substringBeforeLast(".")}_protected.pdf")

        _isProcessing.value = true
        val protectedFile = runBlocking(Dispatchers.IO) {
            pdfEngine.setPassword(Uri.fromFile(sourceFile), pass, outputFile)
        }
        _isProcessing.value = false

        val realSize = PdfEngine.formatFileSize(protectedFile.length())
        val protected = doc.copy(
            id = docId,
            isProtected = true,
            password = pass,
            fileSize = realSize,
            filePath = protectedFile.absolutePath
        )
        _documents.update { list ->
            list.map { if (it.id == doc.id) protected else it }
        }
        _selectedDocument.value = protected
        showToast("Password protection applied ($realSize)")
        return protected
    }

    fun watermarkDocument(doc: DocumentItem, watermarkText: String, pos: WatermarkPosition, opacity: Float): DocumentItem {
        val sourceFile = ensurePdfFile(doc)
        val docId = "doc-${System.currentTimeMillis()}"
        val outputFile = File(documentsDir, "${doc.title.substringBeforeLast(".")}_watermarked.pdf")

        _isProcessing.value = true
        val watermarkedFile = runBlocking(Dispatchers.IO) {
            pdfEngine.addWatermark(Uri.fromFile(sourceFile), watermarkText, pos, opacity, outputFile)
        }
        _isProcessing.value = false

        val realSize = PdfEngine.formatFileSize(watermarkedFile.length())
        val watermarked = doc.copy(
            id = docId,
            title = outputFile.name,
            watermark = watermarkText,
            fileSize = realSize,
            filePath = watermarkedFile.absolutePath
        )
        _documents.update { listOf(watermarked) + it }
        _selectedDocument.value = watermarked
        showToast("Watermark '$watermarkText' applied ($realSize)")
        return watermarked
    }

    private fun Double.formatSize(): String = String.format("%.1f", this)
}

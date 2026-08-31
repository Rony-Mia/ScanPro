package com.example.data

import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class ScanProViewModel : ViewModel() {

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
            pageCount = 12,
            format = DocFormat.OCR,
            fileSize = "4.1 MB",
            thumbnailRes = R.drawable.sample_spreadsheet,
            category = DocCategory.TODAY,
            pages = initialSamplePages,
            ocrText = "Quarterly Expense Summary\nTotal Expenses: $42,500.00\nDepartment: Operations & Engineering"
        ),
        DocumentItem(
            id = "doc-4",
            title = "NDA_TechCorp_Final",
            date = "Yesterday",
            time = "02:10 PM",
            pageCount = 5,
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
            pageCount = 12,
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

    fun finishScanAndSave(): DocumentItem {
        val pages = _activeDraftPages.value
        val firstPage = pages.firstOrNull()
        val newDoc = DocumentItem(
            id = "doc-${System.currentTimeMillis()}",
            title = "Scan_${System.currentTimeMillis() % 10000}.pdf",
            date = "Today",
            time = "Just now",
            pageCount = pages.size,
            format = DocFormat.PDF,
            fileSize = "${(pages.size * 0.8).formatSize()} MB",
            thumbnailRes = firstPage?.drawableRes ?: R.drawable.sample_invoice,
            thumbnailUri = firstPage?.imageUri,
            category = DocCategory.TODAY,
            pages = pages,
            ocrText = defaultOcrInvoiceText
        )
        _documents.update { listOf(newDoc) + it }
        _selectedDocument.value = newDoc
        showToast("Document saved to Library")
        return newDoc
    }

    // PDF Utilities Operations
    fun mergeDocuments(docIds: List<String>, outputTitle: String = "Merged_Document.pdf"): DocumentItem {
        val selected = _documents.value.filter { it.id in docIds }
        val allPages = selected.flatMap { it.pages }
        val newDoc = DocumentItem(
            id = "doc-${System.currentTimeMillis()}",
            title = outputTitle,
            date = "Today",
            time = "Just now",
            pageCount = allPages.size.coerceAtLeast(selected.sumOf { it.pageCount }),
            format = DocFormat.PDF,
            fileSize = "4.8 MB",
            thumbnailRes = selected.firstOrNull()?.thumbnailRes ?: R.drawable.sample_invoice,
            category = DocCategory.TODAY,
            pages = allPages.ifEmpty { initialSamplePages }
        )
        _documents.update { listOf(newDoc) + it }
        _selectedDocument.value = newDoc
        showToast("Merged into $outputTitle")
        return newDoc
    }

    fun splitDocument(doc: DocumentItem, splitCuts: List<Int>): List<DocumentItem> {
        val result = mutableListOf<DocumentItem>()
        val partsCount = splitCuts.size + 1
        for (i in 1..partsCount) {
            val partDoc = DocumentItem(
                id = "doc-${System.currentTimeMillis()}-$i",
                title = "${doc.title.substringBeforeLast(".")}_part$i.pdf",
                date = "Today",
                time = "Just now",
                pageCount = (doc.pageCount / partsCount).coerceAtLeast(1),
                format = DocFormat.PDF,
                fileSize = "1.1 MB",
                thumbnailRes = doc.thumbnailRes,
                category = DocCategory.TODAY,
                pages = doc.pages.take(2)
            )
            result.add(partDoc)
        }
        _documents.update { result + it }
        showToast("Split into $partsCount files successfully")
        return result
    }

    fun compressDocument(doc: DocumentItem, level: CompressionLevel): DocumentItem {
        val compressed = doc.copy(
            id = "doc-${System.currentTimeMillis()}",
            title = "${doc.title.substringBeforeLast(".")}_compressed.pdf",
            fileSize = level.estimatedSize.replace(" est.", ""),
            isCompressed = true
        )
        _documents.update { listOf(compressed) + it }
        _selectedDocument.value = compressed
        showToast("Compressed to ${compressed.fileSize} (${level.reductionPercent})")
        return compressed
    }

    fun passwordProtectDocument(doc: DocumentItem, pass: String): DocumentItem {
        val protected = doc.copy(
            isProtected = true,
            password = pass
        )
        _documents.update { list ->
            list.map { if (it.id == doc.id) protected else it }
        }
        _selectedDocument.value = protected
        showToast("Password protection applied")
        return protected
    }

    fun watermarkDocument(doc: DocumentItem, watermarkText: String, pos: WatermarkPosition, opacity: Float): DocumentItem {
        val watermarked = doc.copy(
            id = "doc-${System.currentTimeMillis()}",
            title = "${doc.title.substringBeforeLast(".")}_watermarked.pdf",
            watermark = watermarkText
        )
        _documents.update { listOf(watermarked) + it }
        _selectedDocument.value = watermarked
        showToast("Watermark '$watermarkText' applied")
        return watermarked
    }

    private fun Double.formatSize(): String = String.format("%.1f", this)
}

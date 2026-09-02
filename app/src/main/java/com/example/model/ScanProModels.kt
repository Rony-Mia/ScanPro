package com.example.model

import androidx.annotation.DrawableRes

enum class DocFormat {
    PDF, JPG, OCR
}

enum class DocCategory {
    TODAY, YESTERDAY, EARLIER
}

data class ScannedPage(
    val id: String,
    val pageNumber: Int,
    @DrawableRes val drawableRes: Int = 0,
    val imageUri: String? = null,
    val rotationAngle: Float = 0f,
    val filter: PageFilter = PageFilter.ORIGINAL,
    val cropTop: Float = 0f,
    val cropBottom: Float = 1f,
    val cropLeft: Float = 0f,
    val cropRight: Float = 1f
)

enum class PageFilter(val displayName: String) {
    ORIGINAL("Original"),
    COLOR("Color"),
    GRAYSCALE("Grayscale"),
    BW("B&W"),
    MAGIC("Magic Color")
}

data class DocumentItem(
    val id: String,
    val title: String,
    val date: String,
    val time: String,
    val pageCount: Int,
    val format: DocFormat,
    val fileSize: String,
    @DrawableRes val thumbnailRes: Int = 0,
    val thumbnailUri: String? = null,
    val category: DocCategory = DocCategory.TODAY,
    val pages: List<ScannedPage> = emptyList(),
    val isProtected: Boolean = false,
    val password: String = "",
    val watermark: String = "",
    val isCompressed: Boolean = false,
    val ocrText: String = "",
    val filePath: String? = null
)

enum class WatermarkPosition {
    CENTER, DIAGONAL, CORNER
}

enum class CompressionLevel(
    val title: String,
    val subtitle: String,
    val estimatedSize: String,
    val reductionPercent: String
) {
    LOW("Low", "Best Quality", "2.1 MB est.", "~15% reduction"),
    MEDIUM("Medium", "Recommended", "1.2 MB est.", "~50% reduction"),
    HIGH("High", "Smallest Size", "0.5 MB est.", "~79% reduction")
}

enum class ToolType(
    val title: String,
    val category: ToolCategory
) {
    SCAN("Scan", ToolCategory.SCANNING),
    OCR("OCR Text", ToolCategory.SCANNING),
    MERGE("Merge PDF", ToolCategory.EDITING),
    SPLIT("Split PDF", ToolCategory.EDITING),
    COMPRESS("Compress", ToolCategory.EDITING),
    IMAGE_TO_PDF("Image to PDF", ToolCategory.EDITING),
    IMAGE_MERGER("Image Merger", ToolCategory.EDITING),
    PDF_TO_IMAGE("PDF to Image", ToolCategory.EDITING),
    WATERMARK("Add Watermark", ToolCategory.EDITING),
    ROTATE("Rotate Pages", ToolCategory.EDITING),
    DELETE_PAGES("Delete Pages", ToolCategory.EDITING),
    PASSWORD("Password Protect", ToolCategory.SECURITY),
    SIGN("Sign Document", ToolCategory.SECURITY)
}

enum class ToolCategory {
    SCANNING, EDITING, SECURITY
}

enum class DocSortOrder(val displayName: String) {
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    SIZE_DESC("Largest First"),
    SIZE_ASC("Smallest First")
}

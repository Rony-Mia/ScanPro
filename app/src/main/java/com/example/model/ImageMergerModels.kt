package com.example.model

import android.net.Uri

data class MergerImageItem(
    val id: String,
    val uri: Uri,
    val fileName: String,
    val rotationDegrees: Int = 0,
    val customCaption: String = ""
)

enum class MergerPagePreset(val label: String, val count: Int, val defaultCols: Int, val defaultRows: Int) {
    ONE("1", 1, 1, 1),
    TWO("2", 2, 1, 2),
    FOUR("4", 4, 2, 2),
    SIX("6", 6, 2, 3),
    NINE("9", 9, 3, 3),
    CUSTOM("Custom", 0, 2, 2)
}

enum class MergerPageSize(val displayName: String, val widthPt: Float, val heightPt: Float) {
    A4("A4 (210 × 297 mm)", 595.28f, 841.89f),
    LETTER("Letter (8.5 × 11 in)", 612f, 792f),
    LEGAL("Legal (8.5 × 14 in)", 612f, 1008f),
    CUSTOM("Custom Size", 595.28f, 841.89f)
}

enum class MergerOrientation(val label: String) {
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape"),
    AUTO("Auto (Match Images)")
}

enum class MergerFitMode(val label: String, val description: String) {
    FIT("Fit (Contain)", "Entire image visible without cropping"),
    FILL("Fill (Cover)", "Fills cell completely, maintains aspect ratio")
}

enum class MergerCaptionMode(val label: String) {
    NONE("None"),
    FILENAME("Filename"),
    CUSTOM("Custom Caption")
}

enum class MergerExportFormat(val label: String, val extension: String, val mimeType: String) {
    PDF("PDF Document", "pdf", "application/pdf"),
    IMAGE_PNG("PNG Image", "png", "image/png"),
    IMAGE_JPG("JPEG Image", "jpg", "image/jpeg")
}

enum class MergerExportQuality(val label: String, val dpi: Int, val scaleFactor: Float) {
    LOW("Low (72 DPI)", 72, 1.0f),
    MEDIUM("Medium (150 DPI)", 150, 2.08f),
    HIGH("High (300 DPI)", 300, 4.16f)
}

data class ImageMergerConfig(
    val preset: MergerPagePreset = MergerPagePreset.FOUR,
    val customCols: Int = 2,
    val customRows: Int = 2,
    val pageSize: MergerPageSize = MergerPageSize.A4,
    val customWidthMm: Float = 210f,
    val customHeightMm: Float = 297f,
    val orientation: MergerOrientation = MergerOrientation.PORTRAIT,
    val autoOrientation: Boolean = false,
    
    // Spacing & Margins (in points/dp)
    val horizontalGapDp: Float = 8f,
    val verticalGapDp: Float = 8f,
    val uniformGap: Boolean = true,
    
    val marginTopDp: Float = 16f,
    val marginBottomDp: Float = 16f,
    val marginLeftDp: Float = 16f,
    val marginRightDp: Float = 16f,
    val uniformMargin: Boolean = true,
    
    // Fit & Styling
    val fitMode: MergerFitMode = MergerFitMode.FIT,
    val backgroundColorArgb: Long = 0xFFFFFFFF, // Default Pure White
    val hasBorder: Boolean = false,
    val borderColorArgb: Long = 0xFFD1D5DB,
    val borderThicknessDp: Float = 1.5f,
    val borderRadiusDp: Float = 4f,
    val hasShadow: Boolean = false,
    
    // Advanced
    val captionMode: MergerCaptionMode = MergerCaptionMode.NONE,
    val showPageNumber: Boolean = false,
    val showImageIndex: Boolean = false,
    val exportFormat: MergerExportFormat = MergerExportFormat.PDF,
    val exportQuality: MergerExportQuality = MergerExportQuality.MEDIUM
) {
    val activeCols: Int
        get() = when (preset) {
            MergerPagePreset.CUSTOM -> customCols.coerceAtLeast(1)
            else -> preset.defaultCols
        }

    val activeRows: Int
        get() = when (preset) {
            MergerPagePreset.CUSTOM -> customRows.coerceAtLeast(1)
            else -> preset.defaultRows
        }

    val imagesPerPage: Int
        get() = activeCols * activeRows
}

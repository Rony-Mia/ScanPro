package com.example.data

import android.content.Context
import android.graphics.*
import android.net.Uri
import com.example.model.*
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class ImageMergerEngine(private val context: Context) {

    private fun openInputStream(uri: Uri): InputStream? {
        return when (uri.scheme) {
            "content" -> context.contentResolver.openInputStream(uri)
            "file" -> File(uri.path ?: return null).inputStream()
            else -> {
                val path = uri.path ?: uri.toString()
                val file = File(path)
                if (file.exists()) file.inputStream() else null
            }
        }
    }

    /**
     * Efficiently decodes and downsamples bitmap from URI with proper rotation applied.
     */
    fun decodeSampledBitmapFromUri(uri: Uri, targetWidth: Int, targetHeight: Int, rotationDegrees: Int): Bitmap? {
        return try {
            // 1. Decode bounds only
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }

            val srcWidth = options.outWidth
            val srcHeight = options.outHeight
            if (srcWidth <= 0 || srcHeight <= 0) return null

            // 2. Calculate inSampleSize
            var sampleSize = 1
            val maxDim = max(srcWidth, srcHeight)
            val targetMaxDim = max(targetWidth, targetHeight).coerceAtLeast(100)
            while (maxDim / (sampleSize * 2) >= targetMaxDim) {
                sampleSize *= 2
            }

            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            val decoded = openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null

            val totalRotation = (rotationDegrees % 360 + 360) % 360
            if (totalRotation != 0) {
                val matrix = Matrix().apply { postRotate(totalRotation.toFloat()) }
                val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                if (rotated !== decoded) decoded.recycle()
                rotated
            } else {
                decoded
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Inspect image dimensions (accounting for rotation) to check aspect ratio for auto-orientation.
     */
    fun getImageAspectRatio(item: MergerImageItem): Float {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            val w = options.outWidth.toFloat()
            val h = options.outHeight.toFloat()
            val rot = (item.rotationDegrees % 360 + 360) % 360
            if (rot == 90 || rot == 270) {
                if (w > 0) h / w else 1f
            } else {
                if (h > 0) w / h else 1f
            }
        } catch (e: Exception) {
            1f
        }
    }

    /**
     * Computes the total page count needed for the given images and config.
     */
    fun calculateTotalPages(totalImages: Int, config: ImageMergerConfig): Int {
        val perPage = config.imagesPerPage.coerceAtLeast(1)
        return if (totalImages == 0) 1 else ceil(totalImages.toFloat() / perPage).toInt()
    }

    /**
     * Computes page dimensions in PDF points (72 points = 1 inch).
     */
    fun getPageDimensionsPt(pageIndex: Int, imagesForPage: List<MergerImageItem>, config: ImageMergerConfig): Pair<Float, Float> {
        val baseWidthPt: Float
        val baseHeightPt: Float

        when (config.pageSize) {
            MergerPageSize.A4 -> {
                baseWidthPt = 595.28f
                baseHeightPt = 841.89f
            }
            MergerPageSize.LETTER -> {
                baseWidthPt = 612f
                baseHeightPt = 792f
            }
            MergerPageSize.LEGAL -> {
                baseWidthPt = 612f
                baseHeightPt = 1008f
            }
            MergerPageSize.CUSTOM -> {
                // 1 mm = 2.83465 pt
                baseWidthPt = (config.customWidthMm * 2.83465f).coerceAtLeast(100f)
                baseHeightPt = (config.customHeightMm * 2.83465f).coerceAtLeast(100f)
            }
        }

        // Determine orientation
        val isLandscape = when {
            config.autoOrientation -> {
                // Determine based on majority aspect ratio of images on this page
                if (imagesForPage.isNotEmpty()) {
                    val landscapeCount = imagesForPage.count { getImageAspectRatio(it) > 1.05f }
                    landscapeCount > imagesForPage.size / 2
                } else {
                    config.orientation == MergerOrientation.LANDSCAPE
                }
            }
            config.orientation == MergerOrientation.LANDSCAPE -> true
            else -> false
        }

        val w = min(baseWidthPt, baseHeightPt)
        val h = max(baseWidthPt, baseHeightPt)
        return if (isLandscape) Pair(h, w) else Pair(w, h)
    }

    /**
     * Renders a single page to a high-quality Bitmap matching the exact WYSIWYG settings.
     */
    suspend fun renderPageToBitmap(
        pageIndex: Int,
        allImages: List<MergerImageItem>,
        config: ImageMergerConfig,
        scaleFactor: Float = 1.0f
    ): Bitmap = withContext(Dispatchers.IO) {
        val perPage = config.imagesPerPage.coerceAtLeast(1)
        val startIndex = pageIndex * perPage
        val endIndex = min(startIndex + perPage, allImages.size)
        val pageImages = if (startIndex < allImages.size) allImages.subList(startIndex, endIndex) else emptyList()

        val (pageWidthPt, pageHeightPt) = getPageDimensionsPt(pageIndex, pageImages, config)
        val targetWidthPx = (pageWidthPt * scaleFactor).toInt().coerceAtLeast(100)
        val targetHeightPx = (pageHeightPt * scaleFactor).toInt().coerceAtLeast(100)

        val bitmap = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Draw Page Background
        val bgPaint = Paint().apply {
            color = config.backgroundColorArgb.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, targetWidthPx.toFloat(), targetHeightPx.toFloat(), bgPaint)

        // Scale factors for margins & paddings
        val density = scaleFactor * (targetWidthPx.toFloat() / pageWidthPt)
        val ptToPx = scaleFactor

        val marginL = (if (config.uniformMargin) config.marginLeftDp else config.marginLeftDp) * ptToPx
        val marginR = (if (config.uniformMargin) config.marginLeftDp else config.marginRightDp) * ptToPx
        val marginT = (if (config.uniformMargin) config.marginTopDp else config.marginTopDp) * ptToPx
        val marginB = (if (config.uniformMargin) config.marginTopDp else config.marginBottomDp) * ptToPx

        val gapH = (if (config.uniformGap) config.horizontalGapDp else config.horizontalGapDp) * ptToPx
        val gapV = (if (config.uniformGap) config.horizontalGapDp else config.verticalGapDp) * ptToPx

        val contentWidth = (targetWidthPx - marginL - marginR).coerceAtLeast(10f)
        val contentHeight = (targetHeightPx - marginT - marginB).coerceAtLeast(10f)

        val cols = config.activeCols.coerceAtLeast(1)
        val rows = config.activeRows.coerceAtLeast(1)

        val cellWidth = ((contentWidth - (cols - 1) * gapH) / cols).coerceAtLeast(5f)
        val cellHeight = ((contentHeight - (rows - 1) * gapV) / rows).coerceAtLeast(5f)

        val borderRadiusPx = config.borderRadiusDp * ptToPx
        val borderThicknessPx = (config.borderThicknessDp * ptToPx).coerceAtLeast(1f)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = config.borderColorArgb.toInt()
            style = Paint.Style.STROKE
            strokeWidth = borderThicknessPx
        }

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(45, 0, 0, 0)
            style = Paint.Style.FILL
            maskFilter = BlurMaskFilter(4f * ptToPx, BlurMaskFilter.Blur.NORMAL)
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isColorDark(config.backgroundColorArgb)) Color.WHITE else Color.parseColor("#1E293B")
            textSize = (11f * ptToPx).coerceAtLeast(10f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }

        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#15803D") // Primary ScanPro Green
            style = Paint.Style.FILL
        }

        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (10f * ptToPx).coerceAtLeast(9f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        // Draw Cells
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cellIndex = r * cols + c
                if (cellIndex >= pageImages.size) break

                val item = pageImages[cellIndex]
                val globalIndex = startIndex + cellIndex + 1

                val cellLeft = marginL + c * (cellWidth + gapH)
                val cellTop = marginT + r * (cellHeight + gapV)
                val cellRight = cellLeft + cellWidth
                val cellBottom = cellTop + cellHeight

                val cellRect = RectF(cellLeft, cellTop, cellRight, cellBottom)

                // Optional caption space reserve
                val hasCaption = config.captionMode != MergerCaptionMode.NONE
                val captionHeight = if (hasCaption) 18f * ptToPx else 0f
                val imageAreaRect = RectF(cellLeft, cellTop, cellRight, cellBottom - captionHeight)

                // 1. Draw Drop Shadow
                if (config.hasShadow) {
                    val shadowRect = RectF(
                        imageAreaRect.left + 2f * ptToPx,
                        imageAreaRect.top + 3f * ptToPx,
                        imageAreaRect.right + 2f * ptToPx,
                        imageAreaRect.bottom + 3f * ptToPx
                    )
                    canvas.drawRoundRect(shadowRect, borderRadiusPx, borderRadiusPx, shadowPaint)
                }

                // 2. Decode & Draw Image
                val targetDecodeW = (imageAreaRect.width()).toInt().coerceAtLeast(50)
                val targetDecodeH = (imageAreaRect.height()).toInt().coerceAtLeast(50)
                val imageBitmap = decodeSampledBitmapFromUri(item.uri, targetDecodeW, targetDecodeH, item.rotationDegrees)

                if (imageBitmap != null) {
                    // Clip to rounded rect
                    val path = Path().apply {
                        addRoundRect(imageAreaRect, borderRadiusPx, borderRadiusPx, Path.Direction.CW)
                    }

                    canvas.save()
                    canvas.clipPath(path)

                    val bmpW = imageBitmap.width.toFloat()
                    val bmpH = imageBitmap.height.toFloat()

                    val drawRect = when (config.fitMode) {
                        MergerFitMode.FIT -> {
                            val scale = min(imageAreaRect.width() / bmpW, imageAreaRect.height() / bmpH)
                            val scaledW = bmpW * scale
                            val scaledH = bmpH * scale
                            val left = imageAreaRect.centerX() - scaledW / 2f
                            val top = imageAreaRect.centerY() - scaledH / 2f
                            RectF(left, top, left + scaledW, top + scaledH)
                        }
                        MergerFitMode.FILL -> {
                            val scale = max(imageAreaRect.width() / bmpW, imageAreaRect.height() / bmpH)
                            val scaledW = bmpW * scale
                            val scaledH = bmpH * scale
                            val left = imageAreaRect.centerX() - scaledW / 2f
                            val top = imageAreaRect.centerY() - scaledH / 2f
                            RectF(left, top, left + scaledW, top + scaledH)
                        }
                    }

                    canvas.drawBitmap(imageBitmap, null, drawRect, Paint(Paint.FILTER_BITMAP_FLAG))
                    canvas.restore()
                    imageBitmap.recycle()
                }

                // 3. Draw Border
                if (config.hasBorder) {
                    canvas.drawRoundRect(imageAreaRect, borderRadiusPx, borderRadiusPx, borderPaint)
                }

                // 4. Draw Sequence Number Badge
                if (config.showImageIndex) {
                    val badgeRadius = 10f * ptToPx
                    val badgeCx = imageAreaRect.left + badgeRadius + 4f * ptToPx
                    val badgeCy = imageAreaRect.top + badgeRadius + 4f * ptToPx
                    canvas.drawCircle(badgeCx, badgeCy, badgeRadius, badgeBgPaint)
                    val textBaseline = badgeCy - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f
                    canvas.drawText("$globalIndex", badgeCx, textBaseline, badgeTextPaint)
                }

                // 5. Draw Caption
                if (hasCaption) {
                    val captionText = when (config.captionMode) {
                        MergerCaptionMode.FILENAME -> item.fileName
                        MergerCaptionMode.CUSTOM -> item.customCaption.ifBlank { item.fileName }
                        MergerCaptionMode.NONE -> ""
                    }
                    if (captionText.isNotBlank()) {
                        val truncated = truncateString(captionText, textPaint, cellWidth - 4f * ptToPx)
                        val captionY = cellBottom - 4f * ptToPx
                        canvas.drawText(truncated, cellRect.centerX(), captionY, textPaint)
                    }
                }
            }
        }

        // 6. Draw Page Number Footer
        if (config.showPageNumber) {
            val totalPages = calculateTotalPages(allImages.size, config)
            val footerText = "Page ${pageIndex + 1} of $totalPages"
            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isColorDark(config.backgroundColorArgb)) Color.LTGRAY else Color.GRAY
                textSize = (9f * ptToPx).coerceAtLeast(8f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
            }
            val footerY = targetHeightPx - (marginB / 2f).coerceAtLeast(6f * ptToPx)
            canvas.drawText(footerText, targetWidthPx / 2f, footerY, footerPaint)
        }

        bitmap
    }

    /**
     * Exports merged images into a high-quality multi-page PDF document.
     */
    suspend fun exportToPdf(
        allImages: List<MergerImageItem>,
        config: ImageMergerConfig,
        outputFile: File,
        onProgress: ((Float) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val doc = PDDocument()
        val totalPages = calculateTotalPages(allImages.size, config)

        try {
            val scaleFactor = config.exportQuality.scaleFactor

            for (pageIndex in 0 until totalPages) {
                val pageBitmap = renderPageToBitmap(pageIndex, allImages, config, scaleFactor)
                val perPage = config.imagesPerPage.coerceAtLeast(1)
                val startIndex = pageIndex * perPage
                val endIndex = min(startIndex + perPage, allImages.size)
                val pageImages = if (startIndex < allImages.size) allImages.subList(startIndex, endIndex) else emptyList()

                val (pageWidthPt, pageHeightPt) = getPageDimensionsPt(pageIndex, pageImages, config)
                val pdPage = PDPage(PDRectangle(pageWidthPt, pageHeightPt))
                doc.addPage(pdPage)

                val pdImage = JPEGFactory.createFromImage(doc, pageBitmap, 0.90f)
                val contentStream = PDPageContentStream(doc, pdPage)
                contentStream.drawImage(pdImage, 0f, 0f, pageWidthPt, pageHeightPt)
                contentStream.close()
                pageBitmap.recycle()

                onProgress?.invoke((pageIndex + 1).toFloat() / totalPages)
            }
            doc.save(outputFile)
        } finally {
            doc.close()
        }
        outputFile
    }

    /**
     * Exports merged images as one or more image files (PNG/JPG).
     */
    suspend fun exportToImages(
        allImages: List<MergerImageItem>,
        config: ImageMergerConfig,
        outputDir: File,
        baseName: String,
        onProgress: ((Float) -> Unit)? = null
    ): List<File> = withContext(Dispatchers.IO) {
        outputDir.mkdirs()
        val totalPages = calculateTotalPages(allImages.size, config)
        val results = mutableListOf<File>()
        val scaleFactor = config.exportQuality.scaleFactor
        val isPng = config.exportFormat == MergerExportFormat.IMAGE_PNG
        val format = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val ext = if (isPng) "png" else "jpg"

        for (pageIndex in 0 until totalPages) {
            val pageBitmap = renderPageToBitmap(pageIndex, allImages, config, scaleFactor)
            val fileName = if (totalPages == 1) "$baseName.$ext" else "${baseName}_page_${pageIndex + 1}.$ext"
            val file = File(outputDir, fileName)

            FileOutputStream(file).use { out ->
                pageBitmap.compress(format, 92, out)
            }
            pageBitmap.recycle()
            results.add(file)

            onProgress?.invoke((pageIndex + 1).toFloat() / totalPages)
        }
        results
    }

    private fun isColorDark(colorArgb: Long): Boolean {
        val r = ((colorArgb shr 16) and 0xFF).toFloat()
        val g = ((colorArgb shr 8) and 0xFF).toFloat()
        val b = (colorArgb and 0xFF).toFloat()
        val luminance = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        return luminance < 0.5f
    }

    private fun truncateString(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var truncated = text
        while (truncated.isNotEmpty() && paint.measureText("$truncated…") > maxWidth) {
            truncated = truncated.dropLast(1)
        }
        return if (truncated.isEmpty()) "" else "$truncated…"
    }
}

package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix as AndroidMatrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.model.CompressionLevel
import com.example.model.ScannedPage
import com.example.model.WatermarkPosition
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.cos
import kotlin.math.sin

class PdfEngine(private val context: Context) {

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

    private fun loadDocument(uri: Uri): PDDocument {
        val path = uri.path
        if (path != null) {
            val file = File(path)
            if (file.exists() && file.isFile) {
                return PDDocument.load(file)
            }
        }
        val stream = openInputStream(uri) ?: throw IllegalArgumentException("Cannot open stream for URI: $uri")
        return stream.use { PDDocument.load(it) }
    }

    /**
     * Concatenates PDF pages from each input URI into one output PDF using PDDocument page imports.
     */
    suspend fun mergePdfs(inputUris: List<Uri>, outputFile: File): File = withContext(Dispatchers.IO) {
        if (inputUris.isEmpty()) throw IllegalArgumentException("No input URIs provided for merge")
        outputFile.parentFile?.mkdirs()

        val mergedDoc = PDDocument()
        try {
            for (uri in inputUris) {
                val doc = loadDocument(uri)
                try {
                    for (i in 0 until doc.numberOfPages) {
                        mergedDoc.importPage(doc.getPage(i))
                    }
                } finally {
                    doc.close()
                }
            }
            mergedDoc.save(outputFile)
        } finally {
            mergedDoc.close()
        }
        outputFile
    }

    /**
     * Splits the source PDF into multiple real PDF files at the given page cut boundaries.
     * splitAtPages contains 1-based page indices after which to split (e.g., [1, 3] on a 4-page PDF -> 3 files).
     */
    suspend fun splitPdf(inputUri: Uri, splitAtPages: List<Int>, outputDir: File): List<File> = withContext(Dispatchers.IO) {
        outputDir.mkdirs()
        val sourceDoc = loadDocument(inputUri)
        val resultFiles = mutableListOf<File>()

        try {
            val totalPages = sourceDoc.numberOfPages
            if (totalPages == 0) return@withContext emptyList()

            val sortedCuts = splitAtPages.filter { it in 1 until totalPages }.distinct().sorted()
            val cutBoundaries = listOf(0) + sortedCuts + listOf(totalPages)

            val baseName = inputUri.lastPathSegment?.substringBeforeLast(".") ?: "document"

            for (partIndex in 0 until cutBoundaries.size - 1) {
                val startPage = cutBoundaries[partIndex] // 0-based inclusive
                val endPage = cutBoundaries[partIndex + 1] // 0-based exclusive
                if (startPage >= endPage) continue

                val partDoc = PDDocument()
                try {
                    for (p in startPage until endPage) {
                        partDoc.importPage(sourceDoc.getPage(p))
                    }
                    val partFile = File(outputDir, "${baseName}_part${partIndex + 1}.pdf")
                    partDoc.save(partFile)
                    resultFiles.add(partFile)
                } finally {
                    partDoc.close()
                }
            }
        } finally {
            sourceDoc.close()
        }
        resultFiles
    }

    /**
     * Compresses PDF by re-encoding embedded images at lower JPEG quality and scaling dimensions.
     */
    suspend fun compressPdf(inputUri: Uri, level: CompressionLevel, outputFile: File): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val doc = loadDocument(inputUri)

        val (quality, maxDimension) = when (level) {
            CompressionLevel.LOW -> Pair(0.75f, 1800)
            CompressionLevel.MEDIUM -> Pair(0.48f, 1200)
            CompressionLevel.HIGH -> Pair(0.22f, 800)
        }

        try {
            for (pageIndex in 0 until doc.numberOfPages) {
                val page = doc.getPage(pageIndex)
                val resources = page.resources ?: continue
                val xObjectNames = resources.xObjectNames ?: continue

                for (name in xObjectNames) {
                    if (resources.isImageXObject(name)) {
                        val image = resources.getXObject(name) as? PDImageXObject ?: continue
                        val originalBmp = image.image ?: continue

                        val scaledBmp = scaleBitmapDown(originalBmp, maxDimension)
                        val compressedXObject = JPEGFactory.createFromImage(doc, scaledBmp, quality)

                        resources.put(name, compressedXObject)
                        if (scaledBmp != originalBmp) {
                            scaledBmp.recycle()
                        }
                    }
                }
            }
            doc.save(outputFile)
        } finally {
            doc.close()
        }
        outputFile
    }

    /**
     * Overlays watermark text on each page using PDPageContentStream.
     */
    suspend fun addWatermark(
        inputUri: Uri,
        text: String,
        position: WatermarkPosition,
        opacity: Float,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val doc = loadDocument(inputUri)

        try {
            val safeOpacity = opacity.coerceIn(0.05f, 1.0f)
            val font = PDType1Font.HELVETICA_BOLD

            for (pageIndex in 0 until doc.numberOfPages) {
                val page = doc.getPage(pageIndex)
                val mediaBox = page.mediaBox ?: PDRectangle.A4
                val pageWidth = mediaBox.width
                val pageHeight = mediaBox.height

                val fontSize = when (position) {
                    WatermarkPosition.DIAGONAL -> 48f
                    WatermarkPosition.CENTER -> 42f
                    WatermarkPosition.CORNER -> 22f
                }

                val textWidth = (font.getStringWidth(text) / 1000f) * fontSize
                val capHeight = (font.fontDescriptor?.capHeight ?: 700f) / 1000f * fontSize

                val contentStream = PDPageContentStream(
                    doc,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                )

                val graphicsState = PDExtendedGraphicsState().apply {
                    nonStrokingAlphaConstant = safeOpacity
                    strokingAlphaConstant = safeOpacity
                }
                contentStream.setGraphicsStateParameters(graphicsState)
                contentStream.setNonStrokingColor(140, 140, 140)

                contentStream.beginText()
                contentStream.setFont(font, fontSize)

                when (position) {
                    WatermarkPosition.CENTER -> {
                        val x = (pageWidth - textWidth) / 2f
                        val y = (pageHeight - capHeight) / 2f
                        contentStream.newLineAtOffset(x, y)
                        contentStream.showText(text)
                    }
                    WatermarkPosition.DIAGONAL -> {
                        val cx = pageWidth / 2f
                        val cy = pageHeight / 2f
                        val rad = Math.toRadians(45.0)
                        val c = cos(rad).toFloat()
                        val s = sin(rad).toFloat()
                        val halfW = textWidth / 2f
                        val halfH = capHeight / 2f
                        val tx = cx - (halfW * c - halfH * s)
                        val ty = cy - (halfW * s + halfH * c)
                        val matrix = Matrix(c, s, -s, c, tx, ty)
                        contentStream.setTextMatrix(matrix)
                        contentStream.showText(text)
                    }
                    WatermarkPosition.CORNER -> {
                        val x = pageWidth - textWidth - 32f
                        val y = 32f
                        contentStream.newLineAtOffset(x, y)
                        contentStream.showText(text)
                    }
                }

                contentStream.endText()
                contentStream.close()
            }
            doc.save(outputFile)
        } finally {
            doc.close()
        }
        outputFile
    }

    /**
     * Encrypts the PDF with the given password using PDFBox StandardProtectionPolicy.
     */
    suspend fun setPassword(inputUri: Uri, password: String, outputFile: File): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val doc = loadDocument(inputUri)

        try {
            val accessPermission = AccessPermission()
            val protectionPolicy = StandardProtectionPolicy(password, password, accessPermission).apply {
                encryptionKeyLength = 128
                permissions = accessPermission
            }
            doc.protect(protectionPolicy)
            doc.save(outputFile)
        } finally {
            doc.close()
        }
        outputFile
    }

    /**
     * Generates a PDF file from scanned pages (either image URIs or resource drawables).
     */
    suspend fun createPdfFromPages(pages: List<ScannedPage>, outputFile: File): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val doc = PDDocument()

        try {
            for (page in pages) {
                val bitmap = loadPageBitmap(page) ?: continue
                try {
                    val pageRect = PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat())
                    val pdPage = PDPage(pageRect)
                    doc.addPage(pdPage)

                    val pdImage = JPEGFactory.createFromImage(doc, bitmap, 0.90f)
                    val contentStream = PDPageContentStream(doc, pdPage)
                    contentStream.drawImage(pdImage, 0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
                    contentStream.close()
                } finally {
                    bitmap.recycle()
                }
            }
            doc.save(outputFile)
        } finally {
            doc.close()
        }
        outputFile
    }

    /**
     * Loads a Bitmap from a ScannedPage applying any rotation or filters.
     */
    fun loadPageBitmap(page: ScannedPage): Bitmap? {
        val baseBitmap: Bitmap? = if (!page.imageUri.isNullOrEmpty()) {
            val uri = Uri.parse(page.imageUri)
            try {
                openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                null
            }
        } else {
            BitmapFactory.decodeResource(context.resources, page.drawableRes)
        }

        if (baseBitmap == null) return null

        if (page.rotationAngle != 0f) {
            val matrix = AndroidMatrix().apply { postRotate(page.rotationAngle) }
            val rotated = Bitmap.createBitmap(baseBitmap, 0, 0, baseBitmap.width, baseBitmap.height, matrix, true)
            if (rotated != baseBitmap) {
                baseBitmap.recycle()
            }
            return rotated
        }
        return baseBitmap
    }

    /**
     * Renders a page of a PDF file to a Bitmap using Android's native PdfRenderer.
     */
    fun renderPdfPageToBitmap(pdfFile: File, pageIndex: Int = 0): Bitmap? {
        if (!pdfFile.exists() || pdfFile.length() == 0L) return null
        return try {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            if (pageIndex >= renderer.pageCount) {
                renderer.close()
                pfd.close()
                return null
            }
            val page = renderer.openPage(pageIndex)
            val width = page.width * 2
            val height = page.height * 2
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            pfd.close()
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Saves the first page of a PDF as a JPEG thumbnail file and returns its URI.
     */
    suspend fun generateThumbnailForPdf(pdfFile: File, thumbFile: File): Uri? = withContext(Dispatchers.IO) {
        val bitmap = renderPdfPageToBitmap(pdfFile, 0) ?: return@withContext null
        thumbFile.parentFile?.mkdirs()
        FileOutputStream(thumbFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        bitmap.recycle()
        Uri.fromFile(thumbFile)
    }

    private fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        val maxOriginal = maxOf(originalWidth, originalHeight)

        if (maxOriginal <= maxDimension) return bitmap

        val scale = maxDimension.toFloat() / maxOriginal
        val newWidth = (originalWidth * scale).toInt().coerceAtLeast(1)
        val newHeight = (originalHeight * scale).toInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    companion object {
        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0) return "0.1 MB"
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            return if (mb >= 1.0) {
                String.format("%.1f MB", mb)
            } else {
                String.format("%.0f KB", kb)
            }
        }
    }
}

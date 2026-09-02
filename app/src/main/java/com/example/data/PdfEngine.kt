package com.example.data

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix as AndroidMatrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.example.model.CompressionLevel
import com.example.model.DocFormat
import com.example.model.ScannedPage
import com.example.model.WatermarkPosition
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
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
        val bytes = stream.use { it.readBytes() }
        return PDDocument.load(java.io.ByteArrayInputStream(bytes))
    }

    /** Result of importing a real file picked from device storage (SAF). */
    data class ImportedFile(
        val file: File,
        val displayName: String,
        val format: DocFormat,
        val pageCount: Int
    )

    /**
     * Copies a file the user picked via the system document/gallery picker
     * (content:// URI) into app-private storage, and reads its real name,
     * MIME type and page count so it can become a genuine DocumentItem
     * instead of one of the hardcoded sample documents.
     */
    suspend fun importExternalFile(uri: Uri, outputDir: File): ImportedFile? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri)

        var displayName = "Imported_${System.currentTimeMillis()}"
        var sizeHint = 0L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor: Cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) cursor.getString(nameIdx)?.let { displayName = it }
                    if (sizeIdx >= 0) sizeHint = cursor.getLong(sizeIdx)
                }
            }

        val isPdf = mimeType == "application/pdf" || displayName.endsWith(".pdf", ignoreCase = true)
        val format = if (isPdf) DocFormat.PDF else DocFormat.JPG

        val safeName = if (isPdf && !displayName.endsWith(".pdf", ignoreCase = true)) {
            "$displayName.pdf"
        } else if (!isPdf && !displayName.substringAfterLast('.', "").let {
                it.equals("jpg", true) || it.equals("jpeg", true) || it.equals("png", true) || it.equals("webp", true)
            }) {
            "$displayName.jpg"
        } else displayName

        outputDir.mkdirs()
        val outputFile = File(outputDir, "imported_${System.currentTimeMillis()}_$safeName")

        val input = openInputStream(uri) ?: return@withContext null
        input.use { inStream ->
            FileOutputStream(outputFile).use { outStream ->
                inStream.copyTo(outStream)
            }
        }

        if (outputFile.length() == 0L && sizeHint <= 0L) {
            // Nothing was actually copied — treat as a failed import rather than
            // silently adding a broken 0-byte document.
            outputFile.delete()
            return@withContext null
        }

        val pageCount = if (format == DocFormat.PDF) {
            try {
                val doc = PDDocument.load(outputFile)
                try {
                    doc.numberOfPages
                } finally {
                    doc.close()
                }
            } catch (e: Exception) {
                1
            }
        } else {
            1
        }

        ImportedFile(
            file = outputFile,
            displayName = safeName,
            format = format,
            pageCount = pageCount.coerceAtLeast(1)
        )
    }

    /**
     * Concatenates PDF pages from each input URI into one output PDF using PDFMergerUtility.
     */
    suspend fun mergePdfs(inputUris: List<Uri>, outputFile: File): File = withContext(Dispatchers.IO) {
        if (inputUris.isEmpty()) throw IllegalArgumentException("No input URIs provided for merge")
        outputFile.parentFile?.mkdirs()

        val merger = PDFMergerUtility()
        merger.destinationFileName = outputFile.absolutePath

        val tempFilesToClean = mutableListOf<File>()
        try {
            for (uri in inputUris) {
                val path = uri.path
                if (path != null && File(path).exists()) {
                    merger.addSource(File(path))
                } else {
                    val stream = openInputStream(uri) ?: continue
                    val tempFile = File.createTempFile("merge_src_", ".pdf", context.cacheDir)
                    tempFilesToClean.add(tempFile)
                    FileOutputStream(tempFile).use { out ->
                        stream.use { inp -> inp.copyTo(out) }
                    }
                    merger.addSource(tempFile)
                }
            }
            merger.mergeDocuments(null)
        } finally {
            for (temp in tempFilesToClean) {
                try { temp.delete() } catch (_: Exception) {}
            }
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
                encryptionKeyLength = 256
                permissions = accessPermission
            }
            try {
                doc.protect(protectionPolicy)
            } catch (_: Exception) {
                val fallbackPolicy = StandardProtectionPolicy(password, password, accessPermission).apply {
                    encryptionKeyLength = 128
                    permissions = accessPermission
                }
                doc.protect(fallbackPolicy)
            }
            doc.save(outputFile)
        } finally {
            doc.close()
        }
        outputFile
    }

    /**
     * Real "Image to PDF" conversion: decodes each picked image URI and writes it as its
     * own page, sized to the image's own dimensions, into a single output PDF.
     */
    suspend fun imagesToPdf(imageUris: List<Uri>, outputFile: File): File = withContext(Dispatchers.IO) {
        if (imageUris.isEmpty()) throw IllegalArgumentException("No images provided")
        outputFile.parentFile?.mkdirs()
        val doc = PDDocument()
        try {
            for (uri in imageUris) {
                val bitmap = openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: continue
                val scaled = scaleBitmapDown(bitmap, 2200)
                try {
                    val pageRect = PDRectangle(scaled.width.toFloat(), scaled.height.toFloat())
                    val pdPage = PDPage(pageRect)
                    doc.addPage(pdPage)

                    val pdImage = JPEGFactory.createFromImage(doc, scaled, 0.90f)
                    val contentStream = PDPageContentStream(doc, pdPage)
                    contentStream.drawImage(pdImage, 0f, 0f, scaled.width.toFloat(), scaled.height.toFloat())
                    contentStream.close()
                } finally {
                    if (scaled !== bitmap) scaled.recycle()
                    bitmap.recycle()
                }
            }
            if (doc.numberOfPages == 0) throw IllegalStateException("None of the selected images could be read")
            doc.save(outputFile)
        } finally {
            doc.close()
        }
        outputFile
    }

    /**
     * Real "PDF to Image" conversion: renders every page of a real PDF file (via Android's
     * native PdfRenderer) to its own JPEG file.
     */
    suspend fun pdfToImages(pdfFile: File, outputDir: File): List<File> = withContext(Dispatchers.IO) {
        outputDir.mkdirs()
        val results = mutableListOf<File>()
        val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        try {
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val outFile = File(outputDir, "page_${i + 1}.jpg")
                FileOutputStream(outFile).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out) }
                bitmap.recycle()
                results.add(outFile)
            }
        } finally {
            renderer.close()
            pfd.close()
        }
        results
    }

    /**
     * Rotates individual pages of a real PDF. [rotations] maps 0-based page index to the
     * number of degrees to add (multiples of 90) via PDFBox's page rotation attribute —
     * this changes how the page is displayed/printed without re-rendering its content.
     */
    suspend fun rotatePages(inputUri: Uri, rotations: Map<Int, Int>, outputFile: File): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val doc = loadDocument(inputUri)
        try {
            for ((index, degrees) in rotations) {
                if (degrees == 0) continue
                if (index !in 0 until doc.numberOfPages) continue
                val page = doc.getPage(index)
                val newRotation = ((page.rotation + degrees) % 360 + 360) % 360
                page.rotation = newRotation
            }
            doc.save(outputFile)
        } finally {
            doc.close()
        }
        outputFile
    }

    /**
     * Deletes the given 0-based page indices from a real PDF by rebuilding a new document
     * from only the pages that were kept (safer than in-place removal for re-saving).
     * Refuses to produce an empty document — if every page was selected, the first
     * original page is kept so the result is never a corrupt 0-page PDF.
     */
    suspend fun deletePages(inputUri: Uri, pageIndicesToDelete: Set<Int>, outputFile: File): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val sourceDoc = loadDocument(inputUri)
        val newDoc = PDDocument()
        try {
            for (i in 0 until sourceDoc.numberOfPages) {
                if (i !in pageIndicesToDelete) {
                    newDoc.importPage(sourceDoc.getPage(i))
                }
            }
            if (newDoc.numberOfPages == 0 && sourceDoc.numberOfPages > 0) {
                newDoc.importPage(sourceDoc.getPage(0))
            }
            newDoc.save(outputFile)
        } finally {
            newDoc.close()
            sourceDoc.close()
        }
        outputFile
    }

    /**
     * Draws a real hand-drawn signature (captured as a list of freehand strokes, each a
     * list of (x, y) points in the signature pad's own coordinate space) onto one page of
     * a PDF as vector line art — scaled into a fixed box near the bottom-right corner.
     */
    suspend fun addSignature(
        inputUri: Uri,
        pageIndex: Int,
        strokes: List<List<Pair<Float, Float>>>,
        padWidth: Float,
        padHeight: Float,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val doc = loadDocument(inputUri)
        try {
            if (doc.numberOfPages == 0) throw IllegalStateException("Document has no pages")
            val safePageIndex = pageIndex.coerceIn(0, doc.numberOfPages - 1)
            val page = doc.getPage(safePageIndex)
            val mediaBox = page.mediaBox ?: PDRectangle.A4

            val boxWidth = 190f
            val boxHeight = 80f
            val originX = (mediaBox.width - boxWidth - 40f).coerceAtLeast(20f)
            val originY = 46f

            val safePadW = if (padWidth > 0f) padWidth else 1f
            val safePadH = if (padHeight > 0f) padHeight else 1f
            val scaleX = boxWidth / safePadW
            val scaleY = boxHeight / safePadH

            val contentStream = PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)
            contentStream.setStrokingColor(25, 25, 25)
            contentStream.setLineWidth(2.2f)
            for (stroke in strokes) {
                if (stroke.size < 2) continue
                val (fx, fy) = stroke.first()
                contentStream.moveTo(originX + fx * scaleX, originY + boxHeight - fy * scaleY)
                for ((x, y) in stroke.drop(1)) {
                    contentStream.lineTo(originX + x * scaleX, originY + boxHeight - y * scaleY)
                }
                contentStream.stroke()
            }
            contentStream.close()
            doc.save(outputFile)
        } finally {
            doc.close()
        }
        outputFile
    }

    /**
     * Generates a PDF file from scanned pages (either image URIs or resource drawables).
     */
    suspend fun createPdfFromPages(
        pages: List<ScannedPage>,
        outputFile: File,
        jpegQuality: Float = 0.90f,
        maxDimension: Int = 2400
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val doc = PDDocument()

        try {
            for (page in pages) {
                val rawBitmap = loadPageBitmap(page) ?: continue
                val bitmap = scaleBitmapDown(rawBitmap, maxDimension)
                try {
                    val pageRect = PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat())
                    val pdPage = PDPage(pageRect)
                    doc.addPage(pdPage)

                    val pdImage = JPEGFactory.createFromImage(doc, bitmap, jpegQuality)
                    val contentStream = PDPageContentStream(doc, pdPage)
                    contentStream.drawImage(pdImage, 0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
                    contentStream.close()
                } finally {
                    if (bitmap !== rawBitmap) {
                        bitmap.recycle()
                    }
                    rawBitmap.recycle()
                }
            }
            doc.save(outputFile)
        } finally {
            doc.close()
        }
        outputFile
    }

    /**
     * Composites front and back scans of an ID card onto a single A4 page for standard printing.
     */
    suspend fun createIdCardPdf(
        frontUri: String,
        backUri: String,
        outputFile: File,
        layout: com.example.model.IdCardLayout = com.example.model.IdCardLayout.TOP_BOTTOM,
        quality: Float = 0.95f,
        maxDimension: Int = 2400
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val doc = PDDocument()

        try {
            val pageWidth = PDRectangle.A4.width
            val pageHeight = PDRectangle.A4.height
            val pdPage = PDPage(PDRectangle.A4)
            doc.addPage(pdPage)

            val rawFrontBitmap = openInputStream(Uri.parse(frontUri))?.use { BitmapFactory.decodeStream(it) }
                ?: throw IllegalArgumentException("Could not load front card image")
            val rawBackBitmap = openInputStream(Uri.parse(backUri))?.use { BitmapFactory.decodeStream(it) }
                ?: throw IllegalArgumentException("Could not load back card image")

            val frontBitmap = scaleBitmapDown(rawFrontBitmap, maxDimension)
            val backBitmap = scaleBitmapDown(rawBackBitmap, maxDimension)

            try {
                val pdFrontImage = JPEGFactory.createFromImage(doc, frontBitmap, quality)
                val pdBackImage = JPEGFactory.createFromImage(doc, backBitmap, quality)
                val contentStream = PDPageContentStream(doc, pdPage)

                if (layout == com.example.model.IdCardLayout.TOP_BOTTOM) {
                    val slotMaxW = (pageWidth - 80f).coerceAtMost(360f)
                    val slotMaxH = 227f

                    val frontAspect = frontBitmap.width.toFloat() / frontBitmap.height.toFloat()
                    var frontW = slotMaxW
                    var frontH = frontW / frontAspect
                    if (frontH > slotMaxH) {
                        frontH = slotMaxH
                        frontW = frontH * frontAspect
                    }

                    val backAspect = backBitmap.width.toFloat() / backBitmap.height.toFloat()
                    var backW = slotMaxW
                    var backH = backW / backAspect
                    if (backH > slotMaxH) {
                        backH = slotMaxH
                        backW = backH * backAspect
                    }

                    val frontX = (pageWidth - frontW) / 2f
                    val frontY = pageHeight * 0.58f - (frontH / 2f)

                    val backX = (pageWidth - backW) / 2f
                    val backY = pageHeight * 0.22f - (backH / 2f)

                    contentStream.setLineWidth(0.75f)
                    contentStream.setStrokingColor(0.75f, 0.75f, 0.75f)

                    contentStream.drawImage(pdFrontImage, frontX, frontY, frontW, frontH)
                    contentStream.addRect(frontX - 2f, frontY - 2f, frontW + 4f, frontH + 4f)
                    contentStream.stroke()

                    contentStream.drawImage(pdBackImage, backX, backY, backW, backH)
                    contentStream.addRect(backX - 2f, backY - 2f, backW + 4f, backH + 4f)
                    contentStream.stroke()
                } else {
                    val slotMaxW = (pageWidth - 60f) / 2f
                    val slotMaxH = 220f

                    val frontAspect = frontBitmap.width.toFloat() / frontBitmap.height.toFloat()
                    var frontW = slotMaxW
                    var frontH = frontW / frontAspect
                    if (frontH > slotMaxH) {
                        frontH = slotMaxH
                        frontW = frontH * frontAspect
                    }

                    val backAspect = backBitmap.width.toFloat() / backBitmap.height.toFloat()
                    var backW = slotMaxW
                    var backH = backW / backAspect
                    if (backH > slotMaxH) {
                        backH = slotMaxH
                        backW = backH * backAspect
                    }

                    val centerY = pageHeight * 0.5f
                    val frontX = 30f + (slotMaxW - frontW) / 2f
                    val frontY = centerY - (frontH / 2f)

                    val backX = pageWidth / 2f + 10f + (slotMaxW - backW) / 2f
                    val backY = centerY - (backH / 2f)

                    contentStream.setLineWidth(0.75f)
                    contentStream.setStrokingColor(0.75f, 0.75f, 0.75f)

                    contentStream.drawImage(pdFrontImage, frontX, frontY, frontW, frontH)
                    contentStream.addRect(frontX - 2f, frontY - 2f, frontW + 4f, frontH + 4f)
                    contentStream.stroke()

                    contentStream.drawImage(pdBackImage, backX, backY, backW, backH)
                    contentStream.addRect(backX - 2f, backY - 2f, backW + 4f, backH + 4f)
                    contentStream.stroke()
                }

                contentStream.close()
            } finally {
                if (frontBitmap !== rawFrontBitmap) frontBitmap.recycle()
                rawFrontBitmap.recycle()
                if (backBitmap !== rawBackBitmap) backBitmap.recycle()
                rawBackBitmap.recycle()
            }

            doc.save(outputFile)
        } finally {
            doc.close()
        }
        outputFile
    }

    /**
     * Loads a Bitmap from a ScannedPage applying any rotation, crop, and filters.
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

        var currentBitmap = baseBitmap

        if (page.rotationAngle != 0f) {
            val matrix = AndroidMatrix().apply { postRotate(page.rotationAngle) }
            val rotated = Bitmap.createBitmap(currentBitmap, 0, 0, currentBitmap.width, currentBitmap.height, matrix, true)
            if (rotated != currentBitmap) {
                if (currentBitmap != baseBitmap) currentBitmap.recycle()
                currentBitmap = rotated
            }
        }

        // Apply Crop if boundaries are set
        if (page.cropLeft > 0.005f || page.cropTop > 0.005f || page.cropRight < 0.995f || page.cropBottom < 0.995f) {
            val width = currentBitmap.width
            val height = currentBitmap.height
            val cl = (page.cropLeft.coerceIn(0f, 0.9f) * width).toInt().coerceIn(0, width - 2)
            val ct = (page.cropTop.coerceIn(0f, 0.9f) * height).toInt().coerceIn(0, height - 2)
            val cr = (page.cropRight.coerceIn(page.cropLeft + 0.05f, 1f) * width).toInt().coerceIn(cl + 2, width)
            val cb = (page.cropBottom.coerceIn(page.cropTop + 0.05f, 1f) * height).toInt().coerceIn(ct + 2, height)
            val cropW = cr - cl
            val cropH = cb - ct
            if (cropW > 0 && cropH > 0 && (cropW < width || cropH < height)) {
                val cropped = Bitmap.createBitmap(currentBitmap, cl, ct, cropW, cropH)
                if (cropped != currentBitmap) {
                    if (currentBitmap != baseBitmap) currentBitmap.recycle()
                    currentBitmap = cropped
                }
            }
        }

        // Apply Filter if not ORIGINAL
        if (page.filter != com.example.model.PageFilter.ORIGINAL) {
            val filtered = applyFilterToBitmap(currentBitmap, page.filter)
            if (filtered != currentBitmap) {
                if (currentBitmap != baseBitmap) currentBitmap.recycle()
                currentBitmap = filtered
            }
        }

        return currentBitmap
    }

    private fun applyFilterToBitmap(bitmap: Bitmap, filter: com.example.model.PageFilter): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = android.graphics.Paint()
        val cm = android.graphics.ColorMatrix()
        when (filter) {
            com.example.model.PageFilter.GRAYSCALE -> {
                cm.setSaturation(0f)
            }
            com.example.model.PageFilter.BW -> {
                cm.setSaturation(0f)
                val contrast = 1.6f
                val translate = (-0.3f * 255f) * contrast
                val array = floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
                cm.postConcat(android.graphics.ColorMatrix(array))
            }
            com.example.model.PageFilter.MAGIC -> {
                cm.setSaturation(1.35f)
            }
            com.example.model.PageFilter.COLOR -> {
                cm.setSaturation(1.15f)
            }
            com.example.model.PageFilter.ORIGINAL -> return bitmap
        }
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
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
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
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

    fun createEmptyPdf(outputFile: File): File {
        outputFile.parentFile?.mkdirs()
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            doc.save(outputFile)
        }
        return outputFile
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

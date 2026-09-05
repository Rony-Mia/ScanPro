package com.example

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.OcrEngine
import com.example.data.OcrWord
import com.example.data.PdfEngine
import com.example.model.ScannedPage
import com.example.model.WatermarkPosition
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfEngineUnicodeTest {

    private lateinit var context: Context
    private lateinit var pdfEngine: PdfEngine

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        pdfEngine = PdfEngine(context)
    }

    private fun createSamplePdf(file: File) {
        val doc = PDDocument()
        doc.addPage(PDPage())
        doc.save(file)
        doc.close()
    }

    @Test
    fun testBengaliWatermarkDoesNotCrash() = runBlocking {
        val tempDir = File(context.cacheDir, "test_watermark").apply { mkdirs() }
        val inputPdf = File(tempDir, "input.pdf")
        val outputPdf = File(tempDir, "output_bengali.pdf")
        createSamplePdf(inputPdf)

        val resultFile = pdfEngine.addWatermark(
            inputUri = Uri.fromFile(inputPdf),
            text = "গোপনীয়",
            position = WatermarkPosition.CENTER,
            opacity = 0.3f,
            outputFile = outputPdf
        )

        assertTrue("Output file must exist", resultFile.exists())
        assertTrue("Output file must have content", resultFile.length() > 0)

        // Verify PDF can be loaded and read
        PDDocument.load(resultFile).use { doc ->
            assertTrue(doc.numberOfPages == 1)
            val stripper = PDFTextStripper()
            val extracted = stripper.getText(doc)
            assertTrue("Extracted text should contain Bengali watermark", extracted.contains("গোপনীয়"))
        }
    }

    @Test
    fun testEnglishWatermarkRegression() = runBlocking {
        val tempDir = File(context.cacheDir, "test_watermark_en").apply { mkdirs() }
        val inputPdf = File(tempDir, "input_en.pdf")
        val outputPdf = File(tempDir, "output_en.pdf")
        createSamplePdf(inputPdf)

        val resultFile = pdfEngine.addWatermark(
            inputUri = Uri.fromFile(inputPdf),
            text = "CONFIDENTIAL",
            position = WatermarkPosition.DIAGONAL,
            opacity = 0.4f,
            outputFile = outputPdf
        )

        assertTrue(resultFile.exists())
        PDDocument.load(resultFile).use { doc ->
            val stripper = PDFTextStripper()
            val extracted = stripper.getText(doc)
            System.err.println("EXTRACTED_DIAGONAL_DEBUG: [$extracted]")
            assertTrue(
                "Extracted text should contain English watermark characters",
                extracted.filter { !it.isWhitespace() }.contains("CONFIDENTIAL")
            )
        }
    }

    @Test
    fun testSearchablePdfWithBengaliWords() = runBlocking {
        val tempDir = File(context.cacheDir, "test_searchable").apply { mkdirs() }
        val outputPdf = File(tempDir, "searchable_bn.pdf")

        // Create a dummy bitmap page
        val imgFile = File(tempDir, "page1.jpg")
        val bitmap = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)
        FileOutputStream(imgFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        val page = ScannedPage(
            id = "p1",
            pageNumber = 1,
            imageUri = Uri.fromFile(imgFile).toString()
        )

        val mockOcrEngine = object : OcrEngine(context) {
            override suspend fun detectWords(bitmap: Bitmap, language: String): List<OcrWord> {
                return listOf(
                    OcrWord(text = "বাংলাদেশ", left = 0.1f, top = 0.1f, right = 0.4f, bottom = 0.15f),
                    OcrWord(text = "ScanPro", left = 0.5f, top = 0.1f, right = 0.8f, bottom = 0.15f)
                )
            }
        }

        val resultFile = pdfEngine.createSearchablePdf(
            pages = listOf(page),
            outputFile = outputPdf,
            ocrEngine = mockOcrEngine
        )

        assertTrue(resultFile.exists())
        assertTrue(resultFile.length() > 0)

        PDDocument.load(resultFile).use { doc ->
            val stripper = PDFTextStripper()
            val extracted = stripper.getText(doc)
            assertTrue("Searchable PDF text layer must contain Bengali word", extracted.contains("বাংলাদেশ"))
            assertTrue("Searchable PDF text layer must contain English word", extracted.contains("ScanPro"))
        }
    }
}

package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Runs on-device OCR text extraction using ML Kit's Text Recognition v2.
 * Fully on-device — no network calls, no cloud processing.
 */
class OcrEngine(@Suppress("UNUSED_PARAMETER") context: Context) {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Runs on-device text recognition on a single bitmap and returns the recognized text.
     */
    suspend fun recognizeText(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                if (cont.isActive) cont.resume(visionText.text)
            }
            .addOnFailureListener { error ->
                if (cont.isActive) cont.resumeWithException(error)
            }
    }

    /**
     * Extracts text from every page of a real PDF file, reporting progress (0f..1f) as it goes.
     * Renders each page to a bitmap via Android's native PdfRenderer, then runs OCR on it.
     */
    suspend fun extractTextFromPdf(
        pdfFile: File,
        onProgress: (Float) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        if (!pdfFile.exists() || pdfFile.length() == 0L) {
            return@withContext "No text was detected in this document."
        }

        val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val pageCount = renderer.pageCount
        val builder = StringBuilder()

        try {
            if (pageCount == 0) {
                return@withContext "No text was detected in this document."
            }
            for (index in 0 until pageCount) {
                val page = renderer.openPage(index)
                val bitmap = Bitmap.createBitmap(
                    page.width * 2,
                    page.height * 2,
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val pageText = try {
                    recognizeText(bitmap)
                } finally {
                    bitmap.recycle()
                }

                if (pageText.isNotBlank()) {
                    if (builder.isNotEmpty()) builder.append("\n\n--- Page ${index + 1} ---\n\n")
                    builder.append(pageText)
                }

                onProgress((index + 1).toFloat() / pageCount)
            }
        } finally {
            renderer.close()
            pfd.close()
        }

        builder.toString().ifBlank { "No text was detected in this document." }
    }

    fun close() {
        recognizer.close()
    }
}

package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.util.Constants
import com.googlecode.leptonica.android.Binarize
import com.googlecode.leptonica.android.Pix
import com.googlecode.leptonica.android.ReadFile
import com.googlecode.leptonica.android.Rotate
import com.googlecode.leptonica.android.Skew
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class OcrLanguage(
    val code: String,
    val name: String,
    val isBundled: Boolean = false
)

/**
 * High-performance, offline-capable multi-language OCR engine using Tesseract4Android.
 * Bundles English and Bengali for immediate offline use, and supports downloading additional
 * high-accuracy language models on demand.
 */
class OcrEngine(private val context: Context) {

    companion object {
        private const val TAG = "OcrEngine"
        const val DEFAULT_OCR_LANG = "eng+ben"
        private const val PREF_KEY_DOWNLOADED_OCR_LANGS = "pref_downloaded_ocr_langs"

        val AVAILABLE_LANGUAGES = listOf(
            OcrLanguage("eng", "English", isBundled = true),
            OcrLanguage("ben", "Bengali (বাংলা)", isBundled = true),
            OcrLanguage("hin", "Hindi (हिन्दी)"),
            OcrLanguage("ara", "Arabic (العربية)"),
            OcrLanguage("urd", "Urdu (اردو)"),
            OcrLanguage("spa", "Spanish (Español)"),
            OcrLanguage("fra", "French (Français)"),
            OcrLanguage("deu", "German (Deutsch)"),
            OcrLanguage("chi_sim", "Chinese Simplified (简体中文)"),
            OcrLanguage("chi_tra", "Chinese Traditional (繁體中文)"),
            OcrLanguage("jpn", "Japanese (日本語)"),
            OcrLanguage("kor", "Korean (한국어)"),
            OcrLanguage("rus", "Russian (Русский)"),
            OcrLanguage("por", "Portuguese (Português)"),
            OcrLanguage("ita", "Italian (Italiano)"),
            OcrLanguage("tur", "Turkish (Türkçe)"),
            OcrLanguage("vie", "Vietnamese (Tiếng Việt)"),
            OcrLanguage("nld", "Dutch (Nederlands)"),
            OcrLanguage("pol", "Polish (Polski)"),
            OcrLanguage("tam", "Tamil (தமிழ்)"),
            OcrLanguage("tel", "Telugu (తెలుగు)"),
            OcrLanguage("mar", "Marathi (मराठी)"),
            OcrLanguage("guj", "Gujarati (ગુજરાતી)"),
            OcrLanguage("fas", "Persian (فارسی)"),
            OcrLanguage("ind", "Indonesian (Bahasa Indonesia)"),
            OcrLanguage("tha", "Thai (ไทย)")
        )
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val baseDir: File
        get() = context.getExternalFilesDir(null) ?: context.filesDir

    val tessdataDir: File
        get() = File(baseDir, "tessdata").apply { mkdirs() }

    /**
     * Initializes tessdata folder and copies bundled assets on first use.
     */
    fun ensureTessdataInitialized() {
        try {
            val targetDir = tessdataDir
            val assetManager = context.assets
            val assetList = assetManager.list("tessdata") ?: emptyArray()
            for (fileName in assetList) {
                if (fileName.endsWith(".traineddata")) {
                    val destFile = File(targetDir, fileName)
                    if (!destFile.exists() || destFile.length() == 0L) {
                        assetManager.open("tessdata/$fileName").use { input ->
                            FileOutputStream(destFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        Log.d(TAG, "Extracted bundled tessdata: $fileName (${destFile.length()} bytes)")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize bundled tessdata", e)
        }
    }

    /**
     * Checks if a language traineddata model is available locally.
     */
    fun isLanguageAvailable(code: String): Boolean {
        if (code == "eng" || code == "ben") {
            val file = File(tessdataDir, "$code.traineddata")
            if (file.exists() && file.length() > 0) return true
            // If not yet copied, check assets
            ensureTessdataInitialized()
            return file.exists() && file.length() > 0
        }
        val file = File(tessdataDir, "$code.traineddata")
        return file.exists() && file.length() > 0
    }

    /**
     * Gets all currently installed / available language codes.
     */
    fun getInstalledLanguageCodes(): List<String> {
        ensureTessdataInitialized()
        val files = tessdataDir.listFiles { _, name -> name.endsWith(".traineddata") } ?: emptyArray()
        return files.map { it.name.substringBefore(".traineddata") }
    }

    /**
     * Downloads an on-demand language pack from tessdata_best repository.
     */
    suspend fun downloadLanguage(
        code: String,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val downloadUrl = "https://github.com/tesseract-ocr/tessdata_best/raw/main/$code.traineddata"
        val targetFile = File(tessdataDir, "$code.traineddata")
        val tempFile = File(tessdataDir, "$code.traineddata.tmp")

        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            var currentUrl = downloadUrl
            var redirects = 0
            while (redirects < 5) {
                val url = URL(currentUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER
                ) {
                    val newUrl = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (newUrl != null) {
                        currentUrl = newUrl
                        redirects++
                        continue
                    }
                }
                break
            }

            if (connection == null || connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Download failed for $code: response code ${connection?.responseCode}")
                return@withContext false
            }

            val totalBytes = connection.contentLength.toLong()
            inputStream = connection.inputStream
            outputStream = FileOutputStream(tempFile)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalDownloaded = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalDownloaded += bytesRead
                if (totalBytes > 0) {
                    onProgress(totalDownloaded.toFloat() / totalBytes)
                }
            }
            outputStream.flush()

            if (tempFile.exists() && tempFile.length() > 0) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
                // Persist downloaded language code
                val savedSet = prefs.getStringSet(PREF_KEY_DOWNLOADED_OCR_LANGS, emptySet())?.toMutableSet() ?: mutableSetOf()
                savedSet.add(code)
                prefs.edit().putStringSet(PREF_KEY_DOWNLOADED_OCR_LANGS, savedSet).apply()
                return@withContext true
            }
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading language $code", e)
            if (tempFile.exists()) tempFile.delete()
            return@withContext false
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            try { outputStream?.close() } catch (_: Exception) {}
            connection?.disconnect()
        }
    }

    /**
     * Preprocesses a page bitmap using Leptonica before feeding into Tesseract:
     * 1. Deskew correction
     * 2. Adaptive threshold binarization & contrast enhancement
     * 3. Noise reduction
     */
    private fun preprocessBitmapWithLeptonica(bitmap: Bitmap): Pix {
        val originalPix = ReadFile.readBitmap(bitmap)
        return try {
            // Find and correct skew angle
            val skewAngle = Skew.findSkew(originalPix)
            val deskewedPix = if (Math.abs(skewAngle) > 0.4f && Math.abs(skewAngle) < 45.0f) {
                val rotated = Rotate.rotate(originalPix, -skewAngle)
                originalPix.recycle()
                rotated
            } else {
                originalPix
            }

            // Otsu Adaptive Thresholding / Binarization for crisp text extraction
            val binarizedPix = try {
                val binarized = Binarize.otsuAdaptiveThreshold(deskewedPix, 32, 32, 0, 0, 0.1f)
                deskewedPix.recycle()
                binarized
            } catch (e: Exception) {
                Log.w(TAG, "Otsu threshold fallback to grayscale", e)
                deskewedPix
            }

            binarizedPix
        } catch (e: Exception) {
            Log.w(TAG, "Leptonica preprocessing fallback to original", e)
            originalPix
        }
    }

    /**
     * Runs on-device text recognition on a single bitmap and returns the recognized text.
     */
    suspend fun recognizeText(bitmap: Bitmap, language: String = DEFAULT_OCR_LANG): String = withContext(Dispatchers.IO) {
        ensureTessdataInitialized()

        // Resolve valid installed languages from combined string (e.g. "eng+ben")
        val requestedLangs = language.split("+").filter { it.isNotBlank() }
        val availableLangs = requestedLangs.filter { isLanguageAvailable(it) }
        val effectiveLang = if (availableLangs.isNotEmpty()) availableLangs.joinToString("+") else "eng"

        val tess = TessBaseAPI()
        var processedPix: Pix? = null
        try {
            val initialized = tess.init(baseDir.absolutePath, effectiveLang)
            if (!initialized) {
                Log.e(TAG, "Failed to initialize TessBaseAPI with datapath=${baseDir.absolutePath}, lang=$effectiveLang")
                return@withContext ""
            }

            processedPix = preprocessBitmapWithLeptonica(bitmap)
            tess.setImage(processedPix)
            val result = tess.utF8Text ?: ""
            result.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error during OCR text recognition", e)
            ""
        } finally {
            try { processedPix?.recycle() } catch (_: Exception) {}
            try {
                tess.recycle()
            } catch (_: Exception) {}
        }
    }

    /**
     * Extracts text from every page of a PDF file, reporting progress (0f..1f) as it goes.
     * Renders each page to a bitmap via Android's native PdfRenderer, applies Leptonica preprocessing,
     * and runs Tesseract OCR.
     */
    suspend fun extractTextFromPdf(
        pdfFile: File,
        language: String = DEFAULT_OCR_LANG,
        onProgress: (Float) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        if (!pdfFile.exists() || pdfFile.length() == 0L) {
            return@withContext "No text was detected in this document."
        }

        ensureTessdataInitialized()

        val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val pageCount = renderer.pageCount
        val builder = StringBuilder()

        val requestedLangs = language.split("+").filter { it.isNotBlank() }
        val availableLangs = requestedLangs.filter { isLanguageAvailable(it) }
        val effectiveLang = if (availableLangs.isNotEmpty()) availableLangs.joinToString("+") else "eng"

        val tess = TessBaseAPI()
        val initialized = tess.init(baseDir.absolutePath, effectiveLang)
        if (!initialized) {
            renderer.close()
            pfd.close()
            return@withContext "OCR engine initialization failed for language: $effectiveLang"
        }

        try {
            if (pageCount == 0) {
                return@withContext "No text was detected in this document."
            }

            for (index in 0 until pageCount) {
                val page = renderer.openPage(index)
                val targetWidth = (page.width * 2).coerceAtLeast(800)
                val targetHeight = (page.height * 2).coerceAtLeast(1100)

                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                var processedPix: Pix? = null
                val pageText = try {
                    processedPix = preprocessBitmapWithLeptonica(bitmap)
                    tess.setImage(processedPix)
                    tess.utF8Text ?: ""
                } catch (e: Exception) {
                    Log.e(TAG, "Error OCRing page $index", e)
                    ""
                } finally {
                    try { processedPix?.recycle() } catch (_: Exception) {}
                    bitmap.recycle()
                }

                if (pageText.isNotBlank()) {
                    if (builder.isNotEmpty()) builder.append("\n\n--- Page ${index + 1} ---\n\n")
                    builder.append(pageText.trim())
                }

                onProgress((index + 1).toFloat() / pageCount)
            }
        } finally {
            try { tess.recycle() } catch (_: Exception) {}
            renderer.close()
            pfd.close()
        }

        builder.toString().ifBlank { "No text was detected in this document." }
    }

    fun close() {
        // TessBaseAPI instances are recycled per operation.
    }
}

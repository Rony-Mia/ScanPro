package com.example.data

import android.content.Context
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Lightweight, zero-dependency OpenXML (.docx) generation and PDF-to-Word conversion engine.
 * Avoids Apache POI (which causes runtime crashes/ProGuard issues on Android).
 *
 * Employs a robust two-stage extraction strategy:
 * 1. Born-Digital PDF: Direct text layer extraction via PDFBox [PDFTextStripper] (100% accurate Unicode extraction).
 * 2. Scanned / Image PDF: Automatic fallback to [OcrEngine] (offline Tesseract with bundled English & Bengali models).
 *
 * Includes legacy non-Unicode (Bijoy/Sutonny ANSI glyph) detection to prevent corrupted output.
 */
object DocxEngine {

    private const val TAG = "DocxEngine"

    enum class ExtractionMethod(val displayName: String) {
        TEXT_LAYER("Direct Text Layer (Born-Digital PDF)"),
        OCR_FALLBACK("Tesseract OCR Fallback (Scanned / Image PDF)")
    }

    data class DocxConversionResult(
        val outputFile: File,
        val extractionMethod: ExtractionMethod,
        val warnings: List<String> = emptyList(),
        val paragraphCount: Int = 0,
        val characterCount: Int = 0
    )

    class LegacyFontException(message: String) : Exception(message)

    data class DocxFontOption(
        val name: String,
        val displayName: String,
        val category: String,
        val isComplexScript: Boolean,
        val isLegacy: Boolean = false,
        val warning: String? = null
    )

    val SUPPORTED_FONTS = listOf(
        DocxFontOption(
            name = "SolaimanLipi",
            displayName = "SolaimanLipi (Recommended)",
            category = "Unicode Bengali",
            isComplexScript = true
        ),
        DocxFontOption(
            name = "Kalpurush",
            displayName = "Kalpurush",
            category = "Unicode Bengali",
            isComplexScript = true
        ),
        DocxFontOption(
            name = "Nikosh",
            displayName = "Nikosh (Government Standard)",
            category = "Unicode Bengali",
            isComplexScript = true
        ),
        DocxFontOption(
            name = "SutonnyMJ",
            displayName = "Sutonny MJ (Legacy Bijoy ANSI)",
            category = "Legacy Compatibility",
            isComplexScript = true,
            isLegacy = true,
            warning = "Legacy visual font — edit করলে বা অন্য software-এ খুললে ভাঙতে পারে, শুধু পুরনো সিস্টেমের সাথে compatibility-র জন্য।"
        ),
        DocxFontOption(
            name = "Calibri",
            displayName = "Calibri (Modern Sans)",
            category = "English / Standard",
            isComplexScript = false
        ),
        DocxFontOption(
            name = "Times New Roman",
            displayName = "Times New Roman (Classic Serif)",
            category = "English / Standard",
            isComplexScript = false
        ),
        DocxFontOption(
            name = "Arial",
            displayName = "Arial (Clean Sans)",
            category = "English / Standard",
            isComplexScript = false
        )
    )

    /**
     * Checks if the extracted text looks like legacy non-Unicode Bijoy / Sutonny ANSI byte representation
     * or contains heavily broken non-Unicode glyphs without proper CMap mapping.
     */
    fun isLikelyLegacyNonUnicodeBengali(text: String): Boolean {
        if (text.isBlank()) return false

        // Common Bijoy / ANSI Sutonny font character sequences when decoded as ANSI/Latin-1
        val bijoySignatures = listOf(
            "Avgv", "evsj", "wewf", "cÖwZ", "Av‡", "eQi", "RvZxq", "‡`k",
            "‡h", "‡m", "GK", "Avw", "Bw", "Dcw", "GKw", "Avcb", "mKj",
            "cÖK", "cÖk", "Aby", "wef", "cÖ_g", "¯^v", "wb‡", "‡ev", "gva"
        )
        val matchCount = bijoySignatures.count { text.contains(it) }
        val containsUnicodeBengali = text.any { it in '\u0980'..'\u09FF' }

        // If there are Bijoy signatures but no or minimal real Unicode Bengali characters
        if (matchCount >= 2 && !containsUnicodeBengali) {
            return true
        }

        // Check for high density of legacy glyph markers (dagger, permille, scaron, etc.)
        val legacyGlyphChars = setOf(
            '‡', 'ˆ', '‰', 'Š', '‹', 'Œ', 'Ž', '‘', '’', '“', '”', '•', '–', '—',
            '˜', '™', 'š', '›', 'œ', 'ž', 'Ÿ', '¢', '£', '¤', '¥', '¦', '§', '¨',
            '©', 'ª', '«', '¬', '®', '¯', '°', '±', '²', '³', '´', 'µ', '¶', '·',
            '¸', '¹', 'º', '»', '¼', '½', '¾', '¿', 'À', 'Á', 'Â', 'Ã', 'Ä', 'Å'
        )
        val legacyCharCount = text.count { it in legacyGlyphChars }
        if (text.length > 50 && (legacyCharCount.toDouble() / text.length) > 0.15 && !containsUnicodeBengali) {
            return true
        }

        return false
    }

    /**
     * Converts a PDF file to a real, valid Microsoft Word (.docx) document using a two-stage extraction strategy:
     * 1. Direct PDF text layer extraction via PDFTextStripper (for born-digital PDFs).
     * 2. Tesseract OCR engine fallback (for scanned/image-based PDFs).
     */
    suspend fun convertPdfToDocx(
        context: Context,
        pdfFile: File,
        outputFile: File,
        fontFamily: String,
        isComplexScript: Boolean,
        ocrEngine: OcrEngine,
        ocrLanguage: String = "eng+ben"
    ): DocxConversionResult = withContext(Dispatchers.IO) {
        if (!pdfFile.exists() || pdfFile.length() == 0L) {
            throw IllegalArgumentException("Source PDF file does not exist or is empty.")
        }

        var textLayerExtracted = ""
        var extractionMethod = ExtractionMethod.TEXT_LAYER
        val warnings = mutableListOf<String>()

        // 1. Try PDFBox PDFTextStripper for direct Unicode text layer
        try {
            val pdDoc = PDDocument.load(pdfFile)
            val stripper = PDFTextStripper().apply {
                sortByPosition = true
            }
            textLayerExtracted = stripper.getText(pdDoc) ?: ""
            pdDoc.close()
        } catch (e: Exception) {
            Log.w(TAG, "PDFTextStripper extraction encountered an issue", e)
            textLayerExtracted = ""
        }

        val cleanedText = textLayerExtracted.trim()

        // Check if extracted text is legacy non-Unicode Bijoy/Sutonny
        if (cleanedText.length >= 15 && isLikelyLegacyNonUnicodeBengali(cleanedText)) {
            throw LegacyFontException(
                "এই PDF-টা সম্ভবত পুরনো (non-Unicode) বাংলা ফন্টে তৈরি, তাই সঠিক টেক্সট বের করা সম্ভব হচ্ছে না।"
            )
        }

        var finalText: String
        if (cleanedText.length >= 15) {
            // High confidence text layer
            finalText = cleanedText
            extractionMethod = ExtractionMethod.TEXT_LAYER
        } else {
            // Scanned PDF fallback to OCR
            extractionMethod = ExtractionMethod.OCR_FALLBACK
            warnings.add("Scanned PDF detected: Text was extracted using offline Tesseract OCR. Accuracy depends on document scan quality.")
            finalText = ocrEngine.extractTextFromPdf(pdfFile, ocrLanguage).trim()
            if (finalText.isBlank() || finalText == "No text was detected in this document.") {
                finalText = "No readable text could be extracted from this document."
                warnings.add("No significant text was recognized in the document.")
            }
        }

        // Split text into paragraphs (handling double newlines and logical paragraph breaks)
        val rawParagraphs = finalText.split(Regex("(?:\r\n|\r|\n){2,}"))
        val paragraphs = if (rawParagraphs.isNotEmpty()) {
            rawParagraphs.flatMap { block ->
                block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            }
        } else {
            listOf(finalText)
        }

        val effectiveParagraphs = if (paragraphs.isEmpty()) listOf("No text extracted.") else paragraphs

        writeSimpleDocx(
            paragraphs = effectiveParagraphs,
            fontFamily = fontFamily,
            isComplexScript = isComplexScript,
            outputFile = outputFile
        )

        val totalChars = effectiveParagraphs.sumOf { it.length }

        DocxConversionResult(
            outputFile = outputFile,
            extractionMethod = extractionMethod,
            warnings = warnings,
            paragraphCount = effectiveParagraphs.size,
            characterCount = totalChars
        )
    }

    /**
     * Writes paragraphs into a standard, valid Microsoft Office Open XML (.docx) ZIP structure.
     * Compatible with Microsoft Word, Google Docs, LibreOffice, and WPS Office.
     */
    fun writeSimpleDocx(
        paragraphs: List<String>,
        fontFamily: String,
        isComplexScript: Boolean,
        outputFile: File
    ) {
        val safeFont = xmlEscape(fontFamily)

        outputFile.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            // 1. [Content_Types].xml
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write(generateContentTypesXml().toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            // 2. _rels/.rels
            zos.putNextEntry(ZipEntry("_rels/.rels"))
            zos.write(generateRootRelsXml().toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            // 3. word/_rels/document.xml.rels
            zos.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
            zos.write(generateDocumentRelsXml().toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            // 4. word/settings.xml
            zos.putNextEntry(ZipEntry("word/settings.xml"))
            zos.write(generateSettingsXml().toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            // 5. word/styles.xml
            zos.putNextEntry(ZipEntry("word/styles.xml"))
            zos.write(generateStylesXml(safeFont, isComplexScript).toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            // 6. word/document.xml
            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(generateDocumentXml(paragraphs, safeFont, isComplexScript).toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
        }
    }

    private fun generateContentTypesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
  <Override PartName="/word/settings.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml"/>
</Types>"""
    }

    private fun generateRootRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""
    }

    private fun generateDocumentRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/settings" Target="settings.xml"/>
</Relationships>"""
    }

    private fun generateSettingsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:settings xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:zoom w:percent="100"/>
  <w:defaultTabStop w:val="720"/>
</w:settings>"""
    }

    private fun generateStylesXml(fontFamily: String, isComplexScript: Boolean): String {
        val csAttr = if (isComplexScript) """ w:cs="$fontFamily"""" else """ w:cs="SolaimanLipi""""
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults>
    <w:rPrDefault>
      <w:rPr>
        <w:rFonts w:ascii="$fontFamily" w:hAnsi="$fontFamily"$csAttr/>
        <w:sz w:val="24"/>
        <w:szCs w:val="24"/>
        <w:lang w:val="en-US" w:bidi="bn-BD"/>
      </w:rPr>
    </w:rPrDefault>
  </w:docDefaults>
</w:styles>"""
    }

    private fun generateDocumentXml(
        paragraphs: List<String>,
        fontFamily: String,
        isComplexScript: Boolean
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        sb.append("<w:body>")

        val rFontsTag = if (isComplexScript) {
            """<w:rFonts w:ascii="$fontFamily" w:hAnsi="$fontFamily" w:cs="$fontFamily"/>"""
        } else {
            """<w:rFonts w:ascii="$fontFamily" w:hAnsi="$fontFamily"/>"""
        }

        for (p in paragraphs) {
            val escapedText = xmlEscape(p)
            sb.append("<w:p>")
            sb.append("<w:pPr>")
            sb.append("""<w:spacing w:after="160" w:line="280" w:lineRule="auto"/>""")
            sb.append("</w:pPr>")
            sb.append("<w:r>")
            sb.append("<w:rPr>")
            sb.append(rFontsTag)
            sb.append("""<w:sz w:val="24"/>""")
            if (isComplexScript) {
                sb.append("""<w:szCs w:val="24"/>""")
            }
            sb.append("</w:rPr>")
            sb.append("""<w:t xml:space="preserve">$escapedText</w:t>""")
            sb.append("</w:r>")
            sb.append("</w:p>")
        }

        // Section properties: standard A4 portrait with 1 inch (1440 twip) margins
        sb.append("<w:sectPr>")
        sb.append("""<w:pgSz w:w="11906" w:h="16838"/>""")
        sb.append("""<w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="720" w:footer="720" w:gutter="0"/>""")
        sb.append("</w:sectPr>")

        sb.append("</w:body>")
        sb.append("</w:document>")
        return sb.toString()
    }

    /**
     * Escapes standard XML entities and discards non-printable control characters.
     */
    fun xmlEscape(input: String): String {
        val sb = StringBuilder(input.length)
        for (c in input) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> {
                    val code = c.code
                    if (code == 0x9 || code == 0xA || code == 0xD ||
                        (code in 0x20..0xD7FF) ||
                        (code in 0xE000..0xFFFD)
                    ) {
                        sb.append(c)
                    }
                }
            }
        }
        return sb.toString()
    }
}

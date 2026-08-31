package com.example.data

import com.example.model.DocCategory
import com.example.model.DocFormat
import com.example.model.DocumentItem
import com.example.model.PageFilter
import com.example.model.ScannedPage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists the document library to a JSON file in app-private storage so that documents
 * (scanned, imported, merged, split, etc.) survive an app restart instead of resetting to
 * the 5 hardcoded sample documents every time the process is killed.
 */
object DocumentStore {

    private const val FILE_NAME = "library.json"

    private fun storeFile(documentsDir: File): File = File(documentsDir, FILE_NAME)

    fun save(documentsDir: File, documents: List<DocumentItem>) {
        try {
            val array = JSONArray()
            for (doc in documents) {
                array.put(documentToJson(doc))
            }
            storeFile(documentsDir).writeText(array.toString())
        } catch (e: Exception) {
            // Persistence is a best-effort convenience; never crash the app over it.
        }
    }

    /** Returns null if there is no saved library yet (first ever launch), so the caller can fall back to samples. */
    fun load(documentsDir: File): List<DocumentItem>? {
        val file = storeFile(documentsDir)
        if (!file.exists()) return null
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { i -> documentFromJson(array.getJSONObject(i)) }
        } catch (e: Exception) {
            null
        }
    }

    private fun documentToJson(doc: DocumentItem): JSONObject = JSONObject().apply {
        put("id", doc.id)
        put("title", doc.title)
        put("date", doc.date)
        put("time", doc.time)
        put("pageCount", doc.pageCount)
        put("format", doc.format.name)
        put("fileSize", doc.fileSize)
        put("thumbnailRes", doc.thumbnailRes)
        put("thumbnailUri", doc.thumbnailUri)
        put("category", doc.category.name)
        put("pages", JSONArray().apply { doc.pages.forEach { put(pageToJson(it)) } })
        put("isProtected", doc.isProtected)
        put("password", doc.password)
        put("watermark", doc.watermark)
        put("isCompressed", doc.isCompressed)
        put("ocrText", doc.ocrText)
        put("filePath", doc.filePath)
    }

    private fun documentFromJson(o: JSONObject): DocumentItem = DocumentItem(
        id = o.getString("id"),
        title = o.getString("title"),
        date = o.optString("date", ""),
        time = o.optString("time", ""),
        pageCount = o.optInt("pageCount", 1),
        format = runCatching { DocFormat.valueOf(o.getString("format")) }.getOrDefault(DocFormat.PDF),
        fileSize = o.optString("fileSize", ""),
        thumbnailRes = o.optInt("thumbnailRes"),
        thumbnailUri = o.optStringOrNull("thumbnailUri"),
        category = runCatching { DocCategory.valueOf(o.getString("category")) }.getOrDefault(DocCategory.TODAY),
        pages = o.optJSONArray("pages")?.let { arr ->
            (0 until arr.length()).map { i -> pageFromJson(arr.getJSONObject(i)) }
        } ?: emptyList(),
        isProtected = o.optBoolean("isProtected", false),
        password = o.optString("password", ""),
        watermark = o.optString("watermark", ""),
        isCompressed = o.optBoolean("isCompressed", false),
        ocrText = o.optString("ocrText", ""),
        filePath = o.optStringOrNull("filePath")
    )

    private fun pageToJson(page: ScannedPage): JSONObject = JSONObject().apply {
        put("id", page.id)
        put("pageNumber", page.pageNumber)
        put("drawableRes", page.drawableRes)
        put("imageUri", page.imageUri)
        put("rotationAngle", page.rotationAngle)
        put("filter", page.filter.name)
        put("cropTop", page.cropTop)
        put("cropBottom", page.cropBottom)
        put("cropLeft", page.cropLeft)
        put("cropRight", page.cropRight)
    }

    private fun pageFromJson(o: JSONObject): ScannedPage = ScannedPage(
        id = o.getString("id"),
        pageNumber = o.optInt("pageNumber", 1),
        drawableRes = o.optInt("drawableRes"),
        imageUri = o.optStringOrNull("imageUri"),
        rotationAngle = o.optDouble("rotationAngle", 0.0).toFloat(),
        filter = runCatching { PageFilter.valueOf(o.getString("filter")) }.getOrDefault(PageFilter.ORIGINAL),
        cropTop = o.optDouble("cropTop", 0.05).toFloat(),
        cropBottom = o.optDouble("cropBottom", 0.95).toFloat(),
        cropLeft = o.optDouble("cropLeft", 0.05).toFloat(),
        cropRight = o.optDouble("cropRight", 0.95).toFloat()
    )

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null
}

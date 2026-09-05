package com.example.data

import com.example.model.DocCategory
import com.example.model.DocFormat
import com.example.model.DocumentItem
import com.example.model.PageFilter
import com.example.model.ScannedPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Plain JVM unit tests -- DocumentStore only touches java.io.File and org.json, so no
 * Android framework or Robolectric is needed here.
 */
class DocumentStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun sampleDoc(id: String = "doc-1", password: String = "") = DocumentItem(
        id = id,
        title = "Invoice.pdf",
        date = "Jan 1",
        time = "10:00 AM",
        pageCount = 2,
        format = DocFormat.PDF,
        fileSize = "1.2 MB",
        category = DocCategory.TODAY,
        pages = listOf(
            ScannedPage(id = "page-1", pageNumber = 1, filter = PageFilter.ENHANCED, cropTop = 0.05f, cropBottom = 0.9f)
        ),
        isProtected = password.isNotEmpty(),
        password = password,
        watermark = "",
        isCompressed = false,
        ocrText = "Sample extracted text",
        filePath = "/some/path/Invoice.pdf"
    )

    @Test
    fun `save then load round-trips document fields correctly`() {
        val dir = tempFolder.newFolder("documents")
        val original = sampleDoc()

        DocumentStore.save(dir, listOf(original))
        val loaded = DocumentStore.load(dir)

        assertTrue(loaded != null)
        val doc = loaded!!.first()
        assertEquals(original.id, doc.id)
        assertEquals(original.title, doc.title)
        assertEquals(original.pageCount, doc.pageCount)
        assertEquals(original.format, doc.format)
        assertEquals(original.ocrText, doc.ocrText)
        assertEquals(1, doc.pages.size)
        assertEquals(PageFilter.ENHANCED, doc.pages[0].filter)
        assertEquals(0.05f, doc.pages[0].cropTop, 0.0001f)
    }

    @Test
    fun `load returns null when no library file exists yet`() {
        val dir = tempFolder.newFolder("empty_documents")
        assertNull(DocumentStore.load(dir))
    }

    @Test
    fun `password is never written into the saved JSON file, even if set on the DocumentItem`() {
        val dir = tempFolder.newFolder("secure_documents")
        val secret = "MySecretPassword123"
        val protectedDoc = sampleDoc(password = secret)

        DocumentStore.save(dir, listOf(protectedDoc))

        val rawFileContents = java.io.File(dir, "library.json").readText()
        assertFalse(
            "The raw password must never appear in the persisted JSON file",
            rawFileContents.contains(secret)
        )
    }

    @Test
    fun `loading a document never returns a non-blank password, even if it somehow existed on disk`() {
        val dir = tempFolder.newFolder("legacy_documents")
        // Simulate an old library.json written before the security fix, which had a
        // plaintext password field -- loading it today must never surface that value.
        val legacyJson = """
            [{
                "id": "doc-legacy",
                "title": "Old.pdf",
                "isProtected": true,
                "password": "leaked-old-password"
            }]
        """.trimIndent()
        java.io.File(dir, "library.json").writeText(legacyJson)

        val loaded = DocumentStore.load(dir)
        assertEquals("", loaded!!.first().password)
    }
}

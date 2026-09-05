package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Plain JVM unit test -- no Android framework or Robolectric needed since
 * formatFileSize is pure Kotlin logic.
 */
class PdfEngineFileSizeTest {

    @Test
    fun `zero or negative bytes formats as a small default`() {
        assertEquals("0.1 MB", PdfEngine.formatFileSize(0L))
        assertEquals("0.1 MB", PdfEngine.formatFileSize(-100L))
    }

    @Test
    fun `sub-megabyte sizes format in KB`() {
        // 200 KB = 204800 bytes
        assertEquals("200 KB", PdfEngine.formatFileSize(204_800L))
    }

    @Test
    fun `megabyte-and-above sizes format in MB with one decimal`() {
        // 2.5 MB = 2.5 * 1024 * 1024 bytes
        val bytes = (2.5 * 1024 * 1024).toLong()
        assertEquals("2.5 MB", PdfEngine.formatFileSize(bytes))
    }

    @Test
    fun `exactly one megabyte formats as 1_0 MB not KB`() {
        val oneMb = 1024L * 1024L
        assertEquals("1.0 MB", PdfEngine.formatFileSize(oneMb))
    }
}

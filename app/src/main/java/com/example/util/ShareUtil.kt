package com.example.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.model.DocFormat
import com.example.model.DocumentItem
import java.io.File

/**
 * Real Android sharing via FileProvider + ACTION_SEND, replacing the old
 * fake share buttons that only showed a "Sharing..." toast and never
 * actually opened the system share sheet.
 */
object ShareUtil {

    /** Shares a document's real file (PDF or image) via the system share sheet. */
    fun shareDocument(context: Context, document: DocumentItem) {
        val path = document.filePath
        if (path.isNullOrBlank()) {
            android.widget.Toast.makeText(
                context,
                "This document has no file to share yet",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        val file = File(path)
        if (!file.exists() || file.length() == 0L) {
            android.widget.Toast.makeText(
                context,
                "File not found on device",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mimeType = when (document.format) {
            DocFormat.PDF -> "application/pdf"
            DocFormat.DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            DocFormat.OCR -> "text/plain"
            DocFormat.JPG, DocFormat.PNG -> "image/*"
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${document.title}"))
    }

    /** Shares plain text (used for OCR-extracted text) via the system share sheet. */
    fun shareText(context: Context, text: String, subject: String = "Extracted text") {
        if (text.isBlank()) {
            android.widget.Toast.makeText(context, "No text to share", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share text"))
    }

    /**
     * Shares ScanPro APK direct download link via the standard Android share sheet (Intent.ACTION_SEND).
     */
    fun shareApp(context: Context, customApkUrl: String? = null) {
        val downloadUrl = customApkUrl ?: Constants.LATEST_APK_DOWNLOAD_URL
        val message = "Check out ScanPro — scan, merge, and manage documents, free and offline!\nDownload: $downloadUrl"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "ScanPro App")
            putExtra(Intent.EXTRA_TEXT, message)
        }
        context.startActivity(Intent.createChooser(intent, Constants.SHARE_CHOOSER_TITLE))
    }
}

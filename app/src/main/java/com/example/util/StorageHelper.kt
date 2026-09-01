package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream

object StorageHelper {

    private const val TAG = "StorageHelper"

    data class SaveResult(
        val success: Boolean,
        val displayPath: String,
        val uri: Uri? = null,
        val errorMessage: String? = null
    )

    /**
     * Returns the human-readable folder name currently configured for saves.
     */
    fun getCurrentSaveLocationDisplay(context: Context): String {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val mode = prefs.getString(Constants.PREF_KEY_SAVE_LOCATION_MODE, Constants.SAVE_LOCATION_MODE_DEFAULT)
        if (mode == Constants.SAVE_LOCATION_MODE_CUSTOM) {
            val customName = prefs.getString(Constants.PREF_KEY_CUSTOM_FOLDER_NAME, null)
            if (!customName.isNullOrBlank()) {
                return customName
            }
        }
        return Constants.DEFAULT_SAVE_LOCATION_NAME
    }

    /**
     * Checks whether the current save mode is set to the default "Documents/ScanPro".
     */
    fun isDefaultLocation(context: Context): Boolean {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val mode = prefs.getString(Constants.PREF_KEY_SAVE_LOCATION_MODE, Constants.SAVE_LOCATION_MODE_DEFAULT)
        return mode != Constants.SAVE_LOCATION_MODE_CUSTOM
    }

    /**
     * Sets the save location to the public default "Documents/ScanPro".
     */
    fun setDefaultSaveLocation(context: Context) {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(Constants.PREF_KEY_SAVE_LOCATION_MODE, Constants.SAVE_LOCATION_MODE_DEFAULT)
            .remove(Constants.PREF_KEY_CUSTOM_FOLDER_URI)
            .remove(Constants.PREF_KEY_CUSTOM_FOLDER_NAME)
            .apply()
    }

    /**
     * Sets a custom folder picked via SAF (ACTION_OPEN_DOCUMENT_TREE) and takes persistable URI permission.
     */
    fun setCustomSaveLocation(context: Context, treeUri: Uri): String {
        try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to take persistable URI permission: ${e.message}")
        }

        val folderDoc = DocumentFile.fromTreeUri(context, treeUri)
        val folderName = folderDoc?.name ?: run {
            try {
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                docId.substringAfterLast(":")
            } catch (e: Exception) {
                "Custom Folder"
            }
        }

        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(Constants.PREF_KEY_SAVE_LOCATION_MODE, Constants.SAVE_LOCATION_MODE_CUSTOM)
            .putString(Constants.PREF_KEY_CUSTOM_FOLDER_URI, treeUri.toString())
            .putString(Constants.PREF_KEY_CUSTOM_FOLDER_NAME, folderName)
            .apply()

        return folderName
    }

    /**
     * Saves or copies [sourceFile] to the user's chosen public destination (MediaStore or SAF tree folder).
     */
    fun saveFileToUserStorage(
        context: Context,
        sourceFile: File,
        targetFileName: String,
        mimeType: String = "application/pdf"
    ): SaveResult {
        if (!sourceFile.exists()) {
            return SaveResult(false, targetFileName, errorMessage = "Source file does not exist")
        }

        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val mode = prefs.getString(Constants.PREF_KEY_SAVE_LOCATION_MODE, Constants.SAVE_LOCATION_MODE_DEFAULT)
        val customUriString = prefs.getString(Constants.PREF_KEY_CUSTOM_FOLDER_URI, null)
        val customFolderName = prefs.getString(Constants.PREF_KEY_CUSTOM_FOLDER_NAME, "Custom Folder") ?: "Custom Folder"

        // 1. Try Custom SAF folder if selected
        if (mode == Constants.SAVE_LOCATION_MODE_CUSTOM && !customUriString.isNullOrBlank()) {
            try {
                val treeUri = Uri.parse(customUriString)
                val targetDir = DocumentFile.fromTreeUri(context, treeUri)
                if (targetDir != null && targetDir.canWrite()) {
                    // Check if file with same name already exists in target dir
                    val existingFile = targetDir.findFile(targetFileName)
                    val targetDoc = existingFile ?: targetDir.createFile(mimeType, targetFileName)

                    if (targetDoc != null) {
                        context.contentResolver.openOutputStream(targetDoc.uri)?.use { outStream ->
                            sourceFile.inputStream().use { inStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                        val finalDisplayName = "$customFolderName/$targetFileName"
                        return SaveResult(true, finalDisplayName, targetDoc.uri)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed writing to custom SAF folder, falling back to default", e)
            }
        }

        // 2. Default public storage: Documents/ScanPro
        return saveToDefaultPublicStorage(context, sourceFile, targetFileName, mimeType)
    }

    /**
     * Saves raw text content (e.g. OCR export) as a file in the user's chosen public destination.
     */
    fun saveTextToUserStorage(
        context: Context,
        textContent: String,
        targetFileName: String,
        mimeType: String = "text/plain"
    ): SaveResult {
        val tempFile = File(context.cacheDir, "temp_export_${System.currentTimeMillis()}_$targetFileName")
        return try {
            FileOutputStream(tempFile).use { out ->
                out.write(textContent.toByteArray(Charsets.UTF_8))
            }
            saveFileToUserStorage(context, tempFile, targetFileName, mimeType)
        } catch (e: Exception) {
            SaveResult(false, targetFileName, errorMessage = e.localizedMessage)
        } finally {
            try { tempFile.delete() } catch (_: Exception) {}
        }
    }

    /**
     * Saves to public Documents/ScanPro using MediaStore (Android 10+) or public directory (legacy).
     */
    private fun saveToDefaultPublicStorage(
        context: Context,
        sourceFile: File,
        targetFileName: String,
        mimeType: String
    ): SaveResult {
        val displayPath = "${Constants.DEFAULT_SAVE_LOCATION_NAME}/$targetFileName"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, targetFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/ScanPro")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val itemUri = context.contentResolver.insert(collection, contentValues)

                if (itemUri != null) {
                    context.contentResolver.openOutputStream(itemUri)?.use { outStream ->
                        sourceFile.inputStream().use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }

                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(itemUri, contentValues, null, null)

                    return SaveResult(true, displayPath, itemUri)
                }
            } catch (e: Exception) {
                Log.e(TAG, "MediaStore save failed, trying direct file write", e)
            }
        }

        // Fallback for API < 29 or if MediaStore insert failed
        try {
            val publicDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val scanProDir = File(publicDocs, "ScanPro").apply { mkdirs() }
            val destFile = File(scanProDir, targetFileName)
            sourceFile.copyTo(destFile, overwrite = true)
            return SaveResult(true, displayPath, Uri.fromFile(destFile))
        } catch (e: Exception) {
            Log.e(TAG, "Direct public file write failed", e)
            return SaveResult(true, displayPath, Uri.fromFile(sourceFile))
        }
    }
}

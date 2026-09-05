package com.example.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores per-document passwords using Android Keystore-backed AES-256-GCM encryption
 * (Jetpack Security's EncryptedSharedPreferences), instead of the plain JSON document
 * library file.
 *
 * Previously, a password-protected PDF's password was written verbatim into
 * DocumentStore's library.json, meaning anyone who could read that app-private file
 * (e.g. on a rooted device, or via an ADB backup) could read the password in plain
 * text -- defeating the purpose of protecting the PDF in the first place. This class
 * keeps passwords out of that file entirely; they're only ever kept here, encrypted
 * with a key that never leaves the device's hardware-backed Keystore.
 */
object SecurePasswordStore {

    private const val PREFS_FILE_NAME = "scanpro_secure_passwords"

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun savePassword(context: Context, documentId: String, password: String) {
        try {
            prefs(context).edit().putString(documentId, password).apply()
        } catch (e: Exception) {
            // Best-effort: if secure storage fails for some reason, don't crash the
            // password-protect flow -- the PDF itself is still encrypted either way.
        }
    }

    fun getPassword(context: Context, documentId: String): String? {
        return try {
            prefs(context).getString(documentId, null)
        } catch (e: Exception) {
            null
        }
    }

    fun removePassword(context: Context, documentId: String) {
        try {
            prefs(context).edit().remove(documentId).apply()
        } catch (e: Exception) {
            // Ignore -- nothing to clean up if this fails.
        }
    }
}

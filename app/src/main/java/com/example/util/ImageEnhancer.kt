package com.example.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.googlecode.leptonica.android.Binarize
import com.googlecode.leptonica.android.Pix
import com.googlecode.leptonica.android.ReadFile
import com.googlecode.leptonica.android.Rotate
import com.googlecode.leptonica.android.Skew
import com.googlecode.leptonica.android.WriteFile
import kotlin.math.abs

/**
 * Image enhancement utility providing auto-deskew, contrast boosting / text binarization,
 * and sharpening passes for scanned pages.
 */
object ImageEnhancer {

    /**
     * Enhances a scanned page [bitmap]:
     * 1. Auto-deskew using Leptonica Skew detection
     * 2. Text clarity / contrast boost (adaptive binarization / thresholding enhancement)
     * 3. 3x3 Unsharp-mask sharpening convolution pass
     */
    fun enhancePage(bitmap: Bitmap): Bitmap {
        var currentBitmap = bitmap
        try {
            // 1. Auto-deskew via Leptonica
            var pix: Pix? = null
            try {
                pix = ReadFile.readBitmap(currentBitmap)
                val skewAngle = Skew.findSkew(pix)
                if (abs(skewAngle) > 0.4f && abs(skewAngle) < 45.0f) {
                    val rotatedPix = Rotate.rotate(pix, -skewAngle)
                    pix.recycle()
                    pix = rotatedPix
                    val deskewedBmp = WriteFile.writeBitmap(pix)
                    if (deskewedBmp != null) {
                        currentBitmap = deskewedBmp
                    }
                }
            } catch (_: Throwable) {
                // Continue with currentBitmap if deskew fails
            } finally {
                try { pix?.recycle() } catch (_: Throwable) {}
            }

            // 2. Text clarity / adaptive contrast boost
            val contrastBoosted = applyContrastBoost(currentBitmap)
            if (contrastBoosted !== currentBitmap && currentBitmap !== bitmap) {
                currentBitmap.recycle()
            }
            currentBitmap = contrastBoosted

            // 3. 3x3 Unsharp mask sharpening convolution
            val sharpened = applySharpenConvolution(currentBitmap)
            if (sharpened !== currentBitmap && currentBitmap !== bitmap) {
                currentBitmap.recycle()
            }
            return sharpened
        } catch (e: Exception) {
            return bitmap
        }
    }

    /**
     * Applies a contrast boost tailored for documents: deepens ink/text while whitening
     * off-white paper background.
     */
    private fun applyContrastBoost(src: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val cm = ColorMatrix()

        // Document contrast enhancement:
        // contrast factor ~ 1.30, black-point shift
        val contrast = 1.30f
        val translate = (-0.10f * 255f) * contrast
        val matrixArray = floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
        cm.set(matrixArray)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }

    /**
     * 3x3 unsharp-mask convolution over pixel array to crispen edges and letter glyphs.
     */
    private fun applySharpenConvolution(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        if (width < 3 || height < 3) return src

        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)
        val output = IntArray(width * height)

        // Unsharp-mask kernel:
        // [  0, -1,  0 ]
        // [ -1,  5, -1 ]
        // [  0, -1,  0 ]
        for (y in 1 until height - 1) {
            val row = y * width
            val prevRow = (y - 1) * width
            val nextRow = (y + 1) * width
            for (x in 1 until width - 1) {
                val c = pixels[row + x]
                val top = pixels[prevRow + x]
                val bottom = pixels[nextRow + x]
                val left = pixels[row + x - 1]
                val right = pixels[row + x + 1]

                val a = (c ushr 24) and 0xFF

                val cr = (c ushr 16) and 0xFF
                val cg = (c ushr 8) and 0xFF
                val cb = c and 0xFF

                val r = (cr * 5 - (((top ushr 16) and 0xFF) + ((bottom ushr 16) and 0xFF) + ((left ushr 16) and 0xFF) + ((right ushr 16) and 0xFF))).coerceIn(0, 255)
                val g = (cg * 5 - (((top ushr 8) and 0xFF) + ((bottom ushr 8) and 0xFF) + ((left ushr 8) and 0xFF) + ((right ushr 8) and 0xFF))).coerceIn(0, 255)
                val b = (cb * 5 - ((top and 0xFF) + (bottom and 0xFF) + (left and 0xFF) + (right and 0xFF))).coerceIn(0, 255)

                output[row + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        // Copy edge rows & columns
        for (x in 0 until width) {
            output[x] = pixels[x]
            output[(height - 1) * width + x] = pixels[(height - 1) * width + x]
        }
        for (y in 0 until height) {
            output[y * width] = pixels[y * width]
            output[y * width + (width - 1)] = pixels[y * width + (width - 1)]
        }

        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }
}

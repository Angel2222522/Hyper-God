package com.angel.hypergod.processing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.max

/**
 * Conservative offline scan cleanup. It normalises camera orientation and
 * increases contrast without inventing or discarding document content.
 */
object ScannerImageProcessor {
    /** Validates external camera output before it is retained or decoded by UI. */
    fun validateCapture(file: File) {
        require(file.isFile && file.length() in 1..MAX_CAPTURE_BYTES) {
            "Η φωτογραφία είναι κενή ή υπερβολικά μεγάλη."
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) {
            "Το αρχείο της κάμερας δεν είναι έγκυρη εικόνα."
        }
        require(bounds.outWidth <= MAX_CAPTURE_SIDE && bounds.outHeight <= MAX_CAPTURE_SIDE) {
            "Η φωτογραφία έχει υπερβολική ανάλυση."
        }
        require(bounds.outWidth.toLong() * bounds.outHeight <= MAX_CAPTURE_PIXELS) {
            "Η φωτογραφία είναι υπερβολικά μεγάλη."
        }
    }

    fun rotateQuarterTurn(input: File, output: File): File {
        val bitmap = decode(input)
        var working = bitmap
        try {
            val exifRotation = runCatching { ExifInterface(input.absolutePath).rotationDegrees }.getOrDefault(0)
            working = rotate(working, (exifRotation + 90) % 360)
            output.parentFile?.mkdirs()
            output.outputStream().use { stream ->
                require(working.compress(Bitmap.CompressFormat.JPEG, 94, stream)) {
                    "Δεν ήταν δυνατή η αποθήκευση της περιστραμμένης σελίδας."
                }
            }
            return output
        } finally {
            if (!working.isRecycled) working.recycle()
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    fun enhance(input: File, output: File, rotationDegrees: Int = 0): File {
        val bitmap = decode(input)
        var working = bitmap
        try {
            val exifRotation = runCatching { ExifInterface(input.absolutePath).rotationDegrees }.getOrDefault(0)
            val rotation = ((exifRotation + rotationDegrees) % 360 + 360) % 360
            if (rotation != 0) working = rotate(working, rotation)
            val enhanced = contrast(working, 1.14f)
            output.parentFile?.mkdirs()
            output.outputStream().use { stream ->
                require(enhanced.compress(Bitmap.CompressFormat.JPEG, 92, stream)) { "Δεν ήταν δυνατή η αποθήκευση της σαρωμένης σελίδας." }
            }
            enhanced.recycle()
            return output
        } finally {
            if (!working.isRecycled) working.recycle()
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun decode(file: File): Bitmap {
        validateCapture(file)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Δεν ήταν δυνατή η ανάγνωση της φωτογραφίας." }
        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > MAX_SIDE) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: error("Δεν ήταν δυνατή η αποκωδικοποίηση της φωτογραφίας.")
    }

    private fun rotate(source: Bitmap, degrees: Int): Bitmap {
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
        if (rotated !== source) source.recycle()
        return rotated
    }

    private fun contrast(source: Bitmap, factor: Float): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val offset = 128f * (1f - factor)
        val matrix = ColorMatrix(floatArrayOf(
            factor, 0f, 0f, 0f, offset,
            0f, factor, 0f, 0f, offset,
            0f, 0f, factor, 0f, offset,
            0f, 0f, 0f, 1f, 0f
        ))
        Canvas(output).drawBitmap(source, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        })
        return output
    }

    private const val MAX_SIDE = 3200
    private const val MAX_CAPTURE_BYTES = 32L * 1024 * 1024
    private const val MAX_CAPTURE_SIDE = 12_000
    private const val MAX_CAPTURE_PIXELS = 50_000_000L
}

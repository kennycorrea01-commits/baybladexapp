package com.bxtracker.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/** Part photos live in the app's private storage, not the phone gallery. */
object PhotoStore {
    fun dir(context: Context) = File(context.filesDir, "photos").apply { mkdirs() }

    fun newFile(context: Context): File = File(dir(context), "part_${System.currentTimeMillis()}.jpg")

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    /** Copies a picked gallery image into app storage and shrinks it. */
    fun copyFromUri(context: Context, uri: Uri, maxSide: Int = 1080): File? = runCatching {
        val file = newFile(context)
        context.contentResolver.openInputStream(uri)!!.use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        shrink(file, maxSide)
        file
    }.getOrNull()

    /** Downscales to about [maxSide] px, fixes camera rotation and re-saves as JPEG. Keeps storage small. */
    fun shrink(file: File, maxSide: Int = 1080) {
        runCatching {
            val bitmap = decodeUpright(file, maxSide) ?: return
            file.outputStream().use { scaleTo(bitmap, maxSide).compress(Bitmap.CompressFormat.JPEG, 85, it) }
        }
    }

    /**
     * For the in-app camera: crops the square under the round guide, then shrinks it to [maxSide].
     * The preview fills a [viewW] x [viewH] view (center crop), with a guide [guide] px wide in its center.
     */
    fun cropToGuide(file: File, viewW: Float, viewH: Float, guide: Float, maxSide: Int = 1080) {
        runCatching {
            val bitmap = decodeUpright(file, maxSide * 2) ?: return
            // Center-crop scale from image to view: the image covers the view.
            val scale = maxOf(viewW / bitmap.width, viewH / bitmap.height)
            val side = (guide / scale).roundToInt().coerceIn(1, min(bitmap.width, bitmap.height))
            val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
            file.outputStream().use { scaleTo(square, maxSide).compress(Bitmap.CompressFormat.JPEG, 88, it) }
        }
    }

    private fun decodeUpright(file: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide && bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val rotation = when (
            ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        return if (rotation != 0f) {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation) }, true)
        } else bitmap
    }

    private fun scaleTo(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val f = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * f).roundToInt(), (bitmap.height * f).roundToInt(), true)
    }

    fun delete(path: String?) {
        if (path != null) runCatching { File(path).delete() }
    }
}

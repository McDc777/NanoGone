package app.nanogone.editor

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import app.nanogone.imaging.geom.IntRect
import app.nanogone.imaging.image.Argb

/** An opened photo. Pixel coordinates are always the stored (unrotated) ones. */
class Photo(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    /** Clockwise rotation from EXIF orientation: 0, 90, 180 or 270. */
    val rotation: Int,
    val byteSize: Long,
) {
    val isJpeg: Boolean get() = mimeType == "image/jpeg" || mimeType == "image/jpg"
    val megapixels: Float get() = width * height / 1_000_000f
}

/** Reads photos read-only. The original is never written. */
class PhotoReader(private val resolver: ContentResolver) {

    fun open(uri: Uri): Photo {
        var name = "photo"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        require(opts.outWidth > 0 && opts.outHeight > 0) { "This file is not a photo NanoGone can open." }
        val mime = opts.outMimeType ?: resolver.getType(uri) ?: "image/jpeg"
        val rotation = resolver.openInputStream(uri)?.use { runCatching { ExifInterface(it).rotationDegrees }.getOrDefault(0) } ?: 0
        return Photo(uri, name.substringBeforeLast('.'), mime, opts.outWidth, opts.outHeight, rotation, size)
    }

    fun bytes(photo: Photo): ByteArray = requireNotNull(resolver.openInputStream(photo.uri)) { "Cannot read the photo." }.use { it.readBytes() }

    /** A screen-sized copy (longest side about [maxSide]) in stored orientation. */
    fun displayBitmap(photo: Photo, maxSide: Int = 2560): Bitmap {
        var sample = 1
        while (maxOf(photo.width, photo.height) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
        }
        val bmp = resolver.openInputStream(photo.uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("Cannot decode the photo.")
        return if (bmp.isMutable) bmp else bmp.copy(Bitmap.Config.ARGB_8888, true)
    }

    /** Pixels of [rect] from the original file, full detail unless [sample] > 1 (then 1/sample size). */
    fun region(photo: Photo, rect: IntRect, sample: Int = 1): Argb {
        val decoder = requireNotNull(resolver.openInputStream(photo.uri)) { "Cannot read the photo." }.use { BitmapRegionDecoder.newInstance(it) }
            ?: error("Cannot read this photo in pieces.")
        try {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = sample
            }
            val bmp = decoder.decodeRegion(Rect(rect.left, rect.top, rect.right, rect.bottom), opts)
            val px = IntArray(bmp.width * bmp.height)
            bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            val out = Argb(bmp.width, bmp.height, px)
            bmp.recycle()
            return out
        } finally {
            decoder.recycle()
        }
    }
}

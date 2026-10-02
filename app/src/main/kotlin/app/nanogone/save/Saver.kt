package app.nanogone.save

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import app.nanogone.editor.Patch
import app.nanogone.editor.Photo
import app.nanogone.editor.PhotoReader
import app.nanogone.imaging.geom.CropPlanner
import app.nanogone.imaging.geom.IntRect
import app.nanogone.imaging.image.Argb
import app.nanogone.imaging.jpeg.BlockPatcher
import app.nanogone.imaging.jpeg.CopyCleaner
import app.nanogone.imaging.jpeg.JpegEncoder
import app.nanogone.imaging.jpeg.JpegParser
import app.nanogone.imaging.jpeg.JpegProbe
import app.nanogone.imaging.jpeg.Segment
import app.nanogone.imaging.jpeg.UltraHdr
import app.nanogone.imaging.jpeg.UnsupportedJpegException
import app.nanogone.imaging.mask.Mask
import java.io.ByteArrayOutputStream
import java.io.File

enum class SaveFormat { JPEG, PNG }

/** Writes a copy into Pictures/NanoGone. The original is only read. */
class Saver(private val resolver: ContentResolver, private val reader: PhotoReader, private val cacheDir: File) {

    class Result(val uri: Uri, val fileName: String, val bytes: Long, val method: String)

    fun estimateBytes(photo: Photo, format: SaveFormat): Long = when (format) {
        SaveFormat.JPEG -> if (photo.isJpeg && photo.byteSize > 0) photo.byteSize else (photo.width.toLong() * photo.height * 9 / 10)
        SaveFormat.PNG -> photo.width.toLong() * photo.height * 16 / 10
    }

    fun save(photo: Photo, patches: List<Patch>, format: SaveFormat, thumbnail: Bitmap): Result {
        val original = reader.bytes(photo)
        val (bytes, method) = when (format) {
            SaveFormat.JPEG -> jpeg(photo, original, patches, thumbnail).let { (b, m) -> withHdr(original, b, photo, patches, m) }
            SaveFormat.PNG -> png(photo, patches) to "lossless PNG"
        }
        val ext = if (format == SaveFormat.JPEG) "jpg" else "png"
        val name = "${photo.displayName}_NanoGone.$ext"
        val uri = write(name, if (format == SaveFormat.JPEG) "image/jpeg" else "image/png", bytes, photo, original)
        return Result(uri, name, bytes.size.toLong(), method)
    }

    /** Save an enhanced picture (changed everywhere, maybe bigger): full encode, details copied. */
    fun saveEnhanced(photo: Photo, img: Argb, format: SaveFormat, patches: List<Patch> = emptyList()): Result {
        val original = reader.bytes(photo)
        var method = "enhanced, ${img.width} x ${img.height}"
        val bytes = when (format) {
            // The gain map is resolution-free, so it still fits a bigger picture.
            SaveFormat.JPEG -> withHdr(original, JpegEncoder.encode(img.width, img.height, img.px), photo, patches, method).also { method = it.second }.first
            SaveFormat.PNG -> {
                val bmp = Bitmap.createBitmap(img.px, img.width, img.height, Bitmap.Config.ARGB_8888)
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                bmp.recycle()
                out.toByteArray()
            }
        }
        val ext = if (format == SaveFormat.JPEG) "jpg" else "png"
        val name = "${photo.displayName}_NanoGone.$ext"
        val uri = write(name, if (format == SaveFormat.JPEG) "image/jpeg" else "image/png", bytes, photo, original)
        return Result(uri, name, bytes.size.toLong(), method)
    }

    private fun jpeg(photo: Photo, original: ByteArray, patches: List<Patch>, thumbnail: Bitmap): Pair<ByteArray, String> {
        if (photo.isJpeg) {
            try {
                val info = JpegProbe.info(original)
                if (info.patchable) {
                    val thumb = thumbnailJpeg(thumbnail)
                    var bytes = original
                    if (patches.isEmpty()) {
                        bytes = app.nanogone.imaging.jpeg.JpegWriter.copy(bytes, CopyCleaner.forCopy(thumb))
                    }
                    patches.forEachIndexed { k, patch ->
                        val region = CropPlanner.alignOut(patch.rect, info.mcuWidth, info.mcuHeight, photo.width, photo.height)
                        val pixels = composite(photo, region, patches.subList(0, k + 1))
                        val changed = Mask(region.width, region.height)
                        for (y in 0 until patch.rect.height) for (x in 0 until patch.rect.width) {
                            if (patch.changed[x, y]) changed[x + patch.rect.left - region.left, y + patch.rect.top - region.top] = true
                        }
                        val rewrite: (Segment) -> Segment? = if (k == patches.lastIndex) CopyCleaner.forCopy(thumb) else { s -> s }
                        bytes = BlockPatcher.patch(bytes, region, pixels.px, changed, rewrite)
                    }
                    return bytes to (if (patches.isEmpty()) "picture data copied exactly" else "only the changed squares rewritten")
                }
            } catch (_: UnsupportedJpegException) {
                // Progressive or unusual JPEG: fall through to a full top-quality encode.
            }
        }
        val full = composite(photo, IntRect(0, 0, photo.width, photo.height), patches)
        return JpegEncoder.encode(photo.width, photo.height, full.px) to "top-quality JPEG"
    }

    /**
     * Ultra HDR photos keep their brightness layer (gain map): the same hole is filled in it, then
     * it is joined to the new picture. Anything that fails leaves a plain (non-HDR) copy.
     */
    private fun withHdr(original: ByteArray, picture: ByteArray, photo: Photo, patches: List<Patch>, method: String): Pair<ByteArray, String> {
        val gm = runCatching { UltraHdr.find(original) }.getOrNull() ?: return picture to method
        return runCatching {
            val bmp = requireNotNull(BitmapFactory.decodeByteArray(gm.jpeg, 0, gm.jpeg.size)) { "gain map unreadable" }
            val px = Argb(bmp.width, bmp.height)
            bmp.getPixels(px.px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            bmp.recycle()
            val hole = UltraHdr.hole(px.width, px.height, photo.width, photo.height, patches.map { it.rect to it.changed })
            val fixed = UltraHdr.repair(gm.jpeg, px, hole)
            val out = UltraHdr.assemble(picture, fixed, gm.isoVersion)
            android.util.Log.i("NanoGone", "save: HDR gain map ${px.width}x${px.height} kept, ${hole.count()} gain pixels refilled")
            out to "$method, HDR kept"
        }.getOrElse {
            android.util.Log.w("NanoGone", "save: HDR gain map could not be kept", it)
            picture to method
        }
    }

    private fun png(photo: Photo, patches: List<Patch>): ByteArray {
        val full = composite(photo, IntRect(0, 0, photo.width, photo.height), patches)
        val bmp = Bitmap.createBitmap(full.px, photo.width, photo.height, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }

    /** Original pixels of [rect] with [patches] applied in order. */
    fun composite(photo: Photo, rect: IntRect, patches: List<Patch>): Argb {
        val img = reader.region(photo, rect)
        for (p in patches) {
            val o = p.rect.intersect(rect)
            if (o.isEmpty) continue
            for (y in o.top until o.bottom) for (x in o.left until o.right) {
                val px = x - p.rect.left
                val py = y - p.rect.top
                if (p.changed[px, py]) img[x - rect.left, y - rect.top] = p.pixels[py * p.rect.width + px]
            }
        }
        return img
    }

    private fun thumbnailJpeg(src: Bitmap): ByteArray {
        val scale = 160f / maxOf(src.width, src.height)
        val w = maxOf(1, (src.width * scale).toInt())
        val h = maxOf(1, (src.height * scale).toInt())
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== src) small.recycle()
        return JpegEncoder.encode(w, h, px)
    }

    private fun write(name: String, mime: String, bytes: ByteArray, photo: Photo, original: ByteArray): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/NanoGone")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)) {
            "Could not create the file in the NanoGone album."
        }
        requireNotNull(resolver.openOutputStream(uri)) { "Could not write the file." }.use { it.write(bytes) }
        // Formats rebuilt from pixels get the original's photo details copied over.
        if (mime == "image/png" || !bytes.startsWithExifFrom(original)) copyExif(original, uri)
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    private fun ByteArray.startsWithExifFrom(original: ByteArray): Boolean =
        String(this, 0, minOf(size, 64), Charsets.ISO_8859_1).contains("Exif") ||
            !String(original, 0, minOf(original.size, 64), Charsets.ISO_8859_1).contains("Exif")

    private fun copyExif(original: ByteArray, target: Uri) {
        runCatching {
            val tmp = File(cacheDir, "exif-src.jpg").apply { writeBytes(original) }
            val src = ExifInterface(tmp.absolutePath)
            resolver.openFileDescriptor(target, "rw")?.use { fd ->
                val dst = ExifInterface(fd.fileDescriptor)
                for (tag in COPY_TAGS) src.getAttribute(tag)?.let { dst.setAttribute(tag, it) }
                dst.saveAttributes()
            }
            tmp.delete()
        }
    }

    private companion object {
        val COPY_TAGS = listOf(
            ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_OFFSET_TIME, ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_ORIENTATION,
            ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_LENS_MODEL,
        )
    }
}

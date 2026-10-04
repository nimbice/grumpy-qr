package io.github.nimbice.grumpyqr.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.graphics.createBitmap
import io.github.nimbice.grumpyqr.BuildConfig
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zxingcpp.BarcodeReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

sealed interface ImageScanResult {
    data class Found(val scans: List<Scan>, val preview: Bitmap?) : ImageScanResult
    data class NothingFound(val preview: Bitmap?) : ImageScanResult
    data class Failed(val reason: Reason) : ImageScanResult

    enum class Reason { UNREADABLE, PDF_LOCKED, TOO_LARGE, DECODER_UNAVAILABLE }
}

/**
 * Finds codes in pictures, screenshots and PDFs. Everything arrives through
 * the system photo picker, file picker or share sheet, which hand us access
 * to just that one file, so no storage permission is ever needed.
 */
class ImageScanner(private val context: Context) {

    suspend fun scan(uri: Uri, mimeTypeHint: String? = null): ImageScanResult = withContext(Dispatchers.Default) {
        val reader = try {
            Decoder.forImages()
        } catch (_: UnsatisfiedLinkError) {
            return@withContext ImageScanResult.Failed(ImageScanResult.Reason.DECODER_UNAVAILABLE)
        }
        try {
            if (isPdf(uri, mimeTypeHint)) scanPdf(uri, reader) else scanImage(uri, reader)
        } catch (e: OutOfMemoryError) {
            debugLog(uri, e)
            ImageScanResult.Failed(ImageScanResult.Reason.TOO_LARGE)
        } catch (e: IOException) {
            debugLog(uri, e)
            ImageScanResult.Failed(ImageScanResult.Reason.UNREADABLE)
        } catch (e: RuntimeException) {
            // Decoder errors, revoked URI grants, unsupported formats...
            debugLog(uri, e)
            ImageScanResult.Failed(ImageScanResult.Reason.UNREADABLE)
        }
    }

    private fun scanImage(uri: Uri, reader: BarcodeReader): ImageScanResult {
        val bitmap = flattenOntoWhite(loadBitmap(uri))
        val scans = decode(reader, bitmap)
        val preview = scaledPreview(bitmap)
        if (preview !== bitmap) bitmap.recycle()
        return if (scans.isEmpty()) ImageScanResult.NothingFound(preview) else ImageScanResult.Found(scans, preview)
    }

    private fun scanPdf(uri: Uri, reader: BarcodeReader): ImageScanResult {
        // PdfRenderer needs a seekable file, which shared streams often aren't.
        val temp = File.createTempFile("scan", ".pdf", context.cacheDir)
        try {
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> copyLimited(input, output, MAX_PDF_BYTES) }
            }
            if (copied == null) return ImageScanResult.Failed(ImageScanResult.Reason.UNREADABLE)

            val descriptor = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = try {
                PdfRenderer(descriptor)
            } catch (_: SecurityException) {
                descriptor.close()
                return ImageScanResult.Failed(ImageScanResult.Reason.PDF_LOCKED)
            }

            val scans = mutableListOf<Scan>()
            var preview: Bitmap? = null
            renderer.use {
                for (index in 0 until min(renderer.pageCount, MAX_PDF_PAGES)) {
                    renderer.openPage(index).use { page ->
                        val scale = PDF_RENDER_LONG_SIDE / max(page.width, page.height).toFloat()
                        val bitmap = createBitmap(
                            (page.width * scale).roundToInt().coerceAtLeast(1),
                            (page.height * scale).roundToInt().coerceAtLeast(1),
                        )
                        bitmap.eraseColor(Color.WHITE) // PDF pages render onto transparency
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        scans += decode(reader, bitmap)
                        if (preview == null) preview = scaledPreview(bitmap)
                        if (preview !== bitmap) bitmap.recycle()
                    }
                }
            }
            val unique = scans.distinctBy { it.format to it.text }
            return if (unique.isEmpty()) ImageScanResult.NothingFound(preview) else ImageScanResult.Found(unique, preview)
        } finally {
            temp.delete()
        }
    }

    private fun decode(reader: BarcodeReader, bitmap: Bitmap): List<Scan> {
        reader.options.binarizer = BarcodeReader.Binarizer.LOCAL_AVERAGE
        val scans = reader.read(bitmap).toScans()
        if (scans.isNotEmpty()) return scans
        // A second opinion helps with unevenly lit photos.
        reader.options.binarizer = BarcodeReader.Binarizer.GLOBAL_HISTOGRAM
        return reader.read(bitmap).toScans()
    }

    private fun isPdf(uri: Uri, mimeTypeHint: String?): Boolean {
        val type = mimeTypeHint?.takeUnless { it == "*/*" } ?: context.contentResolver.getType(uri)
        if (type == "application/pdf") return true
        if (type != null && type.startsWith("image/")) return false
        val header = ByteArray(5)
        val read = context.contentResolver.openInputStream(uri)?.use { it.read(header) } ?: 0
        return read == 5 && String(header, Charsets.US_ASCII) == "%PDF-"
    }

    private fun loadBitmap(uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                // The decoder reads pixels directly, so they must live in normal memory.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val width = info.size.width
                val height = info.size.height
                val longest = max(width, height)
                if (longest > MAX_IMAGE_SIDE) {
                    val scale = MAX_IMAGE_SIDE.toFloat() / longest
                    decoder.setTargetSize(
                        (width * scale).roundToInt().coerceAtLeast(1),
                        (height * scale).roundToInt().coerceAtLeast(1),
                    )
                }
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_IMAGE_SIDE) sampleSize *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Unsupported image")
    }

    /**
     * Transparent pixels read as black, which turns a QR code saved as a
     * transparent PNG into a solid black square. Paint everything onto white.
     */
    private fun flattenOntoWhite(source: Bitmap): Bitmap {
        if (source.config == Bitmap.Config.ARGB_8888 && !source.hasAlpha()) return source
        val flat = createBitmap(source.width, source.height)
        Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(source, 0f, 0f, null)
        }
        source.recycle()
        return flat
    }

    private fun scaledPreview(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= PREVIEW_SIDE) return bitmap
        val scale = PREVIEW_SIDE.toFloat() / longest
        return bitmap.scale(
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
        )
    }

    private fun copyLimited(input: InputStream, output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return total
            total += read
            if (total > limit) throw IOException("File too large")
            output.write(buffer, 0, read)
        }
    }

    private fun debugLog(uri: Uri, error: Throwable) {
        if (BuildConfig.DEBUG) Log.w("GrumpyImage", "Couldn't scan $uri", error)
    }

    private companion object {
        const val MAX_IMAGE_SIDE = 4096
        const val PREVIEW_SIDE = 1280
        const val PDF_RENDER_LONG_SIDE = 2400f
        const val MAX_PDF_PAGES = 20
        const val MAX_PDF_BYTES = 50L * 1024 * 1024
    }
}

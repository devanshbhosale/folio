package com.folio.viewer.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.folio.viewer.domain.DocumentFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * First-page thumbnails, generated on-device, cached on disk.
 *
 * Cache path: `cacheDir/thumbs/<sha1(uri)>.png`. LRU-trimmed via directory
 * size cap (~30 MB). PDF uses PdfRenderer; iWork uses the embedded preview
 * Apple ships. Office/text formats get a paint-by-hand cover so the grid
 * always has SOMETHING to show without expensive parsing at startup.
 */
object ThumbnailGenerator {

    private const val CACHE_DIR = "thumbs"
    private const val MAX_BYTES = 30L * 1024 * 1024
    private const val TARGET_W = 400
    private const val TARGET_H = 240

    fun cachedFile(context: Context, uri: String): File {
        val root = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val hash = MessageDigest.getInstance("SHA-1").digest(uri.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(root, "$hash.png")
    }

    suspend fun ensure(context: Context, uri: Uri, displayName: String, mime: String?): File? =
        withContext(Dispatchers.IO) {
            val f = cachedFile(context, uri.toString())
            if (f.exists() && f.length() > 0) return@withContext f
            val fmt = DocumentFormat.fromMimeOrExtension(mime, displayName)
            val bmp = try {
                when (fmt) {
                    DocumentFormat.PDF -> pdfThumb(context, uri)
                    DocumentFormat.NUMBERS, DocumentFormat.PAGES -> iworkThumb(context, uri, displayName)
                    else -> null
                }
            } catch (_: Throwable) { null }
            val out = bmp ?: syntheticCover(fmt, displayName)
            try {
                FileOutputStream(f).use { out.compress(Bitmap.CompressFormat.PNG, 90, it) }
            } catch (_: Throwable) { return@withContext null }
            trimCache(File(context.cacheDir, CACHE_DIR))
            f
        }

    private fun pdfThumb(context: Context, uri: Uri): Bitmap? {
        var pfd: ParcelFileDescriptor? = null
        var temp: File? = null
        try {
            temp = File.createTempFile("pdf-t-", ".pdf", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { out -> input.copyTo(out) }
            } ?: return null
            pfd = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(pfd).use { r ->
                if (r.pageCount == 0) return null
                r.openPage(0).use { page ->
                    val ratio = page.width.toFloat() / page.height.toFloat()
                    val h = (TARGET_W / ratio).toInt().coerceAtLeast(120)
                    val bmp = Bitmap.createBitmap(TARGET_W, h, Bitmap.Config.ARGB_8888)
                    Canvas(bmp).drawColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bmp
                }
            }
        } catch (_: Throwable) { return null }
        finally {
            try { pfd?.close() } catch (_: Throwable) {}
            temp?.delete()
        }
    }

    private fun iworkThumb(context: Context, uri: Uri, displayName: String): Bitmap? {
        val ext = displayName.substringAfterLast('.', "").lowercase()
        val temp = File.createTempFile("iw-t-", ".$ext", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { out -> input.copyTo(out) }
            } ?: return null
            temp.inputStream().use { fis ->
                ZipInputStream(fis).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val n = entry.name.lowercase()
                        if (n.endsWith("preview.jpg") || n.endsWith("preview.png") ||
                            n.endsWith("thumbnail.jpg")) {
                            val bytes = zip.readBytes()
                            return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        }
                        entry = zip.nextEntry
                    }
                }
            }
            return null
        } finally { temp.delete() }
    }

    /** Format-branded synthetic cover: colored background + big format label. */
    fun syntheticCover(format: DocumentFormat, displayName: String): Bitmap {
        val bmp = Bitmap.createBitmap(TARGET_W, TARGET_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bg = when (format) {
            DocumentFormat.PDF -> 0xFFD64545.toInt()
            DocumentFormat.WORD -> 0xFF2B579A.toInt()
            DocumentFormat.EXCEL -> 0xFF217346.toInt()
            DocumentFormat.POWERPOINT -> 0xFFB7472A.toInt()
            DocumentFormat.CSV -> 0xFF3B7A57.toInt()
            DocumentFormat.TEXT -> 0xFF555555.toInt()
            DocumentFormat.RTF -> 0xFF6E4B8B.toInt()
            DocumentFormat.ODT -> 0xFF0E7C86.toInt()
            DocumentFormat.NUMBERS -> 0xFF35B34A.toInt()
            DocumentFormat.PAGES -> 0xFFE07C24.toInt()
            DocumentFormat.UNSUPPORTED -> 0xFF888888.toInt()
        }
        canvas.drawColor(bg)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 64f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF,
                android.graphics.Typeface.BOLD)
        }
        val label = format.display.uppercase()
        val bounds = Rect()
        paint.getTextBounds(label, 0, label.length, bounds)
        canvas.drawText(label, TARGET_W / 2f, TARGET_H / 2f + bounds.height() / 2f - 12, paint)

        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCCFFFFFF.toInt()
            textSize = 22f
            textAlign = Paint.Align.CENTER
        }
        val nameOnly = displayName.substringBeforeLast('.', displayName)
        val trimmed = if (nameOnly.length > 24) nameOnly.substring(0, 24) + "…" else nameOnly
        canvas.drawText(trimmed, TARGET_W / 2f, TARGET_H - 24f, sub)
        return bmp
    }

    private fun trimCache(dir: File) {
        val files = dir.listFiles()?.toMutableList() ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_BYTES) return
        files.sortBy { it.lastModified() }
        for (f in files) {
            if (total <= MAX_BYTES) break
            total -= f.length()
            f.delete()
        }
    }
}

package com.folio.viewer.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest

/**
 * Ephemeral local copy of an SAF URI in cacheDir. Cleared on viewer exit.
 * Used only when the renderer needs a seekable File (POI, ODF Toolkit, ZIP unpack).
 */
object UriCache {
    fun materialize(context: Context, uri: Uri, suffix: String = ""): File {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(uri.toString().toByteArray()).joinToString("") { "%02x".format(it) }
        val f = File(context.cacheDir, "doc-$digest$suffix")
        if (f.exists() && f.length() > 0) return f
        context.contentResolver.openInputStream(uri)?.use { input ->
            f.outputStream().use { out -> input.copyTo(out) }
        } ?: error("Cannot open URI")
        return f
    }

    fun purge(context: Context) {
        context.cacheDir.listFiles()?.forEach { if (it.name.startsWith("doc-")) it.delete() }
    }
}

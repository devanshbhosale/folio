package com.folio.viewer.data.repo

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.folio.viewer.data.db.DocumentDao
import com.folio.viewer.data.db.DocumentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class DocumentRepository(
    private val dao: DocumentDao,
    private val context: Context
) {
    fun observeRecents(): Flow<List<DocumentEntity>> = dao.observeAll()
    fun observeFavorites(): Flow<List<DocumentEntity>> = dao.observeFavorites()

    /**
     * Take a persistent read grant on the URI (SAF) so the file stays openable
     * across app restarts. Silently ignored if the provider doesn't allow it.
     */
    fun takePersistablePermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) { /* not persistable — that's fine */ }
    }

    suspend fun record(uri: Uri, mime: String?): DocumentEntity = withContext(Dispatchers.IO) {
        val meta = queryMeta(context.contentResolver, uri)
        val entity = DocumentEntity(
            uri = uri.toString(),
            displayName = meta.first ?: uri.lastPathSegment.orEmpty().ifBlank { "Document" },
            mime = mime ?: context.contentResolver.getType(uri),
            sizeBytes = meta.second,
            lastOpenedAt = System.currentTimeMillis()
        )
        // Preserve favorite flag if it existed.
        dao.upsert(entity)
        entity
    }

    suspend fun toggleFavorite(uri: String, fav: Boolean) = dao.setFavorite(uri, fav)
    suspend fun clearRecents() = dao.clear()
    suspend fun remove(uri: String) = dao.delete(uri)

    private fun queryMeta(cr: ContentResolver, uri: Uri): Pair<String?, Long> {
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    val name = c.getString(0)
                    val size = if (c.isNull(1)) 0L else c.getLong(1)
                    return name to size
                }
            }
        return null to 0L
    }
}

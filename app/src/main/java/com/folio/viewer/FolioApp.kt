package com.folio.viewer

import android.app.Application
import com.folio.viewer.data.db.FolioDatabase
import com.folio.viewer.data.repo.DocumentRepository
import com.folio.viewer.data.repo.PreferencesRepository

/**
 * Application-scoped singletons — hand-rolled DI, no framework needed for a viewer app.
 */
class FolioApp : Application() {
    val db by lazy { FolioDatabase.build(this) }
    val documents by lazy { DocumentRepository(db.documentDao(), this) }
    val prefs by lazy { PreferencesRepository(this) }
}

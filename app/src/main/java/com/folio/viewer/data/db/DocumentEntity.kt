package com.folio.viewer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val uri: String,
    val displayName: String,
    val mime: String?,
    val sizeBytes: Long,
    val lastOpenedAt: Long,
    val isFavorite: Boolean = false
)

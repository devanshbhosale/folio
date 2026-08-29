package com.folio.viewer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY lastOpenedAt DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isFavorite = 1 ORDER BY lastOpenedAt DESC")
    fun observeFavorites(): Flow<List<DocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(doc: DocumentEntity)

    @Query("UPDATE documents SET isFavorite = :fav WHERE uri = :uri")
    suspend fun setFavorite(uri: String, fav: Boolean)

    @Query("DELETE FROM documents")
    suspend fun clear()

    @Query("DELETE FROM documents WHERE uri = :uri")
    suspend fun delete(uri: String)
}

package com.folio.viewer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DocumentEntity::class], version = 1, exportSchema = false)
abstract class FolioDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    companion object {
        fun build(ctx: Context): FolioDatabase = Room.databaseBuilder(
            ctx.applicationContext, FolioDatabase::class.java, "folio.db"
        ).fallbackToDestructiveMigration().build()
    }
}

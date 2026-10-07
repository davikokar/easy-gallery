package com.davide.seddio.easygallery.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [AlbumEntity::class, AlbumMembershipEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(AlbumMembershipKindConverters::class)
abstract class EasyGalleryDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao

    companion object {
        private const val DATABASE_NAME = "easy_gallery.db"

        fun create(context: Context): EasyGalleryDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                EasyGalleryDatabase::class.java,
                DATABASE_NAME
            ).build()
        }
    }
}

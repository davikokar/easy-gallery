package com.davide.seddio.easygallery.data

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * A file-backed media index that is not the app's own: the production [MediaIndexDatabase.create], with its callbacks
 * and migrations, opened over a context whose database path is a private directory of its own. The app's index, and
 * the singletons on `EasyGalleryApp`, are never touched. Everything in it is synthetic and [close] deletes the files.
 * It lives in the app's cache directory, which is private to the app and is not backed up.
 */
internal class MediaIndexThrowawayDatabase private constructor(
    val directory: File,
    val database: MediaIndexDatabase
) : AutoCloseable {

    val store: RoomMediaIndexStore = RoomMediaIndexStore(database)

    val databaseFile: File get() = File(directory, DATABASE_NAME)

    /** Every file of the database: the file itself and any `-wal`, `-shm` or `-journal` beside it. */
    fun files(): List<File> = directory.listFiles { file -> file.name.startsWith(DATABASE_NAME) }.orEmpty().toList()

    override fun close() {
        try {
            database.close()
        } finally {
            directory.deleteRecursively()
        }
    }

    // Database files of this class are named like the production one, which the migrations tests also rely on.
    private class RedirectedDatabaseContext(base: Context, private val directory: File) : ContextWrapper(base) {
        // Room builds on the application context; answering with this wrapper keeps the redirected path.
        override fun getApplicationContext(): Context = this

        override fun getDatabasePath(name: String): File = File(directory, name)
    }

    companion object {
        const val DATABASE_NAME = "media_index.db"

        /**
         * [prepare] runs on the empty directory's database file before Room opens it, so a test can create the file
         * as it needs it (see [createWithoutAutoVacuum]).
         */
        fun open(context: Context, prepare: (File) -> Unit = {}): MediaIndexThrowawayDatabase {
            val directory = File(context.cacheDir, "g2-index-${UUID.randomUUID()}")
            check(directory.mkdirs()) { "The throwaway directory could not be created" }
            try {
                prepare(File(directory, DATABASE_NAME))
                val database = MediaIndexDatabase.create(RedirectedDatabaseContext(context.applicationContext, directory))
                return MediaIndexThrowawayDatabase(directory, database)
            } catch (failure: Throwable) {
                directory.deleteRecursively()
                throw failure
            }
        }

        // Auto-vacuum NONE is the worst case: a deleted page stays in the file as a free page. The platform's SQLite
        // defaults to FULL, so the setting takes effect only through a VACUUM, as in MediaIndexFaceDataScrubTest.
        fun createWithoutAutoVacuum(file: File) {
            file.parentFile?.mkdirs()
            val raw = SQLiteDatabase.openOrCreateDatabase(file, null)
            try {
                raw.execSQL("PRAGMA auto_vacuum = 0")
                raw.execSQL("VACUUM")
            } finally {
                raw.close()
            }
        }

        /** Occurrences of [marker] in each file named like the database, by file name. An unreadable `-shm` counts as empty. */
        fun markerCounts(files: List<File>, marker: ByteArray): Map<String, Int> =
            files.associate { file -> file.name to countOccurrences(readFile(file), marker) }

        // The -shm is the log's index of page numbers and hashes, never page content; any other unreadable file is a failure.
        private fun readFile(file: File): ByteArray {
            return try {
                file.readBytes()
            } catch (failure: IOException) {
                if (!file.name.endsWith("-shm")) throw failure
                ByteArray(0)
            }
        }

        private fun countOccurrences(haystack: ByteArray, needle: ByteArray): Int {
            var count = 0
            var index = 0
            while (index <= haystack.size - needle.size) {
                var matches = true
                for (offset in needle.indices) {
                    if (haystack[index + offset] != needle[offset]) {
                        matches = false
                        break
                    }
                }
                if (matches) {
                    count += 1
                    index += needle.size
                } else {
                    index += 1
                }
            }
            return count
        }
    }
}

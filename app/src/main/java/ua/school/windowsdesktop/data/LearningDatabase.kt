package ua.school.windowsdesktop.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [FileEntity::class, StoreMarker::class], version = 1, exportSchema = true)
internal abstract class LearningDatabase : RoomDatabase() {
    abstract fun files(): FileDao

    companion object {
        const val NAME = "learning-files.db"
        fun open(context: Context): LearningDatabase =
            Room.databaseBuilder(context, LearningDatabase::class.java, NAME)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        // A durable metadata commit must precede collection of the old blobs.
                        db.execSQL("PRAGMA synchronous = FULL")
                    }
                })
                // No destructive migration fallback: an incompatible schema must preserve user data.
                .build()
    }
}

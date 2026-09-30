package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.BookDao
import com.example.data.dao.BookReviewDao
import com.example.data.dao.DiscussionDao
import com.example.data.dao.StudyChannelDao
import com.example.data.model.BookEntity
import com.example.data.model.BookReviewEntity
import com.example.data.model.DiscussionMessageEntity
import com.example.data.model.StudyChannelEntity

@Database(
    entities = [
        BookEntity::class,
        DiscussionMessageEntity::class,
        StudyChannelEntity::class,
        BookReviewEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun discussionDao(): DiscussionDao
    abstract fun studyChannelDao(): StudyChannelDao
    abstract fun bookReviewDao(): BookReviewDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "glyph_books_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

package com.shunlight_library.novel_reader.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.shunlight_library.novel_reader.data.dao.EpisodeDao
import com.shunlight_library.novel_reader.data.dao.LastReadNovelDao
import com.shunlight_library.novel_reader.data.dao.NovelDescDao
import com.shunlight_library.novel_reader.data.dao.UpdateQueueDao
import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import com.shunlight_library.novel_reader.data.entity.LastReadNovelEntity
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity

@Database(
    entities = [
        EpisodeEntity::class,
        NovelDescEntity::class,
        LastReadNovelEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NovelDatabase : RoomDatabase() {
    abstract fun episodeDao(): EpisodeDao
    abstract fun novelDescDao(): NovelDescDao
    abstract fun lastReadNovelDao(): LastReadNovelDao
    abstract fun updateQueueDao(): UpdateQueueDao

    companion object {
        @Volatile
        private var INSTANCE: NovelDatabase? = null

        fun getDatabase(context: Context): NovelDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NovelDatabase::class.java,
                    "novel_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
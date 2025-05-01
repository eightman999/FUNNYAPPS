// AppDatabase.kt (Updated)
package com.shunlight_library.nr_reader.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
// AppDatabase.kt の修正
@Database(
    entities = [UnifiedNovelEntity::class],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun UnifiedNovelDao(): UnifiedNovelDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "novel_database"
                )
                    .fallbackToDestructiveMigration()  // マイグレーション失敗時のフォールバック
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
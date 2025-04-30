// AppDatabase.kt (Updated)
package com.shunlight_library.nr_reader.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        NovelEntity::class,
        NovelExtendedEntity::class  // 拡張情報エンティティを追加
    ],
    version = 5,  // バージョンを3から4に増やす
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun novelDao(): NovelDao
    abstract fun novelExtendedDao(): NovelExtendedDao  // 新しいDAOを追加

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
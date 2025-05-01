// AppDatabase.kt (Updated)
package com.shunlight_library.nr_reader.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 一時テーブルを作成
        database.execSQL(
            """
            CREATE TABLE rast_read_novel_temp (
                ncode TEXT NOT NULL,
                date TEXT NOT NULL,
                episode_no INTEGER,
                PRIMARY KEY(ncode, date)
            )
            """.trimIndent()
        )

        // データをコピー
        database.execSQL(
            """
            INSERT INTO rast_read_novel_temp (ncode, date, episode_no)
            SELECT ncode, date, episode_no FROM rast_read_novel
            """.trimIndent()
        )

        // 元のテーブルを削除
        database.execSQL("DROP TABLE rast_read_novel")

        // 一時テーブルの名前を元に戻す
        database.execSQL("ALTER TABLE rast_read_novel_temp RENAME TO rast_read_novel")
    }
}
@Database(
    entities = [
        NovelEntity::class,
        NovelExtendedEntity::class,  // 拡張情報エンティティを追加
        LastReadNovelEntity::class
    ],
    version = 8,  // バージョンを3から4に増やす
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
                ).addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()  // マイグレーション失敗時のフォールバック
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
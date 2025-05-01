package com.shunlight_library.nr_reader.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        NovelDescEntity::class,
        InternalEpisodeEntity::class,
        InternalLastReadEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun novelDescDao(): NovelDescDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun lastReadDao(): LastReadDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // UnifiedNovelEntityからの移行を処理するMigration
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. 新しいテーブルを作成
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS novels_descs (
                        ncode TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        author TEXT NOT NULL,
                        Synopsis TEXT,
                        main_tag TEXT,
                        sub_tag TEXT,
                        rating INTEGER,
                        last_update_date TEXT,
                        total_ep INTEGER,
                        general_all_no INTEGER,
                        updated_at TEXT
                    )
                    """
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS episodes (
                        ncode TEXT NOT NULL,
                        episode_no TEXT NOT NULL,
                        body TEXT,
                        e_title TEXT,
                        update_time TEXT,
                        PRIMARY KEY(ncode, episode_no)
                    )
                    """
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS rast_read_novel (
                        ncode TEXT NOT NULL,
                        date TEXT NOT NULL,
                        episode_no INTEGER,
                        PRIMARY KEY(ncode, date)
                    )
                    """
                )

                // 2. インデックスを作成
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_novels_last_update ON novels_descs(last_update_date)")
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_novels_update_check ON novels_descs(ncode, rating, total_ep, general_all_no, updated_at)")
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_episodes_ncode ON episodes(ncode, episode_no)")
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_last_read ON rast_read_novel(ncode, date)")

                // 3. 古いテーブルから新しいテーブルにデータを移行
                database.execSQL(
                    """
                    INSERT INTO novels_descs (
                        ncode, title, author, Synopsis, main_tag, sub_tag,
                        rating, last_update_date, total_ep, general_all_no, updated_at
                    )
                    SELECT 
                        ncode, title, author, synopsis, main_tags, sub_tags,
                        rating, last_update_date, total_episodes, general_all_no, last_updated
                    FROM novels
                    """
                )

                // 4. 最終読み込み情報を移行
                database.execSQL(
                    """
                    INSERT INTO rast_read_novel (
                        ncode, date, episode_no
                    )
                    SELECT 
                        ncode, datetime('now'), last_read_episode
                    FROM novels
                    """
                )

                // 5. 古いテーブルは後で削除（安全のため一旦保持）
                // database.execSQL("DROP TABLE IF EXISTS novels")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "novel_database"
                )
                    .addMigrations(MIGRATION_10_11)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
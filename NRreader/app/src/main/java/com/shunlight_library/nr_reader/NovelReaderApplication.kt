// NovelReaderApplication.kt
package com.shunlight_library.nr_reader

import android.app.Application
import com.shunlight_library.nr_reader.database.AppDatabase
import com.shunlight_library.nr_reader.repository.NovelRepository

class NovelReaderApplication : Application() {
    // データベースとリポジトリの遅延初期化
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { NovelRepository(database, this) }
}
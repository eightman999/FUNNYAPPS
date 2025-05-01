package com.shunlight_library.nr_reader

import android.app.Application
import android.util.Log
import com.shunlight_library.nr_reader.database.AppDatabase
import com.shunlight_library.nr_reader.database.ExternalDatabaseHandler
import com.shunlight_library.nr_reader.repository.ExternalDatabaseRepository
import com.shunlight_library.nr_reader.repository.NovelRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class NovelReaderApplication : Application() {
    // アプリケーションスコープのコルーチン
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // データベースとリポジトリの遅延初期化
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { NovelRepository(database, this) }

    // 外部データベースコピー用のコンポーネント
    val externalDbHandler by lazy { ExternalDatabaseHandler(this) }
    val externalDbRepository by lazy {
        ExternalDatabaseRepository(this, database, externalDbHandler)
    }

    // 設定ストア
    private val settingsStore by lazy { SettingsStore(this) }

    // タグ（ログ用）
    private val TAG = "NovelReaderApp"

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "アプリケーション初期化")
    }
}
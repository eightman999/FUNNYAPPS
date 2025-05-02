package com.shunlight_library.nr_reader

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class NovelReaderApplication : Application() {
    // アプリケーションスコープのコルーチン
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 設定ストア
    val settingsStore by lazy { SettingsStore(this) }

    // 小説リポジトリ
    val repository by lazy { NovelRepository(this) }

    // タグ（ログ用）
    private val TAG = "NovelReaderApp"

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "アプリケーション初期化")
    }
}
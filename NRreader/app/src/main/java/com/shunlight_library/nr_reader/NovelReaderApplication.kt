// NovelReaderApplication.kt (Updated)
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NovelReaderApplication : Application() {
    // アプリケーションスコープのコルーチン
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // データベースとリポジトリの遅延初期化
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { NovelRepository(database, this) }

    // 外部データベース関連のコンポーネント
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

        // アプリ起動時の初期化処理
        initializeApp()
    }

    private fun initializeApp() {
        applicationScope.launch {
            try {
                // 外部データベース設定を確認
                val dbEnabled = settingsStore.dbEnabled.first()
                val dbUri = settingsStore.dbUri.first()
                val dbCopyToInternal = settingsStore.dbCopyToInternal.first()

                Log.d(TAG, "DB設定: 有効=$dbEnabled, URI=$dbUri, コピー=$dbCopyToInternal")

                // 外部DBが有効かつURIが設定されている場合は初期化
                if (dbEnabled && dbUri.isNotEmpty() && settingsStore.hasValidDatabaseUri(dbUri)) {
                    Log.d(TAG, "外部データベースの初期化を開始")

                    // パフォーマンスの観点から、アプリ起動時には同期は行わず、
                    // DBへの接続のみを行う
                    if (dbCopyToInternal) {
                        // 既にコピー済みの場合は、内部ファイルをそのまま使用
                        // (ユーザーが明示的に同期ボタンを押した時のみ再コピー)
                        externalDbHandler.setExternalDatabaseUri(android.net.Uri.parse(dbUri))
                    } else {
                        // 直接URIを設定
                        externalDbHandler.setExternalDatabaseUri(android.net.Uri.parse(dbUri))
                    }

                    // 内部DBが空の場合は同期を行う
                    val novelCount = repository.getNovelCount()
                    if (novelCount == 0) {
                        Log.d(TAG, "内部DBが空のため、外部DBと同期します")
                        // この場合はバックグラウンドで同期
                        externalDbRepository.synchronizeWithExternalDatabase(
                            shouldCopyToInternal = dbCopyToInternal,
                            externalDbUri = android.net.Uri.parse(dbUri)
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "アプリ初期化中にエラーが発生しました", e)
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        // データベース接続をクローズ
        externalDbHandler.closeDatabase()
    }
}
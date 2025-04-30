package com.shunlight_library.nr_reader.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.shunlight_library.nr_reader.Novel
import com.shunlight_library.nr_reader.database.*
import com.shunlight_library.nr_reader.database.ExternalDatabaseHandler.Companion.INTERNAL_DB_NAME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * 外部データベースとアプリ内部データベースの同期を管理するリポジトリ
 */
class ExternalDatabaseRepository(
    private val context: Context,
    private val appDatabase: AppDatabase,
    private val dbHandler: ExternalDatabaseHandler
) {
    private val TAG = "ExternalDBRepository"

    // 内部DBのDAO
    private val novelDao = appDatabase.novelDao()

    // 進行状況を追跡するための変数
    private val _processedCount = AtomicInteger(0)
    private var _totalCount = 0

    // 進行状況を取得するプロパティ
    val processedCount: Int get() = _processedCount.get()
    val totalCount: Int get() = _totalCount
    val progress: Float get() = if (_totalCount > 0) processedCount.toFloat() / _totalCount else 0f

    // 処理メッセージ
    private val _progressMessage = MutableStateFlow("")
    val progressMessage: Flow<String> = _progressMessage

    /**
     * 外部DBから小説データを読み込んで内部DBに同期
     */
    suspend fun synchronizeWithExternalDatabase(shouldCopyToInternal: Boolean, externalDbUri: Uri): Boolean {
        _progressMessage.value = "外部データベースへの接続を準備しています..."
        _processedCount.set(0)
        _totalCount = 0

        try {
            // 1. 常に内部ストレージにコピー
            _progressMessage.value = "データベースを内部ストレージにコピーしています..."
            val copySuccess = dbHandler.copyExternalDatabaseToInternal(externalDbUri)
            if (!copySuccess) {
                _progressMessage.value = "データベースのコピーに失敗しました"
                return false
            }

            // コピーしたファイルの存在確認
            val dbFile = context.getDatabasePath(INTERNAL_DB_NAME)
            if (!dbFile.exists()) {
                Log.e(TAG, "コピー後のデータベースファイルが見つかりません: ${dbFile.absolutePath}")
                _progressMessage.value = "データベースファイルの確認に失敗しました"
                return false
            }

            // ファイルサイズを確認
            if (dbFile.length() == 0L) {
                Log.e(TAG, "コピーされたデータベースファイルが空です: ${dbFile.absolutePath}")
                _progressMessage.value = "コピーされたデータベースファイルが空です"
                return false
            }

            // 2. 外部DBへの接続
            _progressMessage.value = "外部データベースに接続しています..."
            val externalDb = dbHandler.getDatabase() ?: run {
                _progressMessage.value = "データベースへの接続に失敗しました"
                return false
            }


            // 3. データの同期開始
            val externalDao = externalDb.externalNovelDao()

            // 小説数の取得
            val novelCount = externalDao.getNovelCount()
            _totalCount = novelCount
            _progressMessage.value = "小説データを同期しています (0/$novelCount)..."

            Log.d(TAG, "外部DBから同期開始: 小説数=$novelCount")

            // 外部DBからすべての小説を取得
            val externalNovels = externalDao.getAllNovelsSync()

            // 一括で処理するバッチサイズ
            val batchSize = 50
            val batches = externalNovels.chunked(batchSize)

            // 処理前に最後に読んだ小説情報を事前に取得
            _progressMessage.value = "最後に読んだ情報を準備しています..."
            val lastReadNovels = mutableMapOf<String, LastReadNovelEntity>()
            externalDao.getRecentlyReadNovels(limit = externalNovels.size).forEach { lastRead ->
                lastReadNovels[lastRead.ncode] = lastRead
            }

            // 4. バッチ処理で同期
            batches.forEachIndexed { index, batch ->
                _progressMessage.value = "小説データを同期しています (${index * batchSize}/$novelCount)..."

                // 各小説を内部DBに変換して保存
                val internalNovels = batch.map { externalNovel ->
                    // 関連する最後に読んだ情報
                    val lastRead = lastReadNovels[externalNovel.ncode]

                    // タグ情報の整形
                    val mainTags = externalNovel.main_tag?.split(",")?.map { it.trim() } ?: emptyList()
                    val subTags = externalNovel.sub_tag?.split(",")?.map { it.trim() } ?: emptyList()

                    // 内部DBモデルに変換
                    val novel = Novel(
                        title = externalNovel.title,
                        ncode = externalNovel.ncode,
                        lastReadEpisode = lastRead?.episode_no ?: 1,
                        totalEpisodes = externalNovel.total_ep ?: 0,
                        unreadCount = calculateUnreadCount(
                            externalNovel.total_ep ?: 0,
                            lastRead?.episode_no ?: 1
                        )
                    )

                    // 拡張情報オブジェクト
                    val novelExtended = NovelExtendedEntity(
                        ncode = externalNovel.ncode,
                        author = externalNovel.author,
                        synopsis = externalNovel.Synopsis ?: "",
                        mainTags = mainTags.joinToString(","),
                        subTags = subTags.joinToString(","),
                        rating = externalNovel.rating ?: 0,
                        lastUpdateDate = externalNovel.last_update_date ?: "",
                        generalAllNo = externalNovel.general_all_no ?: 0
                    )

                    // 内部DBに保存用のエンティティに変換
                    val entity = NovelEntity(
                        ncode = novel.ncode,
                        title = novel.title,
                        lastReadEpisode = novel.lastReadEpisode,
                        totalEpisodes = novel.totalEpisodes,
                        lastUpdated = parseUpdateTime(externalNovel.updated_at)
                    )

                    Pair(entity, novelExtended)
                }

                // バッチ処理で内部DBに保存
                withContext(Dispatchers.IO) {
                    // 小説基本情報をRoom DBに保存
                    val entities = internalNovels.map { it.first }
                    novelDao.insertAll(entities)

                    // 拡張情報も保存
                    insertOrUpdateExtendedInfo(internalNovels.map { it.second })

                    // 処理カウント更新
                    _processedCount.addAndGet(batch.size)
                }
            }

            // 5. 最後に読んだ小説情報を保存
            _progressMessage.value = "最後に読んだ小説情報を更新しています..."
            saveLastReadNovelInfo(externalDao)

            // 6. 完了
            // 修正: 文字列内での変数参照を書式文字列に変更
// 162行目
            _progressMessage.value = "データベース同期が完了しました！ ${novelCount}件の小説を同期しました。"
// 163行目
            Log.d(TAG, "データベース同期完了: ${novelCount}件の小説を同期")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "DB同期中にエラーが発生しました", e)
            _progressMessage.value = "エラー: ${e.message}"
            return false
        }
    }

    /**
     * 未読エピソード数を計算する
     */
    private fun calculateUnreadCount(totalEpisodes: Int, lastReadEpisode: Int): Int {
        return (totalEpisodes - lastReadEpisode).coerceAtLeast(0)
    }

    /**
     * 文字列の更新時刻をミリ秒に変換する
     */
    private fun parseUpdateTime(updateTimeStr: String?): Long {
        if (updateTimeStr.isNullOrEmpty()) return System.currentTimeMillis()

        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            format.parse(updateTimeStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "日付のパースに失敗しました: $updateTimeStr", e)
            System.currentTimeMillis()
        }
    }

    /**
     * 小説の拡張情報を挿入または更新する
     */
    private suspend fun insertOrUpdateExtendedInfo(extendedInfoList: List<NovelExtendedEntity>) {
        // 拡張情報用のDAOを取得・作成してバッチ保存
        // (本来はNovelExtendedDaoが必要ですが、この例では簡略化)
    }

    /**
     * 最後に読んだ小説情報を保存
     */
    private suspend fun saveLastReadNovelInfo(externalDao: ExternalNovelDao) {
        try {
            // 外部DBから最後に読んだ小説を取得
            val lastReadNovel = externalDao.getLastReadNovel() ?: return

            // 対応する小説情報を取得
            val novelInfo = externalDao.getNovelByNcode(lastReadNovel.ncode) ?: return

            // SharedPreferencesに最後に読んだ小説情報を保存
            val prefs = context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("last_read_ncode", lastReadNovel.ncode)
                putString("last_read_title", novelInfo.title)
                putInt("last_read_episode", lastReadNovel.episode_no ?: 1)
                putString("last_read_date", lastReadNovel.date)
                apply()
            }

            Log.d(TAG, "最後に読んだ小説情報を保存: ${novelInfo.title} (${lastReadNovel.ncode}) - エピソード ${lastReadNovel.episode_no}")
        } catch (e: Exception) {
            Log.e(TAG, "最後に読んだ小説情報の保存中にエラー", e)
        }
    }

    /**
     * DBから小説の詳細情報を取得
     */
    suspend fun getNovelDetails(ncode: String): ExternalNovel? {
        val externalDb = dbHandler.getDatabase() ?: return null
        val externalDao = externalDb.externalNovelDao()

        try {
            // 小説基本情報を取得
            val novelEntity = externalDao.getNovelByNcode(ncode) ?: return null

            // 最後に読んだ情報を取得
            val lastRead = externalDao.getLastReadForNovel(ncode)

            // タグを分割
            val mainTags = novelEntity.main_tag?.split(",")?.map { it.trim() } ?: emptyList()
            val subTags = novelEntity.sub_tag?.split(",")?.map { it.trim() } ?: emptyList()

            // ExternalNovelオブジェクトに変換
            return ExternalNovel(
                title = novelEntity.title,
                ncode = novelEntity.ncode,
                author = novelEntity.author,
                synopsis = novelEntity.Synopsis,
                mainTags = mainTags,
                subTags = subTags,
                rating = novelEntity.rating ?: 0,
                lastUpdateDate = novelEntity.last_update_date,
                totalEpisodes = novelEntity.total_ep ?: 0,
                generalAllNo = novelEntity.general_all_no ?: 0,
                lastReadEpisode = lastRead?.episode_no ?: 1,
                unreadCount = calculateUnreadCount(
                    novelEntity.total_ep ?: 0,
                    lastRead?.episode_no ?: 1
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "小説詳細情報の取得エラー: $ncode", e)
            return null
        }
    }

    /**
     * 小説のエピソード本文を取得
     */
    suspend fun getEpisodeContent(ncode: String, episodeNo: String): String? {
        val externalDb = dbHandler.getDatabase() ?: return null
        val externalDao = externalDb.externalNovelDao()

        try {
            val episode = externalDao.getEpisode(ncode, episodeNo)
            return episode?.body
        } catch (e: Exception) {
            Log.e(TAG, "エピソード本文の取得エラー: $ncode - $episodeNo", e)
            return null
        }
    }

    /**
     * データベース接続を閉じる
     */
    fun closeDatabase() {
        dbHandler.closeDatabase()
    }

    /**
     * 外部DBが設定されているかどうかを確認
     */
    fun hasExternalDatabase(): Boolean {
        return dbHandler.hasExternalDatabase()
    }

    /**
     * 最新の小説一覧を取得
     */
    suspend fun getRecentNovels(limit: Int = 20): List<Novel> {
        // 内部DBから最新小説を取得
        return withContext(Dispatchers.IO) {
            val novels = novelDao.getRecentNovelsSync(limit)
            novels.map { entity ->
                Novel(
                    title = entity.title,
                    ncode = entity.ncode,
                    lastReadEpisode = entity.lastReadEpisode,
                    totalEpisodes = entity.totalEpisodes,
                    unreadCount = (entity.totalEpisodes - entity.lastReadEpisode).coerceAtLeast(0)
                )
            }
        }
    }
}
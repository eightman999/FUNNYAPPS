package com.shunlight_library.nr_reader.database

/**
 * 外部DBと内部DBの間のデータ変換に使用するモデルクラス
 */
data class ExternalNovel(
    val title: String,
    val ncode: String,
    val author: String,
    val synopsis: String?,
    val mainTags: List<String>,
    val subTags: List<String>,
    val rating: Int = 0,
    val lastUpdateDate: String?,
    val totalEpisodes: Int = 0,
    val generalAllNo: Int = 0,
    val lastReadEpisode: Int = 1,
    val unreadCount: Int = 0
)

/**
 * 外部DBエンティティから内部モデルへの変換関数
 */
fun ExternalNovelEntity.toExternalNovel(): ExternalNovel {
    return ExternalNovel(
        title = this.title,
        ncode = this.ncode,
        author = this.author,
        synopsis = this.Synopsis,
        mainTags = this.main_tag?.split(",")?.map { it.trim() } ?: emptyList(),
        subTags = this.sub_tag?.split(",")?.map { it.trim() } ?: emptyList(),
        rating = this.rating ?: 0,
        lastUpdateDate = this.last_update_date,
        totalEpisodes = this.total_ep ?: 0,
        generalAllNo = this.general_all_no ?: 0
    )
}

/**
 * 内部モデルから小説概要エンティティへの変換関数
 */
fun ExternalNovel.toNovelDescEntity(): NovelDescEntity {
    return NovelDescEntity(
        ncode = this.ncode,
        title = this.title,
        author = this.author,
        Synopsis = this.synopsis,
        main_tag = this.mainTags.joinToString(","),
        sub_tag = this.subTags.joinToString(","),
        rating = this.rating,
        last_update_date = this.lastUpdateDate,
        total_ep = this.totalEpisodes,
        general_all_no = this.generalAllNo,
        updated_at = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
    )
}

/**
 * 内部モデルから最終読取エンティティへの変換関数
 */
fun ExternalNovel.toLastReadEntity(): InternalLastReadEntity {
    return InternalLastReadEntity(
        ncode = this.ncode,
        date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date()),
        episode_no = this.lastReadEpisode
    )
}
package com.sakura.music.ui.navigation

import android.net.Uri
import com.sakura.music.data.model.Platform

/** 全部导航路由集中在这里。 */
object Routes {

    const val HOME = "home"
    const val ACCOUNT = "account"

    /** 搜索不占页签：从发现页顶部的搜索条压栈进来。 */
    const val SEARCH = "search"

    /** 设置不占页签：从账号页右上角的小齿轮压栈进来。 */
    const val SETTINGS = "settings"

    /** 账户资料（昵称、密码、第三方账号、退出登录）：点账号页的头像那一栏进来。 */
    const val ACCOUNT_DETAIL = "account_detail"

    /* ---- 榜单 / 平台歌单 ---- */

    const val COLLECTION_ARG_PLATFORM = "platform"
    const val COLLECTION_ARG_ID = "id"
    const val COLLECTION_ARG_KIND = "kind"
    const val COLLECTION_ARG_TITLE = "title"

    const val COLLECTION = "collection/{$COLLECTION_ARG_PLATFORM}/{$COLLECTION_ARG_ID}" +
        "/{$COLLECTION_ARG_KIND}?$COLLECTION_ARG_TITLE={$COLLECTION_ARG_TITLE}"

    /** [kind] 只用 [CollectionKind] 的两个取值：榜单与平台歌单在网关侧是两组接口。 */
    fun collection(platform: Platform, id: String, kind: CollectionKind, title: String): String =
        "collection/${platform.id}/${Uri.encode(id)}/${kind.id}?$COLLECTION_ARG_TITLE=${Uri.encode(title)}"

    /* ---- 自建歌单 ---- */

    const val PLAYLIST_ARG_ID = "playlistId"
    const val PLAYLIST_ARG_NAME = "playlistName"

    const val PLAYLIST = "playlist/{$PLAYLIST_ARG_ID}?$PLAYLIST_ARG_NAME={$PLAYLIST_ARG_NAME}"

    fun playlist(id: String, name: String): String =
        "playlist/${Uri.encode(id)}?$PLAYLIST_ARG_NAME=${Uri.encode(name)}"

    /**
     * 收藏（「我喜欢的音乐」）。
     *
     * 它不是一个歌单：网关侧收藏是独立的一组接口，没有歌单 id，所以单独一条路由，
     * 从歌单页签顶部那个固定入口进来。
     */
    const val FAVORITES = "favorites"

    /* ---- 歌手 / 专辑 ---- */

    const val MEDIA_ARG_PLATFORM = "platform"
    const val MEDIA_ARG_ID = "id"
    const val MEDIA_ARG_NAME = "name"

    const val ARTIST = "artist/{$MEDIA_ARG_PLATFORM}/{$MEDIA_ARG_ID}?$MEDIA_ARG_NAME={$MEDIA_ARG_NAME}"
    const val ALBUM = "album/{$MEDIA_ARG_PLATFORM}/{$MEDIA_ARG_ID}?$MEDIA_ARG_NAME={$MEDIA_ARG_NAME}"

    fun artist(platform: Platform, id: String, name: String): String =
        "artist/${platform.id}/${Uri.encode(id)}?$MEDIA_ARG_NAME=${Uri.encode(name)}"

    fun album(platform: Platform, id: String, name: String): String =
        "album/${platform.id}/${Uri.encode(id)}?$MEDIA_ARG_NAME=${Uri.encode(name)}"

    // 音乐库没有独立路由：它的三段内容排在账号页里。

    /** 底部栏里的目的地，顺序即栏内顺序。 */
    val mainDestinations = listOf(HOME, ACCOUNT)
}

/** 榜单与平台歌单走的是两组不同的接口，这个枚举决定用哪一个。 */
enum class CollectionKind(val id: String, val label: String) {
    /** 平台榜单。 */
    TOPLIST("toplist", "榜单"),

    /** 平台歌单（来自搜索结果或发现页）。 */
    PLAYLIST("playlist", "歌单");

    companion object {
        fun fromId(id: String?): CollectionKind =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: TOPLIST
    }
}

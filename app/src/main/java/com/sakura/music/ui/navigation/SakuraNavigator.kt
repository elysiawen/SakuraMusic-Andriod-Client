package com.sakura.music.ui.navigation

import androidx.navigation.NavHostController
import com.sakura.music.data.model.Platform

/**
 * 页面之间的跳转入口。
 *
 * 比起把五六个 lambda 挨个传进每个页面，这里只递一个对象：跳转规则集中在一处，
 * 页面也不关心路由字符串长什么样。
 */
class SakuraNavigator(private val nav: NavHostController) {

    /** 从顶部的搜索条进来；同一个搜索页只留一个实例。 */
    fun openSearch() {
        nav.navigate(Routes.SEARCH) { launchSingleTop = true }
    }

    /** 从账号页右上角的小齿轮进来。 */
    fun openSettings() {
        nav.navigate(Routes.SETTINGS) { launchSingleTop = true }
    }

    /** 从账号页的头像那一栏进来。 */
    fun openAccountDetail() {
        nav.navigate(Routes.ACCOUNT_DETAIL) { launchSingleTop = true }
    }

    fun openCollection(platform: Platform, id: String, kind: CollectionKind, title: String) {
        nav.navigate(Routes.collection(platform, id, kind, title))
    }

    fun openPlaylist(id: String, name: String) {
        nav.navigate(Routes.playlist(id, name))
    }

    /** 收藏：歌单页签里「我喜欢的音乐」那一行。 */
    fun openFavorites() {
        nav.navigate(Routes.FAVORITES) { launchSingleTop = true }
    }

    fun openArtist(platform: Platform, id: String, name: String) {
        nav.navigate(Routes.artist(platform, id, name))
    }

    fun openAlbum(platform: Platform, id: String, name: String) {
        nav.navigate(Routes.album(platform, id, name))
    }

    fun back() {
        nav.popBackStack()
    }
}

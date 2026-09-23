package com.sakura.music.data.repo

import com.sakura.music.data.model.PublicUser
import com.sakura.music.data.model.UserStats
import com.sakura.music.data.prefs.SettingsStore
import com.sakura.music.data.remote.SakuraApi
import com.sakura.music.data.session.SakuraCookieJar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 登录态快照。 */
data class AuthState(
    val user: PublicUser? = null,
    val stats: UserStats? = null,
    /** 启动时是否已经问过服务端「我是谁」。 */
    val resolved: Boolean = false,
    /** 正在跑登录 / 注册 / 登出这类有交互反馈的操作。 */
    val busy: Boolean = false,
    /**
     * 没问出登录态（网络不通），但本机留着会话 Cookie，于是先按已登录处理。
     *
     * 这条路径是为了断网也能用：用户可能只是想听下载好的歌，不该被拦在登录页外——
     * 那里的登录同样要联网，等于把人锁死。等网络恢复后 [AuthRepository.refresh] 会给出
     * 真正的答案；服务端明确说未登录（401）时也会照常踢回登录页。
     */
    val offline: Boolean = false,
) {
    val isLoggedIn: Boolean get() = user != null || offline
}

/**
 * 会话与账户。
 *
 * 登录态的唯一来源是服务端的 `/api/auth/me`——本地那个 Cookie 只表示「可能登录着」，
 * 服务端删掉会话行之后它依然在，所以不能拿它当鉴权探针。
 */
class AuthRepository(
    private val api: SakuraApi,
    private val cookies: SakuraCookieJar,
    private val settings: SettingsStore,
) {

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /** 启动时调用一次，拿到登录态与统计。 */
    suspend fun refresh() {
        runCatching { api.me() }
            .onSuccess { response ->
                _state.update {
                    it.copy(user = response.user, stats = response.stats, resolved = true, offline = false)
                }
            }
            .onFailure {
                // 网络不通时不要把人踢下线，保留上一次的登录态；只有服务端明确说
                // 未登录（401）才会走到 onSessionExpired 里清状态。
                //
                // 冷启动时上一次的登录态是空的，所以这里再看一眼本机有没有会话 Cookie：
                // 有就先进主界面（离线也能听本地歌），没有才当作没登录。
                _state.update {
                    it.copy(resolved = true, offline = it.user == null && cookies.hasSessionCookie())
                }
            }
    }

    suspend fun login(username: String, password: String) {
        _state.update { it.copy(busy = true) }
        try {
            val response = api.login(username.trim(), password)
            settings.setRememberedUsername(username.trim())
            _state.update { it.copy(user = response.user, busy = false) }
            refreshStats()
        } catch (error: Throwable) {
            _state.update { it.copy(busy = false) }
            throw error
        }
    }

    suspend fun register(username: String, password: String, nickname: String?) {
        _state.update { it.copy(busy = true) }
        try {
            val response = api.register(username.trim(), password, nickname?.trim())
            settings.setRememberedUsername(username.trim())
            _state.update { it.copy(user = response.user, busy = false) }
            refreshStats()
        } catch (error: Throwable) {
            _state.update { it.copy(busy = false) }
            throw error
        }
    }

    suspend fun logout() {
        _state.update { it.copy(busy = true) }
        // 服务端会话已经无效时登出照样算成功，本地清理不能少。
        runCatching { api.logout() }
        cookies.clear()
        _state.value = AuthState(resolved = true)
    }

    suspend fun updateProfile(nickname: String?, avatar: String?, clearAvatar: Boolean = false) {
        val response = api.updateProfile(nickname = nickname, avatar = avatar, clearAvatar = clearAvatar)
        _state.update { it.copy(user = response.user) }
    }

    /**
     * 改密码。
     *
     * 成功后服务端会把该用户所有设备的会话全部作废，包括当前这台，
     * 所以本地必须一起登出，否则每个请求都会 401。
     */
    suspend fun changePassword(oldPassword: String, newPassword: String) {
        api.changePassword(oldPassword, newPassword)
        cookies.clear()
        _state.value = AuthState(resolved = true)
    }

    suspend fun refreshStats() {
        if (!_state.value.isLoggedIn) return
        runCatching { api.me() }.onSuccess { response ->
            _state.update { it.copy(user = response.user, stats = response.stats) }
        }
    }

    /** 被 [com.sakura.music.data.remote.ApiClient] 在 401 时回调。 */
    fun onSessionExpired() {
        cookies.clear()
        _state.value = AuthState(resolved = true)
    }
}

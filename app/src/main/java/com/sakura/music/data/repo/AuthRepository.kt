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
    /** 正在跑登录 / 注册 / 登出这类有交互反馈的操作。 */
    val busy: Boolean = false,
    /**
     * 还没向服务端确认过身份，但本机留着会话 Cookie，于是先按已登录处理。
     *
     * 冷启动的首帧就是这个状态：先放人进主界面（断网也能听下载好的歌），
     * 真正的登录态由后台的 [AuthRepository.refresh] 补上；服务端明确说未登录（401）时
     * 会照常踢回登录页。把确认放在界面之后，是为了不让「网关没部署 / 地址没配好」
     * 的普通用户卡在连接超时里。
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

    /**
     * 首帧就同步给出结论，不留「等网络回答」的启动门。
     *
     * 留着会话说明此前登录过，值得后台再问一次服务端；没有会话（首次安装、登出之后）
     * 就直接判定未登录——这时候连网关没有意义，网关地址可能还没配好。
     */
    private val _state = MutableStateFlow(
        if (cookies.hasSessionCookie()) AuthState(offline = true) else AuthState()
    )
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /**
     * 冷启动时调用一次：只有本机留着会话时才去连网关确认登录态。
     *
     * 首次安装时 `/api/auth/me` 最多也只会回答「没登录」，而构建时注入的默认地址
     * 在真机上根本不可达；发出去只会让启动页干等连接超时，还把「修改网关地址」
     * 的入口挡在后面。等用户真的点登录、或主动配置地址时再连不迟。
     */
    suspend fun refreshIfSessionExists() {
        if (_state.value.user != null || cookies.hasSessionCookie()) refresh()
    }

    /** 向服务端确认登录态与统计；网络不通时保留本机会话，按离线处理。 */
    suspend fun refresh() {
        runCatching { api.me() }
            .onSuccess { response ->
                _state.update {
                    it.copy(user = response.user, stats = response.stats, offline = false)
                }
            }
            .onFailure {
                // 网络不通时不要把人踢下线，保留上一次的登录态；只有服务端明确说
                // 未登录（401）才会走到 onSessionExpired 里清状态。
                _state.update {
                    it.copy(offline = it.user == null && cookies.hasSessionCookie())
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
        _state.value = AuthState()
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
        _state.value = AuthState()
    }

    suspend fun refreshStats() {
        if (!_state.value.isLoggedIn) return
        runCatching { api.me() }.onSuccess { response ->
            _state.update { it.copy(user = response.user, stats = response.stats) }
        }
    }

    /**
     * 被 [com.sakura.music.data.remote.ApiClient] 在 `401 + unauthorized` 时回调。
     *
     * 网关的会话是**滑动续期**：活跃期间服务端会用 `Set-Cookie` 延长 Cookie 的有效期，
     * CookieJar 会照常把它落盘，这里不需要额外的刷新接口。真正到顶（绝对上限）或
     * 会话在别处作废时才走到这一步；多个并发请求一起 401 时只收尾一次。
     */
    fun onSessionExpired() {
        if (_state.value.user == null && !_state.value.offline) return
        cookies.clear()
        _state.value = AuthState()
    }
}

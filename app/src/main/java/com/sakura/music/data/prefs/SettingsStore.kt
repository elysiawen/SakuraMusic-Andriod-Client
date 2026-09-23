package com.sakura.music.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sakura.music.BuildConfig
import com.sakura.music.data.model.Platform
import com.sakura.music.data.model.Quality
import com.sakura.music.ui.theme.AccentTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "sakura_music_settings")

/** 应用级偏好：外观、音质、播放模式、音源偏好，以及「直连不通」的平台记录。 */
class SettingsStore(private val context: Context) {

    private object Keys {
        /** 音频缓存上限（MB）。 */
        val AudioCacheMb = intPreferencesKey("audio_cache_mb")
        /** 封面缓存上限（MB）。 */
        val CoverCacheMb = intPreferencesKey("cover_cache_mb")
        /** 歌词缓存有效期（天），[FOREVER] 表示永不过期。 */
        val LyricCacheDays = intPreferencesKey("lyric_cache_days")
        /** 歌曲与专辑信息缓存有效期（天），[FOREVER] 表示永不过期。 */
        val MetaCacheDays = intPreferencesKey("meta_cache_days")
        val Accent = stringPreferencesKey("accent")
        val ThemeMode = stringPreferencesKey("theme_mode")
        val Quality = stringPreferencesKey("quality")
        val PlayMode = stringPreferencesKey("play_mode")
        /** 是否与其他应用同时出声（不抢占音频焦点）。 */
        val MixWithOthers = booleanPreferencesKey("mix_with_others")
        /** 仅在 Wi-Fi 下联网取流；移动网络下只播已缓存的歌。 */
        val WifiOnly = booleanPreferencesKey("wifi_only")
        val PreferredPlatform = stringPreferencesKey("preferred_platform")
        /** 直连被 CDN 拒过的平台：这些平台之后一律走网关中转（只对「智能」有用）。 */
        val ProxyOnlyPlatforms = stringSetPreferencesKey("proxy_only_platforms")

        /** 每个平台的取流方式（`smart` / `direct` / `proxy`），见 [RoutePreference]。 */
        fun routePreference(platform: Platform) =
            stringPreferencesKey("route_preference_${platform.id}")
        val RememberedUsername = stringPreferencesKey("remembered_username")

        /** 搜索记录：换行分隔（关键词里的空白在存之前就被压平了，不会含换行）。 */
        val SearchHistory = stringPreferencesKey("search_history")
    }

    /**
     * 网关地址单独存在 SharedPreferences 里，没有跟其它偏好一起进 DataStore。
     *
     * 原因很具体：它在冷启动的**第一次网络请求之前**就要用上（第一个请求就是探登录态的
     * `/api/auth/me`），而 DataStore 是异步的——用 DataStore 的话，首帧到第一次读取之间
     * 有个窗口会拿到默认值。SharedPreferences 的读取是同步的，这个问题就不存在了。
     */
    private val gatewayPrefs = context.getSharedPreferences(PREFS_GATEWAY, Context.MODE_PRIVATE)

    private val _gatewayUrl = MutableStateFlow(
        gatewayPrefs.getString(KEY_GATEWAY_URL, null)
            ?.let(::normalizeGatewayUrl)
            ?: DEFAULT_GATEWAY_URL
    )

    /** 当前生效的网关地址（已规范化）。 */
    val gatewayUrl: StateFlow<String> = _gatewayUrl.asStateFlow()

    /** 同步读取，供请求链路使用——它们没有挂起的机会。 */
    fun gatewayUrlNow(): String = _gatewayUrl.value

    val audioCacheMb: Flow<Int> = context.settingsDataStore.data
        .map { it[Keys.AudioCacheMb] ?: DEFAULT_AUDIO_CACHE_MB }

    val coverCacheMb: Flow<Int> = context.settingsDataStore.data
        .map { it[Keys.CoverCacheMb] ?: DEFAULT_COVER_CACHE_MB }

    val lyricCacheDays: Flow<Int> = context.settingsDataStore.data
        .map { it[Keys.LyricCacheDays] ?: DEFAULT_LYRIC_CACHE_DAYS }

    val metaCacheDays: Flow<Int> = context.settingsDataStore.data
        .map { it[Keys.MetaCacheDays] ?: DEFAULT_META_CACHE_DAYS }

    val accent: Flow<AccentTheme> = context.settingsDataStore.data
        .map { AccentTheme.fromId(it[Keys.Accent]) }

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data
        .map { ThemeMode.fromId(it[Keys.ThemeMode]) }

    val quality: Flow<Quality> = context.settingsDataStore.data
        .map { Quality.fromId(it[Keys.Quality]) }

    val playMode: Flow<PlayMode> = context.settingsDataStore.data
        .map { PlayMode.fromId(it[Keys.PlayMode]) }

    /**
     * 和其他应用一起播放。
     *
     * 默认关闭：正常播放时应当抢占音频焦点，别的应用出声就暂停，这是用户对播放器的预期。
     * 打开之后就各放各的（导航播报、视频配音之类需要混在一起的场景）。
     */
    val mixWithOthers: Flow<Boolean> = context.settingsDataStore.data
        .map { it[Keys.MixWithOthers] ?: false }

    /**
     * 仅 Wi-Fi：移动网络下不联网取音频，只播已经缓存（或下载）下来的歌。
     *
     * 默认关闭：这是「省流量」的取舍，开不开由用户决定。
     */
    val wifiOnly: Flow<Boolean> = context.settingsDataStore.data
        .map { it[Keys.WifiOnly] ?: false }

    /** 用户手动指定的音源平台；为空表示按歌曲自带的优先级。 */
    val preferredPlatform: Flow<Platform?> = context.settingsDataStore.data
        .map { it[Keys.PreferredPlatform]?.let(Platform::fromId)?.takeIf { p -> p.isKnown } }

    val proxyOnlyPlatforms: Flow<Set<Platform>> = context.settingsDataStore.data
        .map { prefs -> (prefs[Keys.ProxyOnlyPlatforms] ?: emptySet()).map(Platform::fromId).toSet() }

    /** 某个平台的取流方式；没设过就是「智能」。 */
    fun routePreference(platform: Platform): Flow<RoutePreference> = context.settingsDataStore.data
        .map { RoutePreference.fromId(it[Keys.routePreference(platform)]) }

    val rememberedUsername: Flow<String> = context.settingsDataStore.data
        .map { it[Keys.RememberedUsername].orEmpty() }

    /** 搜索记录，最近搜过的在最前。 */
    val searchHistory: Flow<List<String>> = context.settingsDataStore.data
        .map { decodeHistory(it[Keys.SearchHistory]) }

    suspend fun setAudioCacheMb(value: Int) = edit { it[Keys.AudioCacheMb] = value }

    suspend fun setCoverCacheMb(value: Int) = edit { it[Keys.CoverCacheMb] = value }

    suspend fun setLyricCacheDays(value: Int) = edit { it[Keys.LyricCacheDays] = value }

    suspend fun setMetaCacheDays(value: Int) = edit { it[Keys.MetaCacheDays] = value }

    suspend fun setAccent(value: AccentTheme) = edit { it[Keys.Accent] = value.name }

    suspend fun setThemeMode(value: ThemeMode) = edit { it[Keys.ThemeMode] = value.name }

    suspend fun setQuality(value: Quality) = edit { it[Keys.Quality] = value.id }

    suspend fun setPlayMode(value: PlayMode) = edit { it[Keys.PlayMode] = value.name }

    suspend fun setMixWithOthers(value: Boolean) = edit { it[Keys.MixWithOthers] = value }

    suspend fun setWifiOnly(value: Boolean) = edit { it[Keys.WifiOnly] = value }

    suspend fun setPreferredPlatform(value: Platform?) = edit { prefs ->
        if (value == null || !value.isKnown) prefs.remove(Keys.PreferredPlatform)
        else prefs[Keys.PreferredPlatform] = value.id
    }

    suspend fun rememberProxyOnly(platform: Platform) = edit { prefs ->
        if (!platform.isKnown) return@edit
        val next = (prefs[Keys.ProxyOnlyPlatforms] ?: emptySet()) + platform.id
        prefs[Keys.ProxyOnlyPlatforms] = next
    }

    suspend fun setRoutePreference(platform: Platform, value: RoutePreference) = edit { prefs ->
        if (!platform.isKnown) return@edit
        // 「智能」是默认值：删掉键就是它，不必存一份和默认一样的东西。
        if (value == RoutePreference.Default) prefs.remove(Keys.routePreference(platform))
        else prefs[Keys.routePreference(platform)] = value.id
    }

    /**
     * 忘掉某个平台「直连不通」的记录。
     *
     * 这个判断会被持久化，而触发它的原因常常是一次性的（当时网络不通、上游临时改策略），
     * 所以直连一旦真的成功就得把它抹掉，否则一次失败会把平台永久钉在网关中转上。
     * 出口是「直连」（用户锁直连验证通过就自动清），不再需要单独的重置按钮。
     */
    suspend fun forgetProxyOnly(platform: Platform) = edit { prefs ->
        if (!platform.isKnown) return@edit
        val current = prefs[Keys.ProxyOnlyPlatforms] ?: return@edit
        val next = current - platform.id
        if (next.isEmpty()) prefs.remove(Keys.ProxyOnlyPlatforms)
        else prefs[Keys.ProxyOnlyPlatforms] = next
    }

    suspend fun setRememberedUsername(value: String) = edit {
        if (value.isBlank()) it.remove(Keys.RememberedUsername) else it[Keys.RememberedUsername] = value
    }

    /**
     * 记一条搜索记录。
     *
     * 去重（搜过的词会被提到最前，而不是留下第二条），并截断到 [MAX_SEARCH_HISTORY] 条。
     * 关键词先做空白压平：它同时是存储时的分隔符依据，也免得记录里出现一堆奇怪的空格。
     */
    suspend fun rememberSearch(keyword: String) = edit { prefs ->
        val normalized = normalizeKeyword(keyword)
        if (normalized.isEmpty()) return@edit
        val next = (listOf(normalized) + decodeHistory(prefs[Keys.SearchHistory]).filterNot {
            it.equals(normalized, ignoreCase = true)
        }).take(MAX_SEARCH_HISTORY)
        prefs[Keys.SearchHistory] = next.joinToString(HISTORY_SEPARATOR)
    }

    suspend fun removeSearch(keyword: String) = edit { prefs ->
        val next = decodeHistory(prefs[Keys.SearchHistory]).filterNot {
            it.equals(keyword, ignoreCase = true)
        }
        if (next.isEmpty()) {
            prefs.remove(Keys.SearchHistory)
        } else {
            prefs[Keys.SearchHistory] = next.joinToString(HISTORY_SEPARATOR)
        }
    }

    suspend fun clearSearchHistory() = edit { it.remove(Keys.SearchHistory) }

    /** 保存网关地址；传进来的地址解析不了时回落到默认值。 */
    fun setGatewayUrl(raw: String) {
        val normalized = normalizeGatewayUrl(raw) ?: DEFAULT_GATEWAY_URL
        gatewayPrefs.edit().putString(KEY_GATEWAY_URL, normalized).apply()
        _gatewayUrl.value = normalized
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(block)
    }

    companion object {
        /**
         * 构建时注入的默认地址，见 `gradle.properties` 的 `sakura.gateway.url`。
         * 应用内改地址改的是它之上的覆盖值，「恢复默认」就是回到这里。
         */
        val DEFAULT_GATEWAY_URL: String = BuildConfig.GATEWAY_BASE_URL

        /** 搜索记录留最近这么多条。 */
        const val MAX_SEARCH_HISTORY = 12

        /** 有效期里的「永久」：换算成毫秒后是 Long.MAX_VALUE。 */
        const val FOREVER = -1

        /** 缓存上限里的「不缓存」：滑块最左端。 */
        const val NO_CACHE_MB = 0

        /** 缓存上限里的「无限」：滑块最右端，换算后是 Long.MAX_VALUE。 */
        const val UNLIMITED_MB = -1

        const val DEFAULT_AUDIO_CACHE_MB = 1024
        const val DEFAULT_COVER_CACHE_MB = 128
        const val DEFAULT_LYRIC_CACHE_DAYS = 30
        const val DEFAULT_META_CACHE_DAYS = 7

        /** 天数换算成毫秒；[FOREVER] 映射到 Long.MAX_VALUE。 */
        fun daysToMillis(days: Int): Long =
            if (days < 0) Long.MAX_VALUE else days * 24L * 60 * 60 * 1000

        fun mbToBytes(mb: Int): Long = mb.toLong() * 1024 * 1024

        private const val PREFS_GATEWAY = "sakura_gateway"
        private const val KEY_GATEWAY_URL = "gateway_url"

        /** 搜索记录的存储分隔符：关键词在存之前已压平空白，所以不会含换行。 */
        private const val HISTORY_SEPARATOR = "\n"

        private val WHITESPACE = Regex("\\s+")
    }

    private fun decodeHistory(raw: String?): List<String> =
        raw?.split(HISTORY_SEPARATOR)
            ?.map(::normalizeKeyword)
            ?.filter { it.isNotEmpty() }
            ?.take(MAX_SEARCH_HISTORY)
            ?: emptyList()

    /** 压平空白并去掉首尾空格；搜索关键词本来也不需要多段空白。 */
    private fun normalizeKeyword(keyword: String): String =
        keyword.trim().replace(WHITESPACE, " ").trim()
}

/** 面向界面的播放模式：把底层的「循环开关」与「随机开关」合成一个列表。 */
enum class PlayMode(val label: String) {
    /** 顺序播放，播完即停。 */
    Order("顺序播放"),

    /** 列表循环。 */
    All("列表循环"),

    /** 单曲循环。 */
    One("单曲循环"),

    /** 随机播放。 */
    Shuffle("随机播放");

    /** 播放器顶栏那个按钮的循环顺序：顺序 → 列表循环 → 单曲循环 → 随机 → 顺序。 */
    fun next(): PlayMode = when (this) {
        Order -> All
        All -> One
        One -> Shuffle
        Shuffle -> Order
    }

    companion object {
        fun fromId(id: String?): PlayMode =
            entries.firstOrNull { it.name.equals(id, ignoreCase = true) } ?: All
    }
}

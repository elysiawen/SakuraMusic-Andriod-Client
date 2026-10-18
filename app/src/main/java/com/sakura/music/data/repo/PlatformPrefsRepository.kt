package com.sakura.music.data.repo

import com.sakura.music.data.model.Platform
import com.sakura.music.data.prefs.PlatformCapability
import com.sakura.music.data.prefs.PlatformPrefs
import com.sakura.music.data.prefs.SettingsStore
import com.sakura.music.data.remote.SakuraApi
import com.sakura.music.data.remote.friendlyMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * 平台开关（总开关 + 能力开关）。
 *
 * 数据只存在网关账号下：本地 DataStore 那份是冷启动首屏用的快照，
 * 登录后由 [refreshFromCloud] 用云端那份覆盖；每次改动都把整份推回去。
 *
 * 这里同时负责「不能把最后一个可搜索的平台关掉」这条约束 ——
 * 放在数据层而不是界面层，是因为设置页与将来的其它入口都要用同一套判断。
 */
class PlatformPrefsRepository(
    private val api: SakuraApi,
    private val settings: SettingsStore,
    private val auth: AuthRepository,
    scope: CoroutineScope,
) {

    val state: StateFlow<PlatformPrefs> = settings.platformPrefs
        .stateIn(scope, SharingStarted.Eagerly, PlatformPrefs())

    /** 请求参数用的平台串：全部启用时为 null（与没有这个功能时完全一致）。 */
    fun queryParam(capability: PlatformCapability): String? = state.value.queryParam(capability)

    fun isEnabled(platform: Platform, capability: PlatformCapability): Boolean =
        state.value.isEnabled(platform, capability)

    fun isPlatformEnabled(platform: Platform): Boolean = state.value.isPlatformEnabled(platform)

    /** 总开关。关掉即该平台任何功能都不参与，但能力开关的值会保留。 */
    suspend fun setPlatformEnabled(platform: Platform, enabled: Boolean): String? {
        if (!enabled) {
            val rest = Platform.known.filter {
                it != platform && state.value.isEnabled(it, PlatformCapability.SEARCH)
            }
            if (rest.isEmpty()) return "至少要保留一个可搜索的平台，否则搜索会没有结果"
        }
        settings.setPlatformEnabled(platform, enabled)
        push()
        return null
    }

    suspend fun setCapabilityEnabled(
        platform: Platform,
        capability: PlatformCapability,
        enabled: Boolean,
    ): String? {
        if (!enabled && capability == PlatformCapability.SEARCH) {
            val rest = Platform.known.filter {
                it != platform && state.value.isEnabled(it, PlatformCapability.SEARCH)
            }
            if (rest.isEmpty()) return "至少要保留一个平台的「${capability.label}」"
        }
        settings.setCapabilityEnabled(platform, capability, enabled)
        push()
        return null
    }

    /**
     * 云端 → 本地；云端还没有这份配置时，把当前这份推上去。
     *
     * 未登录直接返回 null：本机照常按快照工作，不把「没登录」当成错误。
     */
    suspend fun refreshFromCloud(): String? {
        if (!auth.state.value.isLoggedIn) return null
        return runCatching {
            val entry = api.preferences().items.firstOrNull { it.key == KEY }
            if (entry == null) {
                api.putPreference(KEY, payload(settings.platformPrefs.first()))
            } else {
                parse(entry.value)?.let { settings.applyPlatformPrefs(it) }
            }
            null
        }.getOrElse { it.friendlyMessage() }
    }

    private suspend fun push() {
        if (!auth.state.value.isLoggedIn) return
        // 等 DataStore 落地后再取，避免把改动前的那份推上去。
        val latest = settings.platformPrefs.first()
        runCatching { api.putPreference(KEY, payload(latest)) }
    }

    private fun payload(prefs: PlatformPrefs): JsonObject = buildJsonObject {
        putJsonObject("enabled") {
            Platform.known.forEach { platform -> put(platform.id, prefs.isPlatformEnabled(platform)) }
        }
        putJsonObject("switches") {
            Platform.known.forEach { platform ->
                putJsonObject(platform.id) {
                    PlatformCapability.entries.forEach { capability ->
                        put(capability.id, prefs.capabilities[platform]?.get(capability) ?: true)
                    }
                }
            }
        }
    }

    /** 缺字段一律按「启用」：老版本只推过 `switches`，没有 `enabled`。 */
    private fun parse(value: JsonElement?): PlatformPrefs? {
        val root = value as? JsonObject ?: return null
        val enabledObject = root["enabled"] as? JsonObject
        val switches = root["switches"] as? JsonObject
        return PlatformPrefs(
            enabled = Platform.known.associateWith { platform ->
                (enabledObject?.get(platform.id) as? JsonPrimitive)?.booleanOrNull ?: true
            },
            capabilities = Platform.known.associateWith { platform ->
                val platformSwitches = switches?.get(platform.id) as? JsonObject
                PlatformCapability.entries.associateWith { capability ->
                    (platformSwitches?.get(capability.id) as? JsonPrimitive)?.booleanOrNull ?: true
                }
            },
        )
    }

    companion object {
        /** 与网页端共用的云端键名。 */
        const val KEY = "platforms"
    }
}

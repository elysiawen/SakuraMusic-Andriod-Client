package com.sakura.music.data.room

import android.content.Context

/**
 * 记住当前所在的房间码。
 *
 * 房间的成员与播放态都能靠重连后服务端的补推回来，唯独「我在哪个房间」这条信息
 * 只存在于内存里——App 被杀掉再打开、或者网关重启把我们踢下线之后，就再也找不回来了。
 * 存一份，冷启动凭它自动回房。
 */
class RoomCodeStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 记住的房间。
     *
     * @param seat 本机当时是不是**占着席位**（播放就在这台设备上）。差别很重要：
     *   占着席位的设备重开 App 要凭它回来接着放；只是旁观的那台则绝不能自动 `join`
     *   ——那会把播放从真正在放的那台设备抢过来。
     */
    data class SavedRoom(val code: String, val seat: Boolean)

    fun load(): SavedRoom? {
        val code = prefs.getString(KEY_CODE, null)?.takeIf { it.isNotBlank() } ?: return null
        return SavedRoom(code = code, seat = prefs.getBoolean(KEY_SEAT, true))
    }

    fun save(code: String, seat: Boolean) {
        prefs.edit().putString(KEY_CODE, code).putBoolean(KEY_SEAT, seat).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_CODE).remove(KEY_SEAT).apply()
    }

    private companion object {
        const val PREFS_NAME = "sakura_room"
        const val KEY_CODE = "room_code"
        const val KEY_SEAT = "room_seat"
    }
}

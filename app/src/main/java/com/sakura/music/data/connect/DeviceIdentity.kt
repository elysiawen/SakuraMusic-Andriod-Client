package com.sakura.music.data.connect

import android.content.Context
import android.os.Build
import java.util.UUID

/**
 * 本机在多设备列表里的身份。
 *
 * `deviceId` 只在首次运行生成一次，之后一直沿用——它决定了「这台设备是不是上次那台」。
 * 换掉就等于换了台新设备（旧的那条会随连接断开从别人的列表里消失，不会留幽灵，
 * 只是名字得重新起）。
 *
 * 粒度是**一个播放实例**一个：Android 上一台设备一个，所以用设备级持久化就够了。
 */
class DeviceIdentity(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 服务端要求非空且 ≤ 64 字符，UUID 去掉横线正好 32 位。 */
    val id: String = prefs.getString(KEY_DEVICE_ID, null)
        ?: UUID.randomUUID().toString().replace("-", "").also { generated ->
            prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
        }

    /**
     * 展示用名称。用机型而不是「我的手机」之类：多设备列表里一眼能认出是哪台。
     * 服务端会截到 40 字符，这里也顺手截一下。
     */
    val name: String = prefs.getString(KEY_DEVICE_NAME, null)
        ?: defaultName().also { prefs.edit().putString(KEY_DEVICE_NAME, it).apply() }

    /** 供界面挑图标：协议里只认 android / windows，其它值一律当 web。 */
    val kind: String = KIND_ANDROID

    private fun defaultName(): String {
        val model = Build.MODEL?.trim().orEmpty()
        return (model.ifEmpty { "Android" }).take(MAX_NAME_LENGTH)
    }

    private companion object {
        const val PREFS_NAME = "sakura_device"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_DEVICE_NAME = "device_name"
        const val KIND_ANDROID = "android"
        const val MAX_NAME_LENGTH = 40
    }
}

package com.sakura.music.core.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 播放服务当前的 audio session id。
 *
 * 只有 ExoPlayer 自己知道这个 id，而界面手上只有跨进程的 MediaController 遥控器，
 * 拿不到真实会话。服务与界面同进程（Manifest 未声明 `android:process`），所以用一个
 * 进程内单例把 id 递给频谱可视化就够了，不必为它加自定义会话命令。
 *
 * 0 表示播放器还没建好或服务已销毁，此时沉浸样式会自动回退到模拟频谱。
 */
object AudioSessionBridge {

    private val _sessionId = MutableStateFlow(0)
    val sessionId: StateFlow<Int> = _sessionId.asStateFlow()

    fun publish(id: Int) {
        _sessionId.value = id
    }

    fun clear() {
        _sessionId.value = 0
    }
}

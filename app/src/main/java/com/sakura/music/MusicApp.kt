package com.sakura.music

import android.app.Application
import coil.Coil

/** 应用入口：只负责把依赖容器建起来，播放服务等也从这个容器里取依赖。 */
class MusicApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 封面走容器里那个 ImageLoader：它的磁盘缓存目录和上限是固定的，
        // 设置页才能把它和其它缓存一起统计、一起清理。
        Coil.setImageLoader(container.cacheManager.imageLoader)
    }
}

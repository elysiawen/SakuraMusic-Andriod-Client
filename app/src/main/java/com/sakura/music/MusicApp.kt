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
        // 容器构造完了才能起后台观察者：构造过程中就去访问懒加载属性是竞态，
        // 会在启动瞬间随机崩（详见 AppContainer.start 的说明）。
        container.start()
        // 封面走容器里那个 ImageLoader：它的磁盘缓存目录和上限是固定的，
        // 设置页才能把它和其它缓存一起统计、一起清理。
        Coil.setImageLoader(container.cacheManager.imageLoader)
    }
}

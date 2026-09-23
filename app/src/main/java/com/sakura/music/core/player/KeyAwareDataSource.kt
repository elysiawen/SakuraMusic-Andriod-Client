package com.sakura.music.core.player

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheKeyFactory
import java.util.concurrent.atomic.AtomicReference

/**
 * 音频缓存的键：`平台:ID:音质`。
 *
 * 默认的键是整条 URI（`sakura://track/…?quality=…`）。播放时看不出差别，但下载、
 * 统计占用、删除都按 [PlayRequest.cacheKey] 去找内容——两边不一致，就会出现
 * 「歌下下来了、大小却显示 0」这种对不上的情况。所以播放与下载都显式用这一个。
 */
val sakuraCacheKeyFactory: CacheKeyFactory = CacheKeyFactory { dataSpec ->
    PlayRequest.from(dataSpec.uri)?.cacheKey ?: dataSpec.uri.toString()
}

/**
 * 记得自己正在读哪首歌的数据源包装。
 *
 * 缓存层「读到了本地字节」的回调只给字节数、不给曲目。没有这一层包装的话，
 * 下一首被预加载并命中缓存时，那份缓存命中会被记到**当前这首**的头上——
 * 于是明明在读本地，角标却说是 CDN。
 *
 * 包装而不是继承：[androidx.media3.datasource.cache.CacheDataSource] 是 final 的，
 * 而且这里只需要在 open 那一刻记下 URI 对应的曲目键。
 */
class KeyAwareDataSource(
    private val inner: DataSource,
    private val keyHolder: AtomicReference<String?>,
    /** 打开时回调（曲目键, 读取起点）：路由在这一刻判定走哪条路。 */
    private val onOpened: ((String, Long) -> Unit)? = null,
) : DataSource by inner {

    override fun open(dataSpec: DataSpec): Long {
        val key = PlayRequest.from(dataSpec.uri)?.cacheKey
        keyHolder.set(key)
        if (key != null) onOpened?.invoke(key, dataSpec.position)
        return inner.open(dataSpec)
    }
}

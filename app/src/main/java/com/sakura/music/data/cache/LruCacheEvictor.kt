package com.sakura.music.data.cache

import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import java.util.TreeSet

/**
 * 按「最近最少使用」淘汰，且上限可以随时改。
 *
 * 自带的 [androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor] 把上限写死在构造参数上，
 * 而用户要在设置里随时调它。为了改一个数字去重建整个缓存（还会打断正在播的歌）不值得，
 * 所以这里的上限是个 lambda：每次淘汰时现取，改完设置下一次写入就生效。
 */
class LruCacheEvictor(
    private val maxBytes: () -> Long,
    /** 这些键永远不淘汰——它们是用户主动下载的内容，不是缓存。 */
    private val protectedKeys: () -> Set<String> = { emptySet() },
) : CacheEvictor {

    private val leastRecentlyUsed = TreeSet(
        compareBy<CacheSpan> { it.lastTouchTimestamp }
            // 时间戳撞车时再按位置排：同一时刻写下的两个分片不会被当成同一个。
            .thenBy { it.position }
    )

    @Synchronized
    override fun onCacheInitialized() = Unit

    /** 要写入 [maxLength] 字节之前先腾地方，免得一次下载就把上限顶穿。 */
    @Synchronized
    override fun onStartFile(cache: Cache, key: String, position: Long, maxLength: Long) {
        evict(cache, maxLength)
    }

    @Synchronized
    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.add(span)
        evict(cache, 0)
    }

    @Synchronized
    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.remove(span)
    }

    @Synchronized
    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        leastRecentlyUsed.remove(oldSpan)
        leastRecentlyUsed.add(newSpan)
    }

    override fun requiresCacheSpanTouches(): Boolean = true

    /**
     * 压回上限以内；[required] 是马上要写进去的字节数。
     *
     * 大小直接问 [Cache.getCacheSpace] 而不是自己累加：进程重启后已有的缓存分片
     * 未必会再通知一遍，自己数容易从 0 开始、把上限顶穿。
     */
    private fun evict(cache: Cache, required: Long) {
        val limit = maxBytes().coerceAtLeast(0L)
        val protected = protectedKeys()
        // 遍历快照：removeSpan 会同步回调 onSpanRemoved 改动集合本身。
        for (span in leastRecentlyUsed.toList()) {
            if (cache.cacheSpace + required <= limit) break
            // 用户下载的分片一直占着空间，但它们不参与淘汰，跳过即可——
            // 删够了就停，删不够（剩下的全是受保护的）也不再往下翻。
            if (span.key in protected) continue
            cache.removeSpan(span)
        }
    }
}

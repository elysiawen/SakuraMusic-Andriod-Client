package com.sakura.music.data.prefs

import android.content.Context
import com.sakura.music.data.model.UnifiedTrack
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 上次的播放现场：队列、听到第几首、听到哪儿了。 */
@Serializable
data class PlaybackSession(
    val queue: List<UnifiedTrack> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0L,
)

/**
 * 播放现场的落盘。
 *
 * 退出应用后再进来，队列和进度要还在——不然每次都得从搜索/发现里重新找那首歌。
 * 存的是一个 JSON 字符串，读的时候是同步的：启动时要立刻知道「上次听到哪儿」，
 * 而恢复这一步发生在第一帧之前（见 `PlaybackCenter.attach`）。
 */
class PlaybackSessionStore(
    context: Context,
    private val json: Json,
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * @param synchronous 进程可能马上就要没了的时候传 true（比如界面 ON_STOP）。
     *   `apply` 是异步落盘，那种情况下有丢的可能；`commit` 会阻塞几毫秒，但换确定。
     */
    fun save(session: PlaybackSession, synchronous: Boolean = false) {
        val payload = runCatching {
            json.encodeToString(PlaybackSession.serializer(), capQueue(session))
        }.getOrNull() ?: return

        val editor = prefs.edit().putString(KEY_SESSION, payload)
        if (synchronous) editor.commit() else editor.apply()
    }

    fun load(): PlaybackSession? = prefs.getString(KEY_SESSION, null)
        ?.let { raw -> runCatching { json.decodeFromString(PlaybackSession.serializer(), raw) }.getOrNull() }
        ?.takeIf { it.queue.isNotEmpty() }

    fun clear(synchronous: Boolean = false) {
        val editor = prefs.edit().remove(KEY_SESSION)
        if (synchronous) editor.commit() else editor.apply()
    }

    /**
     * 队列太长时只留当前曲目附近的一段。
     *
     * 偏好文件里塞几百首歌会让每次启动读盘变慢，而真正要「续上」的只有当前这一首
     * 和它前后的邻居；直接砍掉尾部会把正在听的那首一起砍掉，所以按当前下标取窗口。
     */
    private fun capQueue(session: PlaybackSession): PlaybackSession {
        val queue = session.queue
        if (queue.size <= MAX_QUEUE) return session

        val index = session.index.coerceIn(0, queue.lastIndex)
        val start = (index - MAX_QUEUE / 2).coerceIn(0, queue.size - MAX_QUEUE)
        return session.copy(
            queue = queue.subList(start, start + MAX_QUEUE),
            index = index - start,
        )
    }

    private companion object {
        const val PREFS_NAME = "sakura_playback"
        const val KEY_SESSION = "session"

        /** 落盘保留的队列上限。 */
        const val MAX_QUEUE = 200
    }
}

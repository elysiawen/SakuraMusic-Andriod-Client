package com.sakura.music.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakura.music.data.cache.CacheKind
import com.sakura.music.data.cache.CacheManager
import com.sakura.music.data.cache.CacheUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「设置 → 存储」的界面状态。
 *
 * 统计与清理都要遍历磁盘（几千个文件是常事），所以都丢到 IO 线程上跑；
 * 界面只读这两个流。
 */
class CacheSettingsViewModel(private val cacheManager: CacheManager) : ViewModel() {

    private val _usage = MutableStateFlow<List<CacheUsage>>(emptyList())
    val usage: StateFlow<List<CacheUsage>> = _usage.asStateFlow()

    /** 正在清理：期间按钮要禁掉，免得连点两次。 */
    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _usage.value = withContext(Dispatchers.IO) { cacheManager.usage() }
        }
    }

    fun clear(kind: CacheKind) {
        viewModelScope.launch {
            _working.value = true
            withContext(Dispatchers.IO) { cacheManager.clear(kind) }
            _working.value = false
            refresh()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            _working.value = true
            withContext(Dispatchers.IO) { cacheManager.clear() }
            _working.value = false
            refresh()
        }
    }
}

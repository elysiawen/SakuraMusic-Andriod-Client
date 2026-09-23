package com.sakura.music.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakura.music.data.remote.friendlyMessage
import com.sakura.music.data.repo.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 账号中心：改资料、改密码、登出。用户与统计本身来自 [AuthRepository] 的全局状态。 */
class AccountViewModel(
    private val auth: AuthRepository,
) : ViewModel() {

    data class UiState(
        val busy: Boolean = false,
        val message: String? = null,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * 保存资料。
     *
     * 头像留空时传 `clearAvatar = true`：网关把「省略字段」理解成不改，
     * 把「显式 null」理解成清空，两者必须区分开。
     */
    fun saveProfile(nickname: String, avatar: String?) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching {
                auth.updateProfile(
                    nickname = nickname.trim().takeIf { it.isNotEmpty() },
                    avatar = avatar?.trim()?.takeIf { it.isNotEmpty() },
                    clearAvatar = avatar.isNullOrBlank(),
                )
            }
                .onSuccess { _state.update { it.copy(busy = false, message = "资料已更新") } }
                .onFailure { error ->
                    _state.update { it.copy(busy = false, message = error.friendlyMessage()) }
                }
        }
    }

    fun changePassword(oldPassword: String, newPassword: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { auth.changePassword(oldPassword, newPassword) }
                .onSuccess {
                    // 改密码会作废所有设备的会话，包括当前这台，所以这里只能提示重新登录。
                    _state.update { it.copy(busy = false, message = "密码已修改，请重新登录") }
                }
                .onFailure { error ->
                    _state.update { it.copy(busy = false, message = error.friendlyMessage()) }
                }
        }
    }

    fun refreshStats() {
        viewModelScope.launch { auth.refreshStats() }
    }

    fun logout() {
        viewModelScope.launch { auth.logout() }
    }

    fun showMessage(text: String) = _state.update { it.copy(message = text) }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

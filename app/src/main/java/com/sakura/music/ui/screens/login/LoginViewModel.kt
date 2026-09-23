package com.sakura.music.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakura.music.data.prefs.SettingsStore
import com.sakura.music.data.remote.friendlyMessage
import com.sakura.music.data.repo.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val auth: AuthRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    data class UiState(
        val username: String = "",
        val password: String = "",
        val nickname: String = "",
        /** true = 注册，false = 登录。 */
        val registerMode: Boolean = false,
        val busy: Boolean = false,
        val error: String? = null,
    ) {
        val canSubmit: Boolean
            get() = username.trim().length >= 3 && password.length >= 8 && !busy
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        // 上次登录用过的用户名留在这里，省得每次都重新敲。
        viewModelScope.launch {
            val remembered = settings.rememberedUsername.first()
            if (remembered.isNotBlank()) _state.update { it.copy(username = remembered) }
        }
    }

    fun setUsername(value: String) = _state.update { it.copy(username = value, error = null) }

    fun setPassword(value: String) = _state.update { it.copy(password = value, error = null) }

    fun setNickname(value: String) = _state.update { it.copy(nickname = value, error = null) }

    fun toggleMode() = _state.update {
        it.copy(registerMode = !it.registerMode, error = null)
    }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(busy = true, error = null) }

        viewModelScope.launch {
            val result = runCatching {
                if (current.registerMode) {
                    auth.register(
                        username = current.username,
                        password = current.password,
                        nickname = current.nickname,
                    )
                } else {
                    auth.login(current.username, current.password)
                }
            }
            _state.update {
                it.copy(
                    busy = false,
                    error = result.exceptionOrNull()?.friendlyMessage(),
                )
            }
            // 成功时不需要额外做什么：登录态是全局的，根组件自己会切到主界面。
        }
    }
}

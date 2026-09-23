package com.sakura.music.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sakura.music.data.model.HealthResponse
import com.sakura.music.data.model.Platform
import com.sakura.music.data.prefs.normalizeGatewayUrl
import com.sakura.music.data.remote.friendlyMessage
import com.sakura.music.ui.appContainer
import kotlinx.coroutines.launch

/** 一次探活的结果，成功与失败共用一套展示。 */
private data class ProbeStatus(val ok: Boolean, val message: String)

/**
 * 改网关地址。
 *
 * 这一项之所以要能在应用内改：模拟器要用 `10.0.2.2`、真机要用局域网 IP、换台机器又要换地址——
 * 如果只能改 `gradle.properties` 重新打包，等于每换一次环境就发一次包。
 *
 * 自己从容器里取依赖，所以设置页和登录页都能直接放一个进来：这两处都需要它，
 * 因为地址填错时根本进不到设置页。
 */
@Composable
fun GatewayDialog(onDismiss: () -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    var text by remember { mutableStateOf(container.settings.gatewayUrlNow()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<ProbeStatus?>(null) }

    val normalized = normalizeGatewayUrl(text)

    /**
     * 拿当前输入探一次活。
     *
     * 跑在界面自己的作用域里：这个弹层没被销毁，结果就该显示在它里面。
     */
    fun testHere() {
        val target = normalized
        if (target == null) {
            status = ProbeStatus(false, "地址用不了：解析不出主机名。")
            return
        }
        keyboard?.hide()
        scope.launch {
            busy = true
            val result = runCatching { container.api.health(target) }
            busy = false
            status = result.fold(
                onSuccess = { ProbeStatus(true, healthSummary(it)) },
                onFailure = { ProbeStatus(false, it.friendlyMessage()) },
            )
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("网关地址") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        status = null
                    },
                    label = { Text("地址") },
                    placeholder = { Text("http://10.0.2.2:8787") },
                    singleLine = true,
                    enabled = !busy,
                    isError = text.isNotBlank() && normalized == null,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { testHere() }),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "模拟器访问本机网关填 10.0.2.2；真机填电脑的局域网 IP（例如 192.168.1.5:8787）。" +
                        "不写 http:// 会自动补上。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "登录态是按地址（域名）保存的，换地址后可能需要重新登录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { testHere() }, enabled = !busy && normalized != null) {
                        Text("测试连接")
                    }
                    TextButton(
                        onClick = {
                            text = container.defaultGatewayBaseUrl
                            status = null
                        },
                        enabled = !busy,
                    ) {
                        Text("恢复默认")
                    }
                    if (busy) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    }
                }

                status?.let { current ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = if (current.ok) {
                                Icons.Rounded.CheckCircle
                            } else {
                                Icons.Rounded.ErrorOutline
                            },
                            contentDescription = null,
                            tint = if (current.ok) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = current.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (current.ok) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val target = normalized ?: return@TextButton
                    container.settings.setGatewayUrl(target)
                    // 换地址可能让会话失效，立刻重新确认一次登录态。
                    container.onGatewayChanged()
                    // 提示交给容器去探、去弹：保存之后根组件可能马上切到登录页，
                    // 这个弹层会随之被销毁，界面自己的协程会连着被取消。
                    container.probeGateway(target) { result ->
                        val message = result.fold(
                            onSuccess = { "已保存：$target（${healthSummary(it)}）" },
                            onFailure = { "已保存，但连接失败：${it.friendlyMessage()}" },
                        )
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                    onDismiss()
                },
                enabled = normalized != null && !busy,
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
        },
    )
}

/** 「网关可达，上游：网易云音乐 http://…、QQ 音乐 http://…」。 */
private fun healthSummary(health: HealthResponse): String {
    if (health.upstreams.isEmpty()) return "网关可达"
    val upstreams = health.upstreams.entries.joinToString("、") { (key, value) ->
        val label = Platform.fromId(key).takeIf { it.isKnown }?.label ?: key
        "$label $value"
    }
    return "网关可达，上游：$upstreams"
}

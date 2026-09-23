package com.sakura.music.data.remote

import com.sakura.music.data.cache.ResponseCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * 网关请求封装。
 *
 * 三件事都收在这里，避免每个调用点各写一遍：
 * - 会话 Cookie 由 [cookieJar] 自动收发（登录后用同一个 jar 的请求天然带登录态）；
 * - 「仅本机」凭据通过 [localCredentialProvider] 挂到 `X-Sakura-Credential`；
 * - 错误统一成 [ApiException]，文案直接来自网关。
 */
class ApiClient(
    private val baseUrlProvider: () -> String,
    private val client: OkHttpClient,
    private val json: Json,
    private val cookieJar: CookieJar,
    private val localCredentialProvider: () -> String? = { null },
    /** 会话过期时的回调：集中在一处清理登录态，免得每个调用点各写一遍。 */
    private val onSessionExpired: () -> Unit = {},
) {

    /** 去掉结尾斜杠，方便与 `/api/...` 直接拼接。 */
    fun baseUrl(): String = baseUrlProvider().trim().trimEnd('/')

    suspend fun <T> request(
        method: String,
        path: String,
        deserializer: DeserializationStrategy<T>,
        query: Map<String, Any?> = emptyMap(),
        body: JsonElement? = null,
        /**
         * 指定另一个网关地址发这一次请求。
         *
         * 只给「测试连接」用：用户还没保存地址，得先替他试一下能不能通，
         * 否则只能先存一个错的地址、再看后面所有请求一起失败。
         */
        baseUrlOverride: String? = null,
        /**
         * 把这次响应存进哪个桶；给 null 表示不缓存。
         *
         * 只有 GET 且 [cacheTtlMillis] > 0 时才真的读写。
         */
        cache: ResponseCache? = null,
        /** 缓存多久算新鲜。0 表示不缓存。 */
        cacheTtlMillis: Long = 0L,
    ): T = withContext(Dispatchers.IO) {
        val base = (baseUrlOverride ?: baseUrlProvider()).trim().trimEnd('/')
        // POST / DELETE 是动作不是内容；「测试连接」用的是临时地址，也不该落盘。
        val activeCache = cache?.takeIf {
            cacheTtlMillis > 0L && method.equals("GET", ignoreCase = true) && baseUrlOverride == null
        }
        val cacheKey = activeCache?.let { cacheKeyOf(path, query) }

        // 命中新鲜的缓存就直接返回：这些内容（歌词、专辑信息）上一次取过就没必要再取。
        if (cacheKey != null) {
            activeCache!!.read(cacheKey, cacheTtlMillis)?.let { cached ->
                decodeOrNull(deserializer, cached)?.let { return@withContext it }
                // 缓存内容和现在的模型对不上了，删掉它继续走网络。
                activeCache.remove(cacheKey)
            }
        }

        val url = buildUrl(base, path, query)
        val builder = Request.Builder().url(url)

        localCredentialProvider()?.takeIf { it.isNotBlank() }?.let {
            builder.header(HEADER_LOCAL_CREDENTIAL, it)
        }

        val payload = body?.let {
            json.encodeToString(JsonElement.serializer(), it).toRequestBody(JSON_MEDIA_TYPE)
        }
        // GET 不允许带请求体；其余方法即使没有 body 也要显式给一个空体。
        builder.method(method, if (method == "GET") null else payload ?: EMPTY_BODY)

        val response = try {
            client.newCall(builder.build()).execute()
        } catch (error: IOException) {
            // 连不上网关时，一份过期的缓存也比报错有用：离线还能看到歌词和专辑。
            if (cacheKey != null) {
                activeCache!!.readStale(cacheKey)?.let { stale ->
                    decodeOrNull(deserializer, stale)?.let { return@withContext it }
                }
            }
            throw ApiException.network(
                "无法连接网关 $base\n${error.message ?: "网络不可用"}"
            )
        }

        response.use { resp ->
            val text = resp.body?.string().orEmpty()
            val element = if (text.isBlank()) {
                null
            } else {
                runCatching { json.parseToJsonElement(text) }.getOrNull()
            }

            if (!resp.isSuccessful) throw errorFrom(resp.code, element)

            if (element == null) {
                throw ApiException(resp.code, "invalid_response", "服务器返回了非 JSON 响应")
            }
            if (cacheKey != null) {
                activeCache!!.write(cacheKey, text)
            }
            try {
                json.decodeFromJsonElement(deserializer, element)
            } catch (error: Exception) {
                throw ApiException(
                    resp.code,
                    "decode_failed",
                    "服务器返回的数据无法解析：${error.message ?: "格式不匹配"}",
                )
            }
        }
    }

    /** 缓存键就是请求本身；网关地址换过不影响内容，所以不参与拼键。 */
    private fun cacheKeyOf(path: String, query: Map<String, Any?>): String {
        val sorted = query.entries
            .mapNotNull { (key, value) -> value?.toString()?.takeIf { it.isNotEmpty() }?.let { key to it } }
            .sortedBy { it.first }
            .joinToString("&") { (key, value) -> "$key=$value" }
        return if (sorted.isEmpty()) path else "$path?$sorted"
    }

    private fun <T> decodeOrNull(deserializer: DeserializationStrategy<T>, text: String): T? =
        runCatching { json.decodeFromString(deserializer, text) }.getOrNull()

    private fun buildUrl(base: String, path: String, query: Map<String, Any?>): String {
        val absolute = base + path
        val builder = absolute.toHttpUrlOrNull()?.newBuilder()
            ?: throw ApiException(
                0,
                "bad_base_url",
                "网关地址无效：$absolute\n请在「设置 → 网络」里改成一个可用的地址",
            )
        query.forEach { (key, value) ->
            if (value == null) return@forEach
            val text = value.toString()
            if (text.isEmpty()) return@forEach
            builder.addQueryParameter(key, text)
        }
        return builder.build().toString()
    }

    private fun errorFrom(status: Int, element: JsonElement?): ApiException {
        val error = (element as? JsonObject)?.get("error") as? JsonObject
        val code = (error?.get("code") as? JsonPrimitive)?.contentOrNull
        val message = (error?.get("message") as? JsonPrimitive)?.contentOrNull
        val resolvedCode = code?.takeIf { it.isNotBlank() } ?: "request_failed"

        // 只有「会话过期」才清登录态：登录时密码错误也是 401，不该顺手把已有会话踢掉。
        if (status == 401 && resolvedCode == "unauthorized") onSessionExpired()

        return ApiException(
            status = status,
            code = resolvedCode,
            message = message?.takeIf { it.isNotBlank() } ?: "请求失败（HTTP ${status}）",
        )
    }

    companion object {
        const val HEADER_LOCAL_CREDENTIAL = "X-Sakura-Credential"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** DELETE 之类没有请求体的调用要塞一个空体，okhttp 才认。 */
        private val EMPTY_BODY = ByteArray(0).toRequestBody(JSON_MEDIA_TYPE)
    }
}

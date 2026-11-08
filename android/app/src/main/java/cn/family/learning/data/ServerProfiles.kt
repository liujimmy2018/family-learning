package cn.family.learning.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

data class ServerProfile(val id: String, val name: String, val url: String)

object ServerAddresses {
    fun normalize(raw: String, allowHttp: Boolean): String {
        val uri = try { URI(raw.trim()) } catch (_: Exception) { throw IllegalArgumentException("请输入完整服务地址，如 http://192.168.1.10:8080") }
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        require(scheme == "https" || (allowHttp && scheme == "http")) { "服务地址必须为HTTPS，调试版可用HTTP" }
        require(!uri.host.isNullOrBlank() && uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            uri.rawPath.orEmpty() in listOf("", "/") && (uri.port == -1 || uri.port in 1..65535)) {
            "请填写服务器根地址，不要包含账号、/api/v1、查询参数或片段"
        }
        val port = if ((scheme == "http" && uri.port == 80) || (scheme == "https" && uri.port == 443)) -1 else uri.port
        return URI(scheme, null, uri.host.lowercase(Locale.ROOT), port, "", null, null).toASCIIString()
    }

    fun owner(url: String, account: String): String {
        val canonical = runCatching { normalize(url, true) }.getOrDefault(url.trim().trimEnd('/'))
        val hash = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return "$hash-$account"
    }

    fun save(list: List<ServerProfile>, id: String?, name: String, url: String): List<ServerProfile> {
        require(name.trim().length in 1..40) { "请输入1—40字的服务器名称" }
        require(list.none { it.url == url && it.id != id }) { "这个服务地址已经保存，请直接选择" }
        require(id == null || list.any { it.id == id }) { "该服务器已不存在，请返回列表重试" }
        val profile = ServerProfile(id ?: UUID.randomUUID().toString(), name.trim(), url)
        return if (id == null) list + profile else list.map { if (it.id == id) profile else it }
    }
}

class ServerProfileStore(context: Context, private val session: SessionStore) {
    private val prefs = context.getSharedPreferences("server_profiles", Context.MODE_PRIVATE)
    // Anchor legacy unscoped drafts to the endpoint selected before installing this version.
    val legacyUrl: String
    init {
        if (!prefs.contains("legacyUrl")) prefs.edit().putString("legacyUrl", session.baseUrl).commit()
        legacyUrl = prefs.getString("legacyUrl", "")!!
        if (!prefs.contains("items")) {
            val current = session.baseUrl
            write(if (current.isBlank()) emptyList() else listOf(ServerProfile(UUID.randomUUID().toString(), "原有服务器", current)))
        }
    }
    fun list(): List<ServerProfile> {
        val array = JSONArray(prefs.getString("items", "[]"))
        return array.objects().map { ServerProfile(it.getString("id"), it.getString("name"), it.getString("url")) }
    }
    fun save(id: String?, name: String, raw: String): ServerProfile {
        val url = ServerAddresses.normalize(raw, cn.family.learning.BuildConfig.DEBUG)
        val result = ServerAddresses.save(list(), id, name, url)
        write(result)
        return result.first { it.url == url }
    }
    fun remove(id: String) {
        val profile = list().firstOrNull { it.id == id } ?: return
        require(profile.url != session.baseUrl) { "当前服务器不能删除，请先选择其他服务器" }
        write(list().filterNot { it.id == id })
    }
    private fun write(items: List<ServerProfile>) {
        prefs.edit().putString("items", JSONArray(items.map { JSONObject().put("id", it.id).put("name", it.name).put("url", it.url) }).toString()).commit()
    }
}

package com.hm.privacyblocker

import android.content.Context
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * v1.4：拦截库可以从 GitHub 上更新，不用每次重新编 App。
 *
 * 用法（用户自己在 GitHub 上改）：
 * 1) 在 GitHub 上建一个公开仓库（或用已有的仓库），里面放一个 blocklist.json，
 *    格式见本项目根目录的 blocklist.json（示例文件，用户照着改就行）。
 *    每条就是：{"package":"包名","name":"中文名","desc":"它是干什么的","action":"block"}
 *    action 目前只认 "block"，不认的条目跳过，不会把 App 弄坏。
 * 2) 在 App 里把那个文件的 Raw 链接填进“GitHub 拦截库链接”那一栏，
 *    比如 https://raw.githubusercontent.com/你的用户名/仓库名/main/blocklist.json
 *    点“更新拦截库”，App 就去下载，下载到的包会加进拦截名单，下次一键拦截就拦它们。
 * 3) 下载失败（没网、链接错了、GitHub 打不开）时，App 什么都不改，
 *    还是用手机里原来那份名单，不会把名单清空。
 *
 * 安全：只下载 JSON 文本，不下载、不运行任何代码；下载到的包如果在
 * KEEP_OUT（小米云、手机管家、高通 systemhelper）里，直接跳过，绝不拦。
 */
object RemoteBlocklist {
    private const val PREF = "privacy_blocker_remote"
    private const val KEY_URL = "url"
    private const val KEY_PACKAGES = "packages" // 存成 "包名|中文名|说明,包名|中文名|说明"
    private const val KEY_UPDATED = "updated"

    data class RemoteEntry(val pkg: String, val name: String, val desc: String)

    fun savedUrl(context: Context): String {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_URL, "") ?: ""
    }

    fun saveUrl(context: Context, url: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_URL, url.trim()).apply()
    }

    fun savedEntries(context: Context): List<RemoteEntry> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_PACKAGES, "") ?: ""
        val out = mutableListOf<RemoteEntry>()
        raw.split(",").forEach { part ->
            val f = part.split("|", limit = 3)
            if (f.size == 3 && f[0].isNotBlank()) out.add(RemoteEntry(f[0], f[1], f[2]))
        }
        return out
    }

    fun lastUpdated(context: Context): String {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_UPDATED, "还没更新过") ?: "还没更新过"
    }

    /** 全部拦截名单：内置 15 个 + GitHub 上更新来的（去重，KEEP_OUT 的跳过）。 */
    fun allPackages(context: Context): List<String> {
        val list = RogueList.DIRECT_BLOCK.toMutableList()
        savedEntries(context).forEach { e ->
            if (e.pkg !in list && e.pkg !in RogueList.KEEP_OUT) list.add(e.pkg)
        }
        return list
    }

    fun descriptionFor(context: Context, pkg: String): String {
        RogueList.DESCRIPTION[pkg]?.let { return it }
        savedEntries(context).firstOrNull { it.pkg == pkg }?.let {
            return if (it.desc.isNotBlank()) it.desc else it.name
        }
        return "网上新加的拦截包，说明见 GitHub 上的 blocklist.json。"
    }

    sealed class UpdateResult {
        data class Ok(val added: Int, val total: Int) : UpdateResult()
        data class Failed(val reason: String) : UpdateResult()
    }

    /** 去 GitHub 下载 blocklist.json。跑在后台线程里，别在主线程调。 */
    fun update(context: Context, url: String): UpdateResult {
        val u = url.trim()
        if (u.isBlank()) return UpdateResult.Failed("先填 GitHub 上 blocklist.json 的 Raw 链接")
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            return UpdateResult.Failed("链接要以 https:// 开头，Raw 链接才行")
        }
        return try {
            val conn = URL(u).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "PrivacyBlocker/1.4")
            val code = conn.responseCode
            if (code != 200) return UpdateResult.Failed("下载失败，GitHub 返回 $code，检查链接对不对、仓库是不是公开的")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val entries = parse(text)
            if (entries.isEmpty()) return UpdateResult.Failed("下载到了，但里面没有能用的包，检查 JSON 格式对不对")
            // 只认 action=block 的，KEEP_OUT 的跳过
            val kept = entries.filter { it.pkg !in RogueList.KEEP_OUT }
            val raw = kept.joinToString(",") { "${it.pkg}|${it.name}|${it.desc}" }
            val time = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString(KEY_URL, u)
                .putString(KEY_PACKAGES, raw)
                .putString(KEY_UPDATED, time)
                .apply()
            val added = kept.count { it.pkg !in RogueList.DIRECT_BLOCK }
            UpdateResult.Ok(added, kept.size)
        } catch (e: Exception) {
            UpdateResult.Failed("下载失败：${e.message ?: "没网或链接打不开"}，原来那份名单还在，没动")
        }
    }

    /** 解析 blocklist.json：支持 {"packages":[...]} 或直接 [...] 两种写法。 */
    fun parse(text: String): List<RemoteEntry> {
        val out = mutableListOf<RemoteEntry>()
        try {
            val trimmed = text.trim()
            val arr: JSONArray = if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else {
                val obj = org.json.JSONObject(trimmed)
                obj.optJSONArray("packages") ?: return out
            }
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val pkg = o.optString("package", "").trim()
                if (pkg.isBlank()) continue
                val action = o.optString("action", "block").trim().lowercase()
                if (action != "block") continue
                out.add(RemoteEntry(pkg, o.optString("name", ""), o.optString("desc", "")))
            }
        } catch (e: Exception) {
            // 解析失败返回空，上层会报“格式不对”，不会把名单清空
        }
        return out
    }
}

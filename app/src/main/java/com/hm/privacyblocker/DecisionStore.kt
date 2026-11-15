package com.hm.privacyblocker

import android.content.Context

/**
 * v1.4：每个包的决定，存在手机里，关了 App 也还在。
 * 决定有三种，面板里点一个包弹出来的就是这三个：
 *   BLOCK（禁止）= 继续拦，一键拦截会拦它
 *   ALLOW_ONCE（允许一次）= 这次放它出来，下次一键拦截再拦回去
 *   ALLOW_ALWAYS（总是允许）= 以后都不拦它，一键拦截会跳过它
 * 默认：拦截名单里的包都是 BLOCK；可信的 Google 组件默认 ALLOW_ALWAYS。
 * “允许一次”只用一次：blockAll 拦它的时候会把这个一次用掉，变回 BLOCK。
 */
object DecisionStore {
    private const val PREF = "privacy_blocker_decisions"
    private const val KEY = "decisions" // 存成 "包名=决定,包名=决定" 的样子

    enum class Decision { BLOCK, ALLOW_ONCE, ALLOW_ALWAYS }

    fun get(context: Context, pkg: String): Decision {
        val map = all(context)
        if (map.containsKey(pkg)) return map[pkg]!!
        // 可信的 Google 组件默认总是允许，别拦
        if (RogueList.trustedDescription(pkg) != null) return Decision.ALLOW_ALWAYS
        return Decision.BLOCK
    }

    fun set(context: Context, pkg: String, decision: Decision) {
        val map = all(context).toMutableMap()
        map[pkg] = decision
        save(context, map)
    }

    /** “允许一次”用掉了，变回禁止，下次一键拦截会再拦它。 */
    fun consumeOnce(context: Context, pkg: String) {
        if (get(context, pkg) == Decision.ALLOW_ONCE) set(context, pkg, Decision.BLOCK)
    }

    fun all(context: Context): Map<String, Decision> {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, "") ?: ""
        val out = mutableMapOf<String, Decision>()
        raw.split(",").forEach { part ->
            val kv = part.split("=", limit = 2)
            if (kv.size == 2) {
                try { out[kv[0]] = Decision.valueOf(kv[1]) } catch (e: Exception) { /* 坏掉的一条跳过 */ }
            }
        }
        return out
    }

    private fun save(context: Context, map: Map<String, Decision>) {
        val raw = map.entries.joinToString(",") { "${it.key}=${it.value}" }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, raw).apply()
    }
}

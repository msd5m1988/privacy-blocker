package com.hm.privacyblocker

import android.content.Context
import android.content.pm.PackageManager

/**
 * 一键拦截的核心逻辑，和用户在电脑 ADB 里做的步骤完全一致：
 * 1) pm uninstall -k --user 0 <包名>
 * 2) 失败（not installed for 0）时改 pm disable-user --user 0 <包名>
 * 3) 再失败（Cannot disable system packages）时改
 *    cmd appops set <包名> RUN_ANY_IN_BACKGROUND ignore
 * 每一步的结果都如实上报，不谎报“已删除”。
 */
object Blocker {

    enum class Outcome { UNINSTALLED, DISABLED, BLOCKED_BACKGROUND, NOT_PRESENT, FAILED, RESTORED }

    data class PackageResult(val pkg: String, val outcome: Outcome, val detail: String)

    fun isInstalled(context: Context, pkg: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /** 对单个包按 卸载 -> 禁用 -> 后台拦截 的顺序尝试。 */
    fun blockOne(pkg: String): PackageResult {
        // v1.1：包已经不在了（这台 Note 13 Pro 上 12 个全是这样），就不用再跑
        // 三步命令了——对不存在的包跑 appops 也会失败，之前会误报 FAILED。
        // 这不是“拦截失败”，是“本来就没在”，直接如实报，不占一次失败。
        if (!isInstalled(App.instance, pkg)) {
            return PackageResult(pkg, Outcome.NOT_PRESENT, "已删 / 不在，不用再拦")
        }
        // 1) 卸载（只删当前用户那一份）
        when (val r = ShizukuRunner.run("pm uninstall -k --user 0 $pkg")) {
            is ShizukuRunner.Result.Ok -> {
                val out = r.output
                if (out.contains("Success", ignoreCase = true)) {
                    return PackageResult(pkg, Outcome.UNINSTALLED, "已卸载（当前用户）")
                }
                // 有些机型 uninstall 返回 Success 之外的内容，继续往下试也不亏
            }
            is ShizukuRunner.Result.Failed -> {
                // "Failure [not installed for 0]" 等都走到下一步，不直接判失败
            }
            is ShizukuRunner.Result.Unavailable -> {
                return PackageResult(pkg, Outcome.FAILED, "Shizuku 不可用：先装 Shizuku 并用 ADB 授权")
            }
        }
        // 2) 禁用
        when (val r = ShizukuRunner.run("pm disable-user --user 0 $pkg")) {
            is ShizukuRunner.Result.Ok -> {
                return PackageResult(pkg, Outcome.DISABLED, "已禁用")
            }
            is ShizukuRunner.Result.Failed -> {
                if (r.output.contains("Cannot disable system packages")) {
                    // 走第 3 步
                }
            }
            is ShizukuRunner.Result.Unavailable -> {
                return PackageResult(pkg, Outcome.FAILED, "Shizuku 不可用")
            }
        }
        // 3) 后台拦截：不能删、不能禁时最后的办法。重启后可能掉回 allow，要重设。
        when (val r = ShizukuRunner.run("cmd appops set $pkg RUN_ANY_IN_BACKGROUND ignore")) {
            is ShizukuRunner.Result.Ok -> {
                return PackageResult(pkg, Outcome.BLOCKED_BACKGROUND, "后台已拦截（重启后可能要重设）")
            }
            is ShizukuRunner.Result.Failed -> {
                return PackageResult(pkg, Outcome.FAILED, "三步都失败：${r.output.take(120)}")
            }
            is ShizukuRunner.Result.Unavailable -> {
                return PackageResult(pkg, Outcome.FAILED, "Shizuku 不可用")
            }
        }
    }

    /** 一键拦截全部已知流氓包。KEEP_OUT 里的包绝不碰。
     *  v1.4：用“内置 15 个 + GitHub 更新来的”整份名单；“总是允许”的包跳过；
     *  “允许一次”的包这次照常拦，拦完把那一次用掉，变回禁止。 */
    fun blockAll(onEach: (PackageResult) -> Unit): List<PackageResult> {
        val ctx = App.instance
        val results = mutableListOf<PackageResult>()
        for (pkg in RemoteBlocklist.allPackages(ctx)) {
            if (pkg in RogueList.KEEP_OUT) continue
            if (DecisionStore.get(ctx, pkg) == DecisionStore.Decision.ALLOW_ALWAYS) {
                results.add(PackageResult(pkg, Outcome.NOT_PRESENT, "总是允许，跳过"))
                onEach(results.last())
                continue
            }
            val r = blockOne(pkg)
            DecisionStore.consumeOnce(ctx, pkg)
            results.add(r)
            onEach(r)
        }
        return results
    }

    /** 恢复一个包：装回来（当前用户）-> 启用 -> 后台放行。
     *  卸载过的用 install-existing 装回来；只被禁用的用 enable 启用；
     *  只被拦后台的把 appops 改回 allow。三步都试一遍，能成的都做，
     *  最后按“还在不在 / 能不能用”如实报，不谎报。 */
    fun restoreOne(pkg: String): PackageResult {
        val ctx = App.instance
        if (pkg in RogueList.KEEP_OUT) {
            return PackageResult(pkg, Outcome.FAILED, "这个包在保留名单里，本来就没拦过")
        }
        // 1) 装回来（只对卸载过的有用，没卸载过会报失败，忽略，继续往下）
        ShizukuRunner.run("pm install-existing --user 0 $pkg")
        // 2) 启用（只对被禁用的有用）
        ShizukuRunner.run("pm enable --user 0 $pkg")
        // 3) 后台放行（只对被拦后台的有用）
        ShizukuRunner.run("cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow")
        return if (isInstalled(ctx, pkg)) {
            PackageResult(pkg, Outcome.RESTORED, "已恢复（装回 / 已启用 / 后台已放行）")
        } else {
            PackageResult(pkg, Outcome.FAILED, "恢复失败：包不在手机上，可能是系统包卸不回来了，重装系统才能回来")
        }
    }
}

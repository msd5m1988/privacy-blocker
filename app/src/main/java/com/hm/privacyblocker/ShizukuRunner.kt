package com.hm.privacyblocker

import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

/**
 * 通过 Shizuku 跑 ADB 级别的 shell 命令，不用 Root。
 * Shizuku 没装 / 没授权时返回 Unavailable，界面会提示安装和授权方法。
 * 注意：Shizuku 本身第一次授权要连电脑用 ADB（或无线调试）启动，重启后
 * Shizuku 也要重新启动一次，否则这里会一直返回不可用。
 *
 * 2026-11-06 修复（v1.1）：旧版用反射调 Shizuku.newProcess，在用户手机上
 * 全部失败，日志是 “Shizuku newProcess 不可用（反射失败）”，一键拦截 0/12。
 * 原因已用 Shizuku 13.1.5 的源码核实，不是手机 / Shizuku 没启动的问题：
 * - Shizuku.java:491 里 newProcess 是 `private static`，还标了 @Deprecated，
 *   注释写着 API 14 计划删掉它；
 * - 旧代码用 Class.getMethod() 找它，而 getMethod() 只找 public 方法，
 *   所以永远找不到；后备的 Class.methods 同样只列 public 方法，也是空的。
 * 正确做法是不走那个私有方法，直接调它内部调的那个公开 AIDL 接口：
 * - moe.shizuku.server.IShizukuService（aidl 13.1.5 产物，公开接口）：
 *   `public IRemoteProcess newProcess(String[] cmd, String[] env, String dir)`
 *   （IShizukuService.java:866，TRANSACTION_newProcess）
 * - 入口是公开的 Shizuku.getBinder()，再用
 *   IShizukuService.Stub.asInterface(binder) 包一层——这正是
 *   Shizuku.onBinderReceived() / requireService() 内部的做法
 *   （Shizuku.java:141，`service = IShizukuService.Stub.asInterface(newBinder)`）。
 * 所以这里不再用任何反射。为了让 aidl 在编译时一定在 classpath 上，
 * app/build.gradle.kts 里还显式加了 dev.rikka.shizuku:aidl:13.1.5。
 * IRemoteProcess 的 stdout / stderr 用两个线程同时读，避免一边的管道
 * 满了把另一边堵死；等进程退出后再按 exit code 判成功 / 失败。
 */
object ShizukuRunner {

    sealed class Result {
        data class Ok(val output: String) : Result()
        data class Failed(val output: String) : Result()
        object Unavailable : Result()
    }

    fun isAvailable(context: Context): Boolean {
        return try {
            Shizuku.pingBinder() &&
                context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0) != null
        } catch (e: Exception) {
            try { Shizuku.pingBinder() } catch (e2: Exception) { false }
        }
    }

    fun hasPermission(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun requestPermission(code: Int) {
        try { Shizuku.requestPermission(code) } catch (e: Exception) { /* ignore */ }
    }

    /** 跑一条 shell 命令，返回 stdout+stderr。 */
    fun run(command: String): Result {
        val binder = try {
            if (!Shizuku.pingBinder()) return Result.Unavailable
            Shizuku.getBinder()
        } catch (e: Exception) {
            return Result.Unavailable
        } ?: return Result.Unavailable
        if (!hasPermission()) return Result.Unavailable
        return try {
            // 公开 AIDL 调用，等价于 Shizuku 私有的 newProcess() 内部做的事，
            // 不用反射，不依赖那个已废弃的私有方法。
            val service: IShizukuService = IShizukuService.Stub.asInterface(binder)
            val remote: IRemoteProcess = service.newProcess(arrayOf("sh", "-c", command), null, null)
            val out = StringBuilder()
            val err = StringBuilder()
            val tOut = Thread {
                try {
                    ParcelFileDescriptor.AutoCloseInputStream(remote.inputStream).bufferedReader().use {
                        out.append(it.readText())
                    }
                } catch (e: Exception) { /* 进程没输出时忽略 */ }
            }
            val tErr = Thread {
                try {
                    ParcelFileDescriptor.AutoCloseInputStream(remote.errorStream).bufferedReader().use {
                        err.append(it.readText())
                    }
                } catch (e: Exception) { /* 进程没输出时忽略 */ }
            }
            tOut.start()
            tErr.start()
            val exitCode = try { remote.waitFor() } catch (e: Exception) { -1 }
            tOut.join(5000)
            tErr.join(5000)
            try { remote.destroy() } catch (e: Exception) { /* ignore */ }
            val combined = (out.toString() + "\n" + err.toString()).trim()
            if (exitCode == 0) Result.Ok(combined) else Result.Failed(combined.ifBlank { "exit=$exitCode" })
        } catch (e: Exception) {
            Result.Failed(e.message ?: e.toString())
        }
    }
}

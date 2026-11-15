package com.hm.privacyblocker

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.hm.privacyblocker.databinding.ActivityMainBinding
import rikka.shizuku.Shizuku
import kotlin.concurrent.thread

/**
 * v1.4：在 v1.3 简单版上加回用户要的三样，不加回 v1.2 那些会闪退的复杂东西。
 * 1) 新加 3 个包（misightservice、traceur、msa.global），共 15 个内置，
 *    再加 GitHub 上更新来的，删不掉就禁用，禁不掉就拦后台。
 * 2) “已拦截”面板：点一个包，先显示中文说明“它是干什么的”，
 *    再弹 禁止 / 允许一次 / 总是允许。允许一次 = 这次放出来，
 *    下次一键拦截再拦回去；总是允许 = 以后都不拦。
 * 3) 检测提示：每个包都有中文说明；可信的 Google 组件（Play 服务、
 *    gstatic 这些）直接写“别拦”，免得上次那样把 Google 的也拦了。
 * 4) “更新拦截库”：在 GitHub 上改 blocklist.json，手机上点一下
 *    就下载新名单，不用重新编 App。下载失败时原来那份名单还在，不动。
 * 没有网络拦截（不用 VPN，用户在用别的 VPN）、没有 Root。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val shizukuRequestCode = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        try {
            Shizuku.addRequestPermissionResultListener { _, grantResult ->
                if (grantResult == PackageManager.PERMISSION_GRANTED) refreshAll()
            }
        } catch (e: Exception) { /* Shizuku 没装时忽略 */ }

        binding.btnBlock.setOnClickListener { runOneTapBlock() }
        binding.btnShizuku.setOnClickListener { requestShizukuPermission() }
        binding.btnOpenShizuku.setOnClickListener { openShizukuApp() }
        binding.btnRefresh.setOnClickListener { refreshAll() }
        binding.btnPanel.setOnClickListener { showBlockedPanel() }
        binding.btnUpdateDb.setOnClickListener { showUpdateDbDialog() }
        binding.btnClearLog.setOnClickListener {
            AppLog.clear(this)
            refreshLog()
        }
        refreshAll()
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    private fun refreshAll() {
        refreshStatus()
        refreshLog()
    }

    /** 每个包一行：中文说明 + 还在不在 + 现在的决定（禁止 / 允许一次 / 总是允许）。 */
    private fun refreshStatus() {
        binding.tvShizuku.text = when {
            !ShizukuRunner.isAvailable(this) -> "Shizuku：没装 / 没启动，点“打开 Shizuku 启动”"
            !ShizukuRunner.hasPermission() -> "Shizuku：已装，没授权，点“给 Shizuku 授权”"
            else -> "Shizuku：已授权，可以一键拦截"
        }
        val pkgs = RemoteBlocklist.allPackages(this)
        val sb = StringBuilder()
        for (pkg in pkgs) {
            val installed = Blocker.isInstalled(this, pkg)
            val desc = describePackage(pkg)
            val decision = when (DecisionStore.get(this, pkg)) {
                DecisionStore.Decision.BLOCK -> "禁止"
                DecisionStore.Decision.ALLOW_ONCE -> "允许一次"
                DecisionStore.Decision.ALLOW_ALWAYS -> "总是允许"
            }
            sb.append(if (installed) "● " else "○ ").append(pkg).append("\n   ").append(desc)
                .append("\n   ").append(if (installed) "还在" else "已删 / 不在").append(" · ").append(decision).append("\n")
        }
        sb.append("\n拦截库更新：").append(RemoteBlocklist.lastUpdated(this))
        sb.append("\n保留不碰：高通 systemhelper、小米云、手机管家")
        binding.tvStatus.text = sb.toString()
    }

    private fun refreshLog() {
        binding.tvLog.text = AppLog.all(this).joinToString("\n").ifBlank { "还没有记录，点“一键拦截”就开始拦。" }
    }

    /**
     * 检测提示：先看是不是可信的 Google 组件，再看内置说明，
     * 再看 GitHub 更新来的说明，最后看它在手机上要了什么权限。
     * 认不出来的就老实写“不认识”，并把权限列出来，让用户自己判断，
     * 不瞎猜一个名字。
     */
    fun describePackage(pkg: String): String {
        RogueList.trustedDescription(pkg)?.let { return it }
        RogueList.DESCRIPTION[pkg]?.let { return it }
        RemoteBlocklist.descriptionFor(this, pkg).let {
            if (it.isNotBlank() && !it.startsWith("网上新加")) return it
        }
        // 不认识的包：读它在手机上的名字和要的权限，提示用户
        return try {
            val pm = packageManager
            val info = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
            val label = pm.getApplicationLabel(info.applicationInfo!!).toString()
            val perms = (info.requestedPermissions ?: emptyArray())
                .mapNotNull { p -> RogueList.RISKY_PERMISSIONS.firstOrNull { it.first == p }?.second }
                .distinct()
            val hint = if (perms.isNotEmpty()) "，它要了：${perms.joinToString("、")}" else ""
            "不认识的包（手机上显示：$label）$hint。建议先别拦，查一下再决定。"
        } catch (e: Exception) {
            "不认识的包，手机上也没有它，不用管。"
        }
    }

    /** “已拦截”面板：列出全部拦截名单，点一个就弹三个选择。 */
    private fun showBlockedPanel() {
        val pkgs = RemoteBlocklist.allPackages(this)
        val labels = pkgs.map { pkg ->
            val installed = Blocker.isInstalled(this, pkg)
            val decision = when (DecisionStore.get(this, pkg)) {
                DecisionStore.Decision.BLOCK -> "禁止"
                DecisionStore.Decision.ALLOW_ONCE -> "允许一次"
                DecisionStore.Decision.ALLOW_ALWAYS -> "总是允许"
            }
            "$pkg（${if (installed) "还在" else "已删"} · $decision）"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("已拦截面板（点一个，选怎么处理它）")
            .setItems(labels) { _, which -> showPackageChoice(pkgs[which]) }
            .setPositiveButton("知道了", null)
            .show()
    }

    /** 点一个包：先显示它是干什么的，再选 禁止 / 允许一次 / 总是允许。 */
    private fun showPackageChoice(pkg: String) {
        val desc = describePackage(pkg)
        val trusted = RogueList.trustedDescription(pkg)
        val message = StringBuilder()
            .append(pkg).append("\n\n").append(desc).append("\n")
        if (trusted != null) message.append("\n这是可信的 Google 组件，默认总是允许，别拦。\n")
        if (pkg in RogueList.KEEP_OUT) message.append("\n这个在保留名单里，本来就没拦过。\n")
        AlertDialog.Builder(this)
            .setTitle("怎么处理它？")
            .setMessage(message.toString())
            .setPositiveButton("禁止") { _, _ ->
                DecisionStore.set(this, pkg, DecisionStore.Decision.BLOCK)
                runRestoreOrBlock(pkg, block = true)
            }
            .setNeutralButton("允许一次") { _, _ ->
                DecisionStore.set(this, pkg, DecisionStore.Decision.ALLOW_ONCE)
                runRestoreOrBlock(pkg, block = false)
            }
            .setNegativeButton("总是允许") { _, _ ->
                DecisionStore.set(this, pkg, DecisionStore.Decision.ALLOW_ALWAYS)
                runRestoreOrBlock(pkg, block = false)
            }
            .show()
    }

    /** 禁止 = 现在就拦它一次；允许 = 现在就把它恢复出来。 */
    private fun runRestoreOrBlock(pkg: String, block: Boolean) {
        if (!ShizukuRunner.isAvailable(this) || !ShizukuRunner.hasPermission()) {
            Toast.makeText(this, "先装 Shizuku 并授权，再改这个包", Toast.LENGTH_LONG).show()
            return
        }
        thread {
            val r = if (block) Blocker.blockOne(pkg) else Blocker.restoreOne(pkg)
            runOnUiThread {
                AppLog.add(this, "${if (block) "禁止" else "恢复"} $pkg：${r.outcome} ${r.detail}")
                Toast.makeText(this, "$pkg：${r.detail}", Toast.LENGTH_LONG).show()
                refreshAll()
            }
        }
    }

    /** “更新拦截库”：填 GitHub 上 blocklist.json 的 Raw 链接，点更新就下载。 */
    private fun showUpdateDbDialog() {
        val input = EditText(this)
        input.hint = "https://raw.githubusercontent.com/你的用户名/仓库名/main/blocklist.json"
        input.setText(RemoteBlocklist.savedUrl(this))
        AlertDialog.Builder(this)
            .setTitle("更新拦截库（从 GitHub 下载）")
            .setMessage("在 GitHub 上改好 blocklist.json 后，把它的 Raw 链接填在这里，点“更新”就下载新名单。不用重新编 App。\n\n下载失败时原来那份名单还在，不会清空。")
            .setView(input)
            .setPositiveButton("更新") { _, _ ->
                val url = input.text.toString().trim()
                RemoteBlocklist.saveUrl(this, url)
                thread {
                    val result = RemoteBlocklist.update(this, url)
                    runOnUiThread {
                        when (result) {
                            is RemoteBlocklist.UpdateResult.Ok -> {
                                AppLog.add(this, "拦截库已更新：新加 ${result.added} 个，共 ${result.total} 个")
                                Toast.makeText(this, "更新好了：新加 ${result.added} 个，共 ${result.total} 个", Toast.LENGTH_LONG).show()
                            }
                            is RemoteBlocklist.UpdateResult.Failed -> {
                                AppLog.add(this, "拦截库更新失败：${result.reason}")
                                Toast.makeText(this, result.reason, Toast.LENGTH_LONG).show()
                            }
                        }
                        refreshAll()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun runOneTapBlock() {
        if (!ShizukuRunner.isAvailable(this) || !ShizukuRunner.hasPermission()) {
            Toast.makeText(this, "先装 Shizuku 并授权，再点一键拦截", Toast.LENGTH_LONG).show()
            AppLog.add(this, "一键拦截没跑：Shizuku 不可用")
            refreshLog()
            return
        }
        binding.btnBlock.isEnabled = false
        binding.btnBlock.text = "正在拦截…"
        thread {
            val results = Blocker.blockAll { r ->
                runOnUiThread {
                    AppLog.add(this, "一键拦截 ${r.pkg}：${r.outcome} ${r.detail}")
                    refreshLog()
                }
            }
            runOnUiThread {
                binding.btnBlock.isEnabled = true
                binding.btnBlock.text = "一键拦截"
                val ok = results.count { it.outcome != Blocker.Outcome.FAILED }
                Toast.makeText(this, "完成：$ok / ${results.size} 个已处理，详情看日志", Toast.LENGTH_LONG).show()
                AppLog.add(this, "一键拦截完成：$ok / ${results.size} 已处理（guardprovider 的后台拦截重启后要重设）")
                refreshAll()
            }
        }
    }

    private fun requestShizukuPermission() {
        if (!ShizukuRunner.isAvailable(this)) {
            AlertDialog.Builder(this)
                .setTitle("还没装 Shizuku")
                .setMessage("先装 Shizuku（应用商店 / 官网），再点“打开 Shizuku 启动”，在里面用无线调试启动它。\n\nShizuku 不能装进本 App 里，它是另外一个 App，本 App 只能通过它去拦截，不用 Root。")
                .setPositiveButton("知道了", null)
                .show()
        } else {
            ShizukuRunner.requestPermission(shizukuRequestCode)
            refreshAll()
        }
    }

    private fun openShizukuApp() {
        val intent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (intent != null) {
            startActivity(intent)
            Toast.makeText(this, "在 Shizuku 里点“通过无线调试启动”，启动完回来点一键拦截", Toast.LENGTH_LONG).show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("还没装 Shizuku")
                .setMessage("先在应用商店搜 Shizuku 装上，再点“打开 Shizuku 启动”，在里面用无线调试启动它。\n\nShizuku 不能装进本 App 里，它是另外一个 App，本 App 只能通过它去拦截，不用 Root。")
                .setPositiveButton("知道了", null)
                .show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == shizukuRequestCode) refreshAll()
    }
}

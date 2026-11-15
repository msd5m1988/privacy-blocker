package com.hm.privacyblocker

/**
 * v1.4：拦截名单 = 内置 15 个 + GitHub 上更新来的（RemoteBlocklist）。
 * 内置 15 个 = 原来 12 个 + 2026-11-09 网上核对后新加 3 个：
 *   com.miui.misightservice、com.android.traceur、com.miui.msa.global
 * 每个包都有中文说明，面板里点它就先显示“它是干什么的”，再让你选
 * 禁止 / 允许一次 / 总是允许，别再像上次那样把 Google 的东西也拦了。
 *
 * 拦截顺序（Blocker 里）：卸载 -> 禁用 -> 后台拦截。
 * 删不掉就禁用，禁不掉就拦后台；不存在的包直接跳过，不算失败。
 * guardprovider / analytics / contentcatcher 在第二台机上
 * 卸载会报 "not installed for 0"，禁用会报 "Cannot disable system packages"，
 * 只能退到 appops 后台拦截，且 guardprovider 的 appops 在重启后会掉回 allow。
 */
object RogueList {
    val DIRECT_BLOCK = listOf(
        "com.mobiletools.systemhelper",
        "com.miui.guardprovider",
        "com.miui.analytics",
        "com.miui.daemon",
        "com.miui.bugreport",
        "com.miui.uireporter",
        "com.miui.contentcatcher",
        "com.xiaomi.security.onetrack",
        "com.miui.systemAdSolution",
        "com.qualcomm.qti.devicestatisticsservice",
        "com.bsp.catchlog",
        "com.miui.dmregservice",
        // v1.4 新加 3 个
        "com.miui.misightservice",
        "com.android.traceur",
        "com.miui.msa.global"
    )

    /** 中文名 + 它是干什么的 + 拦了会怎样。面板和弹窗里直接显示这一段。 */
    val DESCRIPTION = mapOf(
        "com.mobiletools.systemhelper" to "联通 SystemHelper：上报 App 列表、过去 24 小时使用时长和流量、IMEI/IMSI/ICCID。拦了不影响打电话和上网。",
        "com.miui.guardprovider" to "系统安全组件：上报第三方 App 清单，做反诈检测，还有通话录音/录屏云端检测。拦了可能影响手机管家里的反诈提示，删/禁不掉时只能拦后台，重启后要重拦。",
        "com.miui.analytics" to "小米统计：收集使用习惯并上传。拦了没影响，重启后可能回来，再拦一次就行。",
        "com.miui.daemon" to "小米守护进程：匿名数据收集上报。拦了没影响。",
        "com.miui.bugreport" to "Bug 反馈：崩溃日志上报。拦了没影响。",
        "com.miui.uireporter" to "界面使用上报：记录你点了哪些界面。拦了没影响。",
        "com.miui.contentcatcher" to "内容捕捉上报。拦了没影响，重启后可能回来，再拦一次就行。",
        "com.xiaomi.security.onetrack" to "OneTrack 统计 SDK：用户数据收集。拦了没影响。",
        "com.miui.systemAdSolution" to "系统广告服务：系统应用里的广告。拦了广告位会变空白，应用照常用。",
        "com.qualcomm.qti.devicestatisticsservice" to "高通设备统计：设备使用数据上报。拦了没影响。注意：不是高通那个 systemhelper，那个要留着。",
        "com.bsp.catchlog" to "日志抓取上报。拦了没影响。",
        "com.miui.dmregservice" to "设备管理注册：和 SystemHelper 类似的运营商注册服务。拦了不影响打电话。",
        "com.miui.misightservice" to "系统质量检测（HyperOS 3 新增）：每天向小米传约 100KB 诊断数据。拦了没影响；没升 HyperOS 3 的手机上可能根本没有这个包，没有就跳过。",
        "com.android.traceur" to "系统追踪工具：开发者用的进程追踪，普通用户用不上。拦了没影响。",
        "com.miui.msa.global" to "系统广告（海外版包名）：和 systemAdSolution 是同一个功能的不同包名。国行手机上大概率没有，没有就跳过。"
    )

    /** 绝不能删 / 不能拦的，用户已明确要求保留。 */
    val KEEP_OUT = setOf(
        "com.qualcomm.qti.services.systemhelper", // 高通系统服务，和上面那个名字像但不是它
        "com.miui.cloudservice", "com.miui.micloudsync", "com.miui.cloudbackup", // 小米云
        "com.miui.securitycenter", "com.lbe.security.miui", "com.miui.securitycore"
    )

    /**
     * 可信的 Google 组件：别拦，拦了会坏事。上次 www.gstatic.com 被误拦
     * 就是没认出它是 Google 的。这张表是给“检测提示”用的：
     * 碰到这些包名，面板里直接写“别拦”，并说明它是干什么的。
     */
    val TRUSTED = mapOf(
        "com.google.android.gms" to "Google Play 服务：管推送、定位、账号登录。别拦，拦了微信推送、地图定位都可能坏。",
        "com.google.android.gsf" to "Google 服务框架：和 Play 服务配套。别拦。",
        "com.google.android.apps.photos" to "Google 相册。别拦。",
        "com.google.android.youtube" to "YouTube。别拦。",
        "com.google.android.gm" to "Gmail。别拦。",
        "www.gstatic.com" to "Google 静态内容：管图片、字体这些。别拦，不是上报域名。",
        "gstatic.com" to "Google 静态内容域名。别拦。",
        "googleapis.com" to "Google 接口域名。别拦。",
        "google.com" to "Google 主域名。别拦。"
    )

    fun trustedDescription(pkgOrHost: String): String? {
        val k = pkgOrHost.lowercase()
        TRUSTED.forEach { (key, desc) -> if (k == key || k.endsWith(key)) return desc }
        return null
    }

    /** 已知上报 / 追踪域名后缀：DNS 拦截直接拦，不弹窗。 */
    val TRACKER_DOMAINS = listOf(
        "wo.com.cn",
        "miui.com",
        "sec.miui.com",
        "engine.miui.com",
        "tracking.miui.com",
        "api.ad.xiaomi.com",
        "sdkconfig.ad.xiaomi.com"
    )

    val SUSPICIOUS_KEYWORDS = listOf("track", "analytic", "telemetry", "upload", "report", "adservice", "stat")

    /**
     * 可信的静态内容域名：绝不按“可疑”弹窗。
     * 2026-11-06 修复：www.gstatic.com 被误判，就是因为它名字里有 "stat"
     *（gstatic），撞上了上面的 SUSPICIOUS_KEYWORDS。它是谷歌的静态资源
     * 域名，不是上报 / 追踪域名。
     */
    val TRUSTED_STATIC_DOMAINS = listOf("gstatic.com", "googleapis.com", "google.com", "googleusercontent.com")

    fun isTrustedStaticDomain(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        return TRUSTED_STATIC_DOMAINS.any { h == it || h.endsWith(".$it") }
    }

    /** 新装 App 时要检查的隐私权限：有这些权限 + 联网，就弹窗问用户。 */
    val RISKY_PERMISSIONS = listOf(
        "android.permission.READ_CONTACTS" to "通讯录",
        "android.permission.READ_MEDIA_IMAGES" to "照片",
        "android.permission.READ_MEDIA_VIDEO" to "视频",
        "android.permission.READ_EXTERNAL_STORAGE" to "文件",
        "android.permission.ACCESS_FINE_LOCATION" to "精确定位",
        "android.permission.ACCESS_COARSE_LOCATION" to "大概位置",
        "android.permission.READ_PHONE_STATE" to "设备识别码",
        "android.permission.GET_ACCOUNTS" to "账号",
        "android.permission.READ_SMS" to "短信",
        "android.permission.READ_CALL_LOG" to "通话记录",
        "android.permission.RECORD_AUDIO" to "录音",
        "android.permission.CAMERA" to "相机",
        "android.permission.QUERY_ALL_PACKAGES" to "应用列表",
        "android.permission.PACKAGE_USAGE_STATS" to "使用记录"
    )

    fun isTrackerDomain(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        return TRACKER_DOMAINS.any { h == it || h.endsWith(".$it") }
    }

    fun isSuspiciousDomain(host: String): Boolean {
        val h = host.lowercase()
        if (isTrustedStaticDomain(h)) return false
        return SUSPICIOUS_KEYWORDS.any { h.contains(it) }
    }
}

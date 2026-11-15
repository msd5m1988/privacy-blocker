# 构建记录（本机尝试结果）

## 结论：本机没有编出 APK

本机（Ubuntu 24.04 虚拟机）上**没有**编出 `app-debug.apk`。
源码是全的，按 README.md 里“在电脑上编译”那节，在有 Android Studio
的电脑上可以编。**不要声称这里有一个能用的 APK——没有。**

## 本机做了什么

- 装了 Temurin JDK 17（`/opt/jdk17`），Android 命令行工具，
  用 curl 直接下载了 `platforms;android-34`（`platform-34-ext7_r03.zip`）、
  `build-tools;34.0.0`、`platform-tools` 到 `/opt/android-sdk`。
  这些文件在虚拟机里，不在交付的源码包里。
- Gradle 8.7 已下载到 `/opt/gradle-8.7`，项目里有真正的
  `gradlew` / `gradle/wrapper/gradle-wrapper.jar`（Gradle 8.7）。

## 为什么没编出来（两个独立的原因）

1. **Gradle 守护进程的 TCP 被沙箱拦了。**
   Gradle 构建要靠守护进程，客户端和守护进程之间走本机 TCP
   （127.0.0.1）。本机沙箱把这类 TCP 连上了代理，返回：
   “Other TCP connections is turned off for this assistant.
   To allow it, ask the user to open Muse settings -> Permissions ->
   Direct network protocols and switch other_tcp from Deny to Ask.”
   现象：守护进程能启动、能监听 127.0.0.1，但客户端一发构建请求就
   收到 “Could not receive a message from the daemon.”，
   用一个最小的 Gradle 工程（只有 `task hello`）也一样复现，
   不是本项目代码的问题。
   解决办法（二选一）：在 Muse 设置里把 Direct network protocols
   的 other_tcp 改成 Ask / 允许后重试；或者直接在用户自己的电脑
   （Android Studio）里编，那里没有这个限制。

2. **sdkmanager 也走不通同一条代理。**
   `sdkmanager --list / --licenses` 在 Java 的 HttpURLConnection
   做代理 CONNECT 隧道时报 `NoSuchElementException`，
   所以 SDK 组件是改用 curl 直接从 dl.google.com 下载的，
   不是用 sdkmanager 装的。这不影响源码，只影响本机编包。

## 在用户电脑上编（已在 README.md 里写过，这里再记一遍）

1. 装 Android Studio，打开 `privacy-blocker-app` 文件夹，等 Gradle 同步完
   （第一次同步要下载 AGP 8.5.2、Kotlin 1.9.24、AndroidX、Shizuku 13.1.5，
   要联网）。
2. Build → Build App Bundle(s) / APK(s) → Build APK(s)，
   APK 在 `app/build/outputs/apk/debug/app-debug.apk`。
3. 或命令行：`./gradlew assembleDebug`（macOS/Linux）/
   `gradlew.bat assembleDebug`（Windows）。

## v1.1 修复（2026-11-06，Shizuku 执行路径 + gstatic 误判 + 域名决定可改）

- 源码已改，见 README.md 开头的“v1.1 改了什么”。关键核实过程：
  从 Maven Central 下载了 Shizuku 13.1.5 的 `api-13.1.5-sources.jar`、
  `aidl-13.1.5-sources.jar`，读了源码才改，不是猜的：
  - `Shizuku.java:491`：`private static ShizukuRemoteProcess newProcess(...)`，
    还标了 @Deprecated，注释说 API 14 计划删掉；旧代码用
    `Class.getMethod()`（只找 public）找它，永远找不到——这就是
    用户手机上 0/12、全部 “Shizuku newProcess 不可用（反射失败）” 的原因。
  - `IShizukuService.java:866`：`public IRemoteProcess newProcess(String[] cmd,
    String[] env, String dir)`，是公开 AIDL 接口；`Shizuku.java:141`
    的 `onBinderReceived()` 里 `service = IShizukuService.Stub.asInterface(newBinder)`，
    私有 newProcess 内部就是调 `requireService().newProcess(...)`。
    v1.1 的 ShizukuRunner 直接调这个公开接口，不用反射。
  - `app/build.gradle.kts` 显式加了 `dev.rikka.shizuku:aidl:13.1.5`，
    版本号升到 1.1（versionCode 2）。
  - gstatic 误判：`www.gstatic.com` 含 "stat"，撞上 SUSPICIOUS_KEYWORDS；
    加了 TRUSTED_STATIC_DOMAINS 白名单，`isSuspiciousDomain()` 先排除它。
  - 域名决定可改：MainActivity 新增“我选过的域名”列表（allowed /
    blocked 各一条按钮改 / 取消），DnsVpnService 之前没读
    `allowed_domains` 的 bug 也修了（允许的域名直接放行）。
- **仍未在本机编过 v1.1。** 按任务要求，沙箱里不编 APK（Gradle 守护进程
  TCP 被拦，和上面第 1、2 条原因一样）。v1.1 源码只做了人工检查，
  仍可能存在第一次编译才暴露的错误，重点是这两处：
  1. `ShizukuRunner.kt` 里 `IShizukuService.Stub.asInterface(Shizuku.getBinder())`
     和 `remote.inputStream / remote.errorStream / remote.waitFor()`——
     aidl 依赖已显式声明，正常应能编过；如果报找不到
     `moe.shizuku.server.IShizukuService`，先确认 Gradle 同步时
     aidl 13.1.5 下载成功。
  2. `MainActivity.kt` 新增的 `binding.tvDomainsEmpty / binding.llDomains`
     对应 `activity_main.xml` 里新加的两个 id；改布局后要重新同步。
  在用户电脑上编法不变：Android Studio 打开项目，等同步完，
  Build → Generate App Bundles or APKs → Generate APK(s)，
  APK 在 `app/build/outputs/apk/debug/app-debug.apk`。
  编完装到手机上，先点“一键拦截”看日志是不是不再报
  “newProcess 不可用”，再到“我选过的域名”里把 www.gstatic.com
  改回允许。

## 源码完整性检查（没编过，所以只做了人工检查）

- 7 个 Kotlin 文件 + AndroidManifest + 布局 + strings + 两个 build.gradle.kts
  都在 `app/src/main/` 和项目根目录，见下面的文件清单。
- Shizuku 13.1.5 的坐标已在 Maven Central 上核对过是存在的
  （`dev.rikka.shizuku:api:13.1.5` / `:provider:13.1.5`）。
- Shizuku API 13 里 `newProcess` 是隐藏方法，代码里改成了反射调用，
  失败时返回 Failed，不谎报成功。
- 仍有可能存在没编过就发现不了的编译错误（比如某个 API 签名写错），
  在电脑上第一次编如果报错，把报错全文发回来，按报错改。

## 文件清单

```
privacy-blocker-app/
  README.md            中文说明：能做什么、不能做什么、怎么用
  BUILD_NOTES.md       本文件：本机为什么没编出 APK
  settings.gradle.kts / build.gradle.kts / gradle.properties
  gradlew / gradlew.bat（Gradle 8.7 的官方版本）
  gradle/wrapper/gradle-wrapper.properties + gradle-wrapper.jar
  app/build.gradle.kts
  app/proguard-rules.pro
  app/src/main/AndroidManifest.xml
  app/src/main/java/com/hm/privacyblocker/
    App.kt  RogueList.kt  AppLog.kt  ShizukuRunner.kt
    Blocker.kt  NewAppReceiver.kt  DnsVpnService.kt  MainActivity.kt
  app/src/main/res/layout/activity_main.xml
  app/src/main/res/values/strings.xml
  app/src/main/res/values/colors.xml
  app/src/main/res/drawable/bg_btn_primary.xml
  app/src/main/res/drawable/bg_btn_outline_teal.xml
  app/src/main/res/drawable/bg_btn_outline_grey.xml
  app/src/main/res/drawable/bg_btn_outline_green.xml
  app/src/main/res/drawable/bg_btn_outline_red.xml
  app/src/main/res/drawable/ic_launcher_foreground.xml
  app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
  app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
```

注意：`local.properties`（SDK 路径）不要放进源码包，Android Studio
打开项目时会自动生成；本机那份指向 `/opt/android-sdk`，已经删了。

## v1.2（2026-11-06，三选弹窗 + 两个分开的列表 + 美化 + 图标 + 网络拦截可选）

- **三选弹窗。** 可疑域名弹窗原来只有“允许 / 拦截”两个按钮，点了
  “允许”就等于总是允许。v1.2 改成和新装 App 弹窗一样的三个选择：
  只允许一次 / 总是允许 / 拦截（`MainActivity.showDomainPrompt`，
  AlertDialog 正 / 中 / 负三个按钮正好对应）。新装 App 弹窗 v1.1
  就已经是三个按钮，v1.2 把“允许一次”统一改叫“只允许一次”，并且
  新选的只允许一次不再存决定：域名不进允许 / 拦截列表，App 也不存
  DecisionStore——这次放行，下次再问。旧版 v1.1 存过 "once" 的数据
  还在的话，`NewAppReceiver` / `DecisionStore.consumeOnce` 仍会按
  旧规则清掉一次，不会变成总是允许。
- **两个分开的列表。** 主界面不再是一个混在一起的“我选过的域名”，
  而是分开的 **允许列表** 和 **拦截列表**（`activity_main.xml` 里
  `llAllowed` / `tvAllowedEmpty` 和 `llBlocked` / `tvBlockedEmpty`，
  `MainActivity.refreshDecisionLists`）。每个列表里既有域名
  （`allowed_domains` / `blocked_domains`），也有 App
  （`DecisionStore.all()`，新增的方法，只返回总是允许 / 总是拦截，
  不返回只允许一次）。每条显示域名或 App 名字，都有“移到允许 /
  移到拦截”和“取消决定”两个按钮；App 移到允许时放开后台，移到
  拦截时拦后台，取消拦截的 App 决定时先放开后台，避免它一直被拦着
  却没人再问。域名只改 SharedPreferences，网络拦截下次查 DNS 时
  按新决定走。
- **界面美化。** `activity_main.xml` 重写成卡片式：顶部深青绿标题卡，
  一键拦截是大号深青绿按钮（`bg_btn_primary.xml`），给 Shizuku 授权 /
  网络拦截是青绿描边按钮，刷新 / 清空日志是灰色描边按钮；下面是白底
  圆角卡片（`MaterialCardView`，material 1.12.0 已在依赖里）——已知
  上报组件、允许列表（绿）、拦截列表（红）、日志。列表里每条的
  “移到允许”是绿框绿字，“移到拦截”是红框红字，取消决定是灰框灰字
  （`MainActivity.addDecisionRow` 里按状态设背景和文字颜色）。
  新增 `res/values/colors.xml`（深青绿 #00695C、允许绿 #2E7D32、
  拦截红 #C62828、灰 #616161、页面底 #F2F5F3）和 5 个圆角按钮背景
  drawable（`bg_btn_primary` / `bg_btn_outline_teal` /
  `bg_btn_outline_grey` / `bg_btn_outline_green` /
  `bg_btn_outline_red`）。
- **启动图标。** 新增自适应图标，全部是矢量图，没有 PNG：
  `res/mipmap-anydpi-v26/ic_launcher.xml` 和 `ic_launcher_round.xml`
 （深青绿底 `@color/ic_launcher_background` + 前景
  `@drawable/ic_launcher_foreground`），前景是白色盾牌轮廓 + 对勾
  （`res/drawable/ic_launcher_foreground.xml`，108dp 视口）。
  `AndroidManifest.xml` 已恢复 `android:icon="@mipmap/ic_launcher"` 和
  `android:roundIcon="@mipmap/ic_launcher_round"`——v1.0 编不过就是
  因为清单里写了这个图标、工程里却没有这个文件，这次文件已核实存在。
- **网络拦截明确可选、默认关闭。** `MainActivity.vpnOn` 初始就是
  `false`，VPN 只在用户点“开启网络拦截”时才启动，App 里没有任何
  地方自动开 VPN；一键拦截走 Shizuku（`Blocker` / `ShizukuRunner`），
  完全不走 VPN，不开网络拦截也能完整拦截。界面上在网络拦截按钮下面
  加了提示：“如果你在用别的 VPN，这个网络拦截开不了，Android 一次
  只能开一个 VPN，关掉它，只用一键拦截就行。”README 里“网络拦截
  （可选，默认不开）”一节也写了同样的话。不强制开 VPN。
- **Shizuku 状态卡（不能嵌进 App，只能做到最顺）。** Shizuku 的
  服务端是给它自己包名（`moe.shizuku.privileged.api`）授权的独立
  系统进程，本 App 包名不同、没有 Root，拿不到那个授权，也没法把
  服务端嵌进来，所以 Shizuku 必须另外装着——README 里新加了
  “Shizuku 为什么不能合到一个 App 里”一节，如实写了这一点，没有
  谎称已合进去了。v1.2 能做的是在主界面加一张“Shizuku 状态”卡：
  `tvShizuku` 从标题卡挪进这张卡，卡里写了无线调试配对、启动的
  每一步（配对码、通知输码、小米通知改原生样式、重启后要再启动），
  新增 `btnOpenShizuku`（“打开 Shizuku 启动”），点它用
  `packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")`
  跳到 Shizuku，没装就弹窗告诉用户先去装；`btnShizuku`（给 Shizuku
  授权）也挪进了这张卡，和打开按钮并排。
- **版本号升到 1.2**（`app/build.gradle.kts`：versionCode 3，
  versionName "1.2"）。

## v1.2 编译风险（仍未在本机编过）

- 布局改动较大，但所有 `MainActivity` 用到的 binding id 都已核对：
  `btnShizuku` / `btnOpenShizuku` / `btnBlock` / `btnVpn` / `btnRefresh` /
  `btnClearLog` / `tvShizuku` / `tvStatus` / `tvLog` / `tvAllowedEmpty` /
  `llAllowed` / `tvBlockedEmpty` / `llBlocked`——布局里都有，旧的
  `tvDomainsEmpty` / `llDomains` / `refreshDomainDecisions` 已全部删掉，
  没有残留引用。全部 XML 已用解析器过了一遍，都是合法 XML。
- `MaterialCardView` 来自已声明的 `com.google.android.material:material:1.12.0`
  依赖，主题仍是 `Theme.AppCompat.Light.DarkActionBar`；按钮没有用
  MaterialComponents 主题样式，只用了 shape drawable 做背景，主题
  不匹配的风险比较小。如果编时报 `MaterialCardView` 找不到，先确认
  Gradle 同步时 material 1.12.0 下载成功。
- 自适应图标只在 API 26+ 用，minSdk 28，没问题；`ic_launcher.xml`
  里引用的 `@color/ic_launcher_background` 在 `colors.xml` 里有，
  `@drawable/ic_launcher_foreground` 文件存在。
- 在用户电脑上编法不变：Android Studio 打开项目，等同步完，
  Build → Generate App Bundles or APKs → Generate APK(s)，
  APK 在 `app/build/outputs/apk/debug/app-debug.apk`。
  编完装到手机上测试：① 打开 App，界面是卡片式、图标是盾牌；
  ② 点一键拦截，看日志不再报 newProcess 失败；③ 开网络拦截，
  可疑域名弹窗是“只允许一次 / 总是允许 / 拦截”三个按钮；
  ④ 允许列表 / 拦截列表分开显示，点“移到允许 / 移到拦截 /
  取消决定”能改；⑤ 如果手机在用别的 VPN，别开网络拦截，
  只用一键拦截；⑥ 主界面有“Shizuku 状态”卡，点“打开 Shizuku
  启动”能跳到 Shizuku，没装 Shizuku 时点它会弹窗提示先装。

## v1.3（2026-11-06，简单版：只留一键拦截）

- 用户说 v1.2 太麻烦，要“直接做一个程序就行，我开着那个程序就可以”。
  v1.3 按这个要求简化：主界面只剩一个大按钮 **一键拦截**，加一张
  Shizuku 状态卡（显示状态 + “打开 Shizuku 启动” + “给 Shizuku 授权”），
  下面是已知上报组件和日志。打开 App 自动查 Shizuku 状态，点一下就行。
- **删掉的东西：** `DnsVpnService.kt`（VPN 网络拦截，用户在用别的 VPN，
  Android 一次只能开一个 VPN）、`NewAppReceiver.kt`（新装 App 弹窗）、
  `DecisionStore`（允许 / 拦截决定）、MainActivity 里的域名弹窗、
  新装 App 弹窗、允许列表 / 拦截列表（`llAllowed` / `llBlocked` 等）。
  清单里 VPN 服务和 `BIND_VPN_SERVICE` / `FOREGROUND_SERVICE` /
  `INTERNET` / `ACCESS_NETWORK_STATE` 权限也已删；`Blocker.blockOtherApp`
  也已删（只有一键拦截用得上 `blockOne` / `blockAll`）。
- **留下的东西：** v1.2 的卡片式美化界面和盾牌启动图标（矢量自适应图标，
  `ic_launcher.xml` / `ic_launcher_round.xml` / `ic_launcher_foreground.xml`
  都在，清单里 `android:icon` 引用没变）；v1.1 修好的 Shizuku 执行路径
  （`ShizukuRunner` 直接调公开的 `IShizukuService.newProcess()`，不用反射，
  `aidl:13.1.5` 依赖还在）；12 个流氓包名单和 KEEP_OUT 保留名单没变；
  每包结果照样报 已删 / 不在 / 已卸载 / 已禁用 / 后台已拦截 / 失败。
- **版本号升到 1.3**（`app/build.gradle.kts`：versionCode 4，
  versionName "1.3"）。
## v1.3.1（2026-11-06 晚上，补 SDK 位置：明天直接编）

- **问题。** 用户 2026-11-06 下午在电脑上编 v1.3 时报
  `SDK location not found`，然后在文件夹里搜 `local.properties`
  搜不到（他 16:29 发的截图：文件夹搜索“没有与搜索条件匹配的项”）。
  原因是：发给他的 v1.3 包里本来就没有 `local.properties`
  （`.gitignore` 排除了它，BUILD_NOTES 里还写着“不要放进源码包，
  Android Studio 打开项目时会自动生成”——但换新文件夹打开时
  并没有自动生成），而 v1.0 能编成，正是因为最早那个文件夹里
  Android Studio 已经生成过这个文件。
- **改法。** 这次包里直接带上 `local.properties`，一行：
  `sdk.dir=C:/Users/hm01/AppData/Local/Android/Sdk`，
  就是这台电脑上 Android Studio 默认的 SDK 位置（用户名 hm01，
  和之前 v1.0 编成功时同一台电脑、同一个 Android Studio）。
  代码一字没动，还是 v1.3（versionCode 4，versionName "1.3"），
  只是让明天重新解压打开就能编，不用再去设置里找 SDK。
  README 里也加了一节“报 SDK location not found 怎么办”，
  万一他的 SDK 装在别的地方，改那一行就行。
- **还没验证的。** 这个包仍没在本机编过（沙箱 Gradle 守护进程
  TCP 被拦，和以前一样），v1.3 代码本身第一次编译才暴露的错误
  仍可能存在（重点仍是 `IShizukuService` / `IRemoteProcess` 的
  aidl 依赖是否下载成功）。SDK 这一关过去后，如果编的时候报别的
  错，把报错全文发回来，按报错改。
- **明天（2026-11-07）的步骤。** 用户自己说的“明天再弄”。
  步骤只有三步：① 重新下载这个包，解压（不用再找旧文件夹、
  不用再复制 `local.properties`）；② Android Studio 打开它，
  等同步完，如果提示 Gradle JDK 是 Java 25，点蓝字链接
  `Apply compatible Gradle JDK configuration and sync` 改回
  Java 21 再同步；③ Build → Generate APK(s)，编完装到手机上，
  打开点一键拦截。编完装到手机上测试：打开 App 显示 Shizuku 状态，
  点一键拦截 12 个包不再报 newProcess 失败，界面没有网络拦截按钮、
  没有弹窗、没有允许 / 拦截列表。人工检查过的：MainActivity 用到的
  binding id（`btnBlock` / `btnShizuku` / `btnOpenShizuku` /
  `btnRefresh` / `btnClearLog` / `tvShizuku` / `tvStatus` / `tvLog`）
  布局里都有；grep 确认 `DnsVpnService` / `NewAppReceiver` /
  `DecisionStore` / `blockOtherApp` / `btnVpn` / `VpnService`
  在源码里已无任何引用；两个 XML 用解析器过了一遍，都是合法 XML；
  Kotlin 大括号平衡。

## v1.4（2026-11-09，用户确认后做的四样）

- 用户先让网上查别人家再汇报，汇报后确认做。网上 11 月新翻出来的核心
  还是 SystemHelper 和 guardprovider，没有第三个同级别实锤；新加的只有
  2 个 + 1 个有条件：com.miui.misightservice（HyperOS 3 新增诊断遥测）、
  com.android.traceur（系统追踪工具）、com.miui.msa.global（海外版系统
  广告，国行大概率没有）。内置名单从 12 个变 15 个。
- 加回“已拦截”面板：主界面新增 btnPanel，点开是 AlertDialog 列表，
  点一个包先显示中文说明“它是干什么的”，再弹 禁止 / 允许一次 /
  总是允许。DecisionStore.kt 新加，决定存在 SharedPreferences 里。
  允许一次 = 这次放出来，下次一键拦截再拦回去；总是允许 = 以后跳过。
  恢复用 Blocker.restoreOne：install-existing -> enable -> appops allow。
- 检测提示：每个包都有中文说明（RogueList.DESCRIPTION），可信的 Google
  组件（Play 服务、gstatic 等）直接写“别拦”；不认识的包读手机上的
  应用名和要的隐私权限，提示用户，不瞎猜。
- GitHub 更新拦截库：RemoteBlocklist.kt 新加，App 里点“更新拦截库”，
  填 blocklist.json 的 Raw 链接就下载。项目根目录有 blocklist.json
  示例，用户在 GitHub 上改这个文件，手机上点更新，不用重新编 App。
  下载失败时原来那份名单还在，不清空；KEEP_OUT 的包写进来也会被跳过。
  清单里加了 INTERNET 权限，只下载 JSON 文本，不下载、不运行代码。
- 版本号升到 1.4（versionCode 5）。local.properties 还在包里，
  SDK 位置和 v1.3.1 一样。
- 仍未在本机编过（沙箱 Gradle 守护进程 TCP 被拦，和以前一样）。
  新文件 DecisionStore.kt / RemoteBlocklist.kt、Blocker.restoreOne、
  MainActivity 新增的 btnPanel / btnUpdateDb 这两个 binding id
  已和 activity_main.xml 核对过；XML 用解析器过了一遍，都是合法 XML。
  第一次编如果报错，把报错全文发回来，按报错改。

## v1.4.1 闪退修复（2026-11-15，根据用户手机 crash.txt）
- 原因：打开就闪退，日志里是 `InflateException: Error inflating class com.google.android.material.card.MaterialCardView`，在 `activity_main` 第 21 行，`MainActivity.onCreate` 里 binding 一加载布局就崩。布局用了 4 个 MaterialCardView，但 Manifest 里主题是 `Theme.AppCompat.Light.DarkActionBar`，不是 Material 主题，MaterialCardView 在这种主题下加载不起来。
- 修法（不改功能，只换卡片控件）：把布局里 4 个 `com.google.android.material.card.MaterialCardView` 全换成 `androidx.cardview.widget.CardView`，并在 `app/build.gradle.kts` 加 `androidx.cardview:cardview:1.0.0`。CardView 不依赖 Material 主题，AppCompat 主题下也能正常显示，卡片颜色、圆角、阴影属性都不变。
- 版本升到 1.4.1（versionCode 6）。修完要重新编，旧的 v1.4 APK 不能直接用。
- 本机仍未编出 APK（沙箱 Gradle TCP 限制，见上面），请在 GitHub Actions 或用户电脑 Android Studio 里编。

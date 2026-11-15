# 隐私拦截（v1.4）

不用 Root，靠 Shizuku 拦截。给小米 Redmi Turbo 3 / Redmi Note 13 Pro /
HyperOS 做的，不是杀毒软件，也不是系统级实时拦截。

## 怎么用（就三步）

1. **打开 App**，上面会显示 Shizuku 是不是在运行。
2. **点“一键拦截”**，等它跑完，看日志里每个包是“已卸载 / 已禁用 /
   后台已拦截 / 已删 / 不在 / 失败”。
3. **重启手机后**：先打开 Shizuku，让它重新启动一次（点 App 里的
   “打开 Shizuku 启动”，在 Shizuku 里点“通过无线调试启动”），
   再打开本 App，**再点一次“一键拦截”**就行。

就这么简单：打开 App，点一下就行。没有网络拦截，不用 VPN。

## 新增（v1.4，2026-11-09）

- **已拦截面板**：点“已拦截面板”，列出全部拦截名单，点一个包先显示
  中文说明“它是干什么的”，再弹 **禁止 / 允许一次 / 总是允许**。
  允许一次 = 这次放出来，下次一键拦截再拦回去；总是允许 = 以后都不拦。
  恢复用 `pm install-existing` 装回来 + `pm enable` 启用 + appops 放行。
- **检测提示**：每个包都有中文说明；可信的 Google 组件（Google Play 服务、
  gstatic 这些）直接写“别拦”，免得上次那样把 Google 的东西也拦了。
  不认识的包会显示手机上的应用名和它要的权限，让你自己判断，不瞎猜。
- **GitHub 更新拦截库**：点“更新拦截库”，填 GitHub 上 `blocklist.json`
  的 Raw 链接就下载新名单，不用重新编 App。以后有新发现的包，直接在
  GitHub 上改 `blocklist.json`，手机上点更新就行。下载失败时原来那份
  名单还在，不会清空。项目根目录有 `blocklist.json` 示例，照着改就行。

## 一键拦截拦什么

打开 App 点 **一键拦截**，按下面顺序对每个包试一遍，成功就停：
1. `pm uninstall -k --user 0 包名`
2. 失败（`not installed for 0`）时改 `pm disable-user --user 0 包名`
3. 再失败（`Cannot disable system packages`）时改
   `cmd appops set 包名 RUN_ANY_IN_BACKGROUND ignore`

名单（15 个内置，直接拦，不弹窗；再加 GitHub 上更新来的）：
`com.mobiletools.systemhelper`、`com.miui.guardprovider`、`com.miui.analytics`、
`com.miui.daemon`、`com.miui.bugreport`、`com.miui.uireporter`、
`com.miui.contentcatcher`、`com.xiaomi.security.onetrack`、
`com.miui.systemAdSolution`、`com.qualcomm.qti.devicestatisticsservice`、
`com.bsp.catchlog`、`com.miui.dmregservice`、
`com.miui.misightservice`（v1.4 新加，HyperOS 3 系统质量检测）、
`com.android.traceur`（v1.4 新加，系统追踪工具）、
`com.miui.msa.global`（v1.4 新加，海外版系统广告，国行大概率没有）

每个包的结果都会在日志里显示：已卸载 / 已禁用 / 后台已拦截 /
已删 / 不在 / 失败，失败会写明原因，不谎报“已删除”。

## 它不能做什么（先说清楚，别误会）

- **没有 Root 时，系统包删不彻底。** `pm uninstall -k --user 0`
  只删当前用户那一份，恢复出厂 / 系统大更新后可能回来。
- **重启后会回来 / 会掉。** 实测：`analytics`、`contentcatcher`
  重启后回来过；`guardprovider` 的后台拦截（appops ignore）
  重启后会掉回 `allow`。所以重启后要打开本 App 再点一次
  **一键拦截**。
- **不能开机自动运行。** 没有 Root 时，手机自己开机跑不了 ADB 命令，
  必须你开机后打开本 App 点一次。想开机自动禁，得 Root，
  而这台小米现在解不了锁，Root 不了。
- **没有网络拦截。** v1.3 删掉了 VPN 网络拦截，因为用户在用别的 VPN，
  Android 一次只能开一个 VPN。一键拦截走 Shizuku，不走 VPN，
  不开网络拦截也能完整拦截上面那 15 个上报组件。

## 保留不碰的东西

- `com.qualcomm.qti.services.systemhelper`（高通系统服务，和联通那个名字像但不是它）
- 小米云：`com.miui.cloudservice` / `com.miui.micloudsync` / `com.miui.cloudbackup`
- 手机管家：`com.miui.securitycenter`、`com.lbe.security.miui`、`com.miui.securitycore`

## 怎么装 Shizuku

手机上装 Shizuku（应用商店搜 Shizuku，或官网下载）。
第一次要连电脑用 ADB 启动它一次，电脑 CMD 里（`C:\platform-tools`）：
```
adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh
```
新版 Shizuku 也可以用无线调试启动：Shizuku 里点“通过无线调试启动”→
打开开发者选项里的“无线调试”→点“使用配对码配对设备”，把六位数输进
Shizuku 弹出的通知里→回 Shizuku 点“启动”，看到“正在运行”就行。
小米手机要先把通知改成原生样式（设置 → 通知管理 → 通知显示设定），
不然通知里输不了配对码。重启手机后 Shizuku 也要重新启动一次。

本 App 里点 **打开 Shizuku 启动** 就直接跳到 Shizuku，卡里也写了
无线调试配对、启动的每一步，不用再去桌面找 Shizuku。

### Shizuku 为什么不能合到一个 App 里
Shizuku 不能完全嵌进本 App，只能另外装着，原因就一个：它的服务端
是系统里的一个独立进程，是用 ADB / 无线调试给 **Shizuku 自己的包名**
（`moe.shizuku.privileged.api`）授权的，换成本 App 的包名就没这个
权限了；没有 Root 也没法把服务端装进系统里。所以本 App 只能通过
Shizuku API 去调它，调之前 Shizuku 必须已经装好、启动好、授权好。

## 装本 App

把 `app-debug.apk` 传到手机上安装（允许“安装未知应用”）。
打开本 App，点 **给 Shizuku 授权**，在 Shizuku 弹框里点允许，
再点 **一键拦截**。

## v1.4 改了什么（2026-11-09）

- 内置名单从 12 个变 15 个，新加 `com.miui.misightservice`、
  `com.android.traceur`、`com.miui.msa.global`（见上面）。
- 加回“已拦截”面板：能恢复，点一个弹 禁止 / 允许一次 / 总是允许；
  每个包都有中文说明，可信的 Google 组件直接写“别拦”。
- 加“更新拦截库”：GitHub 上改 `blocklist.json`，手机上点更新，
  不用重新编 App。项目根目录有 `blocklist.json` 示例（分行版，好改）。
- 版本号升到 1.4（versionCode 5）。

## v1.3 改了什么（2026-11-06，简单版）

- 主界面只剩一个大按钮 **一键拦截**，加一张 Shizuku 状态卡
  （显示状态 + “打开 Shizuku 启动” + “给 Shizuku 授权”），
  下面是已知上报组件和日志。界面还是 v1.2 的卡片式、深青绿标题，
  启动图标还是深青绿底 + 白色盾牌对勾。
- **删掉了网络拦截（VPN）**：`DnsVpnService.kt` 已删，清单里
  的 VPN 服务和 `BIND_VPN_SERVICE` 等权限也已删。用户在用别的 VPN，
  Android 一次只能开一个 VPN。
- **删掉了所有弹窗和列表**：可疑域名弹窗、新装 App 弹窗、
  允许列表 / 拦截列表都已删，`NewAppReceiver.kt` 和
  `DecisionStore` 已删。流氓包直接拦，不问你。
- 一键拦截还是 v1.1 修好的那条路：直接调公开的 AIDL 接口
  `IShizukuService.newProcess()`，不用反射；包已经不在了就报
  “已删 / 不在”，不再误报失败。
- 版本号升到 1.3（versionCode 4）。

旧版（v1.0–v1.2）有弹窗、允许 / 拦截列表和网络拦截，见
BUILD_NOTES.md 里的记录；v1.3 是用户要的简单版，只留一键拦截。

## 在电脑上编译（如果 APK 不在包里）

本机没有 Android SDK 时，`app-debug.apk` 可能不在包里，
源码是全的，在有 Android Studio 的电脑上这样编：
1. 装 Android Studio（自带 JDK、Gradle、SDK）。
2. 打开 `privacy-blocker-app` 这个文件夹，等 Gradle 同步完。
3. 菜单 Build → Build App Bundle(s) / APK(s) → Build APK(s)，
   生成的 `app-debug.apk` 在 `app/build/outputs/apk/debug/`。
4. 或命令行（装好 SDK、设好 `ANDROID_HOME` 后）：
```
./gradlew assembleDebug
```

### 如果编的时候报 SDK location not found

就是这个包里没有记 SDK 位置的文件。2026-11-06 的 v1.3 包
已经补上了：根目录有个 `local.properties`，里面写着
`sdk.dir=C:/Users/hm01/AppData/Local/Android/Sdk`，
就是这台电脑上 Android Studio 默认装 SDK 的位置。
重新下载这个包，解压，**直接打开就能编**，不用再设 SDK。

如果你的 SDK 装在别的地方（打开 Android Studio →
File → Settings → Languages & Frameworks → Android SDK，
上面那行 Android SDK Location 就是实际位置），
就把 `local.properties` 里那一行改成你那个位置，
斜杠这样写就行：`sdk.dir=C:/Users/你的用户名/AppData/Local/Android/Sdk`。

### 如果打开工程后又报 Gradle JDK 是 Java 25

重新打开工程时 Android Studio 有时会把 Gradle JVM 跳回
Java 25，Gradle 8.7 用不了它。点报错右边那个蓝字链接
**Apply compatible Gradle JDK configuration and sync**，
它会自动改回 Java 21 再同步，同步完再点
Build → Generate APK(s) 重新编一次就行。

技术参数：Kotlin，minSdk 28，targetSdk 34，依赖 Shizuku API 13.1.5，
AndroidX / Material。

# 在 GitHub 上编译隐私拦截（不用 Android Studio，不用给账号密码）

> 先看闪退：v1.4 一打开就闪退，先把 crash 日志发给 Muse 看原因，
> 修好再编才有意义。这个文件是提前备好的，闪退修好就能直接用。

## 第 1 步：新建一个仓库（私有就行）

1. 打开 GitHub，点右上角 **+** → **New repository**。
2. Repository name 随便填一个，比如 `privacy-blocker`。
3. 选 **Private**（私有，只有你自己能看）。
4. **不要**勾选 Add a README，直接点 **Create repository**。

## 第 2 步：把项目传上去

把电脑上 `privacy-blocker-app` 文件夹里的东西传到这个仓库：

- 有 `.github/workflows/build-apk.yml` 这个文件在里面（已经写好了，不用你再写）。
- **不要传 `local.properties`**（它是你 Windows 电脑上的 SDK 位置，
  传上去也没用；就算不小心传了，上面那个配置文件也会在编译前删掉它，不影响编译）。

传法（二选一）：

- **网页上传**：在仓库页面点 **Add file → Upload files**，把文件夹里的
  文件拖进去，点 **Commit changes**。
- **命令行**（装了 git 的话）：在项目文件夹里依次跑
  `git init`、`git add .`、`git commit -m "v1.4"`，
  再按 GitHub 新仓库页面上那段提示把 remote 加上去、push。

## 第 3 步：等它编完，下载 APK

1. 传上去后，GitHub 会自动开始编译（push 一次就编一次）。
2. 在仓库页面点上面 **Actions**，点最新那次运行，等它变成绿色对勾。
   第一次编要下载很多东西，大概要 3–5 分钟，别关页面。
3. 点进去，拉到最下面 **Artifacts**，点
   **privacy-blocker-v1.4-debug** 下载，解压后里面就是 `app-debug.apk`。
4. 把 APK 传到手机上安装，装之前先把手机上旧的隐私拦截卸载掉。

## 编失败了怎么办

- Actions 那次运行点进去，点红叉那一步，报错在最下面。
- 把报错那几行截图发给 Muse，按报错改，不用重传整个项目。
- 最常见的两种报错：
  - `SDK location not found`：就是 `local.properties` 没删掉，
    检查一下仓库里有没有这个文件，有就删掉再推一次。
  - 下载超时：点右上角 **Re-run jobs** 再跑一次就行。

## 每次改代码后怎么再编

改完源码，重新传上去（push）一次，GitHub 就自动再编一次，
再到 Actions 里下载新的 APK。不用每次都新建仓库。

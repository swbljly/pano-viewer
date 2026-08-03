# 360° 全景查看器（pano-viewer）

跨平台的 **等距圆柱（equirectangular）全景图** 查看软件，支持 **网页 / Windows 桌面 exe / Android** 三端。
核心是一个**纯前端网页查看器**（Three.js 本地内置，完全离线、零 CDN 依赖），桌面与 Android 端只是用各自的「壳」把它加载起来。

> 姐妹项目：[`../pano-downloader`](../../pano-downloader)（720yun 全景下载器，把全景下载为等距圆柱 JPG）。本查看器直接打开其产物（2:1 的 JPG）。

---

## 功能

- 打开本地等距圆柱图（**拖拽到窗口 / 点击「打开图片」/ 命令行或启动参数传入路径**）
- **鼠标拖动环视**（改变 yaw / pitch）
- **滚轮缩放**（改变 FOV）
- **全屏切换**
- **自动旋转开关**（缓慢自转 yaw）
- 支持键盘快捷键：方向键环视、`+/-` 缩放、`空格` 自动旋转、`F` 全屏、`R` 复位
- 移动端双指捏合缩放

---

## 目录结构

```
pano-viewer/
├── viewer/                     # 纯前端核心查看器（三端共用）
│   ├── index.html              # 入口页面
│   ├── viewer.js               # 渲染逻辑（Three.js：球贴图 + 相机环视）
│   ├── viewer.css              # 样式
│   └── three.min.js            # 本地内置 Three.js (r160 UMD)，离线可用
├── pano_viewer_app.py          # Windows 桌面启动器（pywebview + 本地 HTTP 服务器）
├── build_viewer_exe.py         # PyInstaller 打包脚本（onefile --windowed）
├── requirements.txt            # 桌面端依赖（pywebview）
├── android/                    # Android Studio WebView 工程（完整可构建）
│   ├── settings.gradle / build.gradle / gradle.properties
│   └── app/
│       ├── build.gradle
│       ├── src/main/AndroidManifest.xml
│       ├── src/main/java/com/example/panoviewer/MainActivity.java
│       ├── src/main/res/...     # 布局 / 主题 / 字符串
│       └── src/main/assets/viewer/   # 内置的网页查看器（与 viewer/ 同源）
└── README.md
```

---

## 一、网页端验证（最快）

双击打开 `viewer/index.html` 即可（或在 viewer 目录起一个本地 server）：

```bash
# 方式 A：直接双击 viewer/index.html（file:// 下拖拽 / 文件选择器均可用）
# 方式 B：本地 server（某些浏览器对 file:// 更严格时用这个）
cd viewer && python -m http.server 8080
# 浏览器访问 http://127.0.0.1:8080/
```

验证步骤：
1. 把一张等距圆柱图（如 4608×2304 的 JPG）**拖拽**到窗口，或点「打开图片」选择。
2. 拖动鼠标环视，滚轮缩放，点「自动旋转」「全屏」「复位」体验各功能。
3. 也可通过 URL 参数预载图片：`http://127.0.0.1:8080/?image=<图片URL>`。

> 说明：双击 `file://` 打开时，图片通过 `FileReader` 转 dataURL 再贴图，**不触发跨域**；
> `?image=` 参数方式更适合同源的本地 server / 桌面 exe 场景。

---

## 二、Windows 桌面 exe

### 1. 开发运行
```bash
pip install -r requirements.txt      # 安装 pywebview
python pano_viewer_app.py             # 启动查看器
python pano_viewer_app.py "D:/a.jpg"  # 启动即载入指定全景图
```
Windows 上 pywebview 默认使用 WebView2（Edge 内核），一般 Win10/11 已自带；若缺失请安装
[WebView2 Runtime](https://developer.microsoft.com/microsoft-edge/webview2/)。

### 2. 打包成离线 exe
```bash
pip install pyinstaller
python build_viewer_exe.py
```
产物：`dist/360全景查看器.exe`（单文件、`--windowed` 无控制台窗口）。
- 双击运行即打开查看器；`360全景查看器.exe "D:/a.jpg"` 直接载入图片。
- Three.js 已随 `viewer/` 打进 exe（`sys._MEIPASS/viewer`），**断网环境照常运行**。
- windowed 健壮性：标准流为 None 时补 devnull 占位；顶层异常用 `MessageBoxW` 弹窗提示，
  避免「双击没反应又看不到错误」。

---

## 三、Android（APK）

> ⚠️ **关于「正式版」的诚实说明**：本开发环境**没有安装 Android SDK，也没有你的签名密钥（keystore）**，因此我**无法在这里直接编译出正式签名的 APK 二进制**。我已完成的是：把工程配置成「填上密钥即可一键出正式版」的状态，并给你下面这套本地一条命令出签名的流程。正式发布必须由你在本机（或 CI）用 Android Studio / Gradle 完成。

### 1. 调试版（无需密钥，最快验证）
1. 用 **Android Studio** 打开 `android/` 目录（首次打开会自动生成 Gradle Wrapper）。
2. 等待 Gradle 同步完成（联网下载 AGP / 依赖一次即可）。
3. 菜单 **Build → Build Bundle(s) / APK(s) → Build APK(s)**，或连接设备后点 ▶ Run。
4. 产物：`android/app/build/outputs/apk/debug/app-debug.apk`（使用的是 Android 默认 debug 密钥，**不能上架**）。

### 2. 正式发布版（自有密钥签名，可上架）
> 正式版必须用**你自己的签名密钥**签，否则无法发布到应用商店 / 无法覆盖安装旧版。

**第 1 步：生成签名密钥库**（仅首次，JDK 自带 `keytool`）
```bash
cd android
keytool -genkeypair -v -keystore my-release-key.jks \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -alias panoviewer \
        -storepass 你的库密码 -keypass 你的密钥密码
# 按提示填姓名/组织等信息，生成的 my-release-key.jks 妥善保管，丢失后无法更新 App！
```

**第 2 步：填写密钥配置**
```bash
cp keystore.properties.example keystore.properties
# 用编辑器打开 keystore.properties，把示例密码改成你上面设的真实密码
```

**第 3 步：构建正式版 APK**
```bash
cd android
./gradlew assembleRelease        # Linux / macOS
# gradlew.bat assembleRelease    # Windows
```
产物：`android/app/build/outputs/apk/release/app-release.apk`（已用你的密钥签名、已混淆压缩，可直接上架或分发）。

> 校验签名：`jarsigner -verify -verbose -certs app-release.apk`，应显示 `jar verified`。
> 密钥库（`my-release-key.jks`）和 `keystore.properties` 都含敏感信息，**不要提交到仓库**。

### 构建/运行要求
- Android Studio（或命令行 Gradle）+ Android SDK（compileSdk 34，minSdk 21）。
- 首次构建需联网下载 AGP 与 AppCompat 依赖一次。

### 使用
- 安装后打开 App，点「打开图片」在 App 内选择全景图（WebView 文件选择器，全程离线）。
- 也可在系统相册 / 文件管理器里「用其他应用打开」一张图片 → 选「360° 全景查看器」，
  会自动载入该图。
- 拖动环视、双指捏合缩放、点工具栏按钮全屏 / 自动旋转。

> 说明：查看器与 three.min.js 已置于 `app/src/main/assets/viewer/`，打包进 APK，
> 运行时通过 `file:///android_asset/viewer/index.html` 加载，**无任何网络请求**。

### 4. 通过 GitHub Actions 自动出正式版（推荐）

把工程推到 GitHub 后，可用 GitHub Actions 在云端**自动签出正式版 APK**，无需本机 Android Studio。流程：打版本 tag（如 `v1.0.0`）push，或到仓库 **Actions** 页手动运行 `Build Release APK` 工作流。

**第 1 步：在仓库配置 4 个 Secrets**（仓库 → **Settings → Secrets and variables → Actions → New repository secret**）

| Secret 名称 | 含义 / 生成方法 |
|---|---|
| `KEYSTORE_BASE64` | 你的签名密钥库（`.jks`）的 **base64 字符串**。<br>先本地用 `keytool` 生成 `my-release-key.jks`（命令见下方），再：<br>`base64 -w0 my-release-key.jks` 把整行输出填进该 Secret。 |
| `KEYSTORE_PASSWORD` | 密钥库密码（即 `keytool -storepass` 的值），对应 `keystore.properties` 的 `storePassword`。 |
| `KEY_ALIAS` | 密钥别名（即 `keytool -alias` 的值，如 `panoviewer`），对应 `keyAlias`。 |
| `KEY_PASSWORD` | 密钥密码（即 `keytool -keypass` 的值），对应 `keyPassword`。 |

> 生成密钥库的命令（仅首次，与「三、2. 正式发布版」一致）：
> ```bash
> keytool -genkeypair -v -keystore my-release-key.jks \
>         -keyalg RSA -keysize 2048 -validity 10000 \
>         -alias panoviewer \
>         -storepass 你的库密码 -keypass 你的密钥密码
> base64 -w0 my-release-key.jks        # 复制整行输出作为 KEYSTORE_BASE64
> ```

**第 2 步：触发构建**
- **方式 A（推荐）**：打 tag 并 push
  ```bash
  git tag v1.0.0
  git push origin v1.0.0
  ```
- **方式 B**：到仓库 **Actions → Build Release APK → Run workflow** 手动运行。

**第 3 步：下载产物**
构建完成后，在 workflow 运行页的 **Artifacts** 里下载 `app-release-apk`，解压得到 `android/app/build/outputs/apk/release/app-release.apk`（已用你的密钥签名、已混淆压缩，可直接分发/上架）。

**说明**
- 仓库**无需提交** `my-release-key.jks` / `keystore.properties`，密钥全部走 GitHub Secrets（已被 `android/.gitignore` 排除）。
- 工程首推在 **Android Studio 打开一次**（自动生成 Gradle Wrapper 并提交到仓库）；CI 也会在检测到仓库缺失 `gradlew` 时**自动生成 Wrapper 自愈**，所以即便没有本地提交 wrapper 也能跑通。
- 工作流文件：`.github/workflows/build-release-apk.yml`。

---

## 技术决策

| 项 | 决策 | 理由 |
|---|---|---|
| 渲染库 | **Three.js r160（UMD 本地内置）** | 需求优先 vendor Three.js；本环境可联网下载并内置到 `viewer/three.min.js`，运行时零外网。 |
| 降级方案 | 原生 WebGL 未启用 | 因成功内置 Three.js，按「优先 Three.js」原则采用之；若未来需零依赖，可改用 `webgl-viewer.js` 替代（结构已预留）。 |
| 桌面壳 | **pywebview + 本地 HTTP 服务器线程** | 规避 `file://` 下 fetch / 纹理跨域限制；`http://127.0.0.1:PORT` 同源加载，离线可用。 |
| Android 壳 | **WebView 加载 assets** | 内置查看器随 APK 分发，`<input type=file>` 由 `WebChromeClient.onShowFileChooser` 支持；外部「打开图片」intent 经 base64 桥接注入。 |
| 图片加载 | `FileReader`→dataURL（浏览器/WebView）/ `?image=` 路由（exe server） | 既保证 file:// 双击可用，又支持桌面 exe 的命令行传参与系统文件对话框。 |

---

## 已知限制 / 备注

- 仅支持**等距圆柱（equirectangular）单图**，不处理立方体 6 面。
- 桌面 exe 依赖系统 WebView2（Windows）；Android 需 API 21+。
- Android APK 需自行在 Android Studio 构建（本仓库不含 Gradle Wrapper 二进制，首次打开由 AS 生成）。

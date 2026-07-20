# CollectorKing 收集王

一款证件照片收集与打包分享的 Android 应用，支持自定义模板、拍照/相册选图、照片压缩、ZIP 打包与一键分享。

## 功能特性

- **模板管理** — 创建/编辑/删除/导入/导出收集模板，自定义证件类型与数量
- **照片拍摄** — 单击拍照，长按从相册多选，支持旋转缩略图预览
- **智能压缩** — 可调节压缩质量（10%-100%），节省存储与传输空间
- **ZIP 打包** — 按类型分文件夹或不分类打包，支持时间戳命名，同名自动追加
- **一键分享** — 打包完成后直接通过系统分享发送至微信/QQ/邮件等
- **历史记录** — 自动归档已打包的收集记录，支持查看与重新分享
- **主题定制** — 8 种主题色 + 深色模式，个性化外观
- **照片命名** — 按证件类型或拍摄顺序两种命名方式

## 隐私说明

- 本APP纯本地、不联网、不收集数据。仅申请必要权限（相机、存储、相册）。

## 技术架构

- **前端**：HTML/CSS/JS（WebView 混合架构）
- **原生层**：Kotlin + Android SDK
- **桥接通信**：NativeBridge（`@JavascriptInterface`）
- **打包引擎**：Kotlin `ZipOutputStream`
- **文件存储**：外部私有目录（`getExternalFilesDir`）+ FileProvider 分享
- **UI 框架**：Tailwind CSS + Lucide Icons

## 环境要求

- Android Studio Hedgehog | 2023.1.1+
- Android SDK 36（compileSdk / targetSdk）
- 最低支持 Android 8.0（minSdk 26）
- Kotlin 1.9+

## 构建与运行

```bash
# 克隆仓库
git clone https://github.com/Elvis-okk/CollectorKing.git
cd CollectorKing

# 使用 Android Studio 打开项目
# File → Open → 选择 CollectorKing 目录

# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK
./gradlew assembleRelease
```

## 项目结构

```
CollectorKing/
├── app/
│   ├── src/main/
│   │   ├── assets/web/
│   │   │   └── index.html          # 前端界面（单页应用）
│   │   ├── java/com/collectorking/app/
│   │   │   ├── MainActivity.kt     # 主活动：拍照、压缩、打包、分享
│   │   │   └── NativeBridge.kt     # JS 桥接：WebView ↔ Native 通信
│   │   ├── res/
│   │   │   ├── xml/file_paths.xml  # FileProvider 路径配置
│   │   │   └── ...
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
├── build.gradle.kts
└── settings.gradle.kts
```

## 版本历史
- V1.5.4  照片预览新增下载到相册；历史记录新增批量删除（按天数选择+倒计时确认）；修复保存到相册不显示的问题
- V1.5.3  跳过版本
- V1.5.2  修复HarmonyOS系统下相关问题：无法调用相机、显示内容与状态栏重叠、深色模式不跟随系统的问题；
- V1.5.1  UI界面优化；优化主题显示；修复自定义背景预览框显示不正确的问题；新增自定义背景历史及缩略图；
- V1.5.0  UI界面优化；新增清理数据数据显示及二次确认；新增使用统计；新增自定义背景（上传/裁切/透明度/模糊度）；新增软件更新入口
- V1.4.1  优化弹窗样式；新增清理缓存功能；照片旋转缩略图适配
- V1.4.0  修复深色模式有时候无法跟随系统；修复照片缩略图显示偏移；修复历史记录删除后存储未释放的问题
- V1.3.1  修复返回手势退出后重入APP失效；修复历史照片存储上限问题；修复HyperOS下深色模式不跟随系统的问题；新增粉红/灰色/黑白主题色
- V1.3.0  新增主题颜色切换（6种配色）；新增深色模式（支持跟随系统）；修复照片预览居中问题；修复旋转方向残留问题； 优化历史照片存储策略
- V1.2.2  修复历史预览图片层级遮挡； 修复手势返回偶发性失效
- V1.2.1  修复返回键导航逻辑；修复权限请求时黑屏闪退问题
- V1.2.0  新增照片命名方式与打包归类设置；修复历史记录照片丢失问题；优化对话框样式
- V1.1.0  全面屏适配；暂存功能优化；新增自定义图标
- V1.0.0  新增照片压缩功能；新增模板的导入导出；新增自定义证件类型


## License

Apache 2.0

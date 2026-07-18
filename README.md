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

### v1.4.4
- Toast 样式优化：白底 + 底部导航栏上方定位
- 上传进度改为弹窗样式：绿色进度条 + 百分比
- 新增压缩包命名时间戳设置
- 不分类打包改为放入以包名命名的文件夹
- FileProvider 路径修复
- 计数器显示已上传照片总数

## License

MIT
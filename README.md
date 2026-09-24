# FK 输入法

[English](README.en.md)

FK 输入法是一款实验性的 Android 输入法，重点是为中文拼音提供可选的谐音候选。它不保证特定游戏或聊天服务一定接受这些文字。

## 功能

- 拼音九键、拼音全键盘、英文直输、数字和标点；横竖屏分别记住布局选择。
- 两行可独立横滑的中文候选：上行谐音改写，下行普通拼音候选，每行首屏最多显示五项。
- 本地谐音改写词条。完整命中词条时逐字改写；长按下行正常候选可把整个词加入，不会同时提交文字。
- 独立的本地敏感词库文件，预置 2,482 条词。聊天应用支持系统文本选择菜单时，长按消息选中文本并点击“加入敏感词库”即可保存；这份文件暂不参与候选生成或消息过滤。
- 九键和全键共用本地拼音习惯队列。选中下行候选只前移一位；上行及语音选择不参与学习。
- 可选的按住说话功能，连接用户自行配置的兼容 WSS 服务。应用内不预置服务地址或凭证。

## 安装与使用

项目支持 Android 10 起（`minSdk 29`），目前只构建 ARM64 APK。可在 [Releases 页面](https://github.com/cassiarota/fk-input/releases)下载已签名安装包，也可按照[构建与测试](#构建与测试)生成调试包，在 ARM64 设备安装 `app/build/outputs/apk/debug/app-debug.apk`。Git 不提交 APK 或签名材料。

打开 FK 输入法，在 Android 输入法设置中启用并选择它。启用第三方输入法时，Android 会显示标准安全提示。竖屏默认九键，横屏默认全键，可用键盘工具栏分别切换。

中文模式下，空格、标点和回车会先提交上行第一个谐音候选。若命中词库但没有有效谐音，应用**不会自动提交原词**，需要手动选择下行候选。设置页可管理词条、导入导出 UTF-8 文本以及重置学习顺序。内置示例词条均可删除。

首次打开应用或输入法时，默认敏感词会复制到应用私有的 `files/sensitive-words.txt`；以后只保留用户文件，不会用新版本的默认表覆盖。选中文本入口使用 Android 的 `PROCESS_TEXT` 菜单，具体是否显示由聊天应用的文本选择实现决定。加入前会显示选中的词供确认，重复词不会改写文件。词库来源和许可见 [来源说明](app/src/main/assets/data/SENSITIVE-WORDS-SOURCES.md)。

密码输入框停用候选、学习和语音。

## 可选语音服务

先授予麦克风权限，再在设置页填写 WSS 地址和访问凭证，语音功能才会启用。两项配置均通过 Android Keystore 在本机加密保存，不会打包进 APK。九键长按 `0`、全键长按空格开始录音；松开发送、上滑取消，单次最多 30 秒。

客户端先发送 `start`，随后以约 100 毫秒一帧上传 16 kHz、单声道 PCM16 音频。兼容服务应返回 `ready`、可修订的 `partial`、按 `segment_id` 标识的 `final`，并在 `end` 后返回 `done`。客户端通过 Bearer 请求头鉴权，收到 `done` 后才生成可选候选。客户端不持久化音频或识别原文；用户自行配置的服务如何留存内容不由本应用控制。网络或鉴权失败时，原有键盘输入会保留。

## 构建与测试

工具链：JDK 17、Android Gradle Plugin 8.13.2、Gradle 8.13、Kotlin 2.2.20、Android SDK 36、NDK 28.0.13004108、CMake 3.22.1。设置 `ANDROID_HOME`，或在不提交的 `local.properties` 中指定 SDK 路径。

```sh
./scripts/fetch-native.sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

原生依赖脚本从固定提交获取 ARM64 静态库，产物不纳入 Git。JNI 桥接代码为本项目原创，没有复用 Trime 客户端或 JNI。Rime 自身的用户词频学习已关闭，由本地 SQLite 队列控制候选顺序。

发布签名密钥应放在仓库外。在本地环境或密钥管理器中设置 `FK_SIGNING_KEYSTORE`、`FK_SIGNING_ALIAS`、`FK_SIGNING_PASSWORD`，再运行 `./scripts/sign-release.sh`。不要把签名信息写入仓库文件或 shell 历史。生成的 `release/` APK 被 Git 忽略。

GitHub Actions 使用仓库加密密钥 `FK_SIGNING_KEYSTORE_B64`、`FK_SIGNING_ALIAS`、`FK_SIGNING_PASSWORD` 签名。推送 `vX.Y.Z` 标签会运行单元测试、构建 Debug 和 Release APK，并把签名包发布为该版本的 GitHub Release，标记为 Latest。请为每个新版本使用新标签；版本号和 Android `versionCode` 由标签计算。也可从 Actions 手动输入版本运行构建，下载测试产物；手动运行不会创建 Release。Debug APK 与发布 APK 签名不同，不能直接覆盖安装彼此。

## 当前验证状态

已在 Android 36 ARM64 模拟器验证安装、输入法注册、竖屏九键和横屏全键候选、密码框行为以及长按候选入库。JVM 测试覆盖候选规则、排序和敏感词文件；设备测试覆盖 SQLite 持久化、模拟 WebSocket 语音事件，以及系统文本选择动作的确认、写入和去重。聊天应用是否显示此系统菜单取决于宿主实现，尚未逐个验证。实体手机及用户自配服务的真实音频端到端测试仍待完成。

## 许可

第三方归属与许可见 [THIRD_PARTY.md](THIRD_PARTY.md)，许可正文也随 APK 放在 `app/src/main/assets/licenses/`。FK 输入法原创应用代码目前尚未授予明确许可；仓库公开不等于自动授权复用。

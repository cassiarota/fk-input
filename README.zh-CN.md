# FK 输入法

[English](README.md)

FK 输入法是一款实验性的 Android 输入法，重点是为中文拼音提供可选的谐音候选。它不保证特定游戏或聊天服务一定接受这些文字。

## 功能

- 拼音九键、拼音全键盘、英文直输、数字和标点；横竖屏分别记住布局选择。
- 两行可独立横滑的中文候选：上行谐音改写，下行普通拼音候选，每行首屏最多显示五项。
- 本地全局词库。完整命中词条时逐字改写；长按下行正常候选可把整个词加入词库，不会同时提交文字。
- 九键和全键共用本地拼音习惯队列。选中下行候选只前移一位；上行及语音选择不参与学习。
- 可选的按住说话功能，连接用户自行配置的兼容 WSS 服务。应用内不预置服务地址或凭证。

## 安装与使用

项目支持 Android 10 起（`minSdk 29`），目前只构建 ARM64 APK。可[下载已签名的 ARM64 预发布安装包](https://github.com/cassiarota/fk-input/releases/tag/v0.1.0)，也可按照[构建与测试](#构建与测试)生成调试包，在 ARM64 设备安装 `app/build/outputs/apk/debug/app-debug.apk`。Git 不提交 APK 或签名材料。

打开 FK 输入法，在 Android 输入法设置中启用并选择它。启用第三方输入法时，Android 会显示标准安全提示。竖屏默认九键，横屏默认全键，可用键盘工具栏分别切换。

中文模式下，空格、标点和回车会先提交上行第一个谐音候选。若命中词库但没有有效谐音，应用**不会自动提交原词**，需要手动选择下行候选。设置页可管理词条、导入导出 UTF-8 文本以及重置学习顺序。内置示例词条均可删除。

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

## 当前验证状态

已在 Android 36 ARM64 模拟器验证安装、输入法注册、竖屏九键和横屏全键候选、密码框行为以及长按入库。JVM 测试覆盖候选规则和排序；设备测试覆盖 SQLite 持久化和模拟 WebSocket 语音事件。实体手机及用户自配服务的真实音频端到端测试仍待完成。

## 许可

第三方归属与许可见 [THIRD_PARTY.md](THIRD_PARTY.md)，许可正文也随 APK 放在 `app/src/main/assets/licenses/`。FK 输入法原创应用代码目前尚未授予明确许可；仓库公开不等于自动授权复用。

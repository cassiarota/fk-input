# FK 输入法

[English](README.en.md)

FK 输入法是一款实验性的 Android 输入法，重点是为中文拼音提供可选的谐音候选。它不保证特定游戏或聊天服务一定接受这些文字。

## 功能

- 拼音统一使用26键，横竖屏均为 QWERTY；支持英文直输、数字和标点。手机号、纯数字、邮箱和网址输入框自动切换专用键位。
- 键盘顶部“切换”键点按切回上一个输入法，长按打开系统输入法列表；没有可返回的输入法时，点按也会打开列表。
- 两行同步横滑的中文候选：上行谐音改写，下行正常交流词，每列一一对应，首屏最多显示五列。谐音逐字匹配拼音和声调，包括轻声；没有有效替换时上行显示不可选的 `—`。
- 正常候选优先使用完整词汇与词频，内置112条日常交流和游戏常用表达作为补充，例如 `made` 首选“妈的”。Rime 长句候选保留；自动拼接只作为后备。
- 本地谐音改写词条。完整命中词条时逐字改写；长按下行正常候选可把整个词加入，不会同时提交文字。
- 本地敏感词库预置 2,482 条词，预置词及用户新增词统一保存在 SQLite，并与应用私有 UTF-8 文件同步。聊天应用支持系统文本选择菜单时，长按消息选中文本并点击“加入敏感词库”即可保存；这份词库暂不参与候选生成或消息过滤。
- 键盘编辑栏支持光标左右移动、选中编辑框文字；长按退格连续删除。中文长句可点“短词/长词”调整当前候选范围，选词后继续输入，点已选片段可回退修改。
- 本地拼音习惯队列。选中下行候选只前移一位，上方对应谐音随之更新；上行及语音选择不参与学习。
- 可选的按住说话功能，连接用户自行配置的兼容 WSS 服务。应用内不预置服务地址或凭证。

## 安装与使用

项目支持 Android 10 起（`minSdk 29`），目前只构建 ARM64 APK。可在 [Releases 页面](https://github.com/cassiarota/fk-input/releases)下载已签名安装包，也可按照[构建与测试](#构建与测试)生成调试包，在 ARM64 设备安装 `app/build/outputs/apk/debug/app-debug.apk`。Git 不提交 APK 或签名材料。

打开 FK 输入法，在 Android 输入法设置中启用它。启用第三方输入法时，Android 会显示标准安全提示。仅启用不会替换当前输入法，你可以继续使用手机原来的输入法。

需要在游戏等场景使用时，通过系统键盘切换入口选择 FK，也可打开 FK 设置页点击“选择 / 切换输入法”。用完后点按 FK 键盘顶部的“切换”键，直接返回上一个输入法；长按可选择其他已启用的输入法，没有可返回的输入法时也会打开列表。切换前会按现有候选规则完成未上屏的输入；遇到需手动选词的情况，请先处理候选再切换。Android 的输入法选择对所有应用生效，本应用不会在进入或退出游戏时自动切换。

横竖屏都使用26键，旧版保存的九键布局选择不再生效。升级时自动刷新内置 Rime 词典，用户词条与学习顺序保留。

谐音须保持原词的读音与声调。例如“妈的”对应“妈地”（`ma1 de5`），不会使用“吗得”这种声调不一致的组合。词组读音表用于区分多音字和轻声；无法确定读音或没有严格同调替换时，保留对应空位。

中文模式下，空格、标点和回车会先提交上行第一个谐音候选。若命中词库但没有有效谐音，应用**不会自动提交原词**，需要手动选择下行候选。设置页可管理词条、导入导出 UTF-8 文本以及重置学习顺序。内置示例词条均可删除。

首次打开应用或输入法时，默认敏感词会复制到应用私有的 `files/sensitive-words.txt`，并导入 SQLite 的 `sensitive_words` 表；旧版用户文件也会导入，已有用户词不会被默认表覆盖。选中文本入口使用 Android 的 `PROCESS_TEXT` 菜单，具体是否显示由聊天应用的文本选择实现决定。加入前会显示选中的词供确认，重复词不会改写文件。词库来源和许可见 [来源说明](app/src/main/assets/data/SENSITIVE-WORDS-SOURCES.md)。

密码输入框停用候选、学习和语音。

## 可选语音服务

先授予麦克风权限，再在设置页填写 WSS 地址和访问凭证，语音功能才会启用。两项配置均通过 Android Keystore 在本机加密保存，不会打包进 APK。长按空格开始录音；松开发送、上滑取消，单次最多 30 秒。

客户端先发送 `start`，随后以约 100 毫秒一帧上传 16 kHz、单声道 PCM16 音频。兼容服务应返回 `ready`、可修订的 `partial`、按 `segment_id` 标识的 `final`，并在 `end` 后返回 `done`。客户端通过 Bearer 请求头鉴权，收到 `done` 后才生成可选候选。客户端不持久化音频或识别原文；用户自行配置的服务如何留存内容不由本应用控制。网络或鉴权失败时，原有键盘输入会保留。

## 构建与测试

工具链：JDK 17、Android Gradle Plugin 8.13.2、Gradle 8.13、Kotlin 2.2.20、Android SDK 36、NDK 28.0.13004108、CMake 3.22.1。设置 `ANDROID_HOME`，或在不提交的 `local.properties` 中指定 SDK 路径。

```sh
./scripts/fetch-native.sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

原生依赖脚本从固定提交获取 ARM64 静态库，产物不纳入 Git。JNI 桥接代码为本项目原创，没有复用 Trime 客户端或 JNI。Rime 自身的用户词频学习已关闭，由本地 SQLite 队列控制候选顺序。

词组声调表是预生成的离线资源，应用运行时不依赖 Python。更新词典时，在 Python 虚拟环境安装 `pypinyin==0.55.0`，再生成上下文读音：

```sh
python scripts/generate_phrase_readings.py --readings app/src/main/assets/data/readings.tsv --output app/src/main/assets/data/phrase_readings.tsv app/src/main/assets/data/pinyin_simp.dict.yaml app/src/main/assets/data/conversation.dict.yaml
```

发布签名密钥应放在仓库外。在本地环境或密钥管理器中设置 `FK_SIGNING_KEYSTORE`、`FK_SIGNING_ALIAS`、`FK_SIGNING_PASSWORD`，再运行 `./scripts/sign-release.sh`。不要把签名信息写入仓库文件或 shell 历史。生成的 `release/` APK 被 Git 忽略。

GitHub Actions 使用仓库加密密钥 `FK_SIGNING_KEYSTORE_B64`、`FK_SIGNING_ALIAS`、`FK_SIGNING_PASSWORD` 签名。推送 `vX.Y.Z` 标签会运行单元测试、构建 Debug 和 Release APK，并把签名包发布为该版本的 GitHub Release，标记为 Latest。请为每个新版本使用新标签；版本号和 Android `versionCode` 由标签计算。也可从 Actions 手动输入版本运行构建，下载测试产物；手动运行不会创建 Release。Debug APK 与发布 APK 签名不同，不能直接覆盖安装彼此。

## 当前验证状态

已在 Android 36 ARM64 模拟器验证安装、输入法注册、横竖屏26键候选、密码框行为、网址专用键位、键盘光标移动与选中删除、长按连续删除、拼音局部选词上屏，以及长按候选入库。快捷切换已验证启用时保留当前输入法、点按返回上一个输入法、长按打开列表、无历史输入法时回退列表，以及切换前完成拼音候选和局部选词未完成时保留输入。JVM 测试覆盖候选规则、组词状态、输入框类型、排序和敏感词文件；设备测试覆盖旧版 SQLite 升级、敏感词同步、拼音局部候选、Rime 词典升级刷新、常用词排序、后备组词、多音字与轻声、上下行分别上屏及同步横滑、模拟 WebSocket 语音事件，以及系统文本选择动作的确认、写入和去重。聊天应用是否显示此系统菜单取决于宿主实现，尚未逐个验证。实体手机及用户自配服务的真实音频端到端测试仍待完成。

## 许可

第三方归属与许可见 [THIRD_PARTY.md](THIRD_PARTY.md)，许可正文也随 APK 放在 `app/src/main/assets/licenses/`。FK 输入法原创应用代码目前尚未授予明确许可；仓库公开不等于自动授权复用。

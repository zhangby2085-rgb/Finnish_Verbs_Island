# 芬兰语动词闯关岛（Verbisaari）

给在芬兰读 4 年级、母语是中文的孩子练 **芬兰语现在时动词变位**（preesens / persoonataivutus）。
Kotlin + Jetpack Compose (Material 3)，单 Activity，全离线，无后端。

- 安装包：[`release/FinnishVerbIsland.apk`](release/FinnishVerbIsland.apk)
- 需求原文与改动说明：[`docs/需求.md`](docs/需求.md)
- 演示视频（不在仓库里，视频文件太大）：`..\演示视频\芬兰语动词闯关岛_演示.mp4`

## 5 关

| 关 | 内容 | 题型 |
|---|---|---|
| 1 词尾配对小达人 | 固定词干 `puhu-` + 6 个词尾，代词和词尾同色 | 6 张彩色词尾卡 |
| 2 规则动词大挑战 | 17 词（含 `unohtaa` 的 ht → hd 提示） | 选变位 + 1 道“谁在做？” |
| 3 KPT 辅音“变软”魔法 | 14 词，顶部置顶黄金口诀，小锤子 `AnimatedContent` 动画 | 选变位（陷阱：kirjoittan / luee） |
| 4 吃喝与校园日常 | 20 词（-da/-dä、-la/-na/-ra/-sta、tehdä/nähdä/olla） | 选变位 + 1 道“谁在做？” |
| 5 状态、需求与否定句 | 14 词（11 + 3 个补充词），en/et/ei… + 光杆词干 | 2 道肯定 + 3 道否定 |

每轮随机 5 题、约 90 秒。没见过的动词和最近答错的动词更容易被抽到。
第一次就答对 +1 ⭐；一轮 5 题全对 3 星、3–4 题 2 星。通过一关解锁下一关（家长设置里可以全部解锁）。

## 目录

```
app/                      Android 工程（Gradle）
  src/main/java/com/finnishkids/verbisland/
    VerbData.kt           65 个动词：原形、中文、6 个人称变位、动词类型、KPT 强/弱词干、特别提醒
    GameLogic.kt          出题（含典型错误陷阱）与每种错误的中文解析
    FinnishTtsManager.kt  系统 TTS（Locale("fi","FI")，语速 0.88）+ 内置录音兜底
    MainActivity.kt       全部界面：岛屿地图、闯关、结算庆祝、动词词卡本、语音/家长设置
  src/main/assets/fi/     855 条内置芬兰语录音（tools/make_voice.py 生成）
  src/test/               VerbDataTest：检查 65 个变位表和 2000 轮出题，把所有提示写到 app/build/hints.txt
release/                  打包好的 APK
design/
  images/                 原始的 16 张图片（文件名 08–16 和画的内容对不上，见 design/README.md）
  original-draft/         最早的草稿代码（不能直接编译，只作参考）
docs/
  需求.md                 需求原文 + 实现时的改动
  youtube/                YouTube 封面图 thumbnail.png 和说明文字（标题、简介、章节时间）
tools/
  prepare_images.py       design/images → app 里的 WebP 图片和启动图标
  make_voice.py           用 Piper fi_FI-harri-medium 生成全部芬兰语录音
  demo/                   演示视频：record_demo.py 录屏，make_video.py 剪辑
    data/                 每段录屏的点按时间线、App 朗读日志（speak.log）、章节时间
    narration/            中文旁白的语音缓存（Edge TTS）
build.ps1                 一键测试 + 打包
```

## 构建

```powershell
.\build.ps1
```

先跑单元测试，再打签名的 release 包，复制到 `release\FinnishVerbIsland.apk`。
（`-SkipTests` 跳过测试。没有 `local.properties` 时脚本会自动指向 `%LOCALAPPDATA%\Android\Sdk`。）

签名用和其它家庭 App 相同的 keystore（所以新版能覆盖安装）。密码不在 git 里，写在本机的 `local.properties`（已被 git 忽略）：

```properties
family.storeFile=../../../android/hanzimaze.keystore
family.storePassword=...
family.keyAlias=...
family.keyPassword=...
```

没有这几行时 release 包会用 debug 签名（能装，但不能覆盖安装用家庭签名的旧版）。
发新版前把 `app/build.gradle.kts` 的 `versionCode` +1。

改了 `VerbData.kt` 里的词以后，先重新生成录音：

```powershell
..\..\.devenv\Scripts\python.exe tools\make_voice.py --voice ..\..\.voices\piper
```

改了 `design/images` 里的图片以后：

```powershell
..\..\.devenv\Scripts\python.exe tools\prepare_images.py
```

## 演示视频

视频文件不进 git，放在仓库外面的 `..\演示视频\`：

- `芬兰语动词闯关岛_演示.mp4` —— 成片（4:03，1080p，中文旁白 + 芬兰语朗读 + 背景音乐）
- `_raw\*.mp4` —— 模拟器原始录屏；`video_only.mp4`、`audio.wav` 是剪辑中间文件

重新剪辑（改字幕、改旁白后）：

```powershell
..\..\.devenv\Scripts\python.exe tools\demo\make_video.py              # 完整重做
..\..\.devenv\Scripts\python.exe tools\demo\make_video.py --plan-only  # 只看旁白排得下不下
..\..\.devenv\Scripts\python.exe tools\demo\make_video.py --mux-only   # 只重新混音
```

字幕和旁白都写在 `make_video.py` 顶部的 `SEGMENTS` 和 `NARRATION` 里。旁白和 App 的芬兰语朗读不会重叠：
一句旁白没讲完，画面会停在那一帧等它。成片做完后，`docs/youtube/YouTube说明.txt` 里的章节时间会自动更新。

重新录屏需要模拟器 `emulator-5556`（Test_Phone_2），见 `tools/demo/record_demo.py` 开头的说明。

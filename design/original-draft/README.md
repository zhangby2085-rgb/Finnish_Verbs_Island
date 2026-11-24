# 芬兰语动词闯关岛 (Android 原生 Kotlin + Jetpack Compose)

专为芬兰小学 4 年级 S2 中文母语学生设计的芬兰语现在时动词变位练习 App。

## 包含内容
1. **完整 Kotlin + Jetpack Compose 源码**（`app/src/main/java/com/finnishkids/verbisland/`）：
   - `MainActivity.kt`：5 大渐进式关卡、温和中文提示、左右晃动纠错动画、震动反馈。
   - `VerbData.kt`：完整内置 **65 个芬兰语 4 年级 S2 核心动词**、6 个人称变位及 KPT 变软规则。
   - `FinnishTtsManager.kt`：原生 `Locale("fi", "FI")` 芬兰语语音朗读封装。
2. **全套儿童卡通美术素材**（`app/src/main/res/drawable/`）：
   - `mascot_reindeer.jpg`：芬兰蓝驯鹿吉祥物
   - `bg_island_map.jpg`：芬兰群岛 5 关卡冒险地图
   - `icon_kpt_hammer.jpg`：KPT 辅音敲软魔法小锤子
   - `icon_strong_helmet.jpg`：强壮词干头盔图标（Hän / He）
   - `icon_soft_cloud.jpg`：变软词干云朵图标（Minä / Sinä / Me / Te）
   - `badge_three_stars.jpg`：三星过关庆祝徽章

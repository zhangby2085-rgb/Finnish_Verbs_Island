# 设计素材

## images/

最早拿到的 16 张图片，文件名保持原样。**注意：08–16 的文件名和画的内容对不上**，
`tools/prepare_images.py` 是按画的内容对应的：

| 文件 | 实际画的是 | App 里的资源名 |
|---|---|---|
| 01_mascot_reindeer.png | 小驯鹿吉祥物 | mascot_reindeer |
| 02_bg_island_map.png | 5 座小岛的地图 | bg_island_map |
| 03_icon_kpt_hammer.png | KPT 小锤子 | icon_kpt_hammer |
| 04_badge_three_stars.png | 三星徽章 | badge_three_stars |
| 05_icon_strong_helmet.png | 大力士头盔 | icon_strong_helmet |
| 06_icon_soft_cloud.png | 软云朵 | icon_soft_cloud |
| 07_stage1_speech_bubble.png | 木牌对话框（第 1 关） | stage1_speech_bubble |
| 08_reward_treasure_chest.png | **故事书和铅笔**（第 2 关） | stage2_storybook_pencil |
| 09_icon_streak_flame.png | **书包、苹果、牛奶**（第 4 关） | stage4_school_lunch_bag |
| 10_icon_tts_megaphone.png | **魔法盾牌**（第 5 关） | stage5_magic_shield |
| 11_app_launcher_icon.png | 启动图标 | ic_launcher（自适应图标） |
| 12_stage2_storybook_pencil.png | **戴毛线帽的男孩** | avatar_boy_beanie |
| 13_stage5_magic_shield.png | **扎辫子的女孩** | avatar_girl_pigtails |
| 14_avatar_girl_pigtails.png | **小喇叭** | icon_tts_megaphone |
| 15_avatar_boy_beanie.png | **宝箱** | reward_treasure_chest |
| 16_stage4_school_lunch_bag.png | **小火苗** | icon_streak_flame |

## original-draft/

最早的草稿（Kotlin + Compose 代码和说明），没有 Gradle wrapper 和 settings 文件，不能直接编译，只留作参考。
草稿里 `res/drawable` 的 16 张图和 `images/` 完全一样（逐字节相同），所以没有重复保存。
现在真正的工程在仓库根目录的 `app/`。

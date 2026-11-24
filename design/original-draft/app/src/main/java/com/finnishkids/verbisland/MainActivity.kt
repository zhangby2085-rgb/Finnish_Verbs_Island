package com.finnishkids.verbisland

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class Question(
    val verb: VerbItem,
    val pronoun: Pronoun,
    val promptStem: String,
    val correctAnswer: String,
    val options: List<String>,
    val isNegativeMode: Boolean = false
)

class MainActivity : ComponentActivity() {
    private lateinit var ttsManager: FinnishTtsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ttsManager = FinnishTtsManager(this)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF2B7BBB),
                    secondary = Color(0xFFFFB300),
                    background = Color(0xFFF3F8FC)
                )
            ) {
                VerbIslandApp(ttsManager)
            }
        }
    }

    override fun onDestroy() {
        ttsManager.shutdown()
        super.onDestroy()
    }
}

@Composable
fun VerbIslandApp(tts: FinnishTtsManager) {
    var currentStage by remember { mutableStateOf<Int?>(null) }
    var totalStars by remember { mutableIntStateOf(0) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF3F8FC)) {
        if (currentStage == null) {
            HomeScreen(
                tts = tts,
                totalStars = totalStars,
                onSelectStage = { stage -> currentStage = stage }
            )
        } else {
            GameScreen(
                stage = currentStage!!,
                tts = tts,
                onBack = { currentStage = null },
                onEarnStars = { earned -> totalStars += earned }
            )
        }
    }
}

@Composable
fun HomeScreen(tts: FinnishTtsManager, totalStars: Int, onSelectStage: (Int) -> Unit) {
    val stages = listOf(
        Triple(1, "第 1 关：词尾配对小达人", "只练 6 个人称词尾，建立肌肉记忆"),
        Triple(2, "第 2 关：规则动词大挑战", "17 个词干不变的常用动词"),
        Triple(3, "第 3 关：KPT 辅音变软魔法", "掌握 Hän/He 强壮、其余变软口诀"),
        Triple(4, "第 4 关：吃喝与校园高频词", "20 个生活动作与高频词 olla"),
        Triple(5, "第 5 关：意愿与否定句实战", "11 个需求动词 + ei 抢走词尾彩蛋")
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.height(150.dp).fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.bg_island_map),
                            contentDescription = "闯关岛地图",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.mascot_reindeer),
                            contentDescription = "芬兰小驯鹿",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .clickable { tts.speak("Tervetuloa! Oppitaan suomen verbejä!") }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("芬兰语动词闯关岛", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A4B78))
                            Text("点击小驯鹿听芬兰语问好！已收集 ⭐ $totalStars", fontSize = 13.sp, color = Color(0xFF5A6B7C))
                        }
                    }
                    if (!tts.isFinnishAvailable) {
                        Text(
                            "⚠️ 提示：当前手机未检测到芬兰语语音包，可在系统设置 -> 文字转语音中下载 Suomi",
                            color = Color(0xFFD9534F),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        items(stages.size) { idx ->
            val (stageNum, title, desc) = stages[idx]
            val badgeColors = listOf(
                Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFFF9800), Color(0xFF9C27B0), Color(0xFFE91E63)
            )
            Card(
                onClick = { onSelectStage(stageNum) },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(2.dp, badgeColors[idx].copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(badgeColors[idx]),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("$stageNum", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF223344))
                        Text(desc, fontSize = 13.sp, color = Color(0xFF667788))
                    }
                    Text("开始 ▶", color = badgeColors[idx], fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

fun generateQuestionsForStage(stage: Int): List<Question> {
    val pronouns = Pronoun.entries
    if (stage == 1) {
        val baseVerb = VerbRepository.allVerbs.first { it.infinitive == "puhua" }
        val endings = listOf("-n", "-t", "-u", "-mme", "-tte", "-vat")
        return pronouns.shuffled().take(5).map { p ->
            val correctEnding = endings[p.index]
            val distractors = endings.filter { it != correctEnding }.shuffled().take(3)
            Question(
                verb = baseVerb,
                pronoun = p,
                promptStem = "puhu + ___",
                correctAnswer = correctEnding,
                options = (distractors + correctEnding).shuffled()
            )
        }
    }

    val pool = VerbRepository.allVerbs.filter { it.stage == stage }
    return (1..5).map { idx ->
        val verb = pool.random()
        val p = pronouns.random()
        val isNeg = (stage == 5 && idx >= 4) // 第5关后两题为否定句彩蛋
        if (isNeg) {
            val correct = "${p.negWord} ${verb.negativeStem}"
            val wrong1 = "${p.negWord} ${verb.forms[p.index]}" // 常见错误：否定句还保留词尾
            val wrong2 = "ei ${verb.negativeStem}"
            val wrong3 = "${p.negWord} ${verb.infinitive}"
            val opts = listOf(correct, wrong1, wrong2, wrong3).distinct().toMutableList()
            while (opts.size < 4) {
                opts.add("${pronouns.random().negWord} ${verb.negativeStem}")
            }
            Question(
                verb = verb,
                pronoun = p,
                promptStem = "${p.fi} ______ (${verb.chinese} - 否定)",
                correctAnswer = correct,
                options = opts.take(4).shuffled(),
                isNegativeMode = true
            )
        } else {
            val correct = verb.forms[p.index]
            val distractors = verb.forms.filter { it != correct }.distinct().shuffled().take(3).toMutableList()
            while (distractors.size < 3) {
                distractors.add(verb.infinitive)
            }
            Question(
                verb = verb,
                pronoun = p,
                promptStem = "${p.fi} ______",
                correctAnswer = correct,
                options = (distractors + correct).shuffled()
            )
        }
    }
}

@Composable
fun GameScreen(stage: Int, tts: FinnishTtsManager, onBack: () -> Unit, onEarnStars: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val questions = remember(stage) { generateQuestionsForStage(stage) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var hintMessage by remember { mutableStateOf<String?>(null) }
    var isSolved by remember { mutableStateOf(false) }
    var isCompleted by remember { mutableStateOf(false) }

    val shakeOffset = remember { Animatable(0f) }

    if (isCompleted) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.badge_three_stars),
                contentDescription = "三星庆祝徽章",
                modifier = Modifier.size(160.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("太棒啦！本关挑战成功！", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A4B78))
            Text("你又掌握了 5 个芬兰语动词变位，获得 ⭐ 5 颗星！", fontSize = 15.sp, color = Color(0xFF556677), modifier = Modifier.padding(top = 8.dp))
            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = {
                    onEarnStars(5)
                    onBack()
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text("收集星星并返回岛屿地图", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    val q = questions[currentIndex]
    val pronounColors = mapOf(
        Pronoun.MINA to Color(0xFF2196F3),
        Pronoun.SINA to Color(0xFF00BCD4),
        Pronoun.HAN to Color(0xFFFF7043),
        Pronoun.ME to Color(0xFF4CAF50),
        Pronoun.TE to Color(0xFF9C27B0),
        Pronoun.HE to Color(0xFFE91E63)
    )
    val themeColor = pronounColors[q.pronoun] ?: Color(0xFF2B7BBB)

    LaunchedEffect(currentIndex) {
        hintMessage = null
        isSolved = false
        tts.speak(q.pronoun.fi)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 顶部导航与进度
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("← 返回", fontSize = 15.sp) }
            LinearProgressIndicator(
                progress = { (currentIndex + 1) / 5f },
                modifier = Modifier.weight(1f).height(10.dp).clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text("🔥 $streak  (${currentIndex + 1}/5)", fontWeight = FontWeight.Bold, color = Color(0xFFFF6D00))
        }

        // 第3关专属 KPT 黄金口诀横幅
        if (stage == 3) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                border = BorderStroke(1.dp, Color(0xFFFFCA28)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(
                            id = if (q.pronoun.isStrongStem) R.drawable.icon_strong_helmet else R.drawable.icon_soft_cloud
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "黄金口诀：只有 Hän(他) 和 He(他们) 很强壮，词干保持原样！其他四个人称词干都要变软。",
                        fontSize = 12.sp,
                        color = Color(0xFF6D4C00),
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // 题目主卡片
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
        ) {
            Column(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 原形动词点击朗读
                AssistChip(
                    onClick = { tts.speak(q.verb.infinitive) },
                    label = {
                        Text(
                            "🔊 原形：${q.verb.infinitive} (${q.verb.chinese})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 人称主语色块
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(themeColor.copy(alpha = 0.14f))
                        .clickable { tts.speak(q.pronoun.fi) }
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Text(
                        "${q.pronoun.fi} (${q.pronoun.cn})",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = themeColor
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 词干/结果展示区
                AnimatedContent(targetState = isSolved, label = "verb_result") { solved ->
                    val displayText = if (solved) {
                        if (stage == 1) "${q.pronoun.fi} puhu${q.correctAnswer.removePrefix("-")}"
                        else if (q.isNegativeMode) "${q.pronoun.fi} ${q.correctAnswer}"
                        else "${q.pronoun.fi} ${q.correctAnswer}"
                    } else {
                        q.promptStem
                    }
                    Text(
                        text = displayText,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (solved) Color(0xFF2E7D32) else Color(0xFF223344),
                        textAlign = TextAlign.Center
                    )
                }

                // KPT 音变小锤子提示
                if (q.verb.kptRule != null && (stage == 3 || isSolved)) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.icon_kpt_hammer),
                            contentDescription = "音变小锤子",
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(q.verb.kptRule, fontSize = 13.sp, color = Color(0xFFE65100), fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // 温和的中文提示横幅
        if (hintMessage != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSolved) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = hintMessage!!,
                    modifier = Modifier.padding(14.dp),
                    fontSize = 14.sp,
                    color = if (isSolved) Color(0xFF1B5E20) else Color(0xFFE65100),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 4 个选项大按钮
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            q.options.chunked(2).forEach { rowOpts ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    rowOpts.forEach { option ->
                        Button(
                            onClick = {
                                if (isSolved) return@Button
                                val spokenPhrase = if (stage == 1) {
                                    "${q.pronoun.fi} puhu${option.removePrefix("-")}"
                                } else {
                                    "${q.pronoun.fi} $option"
                                }
                                tts.speak(spokenPhrase)

                                if (option == q.correctAnswer) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    isSolved = true
                                    streak++
                                    hintMessage = "🎉 太棒了！完全正确：$spokenPhrase"
                                    scope.launch {
                                        delay(1300)
                                        if (currentIndex < 4) {
                                            currentIndex++
                                        } else {
                                            isCompleted = true
                                        }
                                    }
                                } else {
                                    streak = 0
                                    hintMessage = buildFriendlyChineseHint(stage, q, option)
                                    scope.launch {
                                        for (offset in listOf(-14f, 14f, -10f, 10f, 0f)) {
                                            shakeOffset.animateTo(offset, tween(55))
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSolved && option == q.correctAnswer) Color(0xFF43A047) else themeColor
                            ),
                            modifier = Modifier.weight(1f).height(58.dp)
                        ) {
                            Text(option, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

fun buildFriendlyChineseHint(stage: Int, q: Question, chosen: String): String {
    if (q.isNegativeMode) {
        return "💡 小提示：否定词 (${q.pronoun.negWord}) 已经抢走了人称词尾，后面的动词要变成光杆词干 (${q.verb.negativeStem}) 哦！"
    }
    if (stage == 1) {
        val endingTips = mapOf(
            Pronoun.MINA to "-n",
            Pronoun.SINA to "-t",
            Pronoun.HAN to "双元音 (-u)",
            Pronoun.ME to "-mme",
            Pronoun.TE to "-tte",
            Pronoun.HE to "-vat"
        )
        return "💡 再试一次：主语是 ${q.pronoun.fi} (${q.pronoun.cn})，对应的词尾是 ${endingTips[q.pronoun]} 哦！"
    }
    if (stage == 3 && q.verb.kptRule != null) {
        return if (q.pronoun.isStrongStem) {
            "💡 记住口诀：${q.pronoun.fi} (${q.pronoun.cn}) 很强壮，词干保持强级不变哦！"
        } else {
            "💡 记住口诀：${q.pronoun.fi} (${q.pronoun.cn}) 词干要变软（${q.verb.kptRule}）哦！"
        }
    }
    return "💡 看清楚主语是 ${q.pronoun.fi} (${q.pronoun.cn}) 哦，听听读音再选一次！"
}

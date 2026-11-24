@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.finnishkids.verbisland

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// ═══════════════════════════════ Activity ═══════════════════════════════

class MainActivity : ComponentActivity() {
    private lateinit var tts: FinnishTtsManager
    private lateinit var store: ProgressStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = FinnishTtsManager(this)
        store = ProgressStore(this)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = C.Sea, onPrimary = Color.White, secondary = C.Sun,
                    background = C.Sky, surface = Color.White, onSurface = C.Ink,
                    surfaceTint = Color.White,   // 白色卡片不要被 Material 3 染成灰蓝色
                )
            ) { VerbIslandApp(tts, store) }
        }
    }

    // 从系统“文字转语音”设置回来时，重新检查是否装好了芬兰语
    override fun onResume() {
        super.onResume()
        tts.recheck()
    }

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }
}

// ═══════════════════════════════ 颜色与素材 ═══════════════════════════════

object C {
    val Sea = Color(0xFF1E78B4)
    val SeaDeep = Color(0xFF15507C)
    val Sky = Color(0xFFE6F4FC)
    val Sand = Color(0xFFFFF8EC)
    val Sun = Color(0xFFFFB627)
    val SunSoft = Color(0xFFFFF3D2)
    val Ink = Color(0xFF223548)
    val InkSoft = Color(0xFF5E6E80)
    val Leaf = Color(0xFF2E9E4F)
    val LeafSoft = Color(0xFFE2F5E5)
    val Apricot = Color(0xFFFFF0DA)
    val ApricotInk = Color(0xFF8E5410)
    val Line = Color(0xFFDCE7F0)
    val Muted = Color(0xFFEEF1F4)
    val MutedInk = Color(0xFFA7B2BE)
    val Magic = Color(0xFFE2570C)
}

val Person.main: Color get() = Color(color)
val Person.bg: Color get() = Color(soft)

private val stageColors = listOf(
    Color(0xFF2F9BD6), Color(0xFF7C5CD6), Color(0xFFF08A24), Color(0xFF3FA34D), Color(0xFFD6558F)
)
private val stageIcons = listOf(
    R.drawable.stage1_speech_bubble, R.drawable.stage2_storybook_pencil, R.drawable.icon_kpt_hammer,
    R.drawable.stage4_school_lunch_bag, R.drawable.stage5_magic_shield,
)

/** 地图上 5 座小岛的位置（占图片宽高的比例）。中间最大的岛是第 3 关 KPT。 */
private val mapPins = listOf(0.23f to 0.60f, 0.28f to 0.29f, 0.52f to 0.47f, 0.72f to 0.29f, 0.79f to 0.62f)

private fun stageColor(n: Int) = stageColors[n - 1]

// ═══════════════════════════════ 进度保存 ═══════════════════════════════

class ProgressStore(context: Context) {
    private val sp = context.getSharedPreferences("verbisland", Context.MODE_PRIVATE)

    var totalStars by mutableIntStateOf(sp.getInt("stars", 0)); private set
    val best = mutableStateListOf<Int>().apply { for (i in 1..5) add(sp.getInt("best$i", 0)) }   // 每关最好成绩 0..3
    var avatar by mutableIntStateOf(sp.getInt("avatar", -1)); private set                          // 0 男孩 1 女孩
    var unlockAll by mutableStateOf(sp.getBoolean("unlockAll", false)); private set
    var bestStreak by mutableIntStateOf(sp.getInt("bestStreak", 0)); private set

    fun unlocked(stage: Int) = unlockAll || stage == 1 || best[stage - 2] > 0
    val cleared: Int get() = best.count { it > 0 }

    /** 推荐下一关：第一个还没通过的已解锁关卡，都通过了就推荐星星最少的 */
    fun recommended(): Int =
        (1..5).firstOrNull { best[it - 1] == 0 && unlocked(it) } ?: (1..5).minByOrNull { best[it - 1] } ?: 1

    /** 抽题权重：没见过的动词、最近答错过的动词更容易出现 */
    fun weight(v: Verb): Double {
        val seen = sp.getInt("seen_${v.inf}", 0)
        val need = sp.getInt("need_${v.inf}", 0)
        return 1.0 + need + if (seen == 0) 3.0 else 0.0
    }

    fun record(v: Verb, clean: Boolean) {
        val seen = sp.getInt("seen_${v.inf}", 0)
        val need = sp.getInt("need_${v.inf}", 0)
        sp.edit().putInt("seen_${v.inf}", seen + 1)
            .putInt("need_${v.inf}", if (clean) (need - 1).coerceAtLeast(0) else (need + 2).coerceAtMost(6)).apply()
    }

    fun addStar() {
        totalStars++
        sp.edit().putInt("stars", totalStars).apply()
    }

    fun noteStreak(s: Int) {
        if (s > bestStreak) {
            bestStreak = s
            sp.edit().putInt("bestStreak", s).apply()
        }
    }

    /** 记下这一轮的成绩；返回 true 表示这一轮刚好解锁了下一关 */
    fun finishRound(stage: Int, rating: Int): Boolean {
        val wasLocked = stage < 5 && !unlocked(stage + 1)
        if (rating > best[stage - 1]) {
            best[stage - 1] = rating
            sp.edit().putInt("best$stage", rating).apply()
        }
        sp.edit().putInt("rounds", sp.getInt("rounds", 0) + 1).apply()
        return wasLocked && unlocked(stage + 1)
    }

    fun chooseAvatar(i: Int) {
        avatar = i
        sp.edit().putInt("avatar", i).apply()
    }

    fun setUnlockAllStages(on: Boolean) {
        unlockAll = on
        sp.edit().putBoolean("unlockAll", on).apply()
    }

    fun reset() {
        val keepAvatar = avatar
        val keepVoice = sp.getBoolean("preferClips", false)
        sp.edit().clear().putInt("avatar", keepAvatar).putBoolean("preferClips", keepVoice).apply()
        totalStars = 0
        bestStreak = 0
        unlockAll = false
        for (i in 0 until 5) best[i] = 0
    }
}

// ═══════════════════════════════ 导航 ═══════════════════════════════

data class RoundResult(
    val firstTry: Int,
    val mistakes: Int,
    val seconds: Int,
    val rating: Int,
    val verbs: List<String>,
    val unlockedNext: Boolean,
)

sealed interface Screen {
    data object Home : Screen
    data class Play(val stage: Int, val round: Int) : Screen
    data class Done(val stage: Int, val result: RoundResult, val round: Int) : Screen
    data class Book(val stage: Int) : Screen
}

@Composable
fun VerbIslandApp(tts: FinnishTtsManager, store: ProgressStore) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var rounds by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var showVoice by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAvatar by remember { mutableStateOf(store.avatar < 0) }
    var cardVerb by remember { mutableStateOf<Verb?>(null) }

    fun play(stage: Int) {
        if (!store.unlocked(stage)) {
            Toast.makeText(context, "先完成第 ${stage - 1} 关，就能解锁这一关哦！", Toast.LENGTH_SHORT).show()
            return
        }
        rounds++
        screen = Screen.Play(stage, rounds)
    }

    BackHandler(enabled = screen != Screen.Home) { tts.stop(); screen = Screen.Home }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(C.Sky, C.Sand)))
    ) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(160)) },
            label = "screen",
        ) { s ->
            when (s) {
                Screen.Home -> HomeScreen(
                    store, tts,
                    onPlay = ::play,
                    onBook = { screen = Screen.Book(it) },
                    onVoice = { showVoice = true },
                    onAvatar = { showAvatar = true },
                    onSettings = { showSettings = true },
                )
                is Screen.Play -> GameScreen(
                    stage = s.stage, store = store, tts = tts,
                    streak = streak,
                    onStreak = { streak = it; store.noteStreak(it) },
                    onExit = { tts.stop(); screen = Screen.Home },
                    onVoice = { showVoice = true },
                    onFinish = { r -> screen = Screen.Done(s.stage, r, s.round) },
                )
                is Screen.Done -> ResultScreen(
                    stage = s.stage, result = s.result, tts = tts,
                    onAgain = { play(s.stage) },
                    onNext = { play(s.stage + 1) },
                    onHome = { screen = Screen.Home },
                    onCard = { cardVerb = it },
                )
                is Screen.Book -> WordBookScreen(
                    startStage = s.stage, tts = tts,
                    onBack = { screen = Screen.Home },
                    onCard = { cardVerb = it },
                    onVoice = { showVoice = true },
                )
            }
        }
    }

    if (showVoice) VoiceDialog(tts) { showVoice = false }
    if (showSettings) SettingsDialog(
        store, tts,
        onVoice = { showSettings = false; showVoice = true },
        onDismiss = { showSettings = false },
    )
    if (showAvatar) AvatarDialog(store.avatar) { store.chooseAvatar(it); showAvatar = false }
    cardVerb?.let { v -> VerbCardDialog(v, tts) { cardVerb = null } }
}

// ═══════════════════════════════ 首页：岛屿地图 ═══════════════════════════════

@Composable
fun HomeScreen(
    store: ProgressStore,
    tts: FinnishTtsManager,
    onPlay: (Int) -> Unit,
    onBook: (Int) -> Unit,
    onVoice: () -> Unit,
    onAvatar: () -> Unit,
    onSettings: () -> Unit,
) {
    val rec = store.recommended()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarBubble(store.avatar, 52.dp, Modifier.clickable(onClick = onAvatar))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("芬兰语动词闯关岛", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = C.SeaDeep)
                    Text("Verbisaari · 现在时动词变位", fontSize = 12.sp, color = C.InkSoft)
                }
                VoiceChip(tts, compact = false, onClick = onVoice)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill(Modifier.weight(1f)) { Text("⭐ ${store.totalStars}", fontWeight = FontWeight.Bold, color = C.Ink); Text(" 颗星", fontSize = 12.sp, color = C.InkSoft) }
                StatPill(Modifier.weight(1f)) {
                    Image(painterResource(R.drawable.icon_streak_flame), null, Modifier.size(18.dp))
                    Text(" 最佳连对 ${store.bestStreak}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                }
                StatPill(Modifier.weight(1f)) { Text("🏝 通关 ${store.cleared}/5", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Ink) }
            }
        }
        item { IslandMap(store, rec, onPlay) }
        item { MascotBubble(tts, rec) }
        if (tts.status == FinnishTtsManager.Status.MISSING_VOICE || tts.status == FinnishTtsManager.Status.NO_ENGINE) {
            item { VoiceNotice(tts, onVoice) }
        }
        items(5) { i ->
            val n = i + 1
            StageCard(n, store.best[i], store.unlocked(n), n == rec, onPlay = { onPlay(n) }, onBook = { onBook(n) })
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onBook(2) }, Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)) {
                    Text("📖 动词词卡本（65 个）", fontSize = 14.sp)
                }
                OutlinedButton(onClick = onSettings, Modifier.height(48.dp), shape = RoundedCornerShape(14.dp)) {
                    Text("⚙ 家长设置", fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun StatPill(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.85f))
            .border(1.dp, C.Line, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content,
    )
}

@Composable
fun AvatarBubble(avatar: Int, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White)
            .border(2.dp, C.Sun, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(if (avatar == 1) R.drawable.avatar_girl_pigtails else R.drawable.avatar_boy_beanie),
            contentDescription = "我的头像",
            modifier = Modifier.fillMaxSize().padding(2.dp),
        )
    }
}

@Composable
fun IslandMap(store: ProgressStore, recommended: Int, onPlay: (Int) -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pin")
    val pulseScale by pulse.animateFloat(
        1f, 1.18f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pinScale"
    )
    Card(
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(3.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1280f / 714f)
        ) {
            Image(
                painterResource(R.drawable.bg_island_map), "闯关岛地图",
                Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds,
            )
            mapPins.forEachIndexed { i, (fx, fy) ->
                val n = i + 1
                val open = store.unlocked(n)
                val pin = 38.dp
                Column(
                    Modifier
                        .offset(x = maxWidth * fx - pin / 2, y = maxHeight * fy - pin / 2)
                        .clickable { onPlay(n) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .scale(if (n == recommended && open) pulseScale else 1f)
                            .size(pin)
                            .shadow(4.dp, CircleShape)
                            .clip(CircleShape)
                            .background(if (open) stageColor(n) else Color(0xFF9AA6B2))
                            .border(3.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (open) Text("$n", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        else Icon(Icons.Filled.Lock, "未解锁", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    if (store.best[i] > 0) {
                        Text(
                            "★".repeat(store.best[i]),
                            Modifier
                                .padding(top = 2.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.9f))
                                .padding(horizontal = 5.dp),
                            color = C.Sun, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MascotBubble(tts: FinnishTtsManager, recommended: Int) {
    val wiggle = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painterResource(R.drawable.mascot_reindeer), "小驯鹿 Poro",
            Modifier
                .size(78.dp)
                .rotate(wiggle.value)
                .clickable {
                    tts.speak(Phrases.WELCOME)
                    scope.launch {
                        for (a in listOf(-8f, 8f, -5f, 4f, 0f)) wiggle.animateTo(a, tween(90))
                    }
                },
        )
        Spacer(Modifier.width(8.dp))
        Card(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, C.Line),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("Hei! 我是小驯鹿 Poro，点我听芬兰语问好！", fontSize = 13.sp, color = C.Ink)
                Spacer(Modifier.height(3.dp))
                Text(
                    "今天推荐：第 $recommended 关「${Stages.info(recommended).title}」",
                    fontSize = 13.sp, color = stageColor(recommended), fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun VoiceNotice(tts: FinnishTtsManager, onVoice: () -> Unit) {
    Card(
        onClick = onVoice,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = C.Apricot),
        border = BorderStroke(1.dp, C.Sun.copy(alpha = 0.5f)),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.icon_tts_megaphone), null, Modifier.size(36.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                if (tts.hasClips) "手机里没有芬兰语系统语音，已经自动改用 App 内置的芬兰语录音，放心学！想开启系统语音，点这里看步骤。"
                else "手机里还没有芬兰语语音，点这里看看怎么开启。",
                fontSize = 13.sp, color = C.ApricotInk, lineHeight = 18.sp,
            )
        }
    }
}

@Composable
fun StageCard(n: Int, best: Int, open: Boolean, recommended: Boolean, onPlay: () -> Unit, onBook: () -> Unit) {
    val info = Stages.info(n)
    val col = stageColor(n)
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(if (recommended) 2.5.dp else 1.5.dp, col.copy(alpha = if (recommended) 0.9f else 0.3f)),
        elevation = CardDefaults.cardElevation(if (recommended) 4.dp else 1.dp),
        modifier = Modifier.graphicsLayer { alpha = if (open) 1f else 0.72f },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(66.dp)
                    .clip(CircleShape)
                    .background(col.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(stageIcons[n - 1]), null, Modifier.size(50.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("第 $n 关" + if (recommended) "  · 推荐" else "", fontSize = 12.sp, color = col, fontWeight = FontWeight.Bold)
                Text(info.title, fontSize = 17.sp, color = C.Ink, fontWeight = FontWeight.Bold)
                Text(
                    if (open) info.subtitle else "先完成第 ${n - 1} 关就能解锁",
                    fontSize = 13.sp, color = C.InkSoft,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = C.Sun)) { append("★".repeat(best)) }
                        withStyle(SpanStyle(color = C.Line)) { append("★".repeat(3 - best)) }
                    },
                    fontSize = 16.sp,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = onPlay,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (open) col else Color(0xFFB8C2CC)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    if (open) Text("开始 ▶", fontWeight = FontWeight.Bold)
                    else Icon(Icons.Filled.Lock, "未解锁", Modifier.size(18.dp))
                }
                TextButton(onClick = onBook, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("📖 词卡", fontSize = 13.sp, color = col)
                }
            }
        }
    }
}

// ═══════════════════════════════ 右上角语音提示 ═══════════════════════════════

@Composable
fun VoiceChip(tts: FinnishTtsManager, compact: Boolean, onClick: () -> Unit) {
    val s = tts.status
    val missing = s == FinnishTtsManager.Status.MISSING_VOICE || s == FinnishTtsManager.Status.NO_ENGINE
    val (label, bg, fg) = when {
        s == FinnishTtsManager.Status.CHECKING -> Triple("检查语音…", C.Muted, C.InkSoft)
        missing && compact -> Triple("语音?", C.Apricot, C.ApricotInk)
        missing -> Triple("开启芬兰语语音", C.Apricot, C.ApricotInk)
        tts.preferClips -> Triple("内置语音", C.LeafSoft, C.Leaf)
        compact -> Triple("✓", C.LeafSoft, C.Leaf)
        else -> Triple("芬兰语 ✓", C.LeafSoft, C.Leaf)
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.25f)),
    ) {
        Row(
            Modifier.padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(painterResource(R.drawable.icon_tts_megaphone), "语音设置", Modifier.size(24.dp))
            Spacer(Modifier.width(3.dp))
            Text(label, fontSize = 12.sp, color = fg, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun VoiceDialog(tts: FinnishTtsManager, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val missing = tts.status == FinnishTtsManager.Status.MISSING_VOICE || tts.status == FinnishTtsManager.Status.NO_ENGINE
    fun fail() = Toast.makeText(context, "这台手机打不开这个页面，请在系统设置里搜索“文字转语音”", Toast.LENGTH_LONG).show()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
        icon = { Image(painterResource(R.drawable.icon_tts_megaphone), null, Modifier.size(56.dp)) },
        title = { Text("芬兰语语音", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    when (tts.status) {
                        FinnishTtsManager.Status.READY -> "✅ 系统芬兰语语音已就绪（${tts.engineName ?: "系统引擎"}），语速 0.88。"
                        FinnishTtsManager.Status.CHECKING -> "正在检查手机上的语音引擎……"
                        FinnishTtsManager.Status.NO_ENGINE -> "这台手机没有找到任何“文字转语音”引擎。"
                        FinnishTtsManager.Status.MISSING_VOICE -> "这台手机还没有安装芬兰语（Suomi）系统语音包。"
                    },
                    fontSize = 14.sp, color = C.Ink,
                )
                if (tts.hasClips) {
                    Text(
                        (if (missing) "别担心：" else "另外，") +
                            "App 已经内置了 ${tts.clipCount} 条芬兰语录音（离线），每个单词、每个变位都能点开听。",
                        fontSize = 14.sp, color = C.Leaf,
                    )
                }
                if (missing) {
                    Card(colors = CardDefaults.cardColors(containerColor = C.SunSoft)) {
                        Text(
                            "想开启系统芬兰语语音：\n" +
                                "① 点下面的「打开语音设置」\n" +
                                "② 首选引擎选「Google 语音服务」（Speech Services by Google）\n" +
                                "③ 点引擎旁边的 ⚙ → 安装语音数据 → 下载 Suomi（芬兰语）\n" +
                                "④ 回到 App，会自动重新检测",
                            Modifier.padding(12.dp), fontSize = 13.sp, color = C.ApricotInk, lineHeight = 19.sp,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { if (!tts.openTtsSettings(context)) fail() }, Modifier.weight(1f)) {
                            Text("打开语音设置", fontSize = 13.sp)
                        }
                        OutlinedButton(onClick = { if (!tts.openInstallVoice(context)) fail() }, Modifier.weight(1f)) {
                            Text("下载芬兰语", fontSize = 13.sp)
                        }
                    }
                }
                if (tts.hasClips && tts.status == FinnishTtsManager.Status.READY) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("优先使用内置录音", Modifier.weight(1f), fontSize = 14.sp, color = C.Ink)
                        Switch(checked = tts.preferClips, onCheckedChange = { tts.usePreferClips(it) })
                    }
                }
                Button(
                    onClick = { tts.speak(Phrases.WELCOME) },
                    enabled = tts.canSpeak,
                    shape = RoundedCornerShape(12.dp),
                ) { Text("🔊 试听：Hei! Tervetuloa verbisaarelle!", fontSize = 13.sp) }
            }
        },
    )
}

// ═══════════════════════════════ 闯关 ═══════════════════════════════

@Composable
fun GameScreen(
    stage: Int,
    store: ProgressStore,
    tts: FinnishTtsManager,
    streak: Int,
    onStreak: (Int) -> Unit,
    onExit: () -> Unit,
    onVoice: () -> Unit,
    onFinish: (RoundResult) -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val info = Stages.info(stage)

    val questions = remember { QuestionMaker.round(stage, store::weight) }
    var index by remember { mutableIntStateOf(0) }
    var solved by remember { mutableStateOf(false) }
    var wrongPicks by remember { mutableStateOf(setOf<String>()) }
    var hint by remember { mutableStateOf<String?>(null) }
    var mistakesNow by remember { mutableIntStateOf(0) }
    var mistakes by remember { mutableIntStateOf(0) }
    var firstTry by remember { mutableIntStateOf(0) }
    var seconds by remember { mutableIntStateOf(0) }
    var hammer by remember { mutableIntStateOf(0) }     // 小锤子动画播放次数（>0 时显示）
    var finished by remember { mutableStateOf(false) }

    val shake = remember { Animatable(0f) }
    val bounce = remember { Animatable(1f) }
    val q = questions[index]

    // 星星飞到右上角的动画
    var origin by remember { mutableStateOf(Offset.Zero) }
    var cardCenter by remember { mutableStateOf(Offset.Zero) }
    var counterCenter by remember { mutableStateOf(Offset.Zero) }
    val fly = remember { Animatable(0f) }
    var flying by remember { mutableStateOf(false) }
    var shownStars by remember { mutableIntStateOf(store.totalStars) }
    val counterPulse = remember { Animatable(1f) }
    var streakToast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            if (!finished) seconds++
        }
    }
    LaunchedEffect(index) {
        solved = false; wrongPicks = emptySet(); hint = null; mistakesNow = 0; hammer = 0
        delay(380)
        when (q.kind) {
            QKind.ENDING -> tts.speak(q.person.fi)
            QKind.FORM, QKind.NEG -> tts.speak(q.verb.inf)
            QKind.WHO -> {}
        }
    }

    fun flyStar() = scope.launch {
        flying = true
        fly.snapTo(0f)
        fly.animateTo(1f, tween(780, easing = FastOutSlowInEasing))
        flying = false
        shownStars = store.totalStars
        counterPulse.animateTo(1.45f, tween(120))
        counterPulse.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f))
    }

    fun pick(c: Choice) {
        if (solved || c.label in wrongPicks) return
        if (c.correct) {
            solved = true
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            tick(context)
            tts.speak(q.solved)
            val clean = mistakesNow == 0
            store.record(q.verb, clean)
            if (clean) {
                firstTry++
                store.addStar()
                flyStar()
                val s = streak + 1
                onStreak(s)
                if (s == 3 || s == 5 || s % 10 == 0) streakToast = "🔥 连续答对 $s 题！"
            } else onStreak(0)
            hint = null
            if (q.verb.kpt != null && q.kind != QKind.WHO && q.kind != QKind.ENDING) hammer++
            scope.launch {
                bounce.animateTo(1.07f, tween(110))
                bounce.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 600f))
            }
        } else {
            wrongPicks = wrongPicks + c.label
            mistakesNow++
            mistakes++
            onStreak(0)
            hint = Hints.wrong(q, c)
            if (q.verb.kpt != null && q.kind == QKind.FORM) hammer++
            scope.launch {
                val px = with(density) { 12.dp.toPx() }
                for (f in listOf(-1f, 1f, -0.75f, 0.6f, -0.35f, 0f)) shake.animateTo(px * f, tween(60))
            }
        }
    }

    fun next() {
        if (index < questions.lastIndex) {
            index++
        } else if (!finished) {
            finished = true
            val rating = when (firstTry) { 5 -> 3; 3, 4 -> 2; else -> 1 }
            val unlocked = store.finishRound(stage, rating)
            onFinish(
                RoundResult(
                    firstTry, mistakes, seconds, rating,
                    questions.map { it.verb.inf }.distinct(), unlocked,
                )
            )
        }
    }

    LaunchedEffect(streakToast) {
        if (streakToast != null) { delay(1800); streakToast = null }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
    ) {
        Column(Modifier.fillMaxSize()) {
            // ── 顶部：返回 · 关卡名 · 进度 · 星星 · 语音
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 12.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = C.SeaDeep) }
                Column(Modifier.weight(1f)) {
                    Text("第 $stage 关 · ${info.title}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.Ink, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    ProgressDots(questions.size, index, solved, stageColor(stage))
                }
                Box(
                    Modifier
                        .padding(horizontal = 8.dp)
                        .scale(counterPulse.value)
                        .onGloballyPositioned { counterCenter = it.boundsInRoot().center }
                        .clip(RoundedCornerShape(50))
                        .background(C.SunSoft)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("⭐ $shownStars", fontWeight = FontWeight.ExtraBold, color = C.ApricotInk, fontSize = 15.sp)
                }
                VoiceChip(tts, compact = true, onClick = onVoice)
            }
            // ── 第几题 · 连对火焰 · 计时
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("第 ${index + 1} / ${questions.size} 题", fontSize = 13.sp, color = C.InkSoft, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Image(painterResource(R.drawable.icon_streak_flame), "连对", Modifier.size(22.dp))
                Text(" 连对 $streak", fontSize = 13.sp, color = C.Magic, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(14.dp))
                Text("⏱ ${clock(seconds)}", fontSize = 13.sp, color = if (seconds <= 90) C.InkSoft else C.ApricotInk)
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TipBanner(stage, q)
                // ── 题目卡（答错时左右晃动，答对时弹跳）
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(3.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { cardCenter = it.boundsInRoot().center }
                        .offset { IntOffset(shake.value.roundToInt(), 0) }
                        .graphicsLayer { scaleX = bounce.value; scaleY = bounce.value },
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            q.kind.label,
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(stageColor(stage).copy(alpha = 0.12f))
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            fontSize = 13.sp, color = stageColor(stage), fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(14.dp))
                        QuestionPrompt(q, tts, solved)
                        Spacer(Modifier.height(14.dp))
                        AnswerLine(q, solved, tts)
                        if (hammer > 0 && q.verb.kpt != null) {
                            Spacer(Modifier.height(12.dp))
                            StemMagic(q.verb, q.person, hammer)
                        }
                    }
                }
                // ── 中文讲解（答错：温和的杏色；答对：绿色）
                AnimatedVisibility(visible = hint != null || solved, enter = fadeIn() + expandVertically(), exit = fadeOut()) {
                    if (solved) SuccessCard(q) else HintCard(hint.orEmpty())
                }
                Spacer(Modifier.height(4.dp))
            }

            // ── 选项与按钮（固定在底部）
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.55f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val cols = if (q.kind == QKind.ENDING) 3 else 2
                val longest = q.choices.maxOf { it.label.length }
                val labelSize = when {
                    longest > 14 -> 16.sp
                    longest > 11 -> 18.sp
                    else -> 21.sp
                }
                q.choices.chunked(cols).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { c ->
                            ChoiceCard(
                                c, q, wrong = c.label in wrongPicks, solved = solved, size = labelSize,
                                modifier = Modifier.weight(1f),
                                bounce = if (solved && c.correct) bounce.value else 1f,
                            ) { pick(c) }
                        }
                    }
                }
                if (solved) {
                    Button(
                        onClick = ::next,
                        Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = C.Leaf),
                    ) {
                        Text(
                            if (index < questions.lastIndex) "下一题 ▶" else "打开宝箱 🎁",
                            fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                } else if (q.verb.kpt != null && q.kind == QKind.FORM) {
                    OutlinedButton(
                        onClick = { hammer++ },
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, C.Sun),
                    ) {
                        Image(painterResource(R.drawable.icon_kpt_hammer), null, Modifier.size(26.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("小锤子帮帮我：敲一敲词干", color = C.ApricotInk, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 连对小横幅
        AnimatedVisibility(
            visible = streakToast != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 104.dp),
        ) {
            Text(
                streakToast.orEmpty(),
                Modifier
                    .shadow(6.dp, RoundedCornerShape(50))
                    .clip(RoundedCornerShape(50))
                    .background(C.Magic)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp,
            )
        }

        // 飞行中的星星
        if (flying) {
            val t = fly.value
            val from = cardCenter - origin
            val to = counterCenter - origin
            val arc = with(density) { 90.dp.toPx() }
            val x = from.x + (to.x - from.x) * t
            val y = from.y + (to.y - from.y) * t - sin(PI * t).toFloat() * arc
            val half = with(density) { 22.dp.toPx() }
            Text(
                "⭐",
                Modifier
                    .offset { IntOffset((x - half).roundToInt(), (y - half).roundToInt()) }
                    .graphicsLayer {
                        val s = 2.2f - 1.4f * t
                        scaleX = s; scaleY = s; rotationZ = 360f * t
                    },
                fontSize = 30.sp,
            )
        }
    }
}

private fun clock(s: Int) = "%d:%02d".format(s / 60, s % 60)

/** 答对时的轻快短震（Compose 的 TextHandleMove 之外再补一下，有的手机前者太轻） */
private fun tick(context: Context) {
    runCatching {
        val vib: Vibrator? = if (Build.VERSION.SDK_INT >= 31)
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vib?.hasVibrator() != true) return
        if (Build.VERSION.SDK_INT >= 29) vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        else if (Build.VERSION.SDK_INT >= 26) vib.vibrate(VibrationEffect.createOneShot(18, 80))
    }
}

@Composable
fun ProgressDots(total: Int, index: Int, solved: Boolean, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(total) { i ->
            val done = i < index || (i == index && solved)
            val current = i == index
            Box(
                Modifier
                    .height(8.dp)
                    .width(if (current) 26.dp else 16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        when {
                            done -> C.Leaf
                            current -> color
                            else -> C.Line
                        }
                    )
            )
        }
    }
}

@Composable
fun TipBanner(stage: Int, q: Question) {
    val (text, icon) = when {
        stage == 3 -> Stages.GOLDEN_RULE to R.drawable.icon_kpt_hammer
        q.kind == QKind.NEG -> Stages.NEG_RULE to R.drawable.stage5_magic_shield
        else -> Stages.info(stage).tip to stageIcons[stage - 1]
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(C.SunSoft)
            .border(1.dp, C.Sun.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(icon), null, Modifier.size(34.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, color = C.ApricotInk, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun PronounChip(p: Person, tts: FinnishTtsManager, big: Boolean = true) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(p.bg)
            .border(2.dp, p.main.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .clickable { tts.speak(p.fi) }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(p.fi, fontSize = if (big) 24.sp else 18.sp, fontWeight = FontWeight.ExtraBold, color = p.main)
        Text(p.cn, fontSize = 12.sp, color = p.main)
    }
}

@Composable
fun WordChip(fi: String, cn: String, onClick: (() -> Unit)?) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(C.Sky)
            .border(2.dp, C.Sea.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(fi + if (onClick != null) " 🔊" else "", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = C.SeaDeep)
        Text(cn, fontSize = 12.sp, color = C.InkSoft)
    }
}

@Composable
private fun Plus() = Text("+", Modifier.padding(horizontal = 6.dp), fontSize = 22.sp, color = C.MutedInk, fontWeight = FontWeight.Bold)

@Composable
fun QuestionPrompt(q: Question, tts: FinnishTtsManager, solved: Boolean) {
    FlowRow(
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        val center = Modifier.align(Alignment.CenterVertically)
        when (q.kind) {
            QKind.ENDING -> {
                PronounChip(q.person, tts)
                Box(center) { Plus() }
                WordChip("puhu-", "说话（词干）", null)
                Box(center) { Plus() }
                Box(
                    center
                        .defaultMinSize(minWidth = 54.dp, minHeight = 54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(q.person.bg)
                        .border(2.dp, q.person.main, RoundedCornerShape(14.dp))
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (solved) stageOneEnding(q.person) else "?",
                        fontSize = 26.sp, color = q.person.main, fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
            QKind.FORM, QKind.NEG -> {
                PronounChip(q.person, tts)
                Box(center) { Plus() }
                WordChip(q.verb.inf, q.verb.cn) { tts.speak(q.verb.inf) }
            }
            QKind.WHO -> {
                Box(
                    center
                        .size(width = 74.dp, height = 54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(C.Muted)
                        .border(2.dp, C.MutedInk, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("谁？", fontSize = 20.sp, color = C.InkSoft, fontWeight = FontWeight.Bold) }
                Box(center) { Plus() }
                WordChip(q.verb.form(q.person), "${q.verb.inf} · ${q.verb.cn}", null)
            }
        }
    }
}

/** 把变位里的人称词尾标成人称的颜色：kirjoita + n */
fun formText(v: Verb, p: Person, negative: Boolean = false, withPronoun: Boolean = true): AnnotatedString =
    buildAnnotatedString {
        if (withPronoun) {
            withStyle(SpanStyle(color = p.main, fontWeight = FontWeight.ExtraBold)) { append(p.fi) }
            append(" ")
        }
        if (negative) {
            withStyle(SpanStyle(color = p.main, fontWeight = FontWeight.ExtraBold)) { append(p.neg) }
            append(" ")
            withStyle(SpanStyle(color = C.Ink)) { append(v.negStem) }
            return@buildAnnotatedString
        }
        val f = v.form(p)
        val end = when (p) {
            Person.HAN -> if (f.length >= 3 && f[f.length - 1] == f[f.length - 2] && v.inf != "olla") 1 else 0
            Person.HE -> 3
            else -> listOf("mme", "tte", "n", "t").firstOrNull { f.endsWith(it) }?.length ?: 0
        }
        withStyle(SpanStyle(color = C.Ink)) { append(f.dropLast(end)) }
        withStyle(SpanStyle(color = p.main, fontWeight = FontWeight.ExtraBold)) { append(f.takeLast(end)) }
    }

@Composable
fun AnswerLine(q: Question, solved: Boolean, tts: FinnishTtsManager) {
    AnimatedContent(
        targetState = solved,
        transitionSpec = {
            (scaleIn(initialScale = 0.6f, animationSpec = spring(dampingRatio = 0.45f, stiffness = 380f)) + fadeIn()) togetherWith
                fadeOut(tween(90))
        },
        label = "answer",
    ) { done ->
        if (!done) {
            val blank = when (q.kind) {
                QKind.ENDING -> "${q.person.fi} puhu＿"
                QKind.FORM -> "${q.person.fi} ＿＿＿＿"
                QKind.NEG -> "${q.person.fi} ＿＿ ＿＿＿＿"
                QKind.WHO -> "＿＿ ${q.verb.form(q.person)}"
            }
            Text(blank, fontSize = 26.sp, color = C.MutedInk, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        } else {
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { tts.speak(q.solved) }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    formText(q.verb, q.person, negative = q.kind == QKind.NEG),
                    fontSize = 30.sp, textAlign = TextAlign.Center,
                )
                Text("${q.cnSolved}   🔊", fontSize = 15.sp, color = C.InkSoft)
            }
        }
    }
}

@Composable
fun HintCard(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(C.Apricot)
            .border(1.dp, C.Sun.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.mascot_reindeer), null, Modifier.size(42.dp))
        Spacer(Modifier.width(8.dp))
        Text("💡 $text", fontSize = 14.sp, color = C.ApricotInk, lineHeight = 20.sp)
    }
}

@Composable
fun SuccessCard(q: Question) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(C.LeafSoft)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🎉", fontSize = 28.sp)
        Spacer(Modifier.width(8.dp))
        Column {
            Text("太棒了！${q.solved} = ${q.cnSolved}", fontSize = 15.sp, color = C.Leaf, fontWeight = FontWeight.Bold)
            Text(Hints.right(q), fontSize = 13.sp, color = C.Ink, lineHeight = 18.sp)
        }
    }
}

@Composable
fun ChoiceCard(
    c: Choice,
    q: Question,
    wrong: Boolean,
    solved: Boolean,
    size: TextUnit,
    modifier: Modifier,
    bounce: Float,
    onClick: () -> Unit,
) {
    val right = solved && c.correct
    val ending = q.kind == QKind.ENDING
    val p = c.person
    val bg = when {
        right -> C.Leaf
        wrong -> C.Muted
        ending && p != null -> p.bg
        else -> Color.White
    }
    val fg = when {
        right -> Color.White
        wrong -> C.MutedInk
        ending && p != null -> p.main
        q.kind == QKind.WHO && p != null -> p.main
        else -> C.Ink
    }
    val line = when {
        right -> C.Leaf
        wrong -> C.Line
        ending && p != null -> p.main.copy(alpha = 0.6f)
        else -> C.Sea.copy(alpha = 0.3f)
    }
    Surface(
        onClick = onClick,
        enabled = !solved && !wrong,
        shape = RoundedCornerShape(18.dp),
        color = bg,
        border = BorderStroke(2.dp, line),
        shadowElevation = if (wrong || solved && !right) 0.dp else 2.dp,
        modifier = modifier
            .height(if (q.kind == QKind.WHO) 70.dp else 62.dp)
            .graphicsLayer { scaleX = bounce; scaleY = bounce; alpha = if (solved && !right) 0.55f else 1f },
    ) {
        Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                c.label, fontSize = size, fontWeight = FontWeight.ExtraBold, color = fg, maxLines = 1,
                textDecoration = if (wrong) TextDecoration.LineThrough else null,
            )
            if (q.kind == QKind.WHO && p != null) Text(p.cn, fontSize = 12.sp, color = fg)
        }
    }
}

// ─────────── 小锤子“敲软”魔法（AnimatedContent） ───────────

@Composable
fun StemText(markup: String, struck: String?, highlight: Color) {
    val prefix = markup.substringBefore('[')
    val seg = markup.substringAfter('[').substringBefore(']')
    val rest = markup.substringAfter(']')
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = C.Ink)) { append(prefix) }
            if (seg.isEmpty() && struck != null) {
                withStyle(SpanStyle(color = C.MutedInk, textDecoration = TextDecoration.LineThrough)) { append(struck) }
            } else {
                withStyle(SpanStyle(color = highlight, fontWeight = FontWeight.ExtraBold, background = highlight.copy(alpha = 0.13f))) {
                    append(seg)
                }
            }
            withStyle(SpanStyle(color = C.Ink)) { append(rest) }
            withStyle(SpanStyle(color = C.MutedInk)) { append("-") }
        },
        fontSize = 28.sp, fontWeight = FontWeight.Bold,
    )
}

@Composable
fun StemMagic(v: Verb, p: Person, plays: Int) {
    val k = v.kpt ?: return
    var phase by remember(v.inf, p) { mutableIntStateOf(0) }
    LaunchedEffect(plays) {
        phase = 0
        delay(260)
        phase = 1
        delay(420)
        phase = 2
    }
    val softens = !k.reverse && !p.strong
    val angle by animateFloatAsState(
        if (phase == 1) 32f else -22f, spring(dampingRatio = 0.4f, stiffness = 520f), label = "hammer"
    )
    val squash by animateFloatAsState(
        if (phase == 1) 0.7f else 1f, spring(dampingRatio = 0.3f, stiffness = 700f), label = "squash"
    )
    val caption = when {
        k.reverse -> "反向魔法：${k.rule}，6 个人称都变强💪"
        softens -> "${p.fi} 要变软：${k.rule}"
        else -> "${p.fi} 是大力士💪，词干不变"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(C.SunSoft)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.padding(top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(R.drawable.icon_kpt_hammer), "小锤子",
                Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        rotationZ = angle
                        transformOrigin = TransformOrigin(0.18f, 0.88f)   // 绕着手柄末端挥动
                    },
            )
            Spacer(Modifier.width(6.dp))
            AnimatedContent(
                targetState = phase == 2,
                transitionSpec = {
                    (scaleIn(initialScale = 0.4f, animationSpec = spring(dampingRatio = 0.45f, stiffness = 400f)) + fadeIn()) togetherWith
                        (scaleOut(targetScale = 1.5f) + fadeOut())
                },
                label = "stem",
            ) { done ->
                Box(Modifier.graphicsLayer { scaleY = if (!done) squash else 1f; scaleX = if (!done) 2f - squash else 1f }) {
                    when {
                        !done -> StemText(k.from, null, C.Magic)
                        softens -> StemText(k.to, k.fromSeg, Color(0xFF7B61D9))
                        k.reverse -> StemText(k.to, null, C.Magic)
                        else -> StemText(k.from, null, C.Magic)
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            AnimatedVisibility(visible = phase == 2, enter = scaleIn() + fadeIn()) {
                Image(
                    painterResource(if (softens) R.drawable.icon_soft_cloud else R.drawable.icon_strong_helmet),
                    if (softens) "变软啦" else "很强壮", Modifier.size(46.dp),
                )
            }
        }
        Text(caption, fontSize = 13.sp, color = C.ApricotInk, fontWeight = FontWeight.Bold)
    }
}

// ═══════════════════════════════ 结算庆祝 ═══════════════════════════════

@Composable
fun ResultScreen(
    stage: Int,
    result: RoundResult,
    tts: FinnishTtsManager,
    onAgain: () -> Unit,
    onNext: () -> Unit,
    onHome: () -> Unit,
    onCard: (Verb) -> Unit,
) {
    val chest = remember { Animatable(0.3f) }
    var starsShown by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        launch { chest.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 200f)) }
        delay(450)
        tts.speak(if (result.rating == 3) Phrases.SUPER else Phrases.GOOD_WORK)
        repeat(result.rating) { delay(330); starsShown++ }
    }
    val title = when (result.rating) {
        3 -> "完美通关！太厉害啦！"
        2 -> "真棒！再练一次就能拿 3 颗星！"
        else -> "完成啦！多练几次会越来越熟～"
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("第 $stage 关 · ${Stages.info(stage).title}", fontSize = 14.sp, color = stageColor(stage), fontWeight = FontWeight.Bold)
            Image(
                painterResource(if (result.rating == 3) R.drawable.badge_three_stars else R.drawable.reward_treasure_chest),
                "宝箱", Modifier.size(170.dp).scale(chest.value),
            )
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = C.SeaDeep, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) { i ->
                    val on = i < starsShown
                    val s by animateFloatAsState(if (on) 1f else 0.7f, spring(dampingRatio = 0.3f, stiffness = 300f), label = "star$i")
                    Text("★", fontSize = 50.sp, color = if (on) C.Sun else C.Line, modifier = Modifier.scale(s))
                }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatLine("第一次就答对", "${result.firstTry} / 5 题")
                    StatLine("获得星星", "+${result.firstTry} ⭐")
                    StatLine("小失误", if (result.mistakes == 0) "0 次，完美！" else "${result.mistakes} 次（都改对啦）")
                    StatLine("用时", clock(result.seconds) + if (result.seconds <= 90) "  ⚡ 90 秒内完成！" else "")
                }
            }
            if (result.unlockedNext && stage < 5) {
                Text(
                    "🎉 第 ${stage + 1} 关「${Stages.info(stage + 1).title}」解锁啦！",
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(C.SunSoft)
                        .padding(12.dp),
                    fontSize = 15.sp, color = C.ApricotInk, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                )
            }
            Text("这一轮练到的动词（点开看完整变位表）", fontSize = 13.sp, color = C.InkSoft)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                result.verbs.mapNotNull { VerbRepository.byInf(it) }.forEach { v ->
                    AssistChip(onClick = { onCard(v) }, label = { Text("${v.inf} ${v.cnShort}", fontSize = 14.sp) })
                }
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onAgain,
                Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = stageColor(stage)),
            ) { Text("再来一轮 🔁", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            if (stage < 5 && (result.unlockedNext || result.rating >= 2)) {
                Button(
                    onClick = onNext,
                    Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = C.Leaf),
                ) { Text("去第 ${stage + 1} 关 ▶", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            }
            OutlinedButton(
                onClick = onHome,
                Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("回到岛屿地图 🏝", fontSize = 16.sp) }
        }
        Confetti(Modifier.fillMaxSize())
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 15.sp, color = C.InkSoft, modifier = Modifier.weight(1f))
        Text(value, fontSize = 15.sp, color = C.Ink, fontWeight = FontWeight.Bold)
    }
}

private class Piece(
    val x: Float, val delay: Float, val speed: Float, val drift: Float,
    val size: Float, val color: Color, val spin: Float, val phase: Float,
)

@Composable
fun Confetti(modifier: Modifier) {
    val colors = listOf(C.Sun, Color(0xFF3F86D9), Color(0xFF3FA34D), Color(0xFFD6558F), Color(0xFF8A63D2), Color(0xFFF08A24))
    val pieces = remember {
        val r = Random(System.nanoTime())
        List(90) {
            Piece(
                r.nextFloat(), r.nextFloat() * 1.2f, 0.22f + r.nextFloat() * 0.25f, r.nextFloat() * 2f - 1f,
                7f + r.nextFloat() * 9f, colors[r.nextInt(colors.size)], r.nextFloat() * 720f - 360f, r.nextFloat() * 6f,
            )
        }
    }
    // time is only read while drawing, so each frame redraws the canvas without recomposing the screen
    var time by remember { mutableFloatStateOf(0f) }
    var over by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (time < 6.5f) withFrameNanos { time = (it - start) / 1e9f }
        over = true
    }
    if (over) return
    Canvas(modifier) {
        for (p in pieces) {
            val t = time - p.delay
            if (t < 0) continue
            val y = -20f + t * p.speed * size.height
            if (y > size.height + 20f) continue
            val x = p.x * size.width + sin(t * 2.2f + p.phase) * 26f * p.drift
            rotate(p.spin * t, Offset(x, y)) {
                drawRect(p.color, Offset(x - p.size / 2, y - p.size / 3), Size(p.size, p.size * 0.6f))
            }
        }
    }
}

// ═══════════════════════════════ 动词词卡本 ═══════════════════════════════

@Composable
fun WordBookScreen(
    startStage: Int,
    tts: FinnishTtsManager,
    onBack: () -> Unit,
    onCard: (Verb) -> Unit,
    onVoice: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(startStage) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = C.SeaDeep) }
            Text("动词词卡本", Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = C.SeaDeep)
            VoiceChip(tts, compact = false, onClick = onVoice)
        }
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (n in 1..5) {
                FilterChip(
                    selected = tab == n,
                    onClick = { tab = n },
                    label = { Text(if (n == 1) "第1关 词尾" else "第${n}关 · ${VerbRepository.stage(n).size}词", fontSize = 13.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = stageColor(n), selectedLabelColor = Color.White,
                    ),
                )
            }
        }
        if (tab == 1) EndingTable(tts)
        else LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(2) }) {
                TipBanner(tab, Question(QKind.FORM, VerbRepository.puhua, Person.MINA, emptyList()))
            }
            items(VerbRepository.stage(tab)) { v -> VerbTile(v, stageColor(tab)) { onCard(v) } }
        }
    }
}

@Composable
fun VerbTile(v: Verb, color: Color, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.5.dp, color.copy(alpha = 0.3f)),
    ) {
        Column(Modifier.padding(12.dp).fillMaxWidth()) {
            Text(v.inf, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = C.SeaDeep)
            Text(v.cn, fontSize = 13.sp, color = C.InkSoft, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Minä ${v.form(Person.MINA)}", fontSize = 12.sp, color = Person.MINA.main)
            }
            if (v.kpt != null || v.extra || v.group == VerbGroup.SPECIAL) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    v.kpt?.let { Tag(if (it.reverse) "💪 ${it.rule}" else "🔨 ${it.rule}", C.Magic) }
                    if (v.group == VerbGroup.SPECIAL) Tag("特殊", Color(0xFFD6558F))
                    if (v.extra) Tag("补充词", C.Leaf)
                }
            }
        }
    }
}

@Composable
fun Tag(text: String, color: Color) {
    Text(
        text,
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 1.dp),
        fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold,
    )
}

@Composable
fun EndingTable(tts: FinnishTtsManager) {
    LazyColumn(
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { TipBanner(1, Question(QKind.ENDING, VerbRepository.puhua, Person.MINA, emptyList())) }
        items(Person.entries.size) { i ->
            val p = Person.entries[i]
            val v = VerbRepository.puhua
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.5.dp, p.main.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .clickable { tts.speak(v.phrase(p)) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PronounChip(p, tts, big = false)
                Spacer(Modifier.width(10.dp))
                Text(
                    stageOneEnding(p),
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(p.bg)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    fontSize = 20.sp, color = p.main, fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(formText(v, p), fontSize = 20.sp)
                    Text(v.cnSentence(p), fontSize = 12.sp, color = C.InkSoft)
                }
                Text("🔊", fontSize = 18.sp)
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = C.SunSoft), shape = RoundedCornerShape(16.dp)) {
                Text(
                    "否定词也跟着人称变：Minä en · Sinä et · Hän ei · Me emme · Te ette · He eivät\n" +
                        "后面的动词用光杆词干：Minä en puhu（我不说话）",
                    Modifier.padding(12.dp), fontSize = 13.sp, color = C.ApricotInk, lineHeight = 19.sp,
                )
            }
        }
    }
}

@Composable
fun VerbCardDialog(v: Verb, tts: FinnishTtsManager, onDismiss: () -> Unit) {
    var negative by remember(v.inf) { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            v.inf, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = C.SeaDeep,
                            modifier = Modifier.clickable { tts.speak(v.inf) },
                        )
                        Text("${v.cn} · 第 ${v.stage} 关 · ${v.group.title}", fontSize = 12.sp, color = C.InkSoft)
                    }
                    Image(
                        painterResource(R.drawable.icon_tts_megaphone), "朗读",
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { tts.speak(v.inf) },
                    )
                }
                Text(
                    Hints.stemLine(v) + (v.note?.let { "\n$it" } ?: ""),
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(C.SunSoft)
                        .padding(10.dp),
                    fontSize = 13.sp, color = C.ApricotInk, lineHeight = 19.sp,
                )
                if (v.kpt != null && !v.kpt.reverse) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KptBox(R.drawable.icon_strong_helmet, "Hän · He", v.kpt.from, null, Modifier.weight(1f))
                        KptBox(R.drawable.icon_soft_cloud, "Minä · Sinä · Me · Te", v.kpt.to, v.kpt.fromSeg, Modifier.weight(1f))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !negative, onClick = { negative = false }, label = { Text("肯定句") })
                    FilterChip(selected = negative, onClick = { negative = true }, label = { Text("否定句") })
                }
                for (p in Person.entries) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(p.bg.copy(alpha = 0.6f))
                            .clickable { tts.speak(if (negative) v.negPhrase(p) else v.phrase(p)) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.width(62.dp)) {
                            Text(p.fi, fontSize = 16.sp, color = p.main, fontWeight = FontWeight.ExtraBold)
                            Text(p.cn, fontSize = 11.sp, color = p.main)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(formText(v, p, negative, withPronoun = false), fontSize = 20.sp)
                            Text(v.cnSentence(p, negative), fontSize = 12.sp, color = C.InkSoft)
                        }
                        Text("🔊", fontSize = 17.sp)
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("关闭", fontSize = 15.sp) }
            }
        }
    }
}

@Composable
private fun KptBox(icon: Int, who: String, markup: String, struck: String?, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(C.Muted)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(icon), null, Modifier.size(34.dp))
        Text(who, fontSize = 11.sp, color = C.InkSoft)
        StemText(markup, struck, if (struck == null) C.Magic else Color(0xFF7B61D9))
    }
}

// ═══════════════════════════════ 小对话框 ═══════════════════════════════

@Composable
fun AvatarDialog(current: Int, onPick: (Int) -> Unit) {
    Dialog(onDismissRequest = { onPick(if (current < 0) 0 else current) }) {
        Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.mascot_reindeer), null, Modifier.size(80.dp))
                Text("选择你的小探险家", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = C.SeaDeep)
                Text("和小驯鹿 Poro 一起闯芬兰语动词岛！", fontSize = 13.sp, color = C.InkSoft)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (i in 0..1) {
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .border(3.dp, if (current == i) C.Sun else C.Line, RoundedCornerShape(18.dp))
                                .clickable { onPick(i) }
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AvatarBubble(i, 96.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(if (i == 0) "男孩" else "女孩", fontSize = 15.sp, color = C.Ink, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("以后点首页左上角的头像可以更换", fontSize = 12.sp, color = C.InkSoft)
            }
        }
    }
}

@Composable
fun SettingsDialog(store: ProgressStore, tts: FinnishTtsManager, onVoice: () -> Unit, onDismiss: () -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
        title = { Text("家长设置", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("全部关卡解锁", fontSize = 15.sp, color = C.Ink, fontWeight = FontWeight.Bold)
                        Text("默认要按顺序闯关；老师正在教哪一课，可以直接打开那一关。", fontSize = 12.sp, color = C.InkSoft)
                    }
                    Switch(checked = store.unlockAll, onCheckedChange = { store.setUnlockAllStages(it) })
                }
                OutlinedButton(onClick = onVoice, modifier = Modifier.fillMaxWidth()) { Text("🔊 语音设置") }
                OutlinedButton(
                    onClick = {
                        if (confirmReset) { store.reset(); confirmReset = false } else confirmReset = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (confirmReset) "再点一次：确认清空星星和成绩" else "重置学习进度", color = C.ApricotInk) }
                Text(
                    "版本 1.0 · 5 关 · 65 个动词 · 内置芬兰语录音 ${tts.clipCount} 条\n" +
                        "补充词（为凑满 65 个）：kerätä、pudota、häiritä",
                    fontSize = 12.sp, color = C.InkSoft, lineHeight = 17.sp,
                )
            }
        },
    )
}

package com.finnishkids.verbisland

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.MessageDigest
import java.util.Locale

/**
 * 芬兰语朗读。
 *
 * 1. 系统 TextToSpeech，强制绑定 Locale("fi", "FI")，语速 0.88。默认引擎没有芬兰语时，会逐个试手机上装的其它引擎。
 * 2. 内置录音 assets/fi/<sha1>.ogg（tools/make_voice.py 用 Piper 芬兰语生成）。系统没有芬兰语语音时自动改用它，
 *    所以没装语音包的手机（比如很多国行小米）也能听到每个单词。
 *
 * 状态 [status] 是 Compose 状态，界面右上角据此显示提示。
 */
class FinnishTtsManager(context: Context) {

    enum class Status { CHECKING, READY, MISSING_VOICE, NO_ENGINE }

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val prefs = app.getSharedPreferences("verbisland", Context.MODE_PRIVATE)
    private val finnish = Locale("fi", "FI")

    private var tts: TextToSpeech? = null
    private var player: MediaPlayer? = null
    private var utterance = 0
    private var generation = 0   // ignores callbacks from engines started by an older check

    var status by mutableStateOf(Status.CHECKING)
        private set

    /** 正在使用的系统引擎包名（例如 com.google.android.tts） */
    var engineName by mutableStateOf<String?>(null)
        private set

    /** 家长可以选择：即使系统有芬兰语，也优先用内置录音 */
    var preferClips by mutableStateOf(prefs.getBoolean("preferClips", false))
        private set

    private val clips: Set<String> =
        runCatching { app.assets.list("fi")?.toSet() }.getOrNull().orEmpty()

    val clipCount: Int get() = clips.size
    val hasClips: Boolean get() = clips.isNotEmpty()

    /** 现在点一下能不能听到声音 */
    val canSpeak: Boolean get() = status == Status.READY || hasClips

    init {
        start()
    }

    // ───────────────────────── 系统 TTS ─────────────────────────

    private fun start() {
        val gen = ++generation
        status = Status.CHECKING
        engineName = null
        tts?.shutdown()
        tts = null
        val holder = arrayOfNulls<TextToSpeech>(1)
        // onInit can run inside the constructor on some phones, so the work is posted to the next loop turn
        holder[0] = TextToSpeech(app) { st -> main.post { onDefaultInit(gen, holder[0], st) } }
    }

    private fun onDefaultInit(gen: Int, t: TextToSpeech?, st: Int) {
        if (gen != generation || t == null) { t?.shutdown(); return }
        if (st == TextToSpeech.SUCCESS && configure(t)) {
            tts = t
            engineName = t.defaultEngine
            status = Status.READY
            return
        }
        val engines = runCatching { t.engines.map { it.name } }.getOrDefault(emptyList())
        val default = runCatching { t.defaultEngine }.getOrNull()
        t.shutdown()
        tryEngines(gen, ArrayDeque(engines.filter { it != default }), engines.isNotEmpty() || st == TextToSpeech.SUCCESS)
    }

    private fun tryEngines(gen: Int, queue: ArrayDeque<String>, hadEngine: Boolean) {
        if (gen != generation) return
        val name = queue.removeFirstOrNull()
        if (name == null) {
            status = if (hadEngine) Status.MISSING_VOICE else Status.NO_ENGINE
            return
        }
        val holder = arrayOfNulls<TextToSpeech>(1)
        holder[0] = TextToSpeech(app, { st ->
            main.post {
                val t = holder[0]
                when {
                    gen != generation -> t?.shutdown()
                    t != null && st == TextToSpeech.SUCCESS && configure(t) -> {
                        tts = t; engineName = name; status = Status.READY
                    }
                    else -> { t?.shutdown(); tryEngines(gen, queue, true) }
                }
            }
        }, name)
    }

    /**
     * 绑定芬兰语。只认已经下载到手机里的离线芬兰语声音：语音数据没下载、或者只有联网声音，都算“没有”，
     * 这时改用内置录音（孩子离线时也能听）。
     */
    private fun configure(t: TextToSpeech): Boolean = runCatching {
        if (t.isLanguageAvailable(finnish) < TextToSpeech.LANG_AVAILABLE) return false
        val r = t.setLanguage(finnish)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) return false
        val voices = runCatching { t.voices }.getOrNull()
        if (voices != null) {
            val local = voices.filter {
                it.locale.language == "fi" && !it.isNetworkConnectionRequired &&
                    it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
            }
            val best = local.minByOrNull { (if (it.locale.country == "FI") 0 else 10) - it.quality / 100 }
            Log.i("verbisland", "tts ${t.defaultEngine}: ${voices.count { it.locale.language == "fi" }} fi voices, local=${best?.name}")
            if (best == null) return false
            t.voice = best
        } else {
            val voice = runCatching { t.voice }.getOrNull()
            if (voice != null && voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true) return false
        }
        t.setSpeechRate(0.88f)   // 稍慢，四年级孩子听得清词尾
        true
    }.getOrDefault(false)

    /** 从系统设置回来时重新检查（用户可能刚装好芬兰语语音包） */
    fun recheck() {
        if (status == Status.MISSING_VOICE || status == Status.NO_ENGINE) start()
    }

    // ───────────────────────── 朗读 ─────────────────────────

    fun speak(text: String) {
        val clip = clipFor(text)
        val useClip = clip != null && (preferClips || status != Status.READY)
        Log.i("verbisland", "speak: $text")
        if (useClip) {
            tts?.stop()
            playClip(clip!!)
        } else if (status == Status.READY) {
            stopClip()
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "fi-${utterance++}")
        }
    }

    fun stop() {
        tts?.stop()
        stopClip()
    }

    fun usePreferClips(on: Boolean) {
        preferClips = on
        prefs.edit().putBoolean("preferClips", on).apply()
    }

    private fun clipFor(text: String): String? {
        val name = clipName(text)
        return if (name in clips) name else null
    }

    private fun playClip(name: String) {
        stopClip()
        runCatching {
            app.assets.openFd("fi/$name").use { fd ->
                val mp = MediaPlayer()
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                mp.setOnCompletionListener {
                    it.release()
                    if (player === it) player = null
                }
                mp.prepare()
                mp.start()
                player = mp
            }
        }
    }

    private fun stopClip() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }

    // ───────────────────────── 引导开启系统语音 ─────────────────────────

    /** 打开系统“文字转语音”设置。打不开时返回 false */
    fun openTtsSettings(context: Context): Boolean {
        val tries = listOf(
            Intent("com.android.settings.TTS_SETTINGS"),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (i in tries) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return false
    }

    /** 打开语音引擎的“安装语音数据”页面（Google 语音服务里可以下载 Suomi） */
    fun openInstallVoice(context: Context): Boolean {
        val base = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val packages = listOfNotNull(engineName, "com.google.android.tts", null)
        for (pkg in packages.distinct() + listOf<String?>(null)) {
            try {
                context.startActivity(Intent(base).apply { if (pkg != null) setPackage(pkg) }); return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return false
    }

    fun shutdown() {
        generation++
        stopClip()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    companion object {
        /** 内置录音的文件名：文字的 SHA-1 前 12 位（与 tools/make_voice.py 一致） */
        fun clipName(text: String): String {
            val d = MessageDigest.getInstance("SHA-1").digest(text.trim().toByteArray(Charsets.UTF_8))
            return d.joinToString("") { "%02x".format(it) }.take(12) + ".ogg"
        }
    }
}

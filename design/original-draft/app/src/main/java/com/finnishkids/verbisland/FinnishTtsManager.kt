package com.finnishkids.verbisland

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

class FinnishTtsManager(context: Context) {
    private var tts: TextToSpeech? = null
    var isFinnishAvailable by mutableStateOf(true)
        private set

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val finnishLocale = Locale("fi", "FI")
                val result = tts?.setLanguage(finnishLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    isFinnishAvailable = false
                } else {
                    tts?.setSpeechRate(0.88f) // 稍慢语速，适合4年级S2孩子听清词尾
                    isFinnishAvailable = true
                }
            } else {
                isFinnishAvailable = false
            }
        }
    }

    fun speak(text: String) {
        if (isFinnishAvailable) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "fi_tts_${System.currentTimeMillis()}")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}

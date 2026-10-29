package com.jarvis.app

import android.content.Context
import android.content.res.AssetManager
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import java.io.File
import java.io.IOException

// Service ko offline engine ki khabar dene ke liye
interface OfflineCallback {
    fun onReady()
    fun onPartial(text: String)
    fun onFinal(text: String)
    fun onFail(msg: String)
}

// =====================================================================
//  OFFLINE VOICE ENGINE (Vosk): bina internet, lagatar sunta hai.
//  Mic kabhi band nahi hota (pause sirf software me hota hai),
//  isliye mic baar-baar on/off nahi hota.
// =====================================================================
class Offline(private val ctx: Context, private val cb: OfflineCallback) {
    private val main = Handler(Looper.getMainLooper())
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var svc: SpeechService? = null

    @Volatile
    private var paused = false

    private fun textOf(h: String?, key: String): String {
        if (h == null) return ""
        return try {
            JSONObject(h).optString(key, "")
        } catch (e: Exception) {
            ""
        }
    }

    private val listener = object : org.vosk.android.RecognitionListener {
        override fun onPartialResult(hypothesis: String?) {
            if (!paused) cb.onPartial(textOf(hypothesis, "partial"))
        }

        override fun onResult(hypothesis: String?) {
            if (!paused) cb.onFinal(textOf(hypothesis, "text"))
        }

        override fun onFinalResult(hypothesis: String?) {}

        override fun onError(exception: Exception?) {
            cb.onFail(exception?.message ?: "vosk error")
        }

        override fun onTimeout() {}
    }

    // model copy + load background me hota hai (pehli baar thoda time lagta hai)
    fun start() {
        Thread {
            try {
                val dir = prepareModel()
                val m = Model(dir.absolutePath)
                val r = Recognizer(m, 16000.0f)
                main.post {
                    try {
                        val s = SpeechService(r, 16000.0f)
                        model = m
                        recognizer = r
                        svc = s
                        s.startListening(listener)
                        cb.onReady()
                    } catch (e: Exception) {
                        cb.onFail(e.message ?: "mic start error")
                    }
                }
            } catch (e: Throwable) {
                main.post { cb.onFail(e.message ?: "model load error") }
            }
        }.start()
    }

    // mic chalu rehta hai, bas Jarvis ke bolte waqt hum sunna ignore karte hain
    fun pause() {
        paused = true
    }

    fun resume() {
        resetState()
        paused = false
    }

    // purani adhoori awaaz (Jarvis ki apni awaaz) saaf kar do
    private fun resetState() {
        try {
            val s = svc
            if (s != null) {
                s.javaClass.getMethod("reset").invoke(s)
                return
            }
        } catch (e: Throwable) {
        }
        try {
            val r = recognizer
            if (r != null) r.javaClass.getMethod("reset").invoke(r)
        } catch (e: Throwable) {
        }
    }

    fun stop() {
        try { svc?.stop() } catch (e: Exception) {}
        try { svc?.shutdown() } catch (e: Exception) {}
        try { recognizer?.close() } catch (e: Exception) {}
        try { model?.close() } catch (e: Exception) {}
        svc = null
        recognizer = null
        model = null
    }

    private fun prepareModel(): File {
        val target = File(ctx.filesDir, "vosk-model-hi")
        val marker = File(target, "copy_done.txt")
        if (marker.exists()) return target
        val am = ctx.assets
        val top = am.list("model-hi")
        if (top == null || top.isEmpty()) throw IllegalStateException("Hindi model APK me nahi mila")
        if (target.exists()) target.deleteRecursively()
        copyAssets(am, "model-hi", target)
        marker.writeText("ok")
        return target
    }

    private fun copyAssets(am: AssetManager, path: String, dest: File) {
        val names = am.list(path) ?: emptyArray()
        if (names.isEmpty()) {
            try {
                dest.parentFile?.mkdirs()
                am.open(path).use { input ->
                    dest.outputStream().use { out -> input.copyTo(out) }
                }
            } catch (e: IOException) {
            }
        } else {
            dest.mkdirs()
            for (n in names) copyAssets(am, "$path/$n", File(dest, n))
        }
    }
}

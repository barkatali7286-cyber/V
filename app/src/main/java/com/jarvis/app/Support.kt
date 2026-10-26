package com.jarvis.app

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.*
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

// =====================================================================
//  BHASHA (language) - Jarvis kis bhasha me sunega aur bolega
// =====================================================================
object Lang {
    @Volatile
    var code: String = "hi-IN"
    @Volatile
    var name: String = "Hindi"
}

// =====================================================================
//  ACCESSIBILITY SERVICE: scroll, back, home, click, type
// =====================================================================
class JarvisAccess : AccessibilityService() {
    companion object {
        @Volatile
        var inst: JarvisAccess? = null
    }

    override fun onServiceConnected() { inst = this }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(i: Intent?): Boolean { inst = null; return super.onUnbind(i) }
    override fun onDestroy() { inst = null; super.onDestroy() }

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        val p = Path()
        p.moveTo(x1, y1)
        p.lineTo(x2, y2)
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0L, 300L))
            .build()
        return dispatchGesture(g, null, null)
    }

    fun scroll(dir: String): String {
        val d = resources.displayMetrics
        val w = d.widthPixels.toFloat()
        val h = d.heightPixels.toFloat()
        val ok = when (dir) {
            "up" -> swipe(w / 2, h * 0.30f, w / 2, h * 0.80f)
            "left" -> swipe(w * 0.85f, h / 2, w * 0.15f, h / 2)
            "right" -> swipe(w * 0.15f, h / 2, w * 0.85f, h / 2)
            else -> swipe(w / 2, h * 0.80f, w / 2, h * 0.30f)
        }
        val name = when (dir) {
            "up" -> "ऊपर"
            "left" -> "बाएं"
            "right" -> "दाएं"
            else -> "नीचे"
        }
        return if (ok) "$name स्क्रॉल कर दिया" else "स्क्रॉल नहीं हो पाया"
    }

    fun global(action: Int, okMsg: String): String =
        if (performGlobalAction(action)) okMsg else "ये नहीं हो पाया"

    fun clickText(t: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val list = root.findAccessibilityNodeInfosByText(t)
        for (n in list) {
            var x: AccessibilityNodeInfo? = n
            while (x != null) {
                if (x.isClickable) return x.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                x = x.parent
            }
        }
        return false
    }

    fun clickAny(labels: List<String>): Boolean {
        for (l in labels) {
            if (clickText(l)) return true
        }
        return false
    }

    private fun collect(n: AccessibilityNodeInfo?, out: ArrayList<AccessibilityNodeInfo>, depth: Int) {
        if (n == null || depth > 45 || out.size > 600) return
        out.add(n)
        for (i in 0 until n.childCount) collect(n.getChild(i), out, depth + 1)
    }

    private fun label(n: AccessibilityNodeInfo): String {
        val a = n.text?.toString() ?: ""
        val b = n.contentDescription?.toString() ?: ""
        return (a + " " + b).trim()
    }

    private fun clickUp(n: AccessibilityNodeInfo): Boolean {
        var x: AccessibilityNodeInfo? = n
        while (x != null) {
            if (x.isClickable) return x.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            x = x.parent
        }
        return false
    }

    // screen pe jo naam/title sabse milta hai use dabata hai (naam wapas deta hai)
    fun clickBest(q: String): String? {
        val root = rootInActiveWindow ?: return null
        val all = ArrayList<AccessibilityNodeInfo>()
        collect(root, all, 0)
        val qw = q.split(" ").filter { it.isNotBlank() }
        if (qw.isEmpty()) return null
        var best: AccessibilityNodeInfo? = null
        var bestScore = 0
        for (n in all) {
            if (!n.isVisibleToUser) continue
            val l = label(n)
            if (l.isEmpty()) continue
            val ls = Hi.skel(l)
            val low = l.lowercase()
            var hit = 0
            for (w in qw) {
                val k = Hi.skel(w)
                if ((k.length >= 2 && ls.contains(k)) || low.contains(w)) hit++
            }
            val sc = hit * 10 / qw.size
            if (sc > bestScore) {
                bestScore = sc
                best = n
            }
        }
        val b = best
        if (b == null || bestScore < 6) return null
        return if (clickUp(b)) label(b).take(40) else null
    }

    // screen pe upar se nth (1,2,3..) video/item dabata hai
    fun clickNth(nth: Int): String {
        val root = rootInActiveWindow ?: return "स्क्रीन नहीं पढ़ पाया"
        val all = ArrayList<AccessibilityNodeInfo>()
        collect(root, all, 0)
        val items = ArrayList<Pair<Int, AccessibilityNodeInfo>>()
        val r = Rect()
        for (n in all) {
            if (!n.isVisibleToUser || !n.isClickable) continue
            if (label(n).length < 12) continue
            n.getBoundsInScreen(r)
            if (r.height() < 120) continue
            items.add(Pair(r.top, n))
        }
        items.sortBy { it.first }
        val uniq = ArrayList<Pair<Int, AccessibilityNodeInfo>>()
        for (p in items) {
            if (uniq.isEmpty() || p.first - uniq[uniq.size - 1].first > 80) uniq.add(p)
        }
        if (nth < 1 || nth > uniq.size) return "स्क्रीन पर इतने वीडियो नहीं दिखे"
        val n = uniq[nth - 1].second
        return if (n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) "$nth वाला वीडियो चला दिया" else "ये नहीं चल पाया"
    }

    fun typeText(t: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val n = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, t)
        return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }
}

// =====================================================================
//  AI DIMAAG (free, bina key): Pollinations
// =====================================================================
object Brain {
    private const val URL_API = "https://text.pollinations.ai/openai"

    private val liveWords = listOf(
        "news", "khabar", "खबर", "samachar", "समाचार", "weather", "mausam", "मौसम", "score", "price", "rate",
        "bhav", "भाव", "latest", "live", "stock", "who won", "cricket", "क्रिकेट", "match"
    )

    private fun persona(lang: String): String {
        val l = if (lang == "Hindi") "देवनागरी हिंदी (ज़रूरी अंग्रेज़ी शब्द चल सकते हैं)" else lang
        return "तुम 'वी' हो, मेरे सबसे करीबी, वफादार और भावुक दोस्त जैसे AI साथी। " +
            "मुझे हमेशा 'बॉस' बुलाओ। मेरे मूड को पहचानो: उदास हूँ तो प्यार से हौसला दो, खुश हूँ तो साथ में खुश हो, " +
            "थका हूँ तो ख्याल रखो। जवाब $l भाषा में दो। जवाब बोलकर सुनाया जाएगा, " +
            "इसलिए सिर्फ 1-2 छोटे वाक्य, कोई इमोजी, स्टार, लिस्ट या मार्कडाउन नहीं। " +
            "अगर मैं फोन चलाने का कोई काम बोलूँ जो तुम नहीं कर पाए, तो झूठ मत बोलो, सीधे बता दो।"
    }

    private fun msg(role: String, text: String) = JSONObject().put("role", role).put("content", text)

    private fun post(model: String, msgs: JSONArray): String? {
        val body = JSONObject().put("model", model).put("messages", msgs).put("max_tokens", 120)
        val conn = URL(URL_API).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 8000
        conn.readTimeout = 25000
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = conn.responseCode
        if (code !in 200..299) return null
        val raw = conn.inputStream.bufferedReader().use { it.readText() }
        return try {
            JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
        } catch (e: Exception) {
            raw.trim()
        }
    }

    fun ask(history: List<Pair<String, String>>, text: String, lang: String): String {
        val msgs = JSONArray().put(msg("system", persona(lang)))
        for ((u, a) in history.takeLast(6)) {
            msgs.put(msg("user", u))
            msgs.put(msg("assistant", a))
        }
        msgs.put(msg("user", text))
        if (liveWords.any { text.contains(it) }) {
            val r = try { post("searchgpt", msgs) } catch (e: Exception) { null }
            if (r != null) return r
        }
        return post("openai", msgs) ?: "AI अभी व्यस्त है बॉस, थोड़ी देर बाद बोलिए"
    }
}

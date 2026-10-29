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
import android.view.accessibility.AccessibilityWindowInfo
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
        val root = rootNode() ?: return false
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

    // active window na mile to app wali window dhoondh lo
    private fun rootNode(): AccessibilityNodeInfo? {
        val r = rootInActiveWindow
        if (r != null) return r
        try {
            for (w in windows) {
                if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    val rt = w.root
                    if (rt != null) return rt
                }
            }
        } catch (e: Exception) {
        }
        return null
    }

    // screen pe jo likha dikh raha hai wo batata hai (jaanch ke liye)
    fun readScreen(max: Int): String {
        val root = rootNode() ?: return "मुझे स्क्रीन नहीं दिख रही, Accessibility बंद करके दोबारा चालू कीजिए"
        val all = ArrayList<AccessibilityNodeInfo>()
        collect(root, all, 0)
        val seen = LinkedHashSet<String>()
        for (n in all) {
            if (!n.isVisibleToUser) continue
            val l = label(n)
            if (l.length < 3) continue
            seen.add(l.take(30))
            if (seen.size >= max) break
        }
        if (seen.isEmpty()) return "स्क्रीन पर मुझे कुछ पढ़ने लायक नहीं दिखा"
        return "स्क्रीन पर ये दिख रहा है: " + seen.joinToString(", ")
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
        val root = rootNode() ?: return null
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
        if (b == null || bestScore < 5) return null
        return if (clickUp(b)) label(b).take(40) else null
    }

    private fun pickItems(all: ArrayList<AccessibilityNodeInfo>, minLabel: Int, minH: Int): List<AccessibilityNodeInfo> {
        val items = ArrayList<Pair<Int, AccessibilityNodeInfo>>()
        val r = Rect()
        for (n in all) {
            if (!n.isVisibleToUser || !n.isClickable) continue
            if (label(n).length < minLabel) continue
            n.getBoundsInScreen(r)
            if (r.height() < minH) continue
            items.add(Pair(r.top, n))
        }
        items.sortBy { it.first }
        val out = ArrayList<AccessibilityNodeInfo>()
        var lastTop = -10000
        for (p in items) {
            if (p.first - lastTop > 80) {
                out.add(p.second)
                lastTop = p.first
            }
        }
        return out
    }

    // screen pe upar se nth (1,2,3..) video/item dabata hai
    fun clickNth(nth: Int): String {
        val root = rootNode() ?: return "स्क्रीन नहीं पढ़ पाया"
        val all = ArrayList<AccessibilityNodeInfo>()
        collect(root, all, 0)
        var items = pickItems(all, 12, 120)
        if (nth > items.size) items = pickItems(all, 5, 70)
        if (nth < 1 || nth > items.size) return "स्क्रीन पर इतने वीडियो नहीं दिखे"
        val n = items[nth - 1]
        return if (clickUp(n)) "$nth वाला वीडियो चला दिया" else "ये नहीं चल पाया"
    }

    // screen pe "search" wala icon/box dhoondh kar dabata hai
    fun clickSearchIcon(): Boolean {
        val root = rootNode() ?: return false
        val all = ArrayList<AccessibilityNodeInfo>()
        collect(root, all, 0)
        for (n in all) {
            if (!n.isVisibleToUser) continue
            val l = label(n).lowercase()
            val id = (n.viewIdResourceName ?: "").lowercase()
            if (l.contains("search") || l.contains("खोज") || l.contains("सर्च") || id.contains("search")) {
                if (n.isEditable) {
                    n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    return true
                }
                if (clickUp(n)) return true
            }
        }
        return false
    }

    // jo bhi likhne wala box khula hai usme text daalta hai
    fun setInput(text: String): Boolean {
        val root = rootNode() ?: return false
        var target: AccessibilityNodeInfo? = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (target == null || !target.isEditable) {
            val all = ArrayList<AccessibilityNodeInfo>()
            collect(root, all, 0)
            target = null
            for (n in all) {
                if (n.isEditable && n.isVisibleToUser) {
                    target = n
                    break
                }
            }
        }
        val tn = target ?: return false
        tn.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return tn.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    // keyboard ka search/enter dabata hai
    fun pressSearch(): Boolean {
        val root = rootNode() ?: return false
        val t = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (t != null && Build.VERSION.SDK_INT >= 30) {
            try {
                if (t.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)) return true
            } catch (e: Exception) {
            }
        }
        return clickAny(listOf("Search", "खोजें", "खोज", "सर्च", "Go"))
    }

    fun typeText(t: String): Boolean {
        val root = rootNode() ?: return false
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

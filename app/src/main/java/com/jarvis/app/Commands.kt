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
import android.graphics.drawable.Icon
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
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
//  HINDI <-> ENGLISH naam milana (jaise "रवि" == "Ravi", "यूट्यूब" == "YouTube")
// =====================================================================
object Hi {
    private val m = mapOf(
        'क' to "k", 'ख' to "k", 'ग' to "g", 'घ' to "g", 'च' to "c", 'छ' to "c", 'ज' to "j", 'झ' to "j",
        'ट' to "t", 'ठ' to "t", 'ड' to "d", 'ढ' to "d", 'ण' to "n", 'त' to "t", 'थ' to "t", 'द' to "d",
        'ध' to "d", 'न' to "n", 'प' to "p", 'फ' to "f", 'ब' to "b", 'भ' to "b", 'म' to "m", 'य' to "y",
        'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "s", 'ष' to "s", 'स' to "s", 'ह' to "h",
        'ं' to "n", 'ँ' to "n"
    )

    fun skel(input: String): String {
        val sb = StringBuilder()
        for (ch in input.lowercase()) {
            val x = m[ch]
            if (x != null) sb.append(x) else if (ch in 'a'..'z') sb.append(ch)
        }
        val s = sb.toString()
            .replace("ph", "f")
            .replace("h", "")
            .replace('c', 'k').replace('q', 'k').replace('w', 'v').replace('z', 'j').replace('x', 'k')
            .filter { it !in "aeiouy" }
        val out = StringBuilder()
        for (c in s) {
            if (out.isEmpty() || out[out.length - 1] != c) out.append(c)
        }
        return out.toString()
    }

    fun score(query: String, target: String): Int {
        val q = query.lowercase().trim()
        val t = target.lowercase().trim()
        if (q.isEmpty() || t.isEmpty()) return 0
        if (t == q) return 4
        if (q.length >= 3 && t.contains(q)) return 3
        val qs = skel(q)
        val ts = skel(t)
        if (qs.length < 2) return 0
        if (ts == qs) return 3
        if (ts.contains(qs)) return 2
        return 0
    }
}

// =====================================================================
//  PHONE COMMANDS (bina AI ke, turant)
// =====================================================================
object Commands {
    const val STOP = "__STOP__"

    // agla bola hua text is function ko jayega (jaise "kya message bhejun?" ka jawab)
    @Volatile
    var pending: ((String) -> String?)? = null

    // aakhri contact (naam, number) - "use", "usko" ke liye
    @Volatile
    var lastContact: Pair<String, String>? = null

    private val BASE = setOf(
        "jarvis", "जार्विस", "please", "plz", "pls", "karo", "kar", "do", "de", "dena", "दो", "दे", "देना",
        "करो", "कर", "कीजिए", "kijiye", "zara", "jara", "जरा", "mujhe", "मुझे", "ko", "को", "ka", "का", "ki",
        "की", "ke", "के", "me", "में", "pe", "पर", "se", "से", "app", "एप", "ऐप", "application", "bhai", "भाई",
        "yaar", "यार", "boss", "बॉस", "jao", "जाओ", "ja", "जा", "to", "तो", "na", "ना", "hai", "है", "ek", "एक"
    )
    private val OPENW = BASE + setOf(
        "open", "kholo", "khol", "launch", "खोल", "खोलो", "खोलिए", "ओपन", "चालू", "chalu", "start", "शुरू",
        "shuru", "चला", "chala", "चलाओ", "chalao", "on", "ऑन"
    )
    private val CALLW = BASE + setOf(
        "call", "कॉल", "phone", "फोन", "dial", "डायल", "लगाओ", "lagao", "laga", "लगा", "milao", "मिलाओ"
    )
    private val PRON = setOf(
        "use", "उसे", "usko", "उसको", "isko", "इसको", "ise", "इसे", "unko", "उनको", "unhe", "उन्हें",
        "him", "her", "them", "wo", "वो", "uss", "उस", "wahi", "वही"
    )
    private val CHANNEL = setOf(
        "message", "msg", "मैसेज", "मेसेज", "sms", "एसएमएस", "text", "टेक्स्ट", "संदेश", "massage"
    )
    private val ACTION = setOf(
        "bhejo", "bhej", "bhejna", "bhejiye", "भेजो", "भेज", "भेजना", "भेजिए", "send", "likho", "likh",
        "लिखो", "लिख", "kardo", "krdo", "karo", "करो", "bolo", "बोलो", "kaho", "कहो", "dena", "देना"
    )
    private val LINK = setOf("ki", "कि", "ke", "के", "that", "liye", "लिए", "ka", "का")
    private val SKIP = BASE + CHANNEL + ACTION + LINK
    private val OPENISH = setOf(
        "kholo", "खोलो", "open", "ओपन", "chalu", "चालू", "start", "शुरू", "launch", "khol", "खोल", "on", "ऑन"
    )
    private val ACTIONS_ALL = setOf(
        "kholo", "khol", "खोलो", "खोल", "open", "ओपन", "launch", "call", "कॉल", "dial", "डायल", "message",
        "msg", "मैसेज", "मेसेज", "sms", "bhejo", "भेजो", "likho", "लिखो", "play", "चलाओ", "chalao", "बजाओ",
        "search", "सर्च", "lagao", "लगाओ", "torch", "टॉर्च", "volume", "scroll", "स्क्रॉल", "back", "home",
        "होम", "screenshot", "timer", "टाइमर", "alarm", "अलार्म", "chalu", "चालू", "on", "ऑन", "off", "band",
        "बंद", "pause", "next", "navigate", "start", "शुरू", "click", "tap", "type"
    )

    private val strongRe = Regex(
        "\\s+(?:fir se|phir se|fir|phir|uske baad|iske baad|baad me|baad mein|then|and then|after that|" +
            "aur fir|aur phir|और फिर|फिर|उसके बाद|इसके बाद|बाद में|karke|kar ke|करके|कर के)\\s+"
    )
    private val weakRe = Regex("\\s+(?:aur|और|and)\\s+")

    private class L(val keys: List<String>, val code: String, val name: String)

    private val LANGS = listOf(
        L(listOf("english", "इंग्लिश", "अंग्रेजी", "angrezi"), "en-IN", "English"),
        L(listOf("hindi", "हिंदी", "हिन्दी"), "hi-IN", "Hindi"),
        L(listOf("tamil", "तमिल"), "ta-IN", "Tamil"),
        L(listOf("telugu", "तेलुगु", "तेलुगू"), "te-IN", "Telugu"),
        L(listOf("marathi", "मराठी"), "mr-IN", "Marathi"),
        L(listOf("bengali", "bangla", "बंगाली", "बांग्ला"), "bn-IN", "Bengali"),
        L(listOf("gujarati", "गुजराती"), "gu-IN", "Gujarati"),
        L(listOf("punjabi", "पंजाबी"), "pa-IN", "Punjabi"),
        L(listOf("kannada", "कन्नड"), "kn-IN", "Kannada"),
        L(listOf("malayalam", "मलयालम"), "ml-IN", "Malayalam"),
        L(listOf("urdu", "उर्दू"), "ur-IN", "Urdu"),
        L(listOf("spanish", "स्पेनिश"), "es-ES", "Spanish"),
        L(listOf("french", "फ्रेंच"), "fr-FR", "French"),
        L(listOf("german", "जर्मन"), "de-DE", "German"),
        L(listOf("arabic", "अरबी"), "ar-SA", "Arabic"),
        L(listOf("japanese", "जापानी"), "ja-JP", "Japanese"),
        L(listOf("korean", "कोरियन"), "ko-KR", "Korean"),
        L(listOf("chinese", "चीनी"), "zh-CN", "Chinese"),
        L(listOf("russian", "रूसी"), "ru-RU", "Russian")
    )

    private fun has(t: String, vararg w: String) = w.any { t.contains(it) }

    private fun clean(t: String, remove: Set<String>): String =
        t.split(" ").filter { it.isNotBlank() && it !in remove }.joinToString(" ").trim()

    private fun go(c: Context, i: Intent): Boolean {
        return try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            c.startActivity(i)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun nums(t: String): List<Int> {
        val out = ArrayList<Int>()
        var cur = -1
        for (ch in t) {
            if (Character.isDigit(ch)) {
                val d = Character.getNumericValue(ch)
                cur = if (cur < 0) d else cur * 10 + d
            } else if (cur >= 0) {
                out.add(cur)
                cur = -1
            }
        }
        if (cur >= 0) out.add(cur)
        return out
    }

    private fun access(f: (JarvisAccess) -> String): String {
        val a = JarvisAccess.inst ?: return "पहले Accessibility में Jarvis चालू कीजिए बॉस"
        return f(a)
    }

    private fun ask(prompt: String, h: (String) -> String?): String {
        pending = h
        return prompt
    }

    // ---------------- Kai kaam ek saath: "call karo fir message karo" ----------------
    private fun isAction(part: String): Boolean =
        part.split(" ").any { it in ACTIONS_ALL || Hi.skel(it) == "vtsp" }

    private fun mergeSplit(re: Regex, text: String, both: Boolean): List<String> {
        val raw = re.split(text.trim()).map { it.trim() }.filter { it.isNotEmpty() }
        if (raw.size < 2) return listOf(text.trim())
        val out = ArrayList<String>()
        var cur = raw[0]
        for (i in 1 until raw.size) {
            val nxt = raw[i]
            val ok = isAction(nxt) && (!both || isAction(cur))
            if (ok) {
                out.add(cur)
                cur = nxt
            } else {
                cur = cur + " " + nxt
            }
        }
        out.add(cur)
        return out
    }

    fun split(t: String): List<String> {
        val out = ArrayList<String>()
        for (p in mergeSplit(strongRe, t, false)) out.addAll(mergeSplit(weakRe, p, true))
        return out
    }

    // ---------------- Main entry ----------------
    fun run(c: Context, raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty()) return null

        val p = pending
        if (p != null) {
            pending = null
            if (has(t, "cancel", "रहने दो", "rehne do", "chhod do", "छोड दो", "mat bhejo", "मत भेजो"))
                return "ठीक है बॉस, रहने देता हूँ"
            return p(t) ?: "ठीक है बॉस"
        }

        val words = t.split(" ").filter { it.isNotBlank() }
        fun hw(vararg x: String) = x.any { it in words }

        // ---- Bhasha badalna ----
        if (has(t, "me baat", "में बात", "me bolo", "में बोलो", "speak", "language", "bhasha", "भाषा", "me bol", "में बोल")) {
            val l = LANGS.firstOrNull { lg -> lg.keys.any { k -> t.contains(k) } }
            if (l != null) {
                Lang.code = l.code
                Lang.name = l.name
                return if (l.code == "en-IN") "Alright boss, I will talk in English now"
                else "ठीक है बॉस, अब मैं ${l.name} में बात करूँगा"
            }
        }

        // ---- Message bhejna (SMS / WhatsApp) ----
        val msg = messageCmd(c, t, words)
        if (msg != null) return msg

        // ---- Jarvis band karna ----
        if (has(t, "band ho jao", "band ho ja", "बंद हो जाओ", "बंद हो जा", "बंद हो जाइए", "off ho jao",
                "shut down", "shutdown", "so jao", "सो जाओ", "सो जा", "jarvis off", "ऑफ हो जाओ") || t == "exit"
        ) return STOP

        // ---- Scroll / swipe ----
        val scrollWord = has(t, "scroll", "स्क्रॉल", "स्क्रोल", "swipe", "स्वाइप", "सरका") ||
            (has(t, "neeche", "niche", "नीचे", "निचे", "upar", "ऊपर", "उपर") &&
                has(t, "jao", "जाओ", "karo", "करो", "चलो"))
        if (scrollWord) {
            val dir = when {
                has(t, "left", "बाएं", "बायें", "बाईं", "लेफ्ट") -> "left"
                has(t, "right", "दाएं", "दायें", "दाईं", "राइट") -> "right"
                has(t, "up", "upar", "ऊपर", "उपर") || hw("अप") -> "up"
                else -> "down"
            }
            return access { it.scroll(dir) }
        }

        // ---- Back / Home / Recents / Notification / Screenshot / Lock ----
        if (has(t, "peeche jao", "पीछे जाओ", "पीछे जा", "go back", "back jao", "back karo", "बैक", "वापस जाओ", "पीछे चलो") ||
            hw("back", "peeche", "पीछे")
        ) return access { it.global(AccessibilityService.GLOBAL_ACTION_BACK, "पीछे आ गया") }

        if (hw("home", "होम") || has(t, "home screen", "होम स्क्रीन"))
            return access { it.global(AccessibilityService.GLOBAL_ACTION_HOME, "होम पर आ गया") }

        if (has(t, "recent", "रिसेंट", "रीसेंट"))
            return access { it.global(AccessibilityService.GLOBAL_ACTION_RECENTS, "रिसेंट ऐप्स खोल दिए") }

        if (has(t, "notification", "नोटिफिकेशन"))
            return access { it.global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, "नोटिफिकेशन खोल दिए") }

        if (has(t, "screenshot", "स्क्रीनशॉट", "स्क्रीन शॉट")) {
            if (Build.VERSION.SDK_INT < 28) return "ये फोन में स्क्रीनशॉट नहीं हो सकता"
            return access { it.global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, "स्क्रीनशॉट ले लिया") }
        }

        if (has(t, "lock karo", "लॉक करो", "screen lock", "स्क्रीन लॉक", "phone lock", "फोन लॉक")) {
            if (Build.VERSION.SDK_INT < 28) return "ये फोन में लॉक नहीं हो सकता"
            return access { it.global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, "फोन लॉक कर दिया") }
        }

        // ---- Click / Type ----
        val clickW = hw("click", "tap", "press") || has(t, "क्लिक", "टैप", "दबाओ", "दबा दो", "दबाइए")
        if (clickW) {
            val target = clean(t, BASE + setOf("click", "tap", "press", "क्लिक", "टैप", "दबाओ", "दबा", "दबाइए"))
            if (target.isBlank()) return "क्या दबाऊँ बॉस?"
            return access { if (it.clickText(target)) "$target दबा दिया" else "$target स्क्रीन पर नहीं दिखा" }
        }
        val typeKw = listOf("type ", "likho ", "लिखो ", "लिखिए ")
        val kw = typeKw.firstOrNull { t.startsWith(it) }
        if (kw != null) {
            val txt = t.removePrefix(kw).trim()
            return access { if (it.typeText(txt)) "लिख दिया" else "लिखने की जगह नहीं मिली" }
        }

        // ---- Torch ----
        if (has(t, "torch", "flashlight", "टॉर्च", "टोर्च", "टार्च", "फ्लैशलाइट", "फ्लैश लाइट"))
            return torch(c, !has(t, "off", "band", "बंद", "बन्द", "बुझा", "bujha"))

        // ---- Battery ----
        if (has(t, "battery", "बैटरी", "बेटरी")) {
            val bm = c.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            return "बैटरी " + bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) + " प्रतिशत है"
        }

        // ---- Volume ----
        if (has(t, "volume", "वॉल्यूम", "आवाज", "awaaz", "awaz")) {
            val up = has(t, "badha", "बढा", "बढाओ", "bada", "बडा", "zyada", "ज्यादा", "tez", "तेज", "full", "increase") || hw("up")
            val down = has(t, "kam", "कम", "dheema", "धीमा", "ghata", "घटा", "low") || hw("down")
            if (up || down) {
                val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                repeat(3) {
                    am.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_SHOW_UI
                    )
                }
                return if (up) "आवाज़ बढ़ा दी" else "आवाज़ कम कर दी"
            }
        }

        // ---- WiFi / Bluetooth (screen khol deta hai) ----
        if (has(t, "wifi", "वाईफाई", "वाई फाई", "wi-fi", "वाइफाई")) {
            val i = if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            else Intent(Settings.ACTION_WIFI_SETTINGS)
            return if (go(c, i)) "वाईफाई की स्क्रीन खोल दी" else "वाईफाई नहीं खुल पाया"
        }
        if (has(t, "bluetooth", "ब्लूटूथ")) {
            return if (go(c, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))) "ब्लूटूथ की स्क्रीन खोल दी" else "ब्लूटूथ नहीं खुल पाया"
        }

        // ---- Time / Date ----
        if (hw("time", "टाइम", "समय") || has(t, "कितने बजे", "kitne baje", "क्या टाइम"))
            return "अभी " + SimpleDateFormat("h:mm", Locale.getDefault()).format(Date()) + " बजे हैं"
        if (hw("date", "तारीख", "तारिख") || has(t, "कौन सा दिन", "kaun sa din"))
            return "आज " + SimpleDateFormat("d MMMM, EEEE", Locale("hi", "IN")).format(Date()) + " है"

        // ---- Timer / Alarm ----
        if (has(t, "timer", "टाइमर")) {
            val n = nums(t).firstOrNull() ?: return "कितने मिनट का टाइमर लगाऊँ?"
            val secs = when {
                has(t, "second", "सेकंड", "सेकेंड", "sec") -> n
                has(t, "ghanta", "घंटा", "घंटे", "hour") -> n * 3600
                else -> n * 60
            }
            val i = Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, secs)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            return if (go(c, i)) "टाइमर लगा दिया" else "टाइमर नहीं लग पाया"
        }
        if (has(t, "alarm", "अलार्म")) {
            val ns = nums(t)
            if (ns.isEmpty()) return "कितने बजे का अलार्म लगाऊँ?"
            var h = ns[0] % 24
            val mi = if (ns.size > 1) ns[1] % 60 else 0
            if (has(t, "pm", "शाम", "रात", "दोपहर", "raat", "sham", "dopahar") && h < 12) h += 12
            val i = Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, h)
                .putExtra(AlarmClock.EXTRA_MINUTES, mi)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            return if (go(c, i)) "$h बजकर $mi मिनट का अलार्म लगा दिया" else "अलार्म नहीं लग पाया"
        }

        // ---- Music keys ----
        val musicW = has(t, "music", "म्यूजिक", "gaana", "gana", "गाना", "गाने", "song", "सॉन्ग", "सोंग")
        if (musicW || t == "pause" || t == "play" || t == "next" || t == "previous") {
            val code = when {
                has(t, "next", "अगला", "agla") -> KeyEvent.KEYCODE_MEDIA_NEXT
                has(t, "previous", "पिछला", "pichla") -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                has(t, "pause", "roko", "रोको", "stop", "बंद", "band") -> KeyEvent.KEYCODE_MEDIA_PAUSE
                else -> KeyEvent.KEYCODE_MEDIA_PLAY
            }
            val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            return "ठीक है बॉस"
        }

        // ---- YouTube pe chalana ----
        if (has(t, "youtube", "यूट्यूब") && has(t, "chalao", "चलाओ", "play", "बजाओ", "search", "सर्च", "dhundo", "ढूंढो")) {
            val q = clean(t, OPENW + setOf("youtube", "यूट्यूब", "play", "बजाओ", "search", "सर्च", "dhundo", "ढूंढो"))
            if (q.isNotBlank()) return youtube(c, q)
        }

        // ---- Navigation / aas-paas ----
        if (has(t, "navigate", "नेविगेट", "रास्ता", "rasta", "directions", "direction", "route")) {
            val q = clean(t, BASE + setOf("navigate", "नेविगेट", "रास्ता", "rasta", "directions", "direction", "route",
                "batao", "बताओ", "dikhao", "दिखाओ", "tak", "तक", "jana", "जाना", "chalo", "चलो"))
            if (q.isBlank()) return "कहाँ जाना है बॉस?"
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(q)))
                .setPackage("com.google.android.apps.maps")
            if (!go(c, i)) {
                go(c, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(q))))
            }
            return "$q का रास्ता खोल रहा हूँ"
        }
        if (has(t, "near me", "nearby", "पास में", "paas me", "aas paas", "आसपास", "आस पास")) {
            val q = clean(t, BASE + setOf("near", "me", "nearby", "पास", "में", "paas", "aas", "आसपास", "आस", "dhundo",
                "ढूंढो", "dikhao", "दिखाओ", "batao", "बताओ"))
              if (q.isBlank()) return "कहाँ जाना है बॉस?"
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(q)))
                .setPackage("com.google.android.apps.maps")
            if (!go(c, i)) {
                go(c, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(q))))
            }
            return "$q का रास्ता खोल रहा हूँ"
        }
        if (has(t, "near me", "nearby", "पास में", "paas me", "aas paas", "आसपास", "आस पास")) {
            val q = clean(t, BASE + setOf("near", "me", "nearby", "पास", "में", "paas", "aas", "आसपास", "आस", "dhundo",
                "ढूंढो", "dikhao", "दिखाओ", "batao", "बताओ"))
            if (q.isNotBlank()) {
                val i = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q)))
                return if (go(c, i)) "आसपास $q ढूंढ रहा हूँ" else "मैप्स नहीं खुल पाया"
            }
        }

        // ---- Google search ----
        if (has(t, "search", "सर्च", "google", "गूगल", "dhundo", "ढूंढो", "khojo", "खोजो")) {
            val strong = hw("open", "kholo", "खोलो", "खोल", "ओपन")
            if (!strong) {
                val q = clean(t, BASE + setOf("search", "सर्च", "google", "गूगल", "dhundo", "ढूंढो", "khojo", "खोजो"))
                if (q.isBlank()) return "क्या खोजूँ बॉस?"
                val i = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q)
                if (!go(c, i)) {
                    go(c, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))))
                }
                return "$q खोज रहा हूँ"
            }
        }

        // ---- App kholna (pakka hukum) ----
        val strongOpen = hw("open", "kholo", "khol", "launch", "खोल", "खोलो", "खोलिए", "ओपन") || has(t, "खोलो", "खोलिए")
        if (strongOpen) return openApp(c, clean(t, OPENW), true)

        // ---- Call ----
        if (has(t, "call", "कॉल", "dial", "डायल", "phone laga", "फोन लगा", "milao", "मिलाओ") ||
            (has(t, "फोन", "phone") && has(t, "karo", "करो", "lagao", "लगाओ"))
        ) return call(c, clean(t, CALLW))

        // ---- App chalu karo (kamzor hukum, app na mile to AI se baat) ----
        if (hw("chalu", "चालू", "start", "शुरू", "shuru", "chalao", "चलाओ", "chala", "चला", "on", "ऑन")) {
            openApp(c, clean(t, OPENW), false)?.let { return it }
        }

        // ---- Sirf app ka naam bola ----
        if (words.size <= 3) {
            openApp(c, clean(t, OPENW), false)?.let { return it }
        }

        return null
    }

    // ---------------- Message ----------------
    private fun messageCmd(c: Context, t: String, words: List<String>): String? {
        val isWa = words.any { Hi.skel(it) == "vtsp" } || Hi.skel(t.replace(" ", "")).contains("vtsp")
        val hasChannel = isWa || words.any { it in CHANNEL }
        if (!hasChannel) return null
        if (words.none { it in ACTION }) return null
        if (words.take(3).any { it in OPENISH }) return null

        val kIdx = words.indexOfFirst { it == "ko" || it == "को" }
        val nameTokens: List<String>
        val usePron: Boolean
        val bodyTokens: List<String>
        if (kIdx >= 0) {
            val before = words.subList(0, kIdx)
            nameTokens = before.filter { it !in SKIP && it !in PRON && Hi.skel(it) != "vtsp" }
            usePron = before.any { it in PRON }
            bodyTokens = words.subList(kIdx + 1, words.size)
        } else {
            val lead = words.takeWhile { it !in CHANNEL && it !in ACTION && Hi.skel(it) != "vtsp" }
            nameTokens = lead.filter { it !in SKIP && it !in PRON }
            usePron = lead.any { it in PRON }
            bodyTokens = words.subList(lead.size, words.size)
        }

        var s = 0
        while (s < bodyTokens.size &&
            (bodyTokens[s] in SKIP || bodyTokens[s] in PRON || Hi.skel(bodyTokens[s]) == "vtsp")
        ) s++
        val body = bodyTokens.subList(s, bodyTokens.size).joinToString(" ").trim()
        val name = nameTokens.joinToString(" ").trim()

        val target: Pair<String, String>? =
            if (name.isNotBlank()) findContact(c, name) else lastContact
        if (name.isNotBlank() && target == null) return "$name नाम का कॉन्टैक्ट नहीं मिला"
        if (target == null) {
            return ask("किसे मैसेज भेजूँ बॉस?") { n ->
                val ct = findContact(c, clean(n, BASE))
                if (ct == null) "वो कॉन्टैक्ट नहीं मिला बॉस" else deliver(c, ct, body, isWa)
            }
        }
        if (usePron && lastContact == null) return "किसे? पहले नाम बताइए बॉस"
        return deliver(c, target, body, isWa)
    }

    private fun deliver(c: Context, ct: Pair<String, String>, body: String, wa: Boolean): String {
        lastContact = ct
        if (body.isBlank()) {
            return ask("${ct.first} को क्या मैसेज भेजूँ बॉस?") { b -> deliverNow(c, ct, b, wa) }
        }
        return deliverNow(c, ct, body, wa)
    }

    private fun deliverNow(c: Context, ct: Pair<String, String>, body: String, wa: Boolean): String =
        if (wa) sendWa(c, ct.first, ct.second, body) else sendSms(c, ct.second, ct.first, body)

    private fun sendSms(c: Context, num: String, name: String, body: String): String {
        if (c.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
            return "SMS की अनुमति दीजिए बॉस"
        return try {
            val sm = if (Build.VERSION.SDK_INT >= 31) c.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
            val parts = sm.divideMessage(body)
            sm.sendMultipartTextMessage(num, null, parts, null, null)
            "$name को मैसेज भेज दिया"
        } catch (e: Exception) {
            "मैसेज नहीं जा पाया"
        }
    }

    private fun waNumber(n: String): String {
        var d = n.filter { it.isDigit() }
        if (d.startsWith("00")) d = d.substring(2)
        else if (d.startsWith("0")) d = d.substring(1)
        if (d.length == 10) d = "91$d"
        return d
    }

    private fun sendWa(c: Context, name: String, num: String, body: String): String {
        val uri = Uri.parse("https://wa.me/" + waNumber(num) + "?text=" + Uri.encode(body))
        val i = Intent(Intent.ACTION_VIEW, uri)
        i.setPackage("com.whatsapp")
        if (!go(c, i)) {
            i.setPackage("com.whatsapp.w4b")
            if (!go(c, i)) return "WhatsApp नहीं मिला बॉस"
        }
        if (JarvisAccess.inst == null) return "WhatsApp खुल गया, भेजना आपको दबाना होगा क्योंकि Accessibility चालू नहीं है"
        val h = Handler(Looper.getMainLooper())
        val labels = listOf("Send", "भेजें", "भेजना")
        h.postDelayed({ JarvisAccess.inst?.clickAny(labels) }, 3500L)
        h.postDelayed({ JarvisAccess.inst?.clickAny(labels) }, 6000L)
        return "$name को WhatsApp भेज रहा हूँ"
    }

    // ---------------- Contacts / Call ----------------
    private fun findContact(c: Context, q: String): Pair<String, String>? {
        val qq = q.trim()
        if (qq.isEmpty()) return null
        if (qq in PRON) return lastContact
        val digits = qq.filter { it.isDigit() }.map { Character.getNumericValue(it) }.joinToString("")
        if (digits.length >= 6) return Pair(digits, digits)
        if (c.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val colName = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        val colNum = ContactsContract.CommonDataKinds.Phone.NUMBER
        var bestName = ""
        var bestNum = ""
        var bestScore = 0
        val cur = c.contentResolver.query(uri, arrayOf(colName, colNum), null, null, null)
        cur?.use {
            while (it.moveToNext()) {
                val n = it.getString(0) ?: continue
                val s = Hi.score(qq, n)
                if (s > bestScore) {
                    bestScore = s
                    bestName = n
                    bestNum = it.getString(1) ?: ""
                }
            }
        }
        if (bestScore == 0 || bestNum.isBlank()) return null
        val r = Pair(bestName, bestNum.replace(" ", ""))
        lastContact = r
        return r
    }

    private fun dial(c: Context, num: String, label: String): String {
        val uri = Uri.fromParts("tel", num, null)
        return try {
            val tm = c.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.placeCall(uri, Bundle())
            "$label को कॉल लगा रहा हूँ"
        } catch (e: Exception) {
            if (go(c, Intent(Intent.ACTION_CALL, uri))) "$label को कॉल लगा रहा हूँ" else "कॉल नहीं लग पाई"
        }
    }

    private fun call(c: Context, q: String): String {
        if (c.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED)
            return "कॉल की अनुमति दीजिए बॉस"
        if (q.isBlank()) {
            return ask("किसे कॉल करना है बॉस?") { n -> call(c, clean(n, CALLW)) }
        }
        val ct = findContact(c, q)
        if (ct == null) {
            return if (q in PRON) "किसे? पहले नाम बताइए बॉस" else "$q नाम का कॉन्टैक्ट नहीं मिला"
        }
        return dial(c, ct.second, ct.first)
    }

    // ---------------- Baaki ----------------
    private fun torch(c: Context, on: Boolean): String = try {
        val cm = c.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.first { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        cm.setTorchMode(id, on)
        if (on) "टॉर्च चालू कर दी" else "टॉर्च बंद कर दी"
    } catch (e: Exception) {
        "टॉर्च नहीं चल पाई"
    }

    private fun youtube(c: Context, q: String): String {
        val i = Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube").putExtra("query", q)
        if (go(c, i)) return "यूट्यूब पर $q चला रहा हूँ"
        val v = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q)))
        return if (go(c, v)) "यूट्यूब पर $q खोज रहा हूँ" else "यूट्यूब नहीं खुल पाया"
    }

    private fun openApp(c: Context, q: String, strict: Boolean): String? {
        if (q.isBlank()) return if (strict) "कौन सा ऐप खोलूँ बॉस?" else null
        val pm = c.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        var bestPkg: String? = null
        var bestLabel = ""
        var bestScore = 0
        for (ri in pm.queryIntentActivities(intent, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == c.packageName) continue
            val label = ri.loadLabel(pm).toString()
            val s = Hi.score(q, label)
            if (s > bestScore) {
                bestScore = s
                bestPkg = pkg
                bestLabel = label
            }
        }
        val need = if (strict) 2 else 3
        val pkg = bestPkg
        if (pkg == null || bestScore < need) return if (strict) "$q ऐप नहीं मिला" else null
        val li = pm.getLaunchIntentForPackage(pkg) ?: return if (strict) "ऐप नहीं खुल पाया" else null
        return if (go(c, li)) "$bestLabel खोल रहा हूँ" else "ऐप नहीं खुल पाया"
    }
}          

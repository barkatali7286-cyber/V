package com.jarvis.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.telecom.TelecomManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =====================================================================
//  FUZZY milan (thodi galti wale shabd bhi pakad le)
// =====================================================================
object Fuzz {
    fun dist(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + cost)
                prev = tmp
            }
        }
        return dp[b.length]
    }

    fun close(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length < 3 || b.length < 3) return false
        return dist(a, b) <= 1
    }
}

// =====================================================================
//  NUMBER: Hindi/English bole hue number ("नौ आठ सात...") ko digits me badalna
// =====================================================================
object Num {
    private val MAP = mapOf(
        "zero" to 0, "jiro" to 0, "जीरो" to 0, "शून्य" to 0, "सुन्ना" to 0,
        "ek" to 1, "एक" to 1, "one" to 1, "वन" to 1,
        "do" to 2, "दो" to 2, "two" to 2, "टू" to 2,
        "teen" to 3, "तीन" to 3, "three" to 3, "थ्री" to 3,
        "char" to 4, "chaar" to 4, "चार" to 4, "four" to 4, "फोर" to 4,
        "paanch" to 5, "panch" to 5, "पांच" to 5, "पाँच" to 5, "पाच" to 5, "five" to 5, "फाइव" to 5,
        "chhe" to 6, "chheh" to 6, "cheh" to 6, "छह" to 6, "छः" to 6, "छे" to 6, "छ" to 6, "six" to 6, "सिक्स" to 6,
        "saat" to 7, "sat" to 7, "सात" to 7, "seven" to 7, "सेवन" to 7,
        "aath" to 8, "aat" to 8, "आठ" to 8, "eight" to 8, "एट" to 8,
        "nau" to 9, "नौ" to 9, "नव" to 9, "nine" to 9, "नाइन" to 9
    )

    private fun digitOf(tok: String): String? {
        val d = MAP[tok]
        if (d != null) return d.toString()
        if (tok.isNotEmpty() && tok.all { Character.isDigit(it) }) {
            return tok.map { Character.getNumericValue(it) }.joinToString("")
        }
        return null
    }

    // "नौ आठ सात डबल पांच" -> "987 55"
    fun digits(t: String): String {
        val sb = StringBuilder()
        var rep = 1
        for (raw in t.split(" ")) {
            if (raw.isBlank()) continue
            val tok = raw.trim(',', '.', '।')
            if (tok == "double" || tok == "डबल") {
                rep = 2
                continue
            }
            if (tok == "triple" || tok == "ट्रिपल") {
                rep = 3
                continue
            }
            val d = digitOf(tok)
            if (d != null) {
                repeat(rep) { sb.append(d) }
                rep = 1
            }
        }
        return sb.toString()
    }

    // agar bahut saare number-shabd hain (6+) to unhe digits me badal do, warna jaisa hai waisa
    fun toDigitsText(t: String): String {
        var count = 0
        for (raw in t.split(" ")) {
            if (raw.isBlank()) continue
            if (digitOf(raw.trim(',', '.', '।')) != null) count++
        }
        return if (count >= 6) digits(t) else t
    }
}

// =====================================================================
//  SEARCH: kuch bhi poochho, tez jawab (Wikipedia, phir AI, phir Google)
// =====================================================================
object Search {
    private val EXPLICIT = listOf(
        "search", "सर्च", "khojo", "खोजो", "dhundo", "ढूंढो", "jaankari", "जानकारी", "ke baare", "के बारे",
        "meaning", "मतलब"
    )
    private val QUESTION = listOf(
        "kya hai", "क्या है", "kaun hai", "कौन है", "kaun tha", "कौन था", "kaun thi", "कौन थी", "kya hota hai",
        "क्या होता है", "who is", "what is", "kahan hai", "कहाँ है", "kab hua", "कब हुआ", "kisne", "किसने",
        "kaise bana", "कैसे बना", "kya hoti hai", "क्या होती है", "tell me about"
    )
    private val BLOCK = listOf(
        "kholo", "खोलो", "call", "कॉल", "message", "मैसेज", "chalao", "चलाओ", "torch", "टॉर्च", "volume",
        "वॉल्यूम", "alarm", "अलार्म", "timer", "टाइमर", "scroll", "स्क्रॉल", "youtube", "यूट्यूब", "whatsapp",
        "google", "गूगल", "navigate", "रास्ता", "near me", "पास में", "battery", "बैटरी", "charge", "चार्ज",
        "time", "टाइम", "समय", "date", "तारीख", "screen", "स्क्रीन", "yaad dila", "याद दिला", "joke", "जोक",
        "shayari", "शायरी"
    )
    private val STOP = setOf(
        "search", "सर्च", "karo", "करो", "kar", "batao", "बताओ", "bata", "बता", "do", "दो", "kya", "क्या", "hai",
        "है", "kaun", "कौन", "tha", "था", "thi", "थी", "ke", "के", "baare", "बारे", "me", "में", "jaankari",
        "जानकारी", "who", "is", "what", "tell", "about", "mujhe", "मुझे", "ka", "का", "ki", "की", "ko", "को",
        "dhundo", "ढूंढो", "khojo", "खोजो", "meaning", "मतलब", "hota", "होता", "hoti", "होती", "kab", "कब",
        "kahan", "कहाँ", "kisne", "किसने", "hua", "हुआ", "please", "jarvis", "boss", "बॉस", "ek", "एक", "ye",
        "ये", "wo", "वो", "bhai", "भाई"
    )

    // (sawal ka vishay, kya "search" shabd khud bola gaya)
    fun parse(t: String): Pair<String, Boolean>? {
        if (BLOCK.any { t.contains(it) }) return null
        val explicit = EXPLICIT.any { t.contains(it) }
        val question = QUESTION.any { t.contains(it) }
        if (!explicit && !question) return null
        val q = t.split(" ").filter { it.isNotBlank() && it !in STOP }.joinToString(" ").trim()
        if (q.length < 2) return null
        return Pair(q, explicit)
    }

    // internet se jawab laata hai (background thread me chalana)
    fun answer(q: String, long: Boolean): String? {
        val dev = q.any { it in '\u0900'..'\u097F' }
        val langs = if (dev) listOf("hi", "en") else listOf("en", "hi")
        for (lg in langs) {
            val r = wiki(lg, q, long)
            if (r != null) return r
        }
        return null
    }

    private fun get(url: String): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "JarvisApp/1.0 (personal assistant)")
            conn.connectTimeout = 4000
            conn.readTimeout = 5000
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }

    private fun wiki(lg: String, q: String, long: Boolean): String? {
        val s = get(
            "https://$lg.wikipedia.org/w/api.php?action=query&list=search&srsearch=" +
                Uri.encode(q) + "&srlimit=1&format=json"
        ) ?: return null
        try {
            val arr = JSONObject(s).getJSONObject("query").getJSONArray("search")
            if (arr.length() == 0) return null
            val title = arr.getJSONObject(0).getString("title")
            val sum = get(
                "https://$lg.wikipedia.org/api/rest_v1/page/summary/" + Uri.encode(title.replace(' ', '_'))
            ) ?: return null
            val ex = JSONObject(sum).optString("extract", "")
            if (ex.isBlank()) return null
            return shorten(ex, if (long) 520 else 240, if (long) 320 else 120)
        } catch (e: Exception) {
            return null
        }
    }

    // pehle 1-2 vaakya (zyada lamba nahi)
    private fun shorten(s: String, limit: Int, enough: Int): String {
        val clean = s.replace(Regex("\\([^)]*\\)"), "").replace("\n", " ").trim()
        val parts = clean.split(Regex("(?<=[।.!?])\\s+"))
        val sb = StringBuilder()
        for (p in parts) {
            if (sb.isNotEmpty() && sb.length + p.length > limit) break
            sb.append(p).append(' ')
            if (sb.length > enough) break
        }
        return sb.toString().trim()
    }
}

// =====================================================================
//  EXTRA COMMANDS: battery/charge, volume, number se call, screen padhna,
//  video chalana, ringer mode, reminder, kai kaam ek saath
//  (shabd ki awaaz se milan, isliye Hindi/English kisi bhi tarah likha ho chalega)
// =====================================================================
object Extra {
    private fun has(t: String, vararg w: String) = w.any { t.contains(it) }

    // shabd ki "awaaz" (skeleton) se milan: "volume" == "वॉल्यूम" == "वाल्यूम"
    private fun kw(toks: List<String>, vararg keys: String): Boolean {
        for (k in keys) {
            val ks = Hi.skel(k)
            if (ks.length < 2) continue
            for (tk in toks) {
                if (tk == ks || Fuzz.close(tk, ks)) return true
            }
        }
        return false
    }

    private fun hit(t: String, toks: List<String>, vararg keys: String): Boolean =
        has(t, *keys) || kw(toks, *keys)

    private val ORD = mapOf(
        "pehla" to 1, "pehle" to 1, "पहला" to 1, "पहले" to 1, "पहली" to 1, "first" to 1, "1st" to 1, "पेहला" to 1,
        "dusra" to 2, "doosra" to 2, "दूसरा" to 2, "दुसरा" to 2, "दूसरे" to 2, "second" to 2, "2nd" to 2,
        "teesra" to 3, "tisra" to 3, "तीसरा" to 3, "तिसरा" to 3, "तीसरे" to 3, "third" to 3, "3rd" to 3,
        "chautha" to 4, "चौथा" to 4, "fourth" to 4, "4th" to 4,
        "paanchva" to 5, "पांचवा" to 5, "पाँचवा" to 5, "पाचवा" to 5, "fifth" to 5, "5th" to 5
    )

    private val FILL = setOf(
        "video", "वीडियो", "वीडिओ", "reel", "रील", "screen", "स्क्रीन", "pe", "पर", "jo", "जो", "wala", "वाला",
        "wali", "वाली", "hai", "है", "chalao", "चलाओ", "play", "lagao", "लगाओ", "kholo", "खोलो", "dikhao",
        "दिखाओ", "chala", "चला", "start", "शुरू", "karo", "करो", "kar", "do", "दो", "ko", "को", "ka", "का",
        "ki", "की", "ke", "के", "mujhe", "मुझे", "please", "jarvis", "boss", "बॉस", "bhai", "भाई", "ek", "एक",
        "wo", "वो", "ye", "ये", "is", "इस", "isko", "इसको", "ise", "इसे", "me", "में", "se", "से"
    )
    private val DROP_SK = setOf("vd", "kl", "pl", "lg", "dk", "strt", "rl")

    private val MINS = mapOf(
        "ek" to 1, "एक" to 1, "do" to 2, "दो" to 2, "teen" to 3, "तीन" to 3, "char" to 4, "चार" to 4,
        "paanch" to 5, "पांच" to 5, "पाँच" to 5, "chhe" to 6, "छह" to 6, "saat" to 7, "सात" to 7,
        "aath" to 8, "आठ" to 8, "nau" to 9, "नौ" to 9, "das" to 10, "दस" to 10, "pandrah" to 15,
        "पंद्रह" to 15, "bees" to 20, "बीस" to 20, "tees" to 30, "तीस" to 30, "chalis" to 40, "चालीस" to 40,
        "pachas" to 50, "पचास" to 50, "saath" to 60, "साठ" to 60
    )
    private val UNITS = setOf("minute", "मिनट", "min", "ghante", "घंटे", "ghanta", "घंटा", "hour")

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

    private fun pickOrdinal(ans: String): String {
        val ws = ans.trim().split(" ").filter { it.isNotBlank() }
        var ord = 0
        for (w in ws) {
            val o = ORD[w]
            if (o != null) {
                ord = o
                break
            }
        }
        if (ord == 0) ord = nums(ans).firstOrNull() ?: 0
        if (ord <= 0) return "ठीक है बॉस, रहने देता हूँ"
        val n = ord
        return access { it.clickNth(n) }
    }

    private fun dialNumber(c: Context, d: String): String {
        if (c.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED)
            return "कॉल की अनुमति दीजिए बॉस"
        val uri = Uri.fromParts("tel", d, null)
        val spoken = d.toCharArray().joinToString(" ")
        return try {
            val tm = c.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.placeCall(uri, Bundle())
            "$spoken पर कॉल लगा रहा हूँ"
        } catch (e: Exception) {
            try {
                val i = Intent(Intent.ACTION_CALL, uri)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                c.startActivity(i)
                "$spoken पर कॉल लगा रहा हूँ"
            } catch (e2: Exception) {
                "कॉल नहीं लग पाई"
            }
        }
    }

    // "10 minute baad paani peene ko yaad dilana" -> (10, "paani peene")
    fun parseReminder(t: String): Pair<Int, String>? {
        if (!has(t, "yaad dila", "याद दिला", "remind", "रिमाइंड", "yad dila")) return null
        val toks = t.split(" ").filter { it.isNotBlank() }
        var n = nums(t).firstOrNull() ?: -1
        if (n < 0) {
            for (i in 1 until toks.size) {
                if (toks[i] in UNITS) {
                    val m = MINS[toks[i - 1]]
                    if (m != null) {
                        n = m
                        break
                    }
                }
            }
        }
        if (n < 1) return null
        val mins = if (has(t, "ghant", "घंट", "hour")) n * 60 else n
        if (mins > 1440) return null
        val drop = setOf(
            "minute", "minutes", "मिनट", "min", "baad", "बाद", "mujhe", "मुझे", "yaad", "याद", "dilana", "दिलाना",
            "dila", "दिला", "dilao", "दिलाओ", "dilado", "remind", "me", "रिमाइंड", "karna", "करना", "ko", "को",
            "ghante", "घंटे", "hour", "ghanta", "घंटा", "ki", "की", "pe", "में", "jarvis", "please"
        )
        val text = toks.filter { it !in drop && !it.all { ch -> Character.isDigit(ch) } && MINS[it] == null }
            .joinToString(" ").trim()
        return Pair(mins, if (text.isBlank()) "समय हो गया" else text)
    }

    // chhota calculator: "5 plus 3 kitna hota hai"
    private fun calc(t: String): String? {
        if (!has(t, "kitna hota", "कितना होता", "kitne hote", "कितने होते", "calculate", "कैलकुलेट", "barabar", "बराबर"))
            return null
        val ops = mapOf(
            "plus" to '+', "जमा" to '+', "जोड" to '+', "add" to '+', "+" to '+',
            "minus" to '-', "माइनस" to '-', "घटा" to '-', "-" to '-',
            "into" to '*', "इनटू" to '*', "गुणा" to '*', "times" to '*', "x" to '*', "*" to '*',
            "divided" to '/', "भाग" to '/', "by" to '/', "/" to '/'
        )
        val nums = ArrayList<Double>()
        val opl = ArrayList<Char>()
        var expectNum = true
        for (tk in t.split(" ")) {
            if (tk.isBlank()) continue
            val num = tk.toDoubleOrNull() ?: Num.digits(tk).let { if (it.length == 1) it.toDouble() else null }
            val op = ops[tk]
            if (expectNum && num != null) {
                nums.add(num)
                expectNum = false
            } else if (!expectNum && op != null) {
                opl.add(op)
                expectNum = true
            }
        }
        if (nums.size < 2 || opl.size != nums.size - 1) return null
        // pehle * aur /, phir + aur -
        val n2 = ArrayList<Double>()
        val o2 = ArrayList<Char>()
        n2.add(nums[0])
        for (i in opl.indices) {
            val o = opl[i]
            val b = nums[i + 1]
            if (o == '*') {
                n2[n2.size - 1] = n2[n2.size - 1] * b
            } else if (o == '/') {
                if (b == 0.0) return "शून्य से भाग नहीं हो सकता बॉस"
                n2[n2.size - 1] = n2[n2.size - 1] / b
            } else {
                o2.add(o)
                n2.add(b)
            }
        }
        var res = n2[0]
        for (i in o2.indices) {
            res = if (o2[i] == '+') res + n2[i + 1] else res - n2[i + 1]
        }
        val shown = if (res == Math.floor(res) && Math.abs(res) < 1e12) res.toLong().toString()
        else String.format(Locale.US, "%.2f", res)
        return "जवाब है $shown"
    }

    // ---------------- Kai kaam ek saath ----------------
    private val strongRe = Regex(
        "\\s+(?:fir se|phir se|fir|phir|uske baad|iske baad|baad me|baad mein|then|and then|after that|" +
            "aur fir|aur phir|और फिर|फिर|उसके बाद|इसके बाद|बाद में|karke|kar ke|करके|कर के)\\s+"
    )
    private val weakRe = Regex("\\s+(?:aur|और|and)\\s+")

    private val ACT = setOf(
        "kholo", "khol", "खोलो", "खोल", "open", "ओपन", "launch", "call", "कॉल", "dial", "डायल", "message", "msg",
        "मैसेज", "मेसेज", "sms", "bhejo", "भेजो", "likho", "लिखो", "play", "प्ले", "chalao", "चलाओ", "बजाओ",
        "search", "सर्च", "lagao", "लगाओ", "torch", "टॉर्च", "volume", "वॉल्यूम", "scroll", "स्क्रॉल", "back",
        "home", "होम", "screenshot", "timer", "टाइमर", "alarm", "अलार्म", "chalu", "चालू", "on", "ऑन", "off",
        "band", "बंद", "pause", "next", "navigate", "start", "शुरू", "click", "tap", "type", "dikhao", "दिखाओ",
        "battery", "बैटरी", "charge", "चार्ज", "joke", "जोक", "shayari", "शायरी", "batao", "बताओ"
    )
    private val ACT_SK = setOf("kl", "strt", "pl", "lg", "dk", "vlm", "btr", "vd", "vtsp")

    private var appSk: Set<String>? = null

    private fun appSkeletons(c: Context): Set<String> {
        val cur = appSk
        if (cur != null) return cur
        val out = HashSet<String>()
        try {
            val pm = c.packageManager
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            for (ri in pm.queryIntentActivities(i, 0)) {
                val sk = Hi.skel(ri.loadLabel(pm).toString())
                if (sk.length >= 2) out.add(sk)
            }
        } catch (e: Exception) {
        }
        appSk = out
        return out
    }

    private fun isAct(c: Context, part: String): Boolean {
        val toks = part.split(" ").filter { it.isNotBlank() }
        val apps = appSkeletons(c)
        for (tk in toks) {
            if (tk in ACT) return true
            val sk = Hi.skel(tk)
            if (sk.length >= 2 && (sk in ACT_SK || sk in apps)) return true
        }
        return false
    }

    private fun mergeSplit(c: Context, re: Regex, text: String, both: Boolean): List<String> {
        val raw = re.split(text.trim()).map { it.trim() }.filter { it.isNotEmpty() }
        if (raw.size < 2) return listOf(text.trim())
        val out = ArrayList<String>()
        var cur = raw[0]
        for (i in 1 until raw.size) {
            val nxt = raw[i]
            val ok = isAct(c, nxt) && (!both || isAct(c, cur))
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

    fun split(c: Context, t: String): List<String> {
        val out = ArrayList<String>()
        for (p in mergeSplit(c, strongRe, t, false)) out.addAll(mergeSplit(c, weakRe, p, true))
        return out
    }

    // ---------------- Main entry ----------------
    fun run(c: Context, raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        if (has(t, "message", "msg", "मैसेज", "मेसेज", "sms")) return null
        val words = t.split(" ").filter { it.isNotBlank() }
        val toks = words.map { Hi.skel(it) }

        // ---------------- Bole hue number par call ----------------
        if (hit(t, toks, "call", "कॉल", "dial", "डायल", "phone", "फोन", "lagao", "लगाओ")) {
            val d = Num.digits(t)
            if (d.length >= 8) return dialNumber(c, d)
        }

        // ---------------- App ke andar search ----------------
        val inApp = Apps.run(c, t)
        if (inApp != null) return inApp

        // ---------------- Notification padhna / briefing / calculator ----------------
        if (has(t, "notification", "नोटिफिकेशन") &&
            has(t, "padho", "पढो", "batao", "बताओ", "kya aaya", "क्या आया", "koi", "कोई", "dikhao", "दिखाओ")) {
            return JarvisNotif.summary(3)
        }
        if (has(t, "briefing", "ब्रीफिंग", "aaj ka plan", "आज का प्लान", "status batao", "स्टेटस बताओ")) {
            val bm0 = c.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val pct0 = bm0.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val time = SimpleDateFormat("h:mm", Locale.getDefault()).format(Date())
            val date = SimpleDateFormat("d MMMM, EEEE", Locale("hi", "IN")).format(Date())
            return "बॉस, अभी $time बजे हैं, आज $date है. बैटरी $pct0 प्रतिशत है. " + JarvisNotif.summary(2)
        }
        val cal = calc(t)
        if (cal != null) return cal

        // ---------------- Battery / Charge ----------------
        if (hit(t, toks, "battery", "बैटरी", "बेटरी", "charge", "चार्ज", "charging", "चार्जिंग")) {
            val bm = c.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val st = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = st?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            return "बैटरी $pct प्रतिशत है" + (if (charging) ", और फोन चार्ज हो रहा है" else ", चार्जर नहीं लगा है")
        }

        // ---------------- Screen pe kya hai (jaanch) ----------------
        if (has(t, "screen par kya", "screen pe kya", "स्क्रीन पर क्या", "स्क्रीन पे क्या", "screen padho",
                "स्क्रीन पढो", "kya dikh raha", "क्या दिख रहा")) {
            return access { it.readScreen(6) }
        }

        // ---------------- Silent / Vibrate / Normal mode ----------------
        if (has(t, "mode", "मोड") && has(t, "silent", "साइलेंट", "vibrate", "वाइब्रेट", "normal", "नॉर्मल")) {
            val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            return try {
                if (has(t, "silent", "साइलेंट")) {
                    am.ringerMode = AudioManager.RINGER_MODE_SILENT
                    "फोन साइलेंट कर दिया"
                } else if (has(t, "vibrate", "वाइब्रेट")) {
                    am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    "फोन वाइब्रेट पर कर दिया"
                } else {
                    am.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    "फोन नॉर्मल मोड पर कर दिया"
                }
            } catch (e: Exception) {
                "ये करने के लिए Do Not Disturb की अनुमति चाहिए बॉस"
            }
        }

        // ---------------- Volume ----------------
        if (hit(t, toks, "volume", "वॉल्यूम", "वाल्यूम", "sound", "साउंड", "awaaz", "आवाज")) {
            val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val step = maxOf(1, max / 5)
            val ns = nums(t)
            val up = hit(t, toks, "badhao", "बढाओ", "badha", "बढा", "zyada", "ज्यादा", "tez", "तेज", "increase") ||
                "up" in words
            val down = hit(t, toks, "kam", "कम", "dheema", "धीमा", "ghata", "घटा", "घटाओ") || "down" in words
            val target: Int? = when {
                has(t, "full", "फुल", "max", "मैक्स", "maximum", "poori", "पूरी") -> max
                has(t, "mute", "म्यूट", "silent", "साइलेंट", "zero", "शून्य", "band", "बंद") -> 0
                ns.isNotEmpty() && ns[0] in 0..100 -> ns[0] * max / 100
                up -> minOf(max, cur + step)
                down -> maxOf(0, cur - step)
                else -> null
            }
            if (target != null) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
                val pct = if (max > 0) target * 100 / max else 0
                return "आवाज़ $pct प्रतिशत कर दी"
            }
        }

        // ---------------- Screen pe dikh rahe video/item chalana ----------------
        val videoW = hit(t, toks, "video", "वीडियो", "वीडिओ", "reel", "रील") || has(t, "screen", "स्क्रीन")
        val playW = hit(t, toks, "chalao", "चलाओ", "play", "प्ले", "lagao", "लगाओ", "dikhao", "दिखाओ",
            "kholo", "खोलो", "start", "शुरू")
        if (videoW && playW && !has(t, "youtube", "यूट्यूब", "screenshot", "स्क्रीनशॉट")) {
            var ord = 0
            for (w in words) {
                val o = ORD[w]
                if (o != null) {
                    ord = o
                    break
                }
            }
            if (ord == 0 && has(t, "upar wala", "ऊपर वाला", "top")) ord = 1
            if (ord == 0 && has(t, "number", "नंबर")) ord = nums(t).firstOrNull() ?: 0
            if (ord > 0) {
                val n = ord
                return access { it.clickNth(n) }
            }

            val q = words.filter { it !in FILL && Hi.skel(it) !in DROP_SK }.joinToString(" ").trim()
            if (q.isBlank()) return "कौन सा वीडियो चलाऊँ बॉस? पहला, दूसरा या नाम बोलिए"
            return access { a ->
                val found = a.clickBest(q)
                if (found != null) {
                    "$found चला दिया"
                } else {
                    Commands.pending = { ans -> pickOrdinal(ans) }
                    "वो नाम स्क्रीन पर नहीं मिला बॉस. पहला, दूसरा या तीसरा, कौन सा चलाऊँ?"
                }
            }
        }

        return null
    }
}

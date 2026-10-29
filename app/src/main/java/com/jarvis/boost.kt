package com.jarvis.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

// =====================================================================
//  BOOST: (1) Jarvis jaisi gehri mard ki awaaz  (2) app ke andar search
//  (3) tez jawab (Wikipedia seedha)  (4) English me Jarvis style bolna
// =====================================================================
object Boost {
    // awaaz badli hai, TTS ko dobara set karna hai
    @Volatile
    var voiceDirty: Boolean = false

    private fun has(t: String, vararg w: String) = w.any { t.contains(it) }
    private fun prefs(c: Context) = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    // ================= AWAAZ =================
    fun pitchBase(c: Context): Float = prefs(c).getFloat("pitch_base", 0.7f)
    fun rateBase(c: Context): Float = prefs(c).getFloat("rate_base", 0.92f)

    fun adjustPitch(c: Context, delta: Float): Float {
        val n = (pitchBase(c) + delta).coerceIn(0.4f, 1.3f)
        prefs(c).edit().putFloat("pitch_base", n).apply()
        return n
    }

    fun adjustRate(c: Context, delta: Float): Float {
        val n = (rateBase(c) + delta).coerceIn(0.6f, 1.4f)
        prefs(c).edit().putFloat("rate_base", n).apply()
        return n
    }

    // mard ki awaaz pehle (andaaza), phir baaki
    private fun maleScore(name: String): Int {
        val n = name.lowercase()
        if (n.contains("female")) return 0
        if (n.contains("male")) return 3
        val male = listOf("-gbb-", "-gbd-", "-rjs-", "-iom-", "-tpd-", "-end-", "-hid-", "-ene-")
        return if (male.any { n.contains(it) }) 2 else 1
    }

    fun voicesFor(tts: TextToSpeech, tag: String): List<Voice> {
        val want = Locale.forLanguageTag(tag)
        val all: List<Voice> = try {
            tts.voices?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val usable = all.filter { v ->
            v.locale.language == want.language && (v.features?.contains("notInstalled") != true)
        }
        val exact = usable.filter { it.locale.country == want.country }
        val pool = if (exact.isNotEmpty()) exact else usable
        return pool.sortedWith(
            compareBy<Voice>({ -maleScore(it.name) }, { if (it.isNetworkConnectionRequired) 1 else 0 }, { it.name })
        )
    }

    fun applyVoice(c: Context, tts: TextToSpeech, tag: String) {
        tts.setLanguage(Locale.forLanguageTag(tag))
        val list = voicesFor(tts, tag)
        if (list.isEmpty()) return
        val idx = prefs(c).getInt("voice_idx", 0)
        tts.setVoice(list[Math.floorMod(idx, list.size)])
    }

    fun nextVoice(c: Context): String {
        val p = prefs(c)
        val n = p.getInt("voice_idx", 0) + 1
        p.edit().putInt("voice_idx", n).apply()
        voiceDirty = true
        return "आवाज़ नंबर " + (n + 1)
    }

    private fun voiceCmd(c: Context, t: String): String? {
        if (has(t, "awaaz badlo", "आवाज बदलो", "awaz badlo", "voice badlo", "voice change", "अगली आवाज",
                "dusri awaaz", "दूसरी आवाज", "awaaz change", "आवाज चेंज")) {
            return nextVoice(c) + ", बॉस ये नई आवाज़ कैसी लगी?"
        }
        if (has(t, "awaaz gehri", "आवाज गहरी", "gehri awaaz", "गहरी आवाज", "bhari awaaz", "भारी आवाज",
                "आवाज भारी", "deep voice", "आवाज और गहरी")) {
            adjustPitch(c, -0.08f)
            voiceDirty = true
            return "आवाज़ और गहरी कर दी बॉस"
        }
        if (has(t, "awaaz patli", "आवाज पतली", "patli awaaz", "पतली आवाज", "halki awaaz", "हल्की आवाज", "thin voice")) {
            adjustPitch(c, 0.08f)
            voiceDirty = true
            return "आवाज़ थोड़ी हल्की कर दी बॉस"
        }
        if (has(t, "dheere bolo", "धीरे बोलो", "slow bolo", "धीमे बोलो")) {
            adjustRate(c, -0.08f)
            return "ठीक है बॉस, अब धीरे बोलूँगा"
        }
        if (has(t, "tez bolo", "तेज बोलो", "jaldi bolo", "जल्दी बोलो")) {
            adjustRate(c, 0.08f)
            return "ठीक है बॉस, अब तेज बोलूँगा"
        }
        return null
    }

    // Hindi jawab ko English me (jab Jarvis English mode me ho)
    fun english(text: String): String {
        if (text.none { it in '\u0900'..'\u097F' }) return text
        val s = text.trim()
        val exact = mapOf(
            "टॉर्च चालू कर दी" to "Torch is on, boss.",
            "टॉर्च बंद कर दी" to "Torch is off, boss.",
            "टाइमर लगा दिया" to "Timer is set, boss.",
            "ये काम नहीं हो पाया" to "I'm sorry boss, I could not do that.",
            "खोज रहा हूँ बॉस" to "Searching, boss.",
            "एक सेकंड बॉस" to "One moment, boss.",
            "पीछे आ गया" to "Going back, boss.",
            "होम पर आ गया" to "Back at the home screen, boss.",
            "फोन लॉक कर दिया" to "Phone locked, boss.",
            "स्क्रीनशॉट ले लिया" to "Screenshot taken, boss.",
            "ठीक है बॉस" to "Understood, boss.",
            "मैं तैयार हूँ बॉस" to "All systems ready, boss."
        )
        val e = exact[s]
        if (e != null) return e
        var m = Regex("^(.+) में (.+) खोज रहा हूँ$").find(s)
        if (m != null) return "Searching " + m.groupValues[2] + " in " + m.groupValues[1] + ", boss."
        m = Regex("^(.+) खोल रहा हूँ$").find(s)
        if (m != null) return "Opening " + m.groupValues[1] + ", boss."
        m = Regex("^(.+) को कॉल लगा रहा हूँ$").find(s)
        if (m != null) return "Calling " + m.groupValues[1] + ", boss."
        m = Regex("^आवाज.{0,2} (\\d+) प्रतिशत कर दी$").find(s)
        if (m != null) return "Volume set to " + m.groupValues[1] + " percent, boss."
        m = Regex("^बैटरी (\\d+) प्रतिशत है(.*)$").find(s)
        if (m != null) {
            val chg = if (m.groupValues[2].contains("चार्ज हो रहा")) " It is charging." else ""
            return "Battery is at " + m.groupValues[1] + " percent, boss." + chg
        }
        m = Regex("^(.+) स्क्रॉल कर दिया$").find(s)
        if (m != null) return "Scrolled, boss."
        return text
    }

    // ================= TEZ JAWAB (Wikipedia seedha, dono bhasha ek saath) =================
    private fun get(url: String): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "JarvisApp/2.0 (personal assistant)")
            conn.connectTimeout = 3500
            conn.readTimeout = 4500
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }

    private fun titleCase(q: String): String =
        q.split(" ").filter { it.isNotBlank() }.joinToString("_") { w -> w.replaceFirstChar { ch -> ch.uppercaseChar() } }

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

    private fun extract(json: String?, long: Boolean): String? {
        if (json == null) return null
        return try {
            val o = JSONObject(json)
            if (o.optString("type") == "disambiguation") {
                null
            } else {
                val ex = o.optString("extract", "")
                if (ex.isBlank()) null else shorten(ex, if (long) 520 else 240, if (long) 320 else 120)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun wikiFast(lg: String, q: String, long: Boolean): String? {
        val dev = q.any { it in '\u0900'..'\u097F' }
        val guess = if (dev) q.trim().replace(' ', '_') else titleCase(q)
        val r1 = extract(get("https://$lg.wikipedia.org/api/rest_v1/page/summary/" + Uri.encode(guess)), long)
        if (r1 != null) return r1
        val s = get(
            "https://$lg.wikipedia.org/w/api.php?action=query&list=search&srsearch=" + Uri.encode(q) +
                "&srlimit=1&format=json"
        ) ?: return null
        val title = try {
            JSONObject(s).getJSONObject("query").getJSONArray("search").getJSONObject(0).getString("title")
        } catch (e: Exception) {
            return null
        }
        return extract(get("https://$lg.wikipedia.org/api/rest_v1/page/summary/" + Uri.encode(title.replace(' ', '_'))), long)
    }

    // background thread me chalana
    fun answer(q: String, long: Boolean): String? {
        val dev = q.any { it in '\u0900'..'\u097F' }
        val langs = if (dev) listOf("hi", "en") else listOf("en", "hi")
        val res = arrayOfNulls<String>(2)
        val th = ArrayList<Thread>()
        for (k in 0..1) {
            val t = Thread {
                res[k] = try {
                    wikiFast(langs[k], q, long)
                } catch (e: Exception) {
                    null
                }
            }
            t.start()
            th.add(t)
        }
        th[0].join(7000)
        if (res[0] != null) return res[0]
        th[1].join(7000)
        return res[1]
    }

    // ================= APP KE ANDAR SEARCH =================
    private val SEARCHW = listOf(
        "search", "सर्च", "khojo", "खोजो", "dhundo", "ढूंढो", "dhundh", "ढूंढ", "find", "खोजें", "खोज",
        "chalao", "चलाओ", "play", "बजाओ", "lagao", "लगाओ"
    )
    private val LOC = setOf("me", "में", "mein", "pe", "पर", "par", "andar", "अंदर", "in", "on")
    private val FILL = setOf(
        "karo", "करो", "kar", "kijiye", "कीजिए", "do", "दो", "mujhe", "मुझे", "ek", "एक", "kuch", "कुछ", "for",
        "please", "jarvis", "boss", "बॉस", "bhai", "भाई", "ke", "के", "ki", "की", "ka", "का", "ko", "को",
        "wala", "वाला", "wali", "वाली", "zara", "जरा", "app", "ऐप", "एप", "sunao", "सुनाओ", "dikhao", "दिखाओ",
        "batao", "बताओ", "kholo", "खोलो", "open", "ओपन", "aur", "और", "and", "phir", "फिर", "fir"
    )

    private class Def(val key: String, val label: String, val names: List<String>) {
        val sks: Set<String> = names.map { Hi.skel(it.replace(" ", "")) }.filter { it.length >= 2 }.toSet()
    }

    private val DEFS = listOf(
        Def("yt", "YouTube", listOf("youtube", "यूट्यूब", "यू ट्यूब", "यूटयूब")),
        Def("ps", "Play Store", listOf("play store", "playstore", "प्ले स्टोर", "प्लेस्टोर")),
        Def("maps", "Maps", listOf("maps", "मैप्स", "google maps", "गूगल मैप्स", "गूगल मैप")),
        Def("sp", "Spotify", listOf("spotify", "स्पॉटिफाई", "स्पोटिफाई")),
        Def("amz", "Amazon", listOf("amazon", "अमेज़न", "अमेजन", "अमेजोन")),
        Def("fk", "Flipkart", listOf("flipkart", "फ्लिपकार्ट", "फ्लिपकार्ड")),
        Def("web", "Chrome", listOf("chrome", "क्रोम"))
    )

    private class AppInfo(val pkg: String, val label: String, val sk: String)
    private class Hit(val def: Def?, val label: String, val pkg: String?, val idx: Int, val len: Int, val score: Int)

    private var cache: List<AppInfo>? = null

    private fun installed(c: Context): List<AppInfo> {
        val cur = cache
        if (cur != null) return cur
        val out = ArrayList<AppInfo>()
        try {
            val pm = c.packageManager
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            for (ri in pm.queryIntentActivities(i, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == c.packageName) continue
                val label = ri.loadLabel(pm).toString()
                out.add(AppInfo(pkg, label, Hi.skel(label)))
            }
        } catch (e: Exception) {
        }
        cache = out
        return out
    }

    private fun findApp(c: Context, words: List<String>): Hit? {
        val list = installed(c)
        var best: Hit? = null
        for (n in 2 downTo 1) {
            var i = 0
            while (i + n <= words.size) {
                val win = words.subList(i, i + n)
                val bad = win.any { it in LOC || it in FILL || SEARCHW.any { s -> it.contains(s) } }
                if (!bad) {
                    val sk = Hi.skel(win.joinToString(""))
                    if (sk.length >= 2) {
                        val adj = i + n < words.size && words[i + n] in LOC
                        var found: Hit? = null
                        val d = DEFS.firstOrNull { sk in it.sks }
                        if (d != null) {
                            found = Hit(d, d.label, null, i, n, if (adj) 3 else 1)
                        } else {
                            val ia = list.firstOrNull { it.sk == sk }
                            if (ia != null) found = Hit(null, ia.label, ia.pkg, i, n, if (adj) 2 else 0)
                        }
                        val cur = best
                        if (found != null && (cur == null || found.score > cur.score)) best = found
                    }
                }
                i++
            }
        }
        return best
    }

    private fun go(c: Context, i: Intent): Boolean {
        return try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            c.startActivity(i)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun doDef(c: Context, d: Def, q: String): String {
        val enc = Uri.encode(q)
        val tries = ArrayList<Intent>()
        when (d.key) {
            "yt" -> {
                tries.add(Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube").putExtra("query", q))
                tries.add(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$enc"))
                        .setPackage("com.google.android.youtube")
                )
                tries.add(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$enc")))
            }
            "ps" -> {
                tries.add(
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$enc&c=apps")).setPackage("com.android.vending")
                )
                tries.add(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$enc&c=apps")))
            }
            "maps" -> {
                tries.add(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$enc")).setPackage("com.google.android.apps.maps"))
                tries.add(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$enc")))
            }
            "sp" -> {
                tries.add(Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$enc")).setPackage("com.spotify.music"))
                tries.add(Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/$enc")))
            }
            "amz" -> {
                tries.add(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.amazon.in/s?k=$enc")))
            }
            "fk" -> {
                tries.add(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.flipkart.com/search?q=$enc")))
            }
            else -> {
                tries.add(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$enc")))
            }
        }
        for (i in tries) {
            if (go(c, i)) return d.label + " में " + q + " खोज रहा हूँ"
        }
        return d.label + " नहीं खुल पाया बॉस"
    }

    // baaki sab apps: app kholo, search icon dabao, text likho, enter dabao
    private fun doGeneric(c: Context, pkg: String, label: String, q: String): String {
        val li = c.packageManager.getLaunchIntentForPackage(pkg) ?: return label + " नहीं खुल पाया"
        if (!go(c, li)) return label + " नहीं खुल पाया"
        if (JarvisAccess.inst == null) {
            return label + " खुल गया, अंदर खोजने के लिए Accessibility चालू कीजिए बॉस"
        }
        val h = Handler(Looper.getMainLooper())
        val ok = BooleanArray(1)
        h.postDelayed({ JarvisAccess.inst?.clickSearchIcon() }, 3000L)
        h.postDelayed({ ok[0] = JarvisAccess.inst?.setInput(q) == true }, 4200L)
        h.postDelayed({ if (!ok[0]) JarvisAccess.inst?.clickSearchIcon() }, 5200L)
        h.postDelayed({ if (!ok[0]) ok[0] = JarvisAccess.inst?.setInput(q) == true }, 6200L)
        h.postDelayed({ if (ok[0]) JarvisAccess.inst?.pressSearch() }, 7000L)
        return label + " में " + q + " खोज रहा हूँ"
    }

    private fun inApp(c: Context, t: String): String? {
        if (SEARCHW.none { t.contains(it) }) return null
        val words = t.split(" ").filter { it.isNotBlank() }
        val hit = findApp(c, words) ?: return null
        val q = words.filterIndexed { i, w ->
            (i < hit.idx || i >= hit.idx + hit.len) && w !in LOC && w !in FILL && SEARCHW.none { s -> w.contains(s) }
        }.joinToString(" ").trim()
        if (q.isBlank()) return null
        if (!Settings.canDrawOverlays(c)) {
            return "बॉस, पहले 'Display over other apps' की अनुमति दीजिए, वरना मैं ऐप नहीं खोल पाऊँगा"
        }
        val d = hit.def
        if (d != null) return doDef(c, d, q)
        val pkg = hit.pkg ?: return null
        return doGeneric(c, pkg, hit.label, q)
    }

    // service isse sabse pehle poochhta hai
    fun run(c: Context, t: String): String? {
        val v = voiceCmd(c, t)
        if (v != null) return v
        return inApp(c, t)
    }
}

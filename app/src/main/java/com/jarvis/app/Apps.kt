package com.jarvis.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper

// =====================================================================
//  APP KE ANDAR SEARCH: "youtube me X search karo", "amazon pe X dhundo"
//  Jis app ka naam bolo, usi me search karta hai
// =====================================================================
object Apps {
    private val SEARCHW = listOf(
        "search", "सर्च", "khojo", "खोजो", "dhundo", "ढूंढो", "dhundh", "ढूंढ", "find", "खोजें", "खोज"
    )
    private val LOC = setOf("me", "में", "mein", "pe", "पर", "par", "andar", "अंदर", "in", "on")
    private val STOPQ = setOf(
        "karo", "करो", "kar", "kijiye", "कीजिए", "do", "दो", "mujhe", "मुझे", "ek", "एक", "kuch", "कुछ", "for",
        "please", "jarvis", "boss", "बॉस", "bhai", "भाई", "ke", "के", "ki", "की", "ka", "का", "ko", "को",
        "wala", "वाला", "wali", "वाली", "zara", "जरा", "app", "ऐप", "एप"
    )

    private class AppInfo(val pkg: String, val label: String, val sk: String)
    private class Found(val pkg: String, val label: String, val idx: Int, val len: Int)

    private var cache: List<AppInfo>? = null

    private fun apps(c: Context): List<AppInfo> {
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

    private fun findApp(c: Context, words: List<String>): Found? {
        val list = apps(c)
        for (pass in 0..1) {
            for (i in words.indices) {
                val w = words[i]
                if (w in LOC || w in STOPQ || SEARCHW.any { w.contains(it) }) continue
                if (pass == 0 && !(i + 1 < words.size && words[i + 1] in LOC)) continue
                // do shabd wale app ("play store")
                if (i + 1 < words.size) {
                    val sk2 = Hi.skel(w + words[i + 1])
                    if (sk2.length >= 3) {
                        val m2 = list.firstOrNull { it.sk == sk2 }
                        if (m2 != null) return Found(m2.pkg, m2.label, i, 2)
                    }
                }
                val sk = Hi.skel(w)
                if (sk.length < 2) continue
                val m = list.firstOrNull { it.sk == sk || (w.length >= 3 && it.label.lowercase() == w) }
                if (m != null) return Found(m.pkg, m.label, i, 1)
            }
        }
        return null
    }

    private fun launch(c: Context, i: Intent): Boolean {
        return try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            c.startActivity(i)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun run(c: Context, t: String): String? {
        if (SEARCHW.none { t.contains(it) }) return null
        if (t.contains("google") || t.contains("गूगल")) return null
        val words = t.split(" ").filter { it.isNotBlank() }
        val app = findApp(c, words) ?: return null
        val q = words.filterIndexed { i, w ->
            (i < app.idx || i >= app.idx + app.len) && w !in LOC && w !in STOPQ && SEARCHW.none { w.contains(it) }
        }.joinToString(" ").trim()
        if (q.isBlank()) return "${app.label} में क्या खोजूँ बॉस?"
        return doSearch(c, app, q)
    }

    private fun doSearch(c: Context, app: Found, q: String): String {
        val pkg = app.pkg
        val enc = Uri.encode(q)
        val direct: Intent? = when {
            pkg == "com.google.android.youtube" ->
                Intent(Intent.ACTION_SEARCH).setPackage(pkg).putExtra("query", q)
            pkg == "com.google.android.apps.maps" ->
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$enc")).setPackage(pkg)
            pkg == "com.android.vending" ->
                Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$enc")).setPackage(pkg)
            pkg == "com.spotify.music" ->
                Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$enc")).setPackage(pkg)
            pkg.startsWith("com.amazon") || pkg == "in.amazon.mShop.android.shopping" ->
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.amazon.in/s?k=$enc")).setPackage(pkg)
            pkg == "com.flipkart.android" ->
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.flipkart.com/search?q=$enc")).setPackage(pkg)
            pkg.contains("chrome") || pkg.contains("browser") ->
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$enc")).setPackage(pkg)
            else -> null
        }
        if (direct != null && launch(c, direct)) return "${app.label} में $q खोज रहा हूँ"

        // baaki sab apps: app kholo, search icon dabao, text likho, enter dabao
        val li = c.packageManager.getLaunchIntentForPackage(pkg) ?: return "${app.label} नहीं खुल पाया"
        if (!launch(c, li)) return "${app.label} नहीं खुल पाया"
        if (JarvisAccess.inst == null) {
            return "${app.label} खुल गया, अंदर खोजने के लिए Accessibility चालू कीजिए बॉस"
        }
        val h = Handler(Looper.getMainLooper())
        val ok = BooleanArray(1)
        h.postDelayed({ JarvisAccess.inst?.clickSearchIcon() }, 3000L)
        h.postDelayed({ ok[0] = JarvisAccess.inst?.setInput(q) == true }, 4200L)
        h.postDelayed({ if (!ok[0]) JarvisAccess.inst?.clickSearchIcon() }, 5200L)
        h.postDelayed({ if (!ok[0]) ok[0] = JarvisAccess.inst?.setInput(q) == true }, 6200L)
        h.postDelayed({ if (ok[0]) JarvisAccess.inst?.pressSearch() }, 7000L)
        return "${app.label} में $q खोज रहा हूँ"
    }
}

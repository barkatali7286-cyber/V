package com.jarvis.app

import android.content.Context
import android.media.AudioManager

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
//  EXTRA COMMANDS: volume, screen pe dikh rahe video ko chalana
// =====================================================================
object Extra {
    private fun has(t: String, vararg w: String) = w.any { t.contains(it) }

    private val ORD = mapOf(
        "pehla" to 1, "pehle" to 1, "पहला" to 1, "पहले" to 1, "first" to 1, "1st" to 1,
        "dusra" to 2, "doosra" to 2, "दूसरा" to 2, "दूसरे" to 2, "second" to 2, "2nd" to 2,
        "teesra" to 3, "तीसरा" to 3, "तीसरे" to 3, "third" to 3, "3rd" to 3,
        "chautha" to 4, "चौथा" to 4, "fourth" to 4, "4th" to 4,
        "paanchva" to 5, "पांचवा" to 5, "पाँचवा" to 5, "fifth" to 5, "5th" to 5
    )

    private val FILL = setOf(
        "video", "वीडियो", "वीडिओ", "reel", "रील", "screen", "स्क्रीन", "pe", "पर", "jo", "जो", "wala", "वाला",
        "wali", "वाली", "hai", "है", "chalao", "चलाओ", "play", "lagao", "लगाओ", "kholo", "खोलो", "dikhao",
        "दिखाओ", "chala", "चला", "start", "शुरू", "karo", "करो", "kar", "do", "दो", "ko", "को", "ka", "का",
        "ki", "की", "ke", "के", "mujhe", "मुझे", "please", "jarvis", "boss", "बॉस", "bhai", "भाई", "ek", "एक",
        "wo", "वो", "ye", "ये", "is", "इस", "isko", "इसको", "ise", "इसे", "me", "में", "se", "से"
    )

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

    fun run(c: Context, raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        if (has(t, "message", "msg", "मैसेज", "मेसेज", "sms")) return null
        val words = t.split(" ").filter { it.isNotBlank() }

        // ---------------- Volume ----------------
        if (has(t, "volume", "वॉल्यूम", "वाल्यूम", "आवाज", "awaaz", "awaz", "साउंड", "sound")) {
            val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val step = maxOf(1, max / 5)
            val ns = nums(t)
            val up = has(t, "badha", "बढा", "बढाओ", "bada", "बडा", "zyada", "ज्यादा", "tez", "तेज", "increase") ||
                "up" in words
            val down = has(t, "kam", "कम", "dheema", "धीमा", "ghata", "घटा", "low") || "down" in words
            val target: Int? = when {
                has(t, "full", "max", "maximum", "poori", "पूरी", "फुल", "मैक्स") -> max
                has(t, "mute", "silent", "zero", "म्यूट", "बंद", "band", "शून्य") -> 0
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
        val videoW = has(t, "video", "वीडियो", "वीडिओ", "reel", "रील", "screen", "स्क्रीन")
        val playW = has(t, "chalao", "चलाओ", "play", "lagao", "लगाओ", "kholo", "खोलो", "dikhao", "दिखाओ",
            "chala", "चला", "start", "शुरू")
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
            if (ord > 0) return access { it.clickNth(ord) }

            val q = t.split(" ").filter { it.isNotBlank() && it !in FILL }.joinToString(" ").trim()
            if (q.isBlank()) return "कौन सा वीडियो चलाऊँ बॉस? पहला, दूसरा या नाम बोलिए"
            return access { a ->
                val hit = a.clickBest(q)
                if (hit != null) "$hit चला दिया" else "वो स्क्रीन पर नहीं दिखा बॉस"
            }
        }

        return null
    }
}

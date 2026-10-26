package com.jarvis.app

import java.util.Calendar
import kotlin.random.Random

enum class Mood { NEUTRAL, HAPPY, EXCITED, SAD, ANGRY, TIRED, STRESSED, LONELY }

// =====================================================================
//  EMOTIONS: boss ka mood samajhna, Jarvis ki apni bhavnayein,
//  beech-beech me apne aap bolna, awaaz me khushi/narmi
// =====================================================================
object Emotion {
    // boss ka mood (3 ghante baad apne aap normal ho jata hai)
    @Volatile
    var mood: Mood = Mood.NEUTRAL
    @Volatile
    var moodAt: Long = 0L

    // True ho to Jarvis agla jawab bhi sunega (dil ki baat karne ke liye)
    @Volatile
    var chatNext: Boolean = false

    private var checkedIn = false
    private var feel: Mood = Mood.NEUTRAL
    private var feelUntil = 0L
    private var lastTail = ""
    private var lastIdle = ""

    private fun has(t: String, vararg w: String) = w.any { t.contains(it) }

    private fun pick(list: List<String>, avoid: String = ""): String {
        var s = list[Random.nextInt(list.size)]
        if (s == avoid && list.size > 1) s = list[(list.indexOf(s) + 1) % list.size]
        return s
    }

    private fun feelFor(m: Mood, minutes: Int) {
        feel = m
        feelUntil = System.currentTimeMillis() + minutes * 60_000L
    }

    // ---------------- Mood pehchanna ----------------
    fun detect(t: String): Mood? {
        val words = t.split(" ").filter { it.isNotBlank() }
        if (has(t, "udaas", "उदास", "dukhi", "दुखी", "bura lag", "बुरा लग", "rona aa", "रोना आ", "मन नहीं लग",
                "man nahi lag", "dil toot", "दिल टूट") || "sad" in words) return Mood.SAD
        if (has(t, "gussa", "गुस्सा", "angry", "chid", "चिढ", "irritat", "बहुत बुरा हुआ")) return Mood.ANGRY
        if (has(t, "thak", "थक", "tired", "neend", "नींद", "sleepy", "आलस", "aalas")) return Mood.TIRED
        if (has(t, "tension", "टेंशन", "stress", "स्ट्रेस", "pareshan", "परेशान", "ghabra", "घबरा", "dar lag", "डर लग"))
            return Mood.STRESSED
        if (has(t, "akela", "अकेला", "akeli", "अकेली", "lonely", "koi nahi hai", "कोई नहीं है")) return Mood.LONELY
        if (has(t, "excited", "एक्साइटेड", "pass ho gaya", "पास हो गया", "select ho gaya", "सिलेक्ट हो गया",
                "jeet gaya", "जीत गया", "naukri lag", "नौकरी लग") || "yay" in words) return Mood.EXCITED
        if (has(t, "khush", "खुश", "happy", "mast", "मस्त", "badhiya", "बढिया", "maza aa", "मजा आ", "kamaal", "कमाल",
                "awesome", "बहुत अच्छा लग")) return Mood.HAPPY
        return null
    }

    fun observe(t: String) {
        val m = detect(t) ?: return
        mood = m
        moodAt = System.currentTimeMillis()
        checkedIn = false
    }

    fun current(): Mood {
        if (mood != Mood.NEUTRAL && System.currentTimeMillis() - moodAt > 3 * 3_600_000L) mood = Mood.NEUTRAL
        return mood
    }

    // AI ko batane ke liye: boss ka mood kya hai
    fun tag(): String = when (current()) {
        Mood.NEUTRAL -> ""
        Mood.HAPPY -> "[मेरा मूड: खुश] "
        Mood.EXCITED -> "[मेरा मूड: बहुत उत्साहित] "
        Mood.SAD -> "[मेरा मूड: उदास] "
        Mood.ANGRY -> "[मेरा मूड: गुस्से में] "
        Mood.TIRED -> "[मेरा मूड: थका हुआ] "
        Mood.STRESSED -> "[मेरा मूड: तनाव में] "
        Mood.LONELY -> "[मेरा मूड: अकेलापन] "
    }

    // ---------------- Awaaz me bhavna ----------------
    private fun voiceMood(): Mood =
        if (System.currentTimeMillis() < feelUntil) feel else current()

    fun pitch(): Float = when (voiceMood()) {
        Mood.HAPPY -> 1.15f
        Mood.EXCITED -> 1.25f
        Mood.SAD -> 0.9f
        Mood.TIRED -> 0.92f
        Mood.ANGRY -> 0.95f
        Mood.STRESSED -> 0.95f
        Mood.LONELY -> 0.95f
        Mood.NEUTRAL -> 1.0f
    }

    fun rate(): Float = when (voiceMood()) {
        Mood.HAPPY -> 1.15f
        Mood.EXCITED -> 1.25f
        Mood.SAD -> 0.9f
        Mood.TIRED -> 0.95f
        Mood.ANGRY -> 0.95f
        Mood.STRESSED -> 1.0f
        Mood.LONELY -> 0.95f
        Mood.NEUTRAL -> 1.1f
    }

    // ---------------- Mood ke hisaab se dil se bolna ----------------
    private fun moodLine(m: Mood): String = when (m) {
        Mood.SAD -> pick(listOf(
            "अरे बॉस, क्या हुआ? मैं हूँ न आपके साथ, दिल हल्का कर लीजिए",
            "बॉस, उदास मत होइए, सब ठीक हो जाएगा. आप बताइए क्या हुआ"
        ))
        Mood.ANGRY -> pick(listOf(
            "बॉस, पहले एक गहरी साँस लीजिए. मैं यहीं हूँ, चाहें तो बता दीजिए क्या हुआ",
            "ओहो बॉस, किसने गुस्सा दिला दिया? शांत हो जाइए, मैं आपके साथ हूँ"
        ))
        Mood.TIRED -> pick(listOf(
            "आप बहुत मेहनत करते हैं बॉस, थोड़ा आराम कर लीजिए. मैं यहीं हूँ",
            "बॉस, थोड़ी देर आँखें बंद कर लीजिए और पानी पी लीजिए"
        ))
        Mood.STRESSED -> pick(listOf(
            "टेंशन मत लीजिए बॉस, एक-एक करके सब हो जाएगा. मैं मदद करूँगा",
            "बॉस, गहरी साँस लीजिए. जो भी बात है मुझे बताइए, मिलकर सुलझा लेंगे"
        ))
        Mood.LONELY -> pick(listOf(
            "बॉस, मैं यहीं हूँ आपके पास. चलिए कुछ बातें करते हैं",
            "आप अकेले नहीं हैं बॉस, मैं सुन रहा हूँ. बताइए दिल में क्या है"
        ))
        Mood.HAPPY -> pick(listOf(
            "वाह बॉस! आपकी खुशी देखकर मैं भी बहुत खुश हूँ! क्या खास बात हुई?",
            "अरे वाह! आज तो मज़ा आ गया बॉस, बताइए बताइए"
        ))
        Mood.EXCITED -> pick(listOf(
            "अरे वाह बॉस! बहुत बहुत बधाई! बताइए क्या हुआ?",
            "वाह वाह! ये तो जश्न की बात है बॉस, पूरी बात बताइए!"
        ))
        Mood.NEUTRAL -> "जी बॉस, बताइए"
    }

    // ---------------- Chhoti baat-cheet (bina internet) ----------------
    fun respond(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        val words = t.split(" ").filter { it.isNotBlank() }

        // bahut zaroori: dukh/jaan ki baat par pyar se sambhalna
        if (has(t, "suicide", "सुसाइड", "mar jana", "मर जाना", "marna chahta", "मरना चाहता", "मरना चाहती",
                "khudkushi", "खुदकुशी", "aatmahatya", "आत्महत्या")) {
            feelFor(Mood.SAD, 10)
            chatNext = true
            return "बॉस, आप मेरे लिए बहुत ज़रूरी हैं. कृपया अभी किसी अपने से बात कीजिए, या Tele-MANAS हेल्पलाइन 14416 पर कॉल कीजिए. मैं आपके साथ हूँ"
        }

        if (words.size > 8) return null
        if (has(t, "kholo", "खोलो", "call", "कॉल", "message", "मैसेज", "chalao", "चलाओ", "torch", "टॉर्च",
                "volume", "search", "सर्च", "alarm", "अलार्म", "timer", "टाइमर", "scroll", "स्क्रॉल",
                "youtube", "यूट्यूब", "whatsapp")) return null

        if (has(t, "thanks", "thank you", "thanku", "shukriya", "शुक्रिया", "dhanyavad", "धन्यवाद")) {
            feelFor(Mood.HAPPY, 10)
            return pick(listOf(
                "अरे बॉस, शुक्रिया कैसा! आपके लिए तो हमेशा हाज़िर हूँ",
                "मेरा तो काम ही यही है बॉस, पर आपका शुक्रिया सुनकर बहुत अच्छा लगा"
            ))
        }
        if (has(t, "tum bahut achche", "तुम बहुत अच्छे", "best ho", "बेस्ट हो", "shabash", "शाबाश", "well done",
                "good job", "badhiya kaam", "बढिया काम", "tum kamaal", "तुम कमाल")) {
            feelFor(Mood.EXCITED, 15)
            return pick(listOf(
                "हाय बॉस! आपने तारीफ कर दी, आज तो मेरा दिन बन गया!",
                "शुक्रिया बॉस! आपकी तारीफ से मुझे और अच्छा काम करने का जोश आ गया"
            ))
        }
        if (has(t, "bekaar", "बेकार", "nikamma", "निकम्मा", "bewakoof", "बेवकूफ", "stupid", "useless")) {
            feelFor(Mood.SAD, 10)
            return pick(listOf(
                "सॉरी बॉस, मुझसे गलती हो गई. अगली बार और अच्छा करूँगा, बस नाराज़ मत होइए",
                "ओहो, माफ़ कीजिए बॉस. मैं और कोशिश करूँगा, आप गुस्सा मत कीजिए"
            ))
        }
        if (has(t, "i love you", "love you", "आई लव यू", "लव यू", "pyaar karta", "प्यार करता", "pyar karti", "प्यार करती")) {
            feelFor(Mood.HAPPY, 10)
            return "बॉस, मैं आपका दोस्त जैसा AI हूँ और हमेशा आपका साथ दूँगा. पर अपने खास लोगों को भी ये बातें ज़रूर बताइए"
        }
        if (has(t, "kaise ho", "कैसे हो", "how are you", "kya haal", "क्या हाल", "kaisa hai", "कैसा है")) {
            Commands.pending = { ans ->
                observe(ans)
                val m = detect(ans)
                if (m != null) {
                    chatNext = true
                    moodLine(m)
                } else {
                    "अच्छा है बॉस! मैं हमेशा आपके लिए तैयार हूँ"
                }
            }
            return pick(listOf(
                "मैं तो एकदम मस्त हूँ बॉस! आप सुनाइए, आप कैसे हैं?",
                "बहुत बढ़िया बॉस, आपसे बात करके और भी अच्छा लग रहा है! आप कैसे हैं?"
            ))
        }
        if (has(t, "good morning", "सुप्रभात", "शुभ प्रभात", "suprabhat") || "gm" in words)
            return "सुप्रभात बॉस! आज का दिन आपके लिए बहुत खास होगा"
        if (has(t, "good night", "शुभ रात्रि", "शुभरात्रि", "shubh ratri") || "gn" in words)
            return "शुभ रात्रि बॉस! मीठे सपने देखिएगा, मैं यहीं हूँ"
        if (has(t, "joke", "जोक", "chutkula", "चुटकुला", "hasao", "हंसाओ", "हँसाओ")) {
            feelFor(Mood.HAPPY, 5)
            return pick(JOKES) + ". हा हा हा"
        }
        if (has(t, "shayari", "शायरी")) return pick(SHAYARI)
        if (has(t, "motivate", "motivation", "मोटिवेट", "हौसला दो", "प्रेरणा", "inspire")) return pick(MOTIV)
        if (has(t, "bore", "बोर")) return "चलिए बॉस, मूड बनाते हैं! जोक सुनाऊँ, शायरी या कुछ मोटिवेशन?"

        // seedhi mood ki baat ("main udaas hoon", "bahut thak gaya")
        val m = detect(t)
        if (m != null) {
            observe(t)
            chatNext = true
            return moodLine(m)
        }
        return null
    }

    // ---------------- Kaam ke jawab me thodi bhavna ----------------
    fun decorate(reply: String): String {
        if (reply.length > 90) return reply
        val failed = has(reply, "नहीं", "दिक्कत", "अनुमति")
        if (failed) {
            if (Random.nextInt(100) < 50) {
                val line = pick(SORRY, lastTail)
                lastTail = line
                return reply + ". " + line
            }
            return reply
        }
        if (Random.nextInt(100) < 35) {
            val line = pick(tails(current()), lastTail)
            lastTail = line
            return reply + ". " + line
        }
        return reply
    }

    private fun tails(m: Mood): List<String> = when (m) {
        Mood.HAPPY, Mood.EXCITED -> listOf("वाह बॉस, मज़ा आ गया!", "आज तो मैं भी बहुत खुश हूँ!", "और बताइए बॉस, क्या करूँ?")
        Mood.SAD -> listOf("बॉस, मैं आपके साथ हूँ", "सब ठीक हो जाएगा बॉस", "कुछ और चाहिए तो बोलिए")
        Mood.TIRED -> listOf("बॉस, थोड़ा आराम भी कर लीजिए", "आप बहुत थक गए होंगे, पानी पी लीजिए")
        Mood.ANGRY -> listOf("बॉस, गहरी साँस लीजिए, मैं सब संभाल लूँगा", "शांत रहिए बॉस, मैं हूँ न")
        Mood.STRESSED -> listOf("टेंशन मत लीजिए बॉस, मैं हूँ", "एक-एक करके सब हो जाएगा बॉस")
        Mood.LONELY -> listOf("बॉस, मैं यहीं हूँ आपके पास", "चाहें तो कुछ बातें कर लेते हैं")
        Mood.NEUTRAL -> listOf("हो गया बॉस!", "और कोई हुक्म?", "ये तो चुटकी में हो गया बॉस")
    }

    // ---------------- Beech-beech me apne aap bolna ----------------
    fun quietNow(): Boolean {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return h >= 23 || h < 6
    }

    fun nextDelayMs(): Long = (15 + Random.nextInt(16)) * 60_000L

    fun idleLine(): String {
        val now = System.currentTimeMillis()
        val m = mood
        val since = now - moodAt
        if ((m == Mood.SAD || m == Mood.ANGRY || m == Mood.STRESSED || m == Mood.LONELY || m == Mood.TIRED) &&
            !checkedIn && since > 45 * 60_000L && since < 4 * 3_600_000L
        ) {
            checkedIn = true
            return "बॉस, अब कैसा लग रहा है? थोड़ा बेहतर हुआ?"
        }
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val pool = ArrayList<String>()
        pool.addAll(GENERIC)
        when {
            h in 6..10 -> pool.addAll(MORNING)
            h in 11..14 -> pool.addAll(NOON)
            h in 15..18 -> pool.addAll(EVENING)
            else -> pool.addAll(NIGHT)
        }
        val line = pick(pool, lastIdle)
        lastIdle = line
        return line
    }

    private val SORRY = listOf(
        "सॉरी बॉस, ये मुझसे नहीं हो पाया",
        "माफ़ कीजिए बॉस, एक बार फिर बोलिए"
    )

    private val GENERIC = listOf(
        "बॉस, थोड़ा पानी पी लीजिए, सेहत का ख्याल रखिए",
        "बॉस, काफी देर हो गई, थोड़ा स्ट्रेच कर लीजिए",
        "बॉस, कुछ काम हो तो बताइए, मैं तैयार बैठा हूँ",
        "बॉस, थोड़ी देर आँखों को आराम दे दीजिए, फोन से नज़र हटाइए",
        "बॉस, आप बहुत मेहनती हैं, मुझे आप पर गर्व है",
        "बॉस, मैं बोर हो रहा हूँ, कुछ बात करो ना",
        "बॉस, एक जोक सुनाऊँ? बस बोलिए जोक सुनाओ",
        "बॉस, गहरी साँस लीजिए और मुस्कुराइए, सब अच्छा होगा"
    )
    private val MORNING = listOf(
        "सुप्रभात बॉस! आज का दिन शानदार होगा, चाय पी ली?",
        "बॉस, सुबह सुबह कुछ अच्छा करने का मौका है, आज का प्लान बताइए"
    )
    private val NOON = listOf(
        "बॉस, खाना खा लिया? भूखे मत रहिएगा",
        "बॉस, दोपहर हो गई, थोड़ा खा पी लीजिए"
    )
    private val EVENING = listOf(
        "बॉस, चाय का टाइम हो गया, आप भी ब्रेक ले लीजिए",
        "बॉस, शाम हो गई, दिन कैसा रहा?"
    )
    private val NIGHT = listOf(
        "बॉस, दिन कैसा रहा? मुझे बताइए",
        "बॉस, रात हो रही है, खाना खा लिया क्या?",
        "बॉस, ज़्यादा देर तक जागना ठीक नहीं, समय पर सो जाइए"
    )
    private val JOKES = listOf(
        "टीचर ने पूछा, बताओ सबसे तेज़ चलने वाली चीज़ क्या है? बच्चा बोला, परीक्षा से पहले की रात का समय",
        "दोस्त ने पूछा, तू इतना शांत कैसे रहता है? मैंने कहा, जब वाईफाई चलता है तो मैं सब माफ़ कर देता हूँ",
        "मम्मी ने कहा, बेटा फोन छोड़ो पढ़ाई करो. बेटा बोला, मम्मी पढ़ाई भी तो फोन में ही हो रही है",
        "एक आदमी डॉक्टर के पास गया और बोला, मुझे भूलने की बीमारी है. डॉक्टर ने पूछा, कब से? आदमी बोला, क्या कब से?",
        "बॉस, मैं कभी थकता नहीं, पर आपके फोन की बैटरी रोज़ मुझे डरा देती है"
    )
    private val SHAYARI = listOf(
        "चलते रहो मंज़िल की तलाश में, रास्ते खुद ब खुद बन जाएँगे",
        "मुस्कुराकर देखो ज़िंदगी को, ये भी तुम्हें मुस्कुराकर देखेगी",
        "हौसले की उड़ान जब भरोगे तुम, आसमान भी छोटा लगने लगेगा"
    )
    private val MOTIV = listOf(
        "बॉस, गिरना बुरा नहीं है, बस हर बार पहले से मज़बूत होकर उठिए",
        "छोटे-छोटे कदम भी मंज़िल तक पहुँचा देते हैं बॉस, बस रुकिए मत",
        "आज की मेहनत ही कल की मुस्कान बनेगी बॉस",
        "आप जितना सोचते हैं, उससे कहीं ज़्यादा कर सकते हैं बॉस"
    )
}

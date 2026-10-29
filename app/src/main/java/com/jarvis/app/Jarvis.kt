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
//  SCREEN: Start / Stop / Settings / Test
// =====================================================================
class MainActivity : Activity() {
    private val perms: Array<String>
        get() = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.SEND_SMS)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()

    private var statusView: TextView? = null

    private fun addButton(col: LinearLayout, label: String, f: () -> Unit) {
        val btn = Button(this)
        btn.text = label
        btn.setOnClickListener { f() }
        col.addView(btn)
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(48, 120, 48, 48)

        val sp = getSharedPreferences("cfg", MODE_PRIVATE)

        val info = TextView(this)
        info.text = "Jarvis\n\n1) Neeche wake word likho (jaise V ya Jarvis)\n" +
            "2) Start Jarvis dabao aur permissions allow karo\n" +
            "3) Accessibility chalu karo (scroll, click, WhatsApp send ke liye)\n" +
            "4) Battery me Unrestricted karo\n\nPhir wake word bolo"
        info.textSize = 16f
        col.addView(info)

        val st = TextView(this)
        st.text = "Status: " + (sp.getString("status", "abhi chalu nahi hua") ?: "")
        st.textSize = 14f
        statusView = st
        col.addView(st)

        val wk = EditText(this)
        wk.hint = "Wake word (jaise: V, Jarvis, Computer)"
        wk.setText(sp.getString("wake", "V"))
        wk.setSingleLine()
        col.addView(wk)

        addButton(col, "Start Jarvis") {
            val w = wk.text.toString().trim()
            sp.edit().putString("wake", if (w.isEmpty()) "v" else w).apply()
            startJarvis()
        }
        addButton(col, "Debug ON/OFF (jo sunta hai wo dikhao)") {
            val on = !sp.getBoolean("debug", false)
            sp.edit().putBoolean("debug", on).apply()
            Toast.makeText(this, if (on) "Debug ON" else "Debug OFF", Toast.LENGTH_SHORT).show()
        }
        addButton(col, "Engine badlo: Offline / Google") {
            val cur = sp.getString("engine", "vosk") ?: "vosk"
            val nw = if (cur == "vosk") "google" else "vosk"
            sp.edit().putString("engine", nw).apply()
            val m = if (nw == "vosk") "Offline engine: tez aur bina internet" else "Google engine: zyada sahi, internet chahiye"
            Toast.makeText(this, m + ". Jarvis ko Stop karke dobara Start karo", Toast.LENGTH_LONG).show()
        }
        addButton(col, "Status dekho (kaun sa engine chal raha hai)") {
            statusView?.text = "Status: " + (sp.getString("status", "abhi chalu nahi hua") ?: "")
        }
        addButton(col, "Notification access dena (Jarvis notification bataye)") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        addButton(col, "Notification bolna ON/OFF") {
            val on = !sp.getBoolean("notif_speak", true)
            sp.edit().putBoolean("notif_speak", on).apply()
            Toast.makeText(this, if (on) "Jarvis notification bolega" else "Jarvis notification nahi bolega", Toast.LENGTH_SHORT).show()
        }
        addButton(col, "Notification detail ON/OFF (kisne kya bheja)") {
            val on = !sp.getBoolean("notif_detail", true)
            sp.edit().putBoolean("notif_detail", on).apply()
            Toast.makeText(this, if (on) "Poori detail bolega" else "Sirf app ka naam bolega", Toast.LENGTH_SHORT).show()
        }
        addButton(col, "Awaaz style badlo: Jarvis / Normal / Soft") {
            val cur = sp.getString("vstyle", "jarvis") ?: "jarvis"
            val nw = if (cur == "jarvis") "normal" else if (cur == "normal") "soft" else "jarvis"
            sp.edit().putString("vstyle", nw).apply()
            Toast.makeText(this, "Awaaz style: $nw", Toast.LENGTH_SHORT).show()
        }
        addButton(col, "English UK male awaaz install karo (Jarvis jaisi)") {
            try {
                startActivity(Intent("com.android.settings.TTS_SETTINGS"))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
        addButton(col, "Bolne ka gap badlo (3 / 8 / 20 min)") {
            val cur = sp.getInt("chat_min", 8)
            val nw = if (cur == 3) 8 else if (cur == 8) 20 else 3
            sp.edit().putInt("chat_min", nw).apply()
            Toast.makeText(this, "Jarvis ab har ~$nw minute me bolega", Toast.LENGTH_SHORT).show()
        }
        addButton(col, "Beech-beech bolna ON/OFF") {
            val on = !sp.getBoolean("chatter", true)
            sp.edit().putBoolean("chatter", on).apply()
            Toast.makeText(this, if (on) "Jarvis beech-beech bolega" else "Jarvis chup rahega", Toast.LENGTH_SHORT).show()
        }
        addButton(col, "Abhi kuch bolo (test)") {
            startService(Intent(this, JarvisService::class.java).setAction(JarvisService.ACTION_CHATTER))
        }
        addButton(col, "Stop Jarvis (band karo)") {
            stopService(Intent(this, JarvisService::class.java))
            Toast.makeText(this, "Jarvis band ho gaya", Toast.LENGTH_SHORT).show()
        }
        addButton(col, "Accessibility chalu karo") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        addButton(col, "Battery: Unrestricted karo") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }

        val et = EditText(this)
        et.hint = "Test: command likho (jaise: youtube kholo)"
        col.addView(et)
        addButton(col, "Test command chalao") {
            val r = try {
                Commands.run(this, et.text.toString().lowercase().trim())
            } catch (e: Exception) {
                "Error: " + e.message
            }
            Toast.makeText(this, r ?: "Local command nahi mila (AI se poochhna padega)", Toast.LENGTH_LONG).show()
        }

        val sv = ScrollView(this)
        sv.addView(col)
        setContentView(sv)
    }

    override fun onResume() {
        super.onResume()
        val sp = getSharedPreferences("cfg", MODE_PRIVATE)
        statusView?.text = "Status: " + (sp.getString("status", "abhi chalu nahi hua") ?: "")
    }

    private fun startJarvis() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) proceed()
        else requestPermissions(perms, 1)
    }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, r: IntArray) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) proceed()
        else Toast.makeText(this, "Mic permission zaroori hai", Toast.LENGTH_LONG).show()
    }

    private fun proceed() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            Toast.makeText(this, "'Display over other apps' ON karo, phir Start Jarvis dobara dabao", Toast.LENGTH_LONG).show()
            return
        }
        startForegroundService(Intent(this, JarvisService::class.java))
        Toast.makeText(this, "Jarvis chalu! Ab wake word bolo", Toast.LENGTH_LONG).show()
    }
}

// =====================================================================
//  BACKGROUND SERVICE: sunta rehta hai, "V" sunte hi "Yes boss"
// =====================================================================
class JarvisService : Service(), TextToSpeech.OnInitListener, OfflineCallback {
    companion object {
        const val ACTION_STOP = "com.jarvis.app.STOP"
        const val ACTION_CHATTER = "com.jarvis.app.CHATTER"
    }

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsTag = ""
    private var rec: SpeechRecognizer? = null
    private val main = Handler(Looper.getMainLooper())
    private val history = ArrayList<Pair<String, String>>()
    private var afterSpeech: (() -> Unit)? = null
    private var alive = true
    private var started = false
    private var gen = 0
    private var wakeRun: Runnable? = null
    private var againRun: Runnable? = null
    private var dot: View? = null
    private var wl: PowerManager.WakeLock? = null
    private val vWords = setOf("v", "vee", "vi", "ve", "bee", "b", "वी", "वि", "बी")
    private var dbg: Toast? = null

    // offline (Vosk) engine ki halat
    private var offline: Offline? = null
    private var useVosk = false
    private var vState = 0
    private var vBusy = true
    private var vLocalOnly = false
    private var followRun: Runnable? = null

    @Volatile
    private var curUtt = ""
    private var sayWatch: Runnable? = null
    private var chatterRun: Runnable? = null
    private var inCommand = false

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (i?.action == ACTION_CHATTER) {
            if (!started) {
                stopSelf()
                return START_NOT_STICKY
            }
            main.post { chatterNow(true) }
            return START_STICKY
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("jarvis", "Jarvis", NotificationManager.IMPORTANCE_LOW))
        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, JarvisService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopAct = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "Band karo", stopPi
        ).build()
        val n = Notification.Builder(this, "jarvis")
            .setContentTitle("Jarvis sun raha hai")
            .setContentText("Wake word bolo")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .addAction(stopAct)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(1, n)
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            showOverlayDot()
            val pmgr = getSystemService(PowerManager::class.java)
            wl = pmgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jarvis:listen")
            wl?.acquire()
            tts = TextToSpeech(this, this)
        }
        return START_STICKY
    }

    // 1x1 chhoti overlay window: isse Android background se app kholne deta hai
    private fun showOverlayDot() {
        if (dot != null || !Settings.canDrawOverlays(this)) return
        try {
            val v = View(this)
            val lp = WindowManager.LayoutParams(
                1, 1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )
            getSystemService(WindowManager::class.java).addView(v, lp)
            dot = v
        } catch (e: Exception) {
        }
    }

    override fun onInit(status: Int) {
        val t = tts
        if (t != null && status == TextToSpeech.SUCCESS) {
            t.setSpeechRate(1.1f)
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) {
                    if (id != null && id.startsWith("s")) {
                        main.post {
                            streamQ--
                            checkStreamEnd()
                        }
                        return
                    }
                    if (id != curUtt) return
                    main.post { val f = afterSpeech; afterSpeech = null; f?.invoke() }
                }
                override fun onError(id: String?) { onDone(id) }
            })
            ttsReady = true
        }
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val en = voiceStyle() == "jarvis"
        val g = if (en) {
            if (h < 12) "Good morning, boss. All systems online."
            else if (h < 17) "Good afternoon, boss. All systems online."
            else "Good evening, boss. All systems online."
        } else {
            if (h < 12) "सुप्रभात बॉस" else if (h < 17) "नमस्ते बॉस" else "शुभ संध्या बॉस"
        }
        say(g) { startEngine() }
    }

    // text me Devanagari ho to Hindi awaaz, warna English (Jarvis style me UK) ya Lang wali awaaz
    private fun applyVoice(text: String) {
        val dev = text.any { it in '\u0900'..'\u097F' }
        val tag = if (dev) "hi-IN"
        else if (Lang.code.startsWith("hi")) (if (voiceStyle() == "jarvis") "en-GB" else "en-IN")
        else Lang.code
        if (tag != ttsTag) {
            pickVoice(tag)
            ttsTag = tag
        }
    }

    private fun say(t: String, then: (() -> Unit)?) {
        afterSpeech = then
        sayWatch?.let { main.removeCallbacks(it) }
        if (!ttsReady) {
            main.post { val f = afterSpeech; afterSpeech = null; f?.invoke() }
            return
        }
        applyVoice(t)
        val id = "u" + System.nanoTime()
        curUtt = id
        applyProsody()
        tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, id)
        val w = Runnable {
            if (curUtt == id) { val f = afterSpeech; afterSpeech = null; f?.invoke() }
        }
        sayWatch = w
        main.postDelayed(w, 25000L)
    }

    private fun cancelWake() {
        wakeRun?.let { main.removeCallbacks(it) }
        wakeRun = null
    }

    private fun again(ms: Long) {
        againRun?.let { main.removeCallbacks(it) }
        val r = Runnable { listen(false) }
        againRun = r
        main.postDelayed(r, ms)
    }

    private fun first(b: Bundle?): String? =
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun norm(s: String): String = s.lowercase()
        .replace("\u093C", "")
        .replace('\u0958', 'क').replace('\u0959', 'ख').replace('\u095A', 'ग').replace('\u095B', 'ज')
        .replace('\u095C', 'ड').replace('\u095D', 'ढ').replace('\u095E', 'फ').replace('\u095F', 'य')

    // app me likha hua wake word (default: v)
    private fun wakeWord(): String {
        val raw = getSharedPreferences("cfg", MODE_PRIVATE).getString("wake", "v") ?: "v"
        val last = raw.lowercase().trim().split(" ").lastOrNull { it.isNotBlank() } ?: "v"
        return last
    }

    private fun isWakeTok(tk: String): Boolean {
        if (tk.isEmpty()) return false
        val w = wakeWord()
        if (w == "v") return tk in vWords
        if (tk == w) return true
        val key = Hi.skel(w)
        return key.length >= 3 && Fuzz.close(Hi.skel(tk), key)
    }

    // debug ON ho to jo phone ne suna wo screen pe dikhata hai
    private fun debug(msg: String) {
        if (!getSharedPreferences("cfg", MODE_PRIVATE).getBoolean("debug", false)) return
        dbg?.cancel()
        dbg = Toast.makeText(this, msg, Toast.LENGTH_SHORT)
        dbg?.show()
    }

    // wake word ke baad ka text ("" = sirf wake word bola, null = bola hi nahi)
    // wake word shuru me hi hona chahiye (ya "hey <wake word>")
    private fun afterWake(low: String): String? {
        val toks = low.split(" ").filter { it.isNotBlank() }
        val idx = toks.indexOfFirst { tk -> isWakeTok(tk.trim { c -> c in ",.?!।" }) }
        if (idx in 0..1) return toks.drop(idx + 1).joinToString(" ")
        for (i in 0..1) {
            if (i + 1 < toks.size && isWakeTok(toks[i] + toks[i + 1])) return toks.drop(i + 2).joinToString(" ")
        }
        return null
    }

    private fun wakeNow() {
        cancelWake()
        gen++
        try { rec?.cancel(); rec?.destroy() } catch (e: Exception) {}
        rec = null
        say(ack()) { listen(true, false) }
    }

    // command=false: sirf "Jarvis" ka intezaar. command=true: aapka hukum sunna.
    // localOnly=true: sirf phone-command maanega (shor se AI na chale)
    private fun listen(command: Boolean, localOnly: Boolean = false) {
        if (!alive) return
        cancelWake()
        againRun?.let { main.removeCallbacks(it) }
        againRun = null
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            say("आवाज़ पहचान उपलब्ध नहीं है", null)
            return
        }
        val my = ++gen
        inCommand = command
        rec?.destroy()
        rec = SpeechRecognizer.createSpeechRecognizer(this)
        rec?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, b: Bundle?) {}

            override fun onPartialResults(b: Bundle?) {
                if (my != gen || command) return
                val t = first(b) ?: return
                debug("सुना: $t")
                val after = afterWake(norm(t)) ?: return
                cancelWake()
                if (after.length <= 2) {
                    val r = Runnable { if (my == gen) wakeNow() }
                    wakeRun = r
                    main.postDelayed(r, 350L)
                }
            }

            override fun onError(e: Int) {
                if (my != gen) return
                if (command) Commands.pending = null
                if (!command && e != SpeechRecognizer.ERROR_NO_MATCH && e != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) debug("mic error: $e")
                val d = when (e) {
                    SpeechRecognizer.ERROR_NO_MATCH -> 150L
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 150L
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1000L
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> 5000L
                    else -> 700L
                }
                again(d)
            }

            override fun onResults(b: Bundle?) {
                if (my != gen) return
                val t = first(b)
                if (t.isNullOrBlank()) {
                    if (command) Commands.pending = null
                    again(150L)
                    return
                }
                val low = norm(t)
                if (!command) debug("सुना: $t")
                if (command) { handle(low, localOnly); return }
                val cmd = afterWake(low)
                if (cmd == null) { again(100L); return }
                if (cmd.length > 2) handle(cmd, false) else wakeNow()
            }
        })
        val rl = if (command) Lang.code else "hi-IN"
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, rl)
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
        rec?.startListening(i)
    }

    // =================================================================
    //  JARVIS 2.0: engine, bolna, notification, docs, search, streaming
    // =================================================================
    private var resumeToken = 0
    private var lastReply = "बॉस, अभी मैंने कुछ बोला नहीं है"
    private val notifQueue = ArrayList<String>()
    private var drainScheduled = false
    private var streamQ = 0
    private var streamEnd = false
    private var streamFirst = true
    private var streamThen: (() -> Unit)? = null
    private var streamWatch: Runnable? = null

    private fun has(t: String, vararg w: String) = w.any { t.contains(it) }

    private fun safeRun(s: String): String? =
        try {
            if (Commands.pending != null) Commands.run(this, Num.toDigitsText(s))
            else (Emotion.respond(s) ?: Extra.run(this, s) ?: Commands.run(this, s))
        } catch (e: Exception) {
            "ये काम नहीं हो पाया"
        }

    private fun setStatus(s: String) {
        getSharedPreferences("cfg", MODE_PRIVATE).edit().putString("status", s).apply()
    }

    // ---------------- Awaaz: Jarvis jaisi gehri awaaz ----------------
    private fun voiceStyle(): String =
        getSharedPreferences("cfg", MODE_PRIVATE).getString("vstyle", "jarvis") ?: "jarvis"

    private fun applyProsody() {
        val st = voiceStyle()
        val bp = if (st == "jarvis") 0.82f else if (st == "soft") 1.1f else 1.0f
        val br = if (st == "jarvis") 0.95f else if (st == "soft") 1.0f else 1.1f
        tts?.setPitch(bp * Emotion.pitch())
        tts?.setSpeechRate(br * Emotion.rate() / 1.1f)
    }

    private fun pickVoice(tag: String) {
        val t = tts ?: return
        val want = Locale.forLanguageTag(tag)
        t.setLanguage(want)
        if (voiceStyle() != "jarvis") return
        try {
            val all = t.voices ?: return
            val sameLang = all.filter { it.locale.language == want.language }
            val exact = sameLang.filter { it.locale.country == want.country }
            val pool = if (exact.isNotEmpty()) exact else sameLang
            val male = pool.filter { v ->
                val n = v.name.lowercase()
                (n.contains("male") && !n.contains("female")) || n.contains("-gbb-") || n.contains("-gbd-") ||
                    n.contains("-rjs-") || n.contains("-iom-")
            }
            val pick = male.firstOrNull()
            if (pick != null) t.setVoice(pick)
        } catch (e: Exception) {
        }
    }

    private fun ack(): String {
        val r = (Math.random() * 10).toInt()
        return if (r < 6) "Yes boss" else if (r < 8) "At your service, boss" else "Right here, boss"
    }

    // ---------------- Lagataar bolna: pehla vaakya milte hi bolo ----------------
    private fun streamSay(sentence: String) {
        if (!ttsReady) return
        applyVoice(sentence)
        applyProsody()
        val id = "s" + System.nanoTime()
        val mode = if (streamFirst) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        streamFirst = false
        streamQ++
        tts?.speak(sentence, mode, null, id)
    }

    private fun checkStreamEnd() {
        if (streamEnd && streamQ <= 0) {
            streamWatch?.let { main.removeCallbacks(it) }
            val f = streamThen
            streamThen = null
            f?.invoke()
        }
    }

    private fun chatStream(low: String) {
        val langName = Lang.name
        val filler = Runnable { say("एक सेकंड बॉस", null) }
        main.postDelayed(filler, 1500L)
        streamQ = 0
        streamEnd = false
        streamFirst = true
        streamThen = { nextCmd(false) }
        Thread {
            Fast.stream(history, Emotion.tag() + low, langName, object : Fast.Sink {
                override fun onSentence(s: String) {
                    main.post {
                        main.removeCallbacks(filler)
                        if (alive) streamSay(s)
                    }
                }

                override fun onDone(full: String) {
                    main.post {
                        main.removeCallbacks(filler)
                        if (!alive) return@post
                        history.add(low to full)
                        lastReply = full
                        streamEnd = true
                        streamWatch?.let { main.removeCallbacks(it) }
                        val w = Runnable {
                            streamQ = 0
                            checkStreamEnd()
                        }
                        streamWatch = w
                        main.postDelayed(w, 40000L)
                        checkStreamEnd()
                    }
                }

                override fun onFail() {
                    main.post {
                        main.removeCallbacks(filler)
                        if (alive) chatBlocking(low)
                    }
                }
            })
        }.start()
    }

    // purana tareeka (poora jawab aane ke baad bolna): live khabar/mausam ke liye
    private fun chatBlocking(low: String) {
        val langName = Lang.name
        val filler = Runnable { say("एक सेकंड बॉस", null) }
        main.postDelayed(filler, 1200L)
        Thread {
            val reply = try { Brain.ask(history, Emotion.tag() + low, langName) } catch (e: Exception) { "इंटरनेट में दिक्कत लग रही है बॉस" }
            main.post {
                main.removeCallbacks(filler)
                if (!alive) return@post
                lastReply = reply
                history.add(low to reply)
                say(reply) { nextCmd(false) }
            }
        }.start()
    }

    // ---------------- Search: tez jawab (Wikipedia -> AI) ----------------
    private fun searchAndSpeak(q: String, low: String, long: Boolean) {
        val filler = Runnable { say("खोज रहा हूँ बॉस", null) }
        main.postDelayed(filler, 1300L)
        val langName = Lang.name
        Thread {
            var ans: String? = try { Search.answer(q, long) } catch (e: Exception) { null }
            if (ans == null) {
                ans = try { Brain.ask(history, Emotion.tag() + low, langName) } catch (e: Exception) { null }
            }
            val out = ans ?: "बॉस, अभी जवाब नहीं मिल पाया, इंटरनेट देखिए"
            main.post {
                main.removeCallbacks(filler)
                if (!alive) return@post
                lastReply = out
                history.add(low to out)
                say(out) { nextCmd(false) }
            }
        }.start()
    }

    // ---------------- PDF / Notes / Assignment ----------------
    private fun beginDoc(kind: String, topic: String, useLast: Boolean) {
        val source = if (useLast) lastReply else topic
        val title = if (useLast) "Jarvis Notes" else topic
        Thread {
            val text = if (useLast) lastReply else Docs.generate(kind, source)
            val uri = if (text != null) Docs.makePdf(this, title, text) else null
            main.post {
                if (!alive) return@post
                whenIdle {
                    if (uri != null) {
                        try {
                            val i = Intent(Intent.ACTION_VIEW)
                            i.setDataAndType(uri, "application/pdf")
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            startActivity(i)
                        } catch (e: Exception) {
                        }
                        speakProactive("बॉस, आपका ${Docs.kindHindi(kind)} तैयार है. Downloads के Jarvis फोल्डर में रखा है", null)
                    } else {
                        speakProactive("बॉस, PDF नहीं बन पाया, इंटरनेट देखिए", null)
                    }
                }
            }
        }.start()
    }

    // ---------------- Jab Jarvis free ho tab kaam karo ----------------
    private fun isIdle(): Boolean =
        if (useVosk) !vBusy && vState == 0 else !inCommand && tts?.isSpeaking != true

    private fun whenIdle(action: () -> Unit) {
        if (!alive) return
        if (isIdle()) action() else main.postDelayed({ whenIdle(action) }, 3000L)
    }

    private fun speakProactive(line: String, after: (() -> Unit)?) {
        if (useVosk) {
            vHold()
            say(line) {
                nextWake()
                after?.invoke()
            }
        } else {
            gen++
            try { rec?.cancel(); rec?.destroy() } catch (e: Exception) {}
            rec = null
            say(line) {
                again(100L)
                after?.invoke()
            }
        }
    }

    // ---------------- Notification bolna ----------------
    private fun onNotifLine(line: String) {
        val sp = getSharedPreferences("cfg", MODE_PRIVATE)
        if (!sp.getBoolean("notif_speak", true)) return
        if (Emotion.quietNow()) return
        notifQueue.add(line)
        while (notifQueue.size > 3) notifQueue.removeAt(0)
        drainNotif()
    }

    private fun drainNotif() {
        if (!alive || notifQueue.isEmpty() || drainScheduled) return
        val am = getSystemService(AudioManager::class.java)
        if (!isIdle() || am.mode != AudioManager.MODE_NORMAL) {
            drainScheduled = true
            main.postDelayed({
                drainScheduled = false
                drainNotif()
            }, 5000L)
            return
        }
        val line = notifQueue.removeAt(0)
        speakProactive(line) { drainNotif() }
    }

    // ---------------- Reminder ----------------
    private fun scheduleReminder(mins: Int, text: String) {
        main.postDelayed({ remindNow(text) }, mins * 60_000L)
    }

    private fun remindNow(text: String) {
        if (!alive) return
        whenIdle { speakProactive("बॉस, याद दिला रहा हूँ, $text", null) }
    }

    // ---------------- Engine: Offline (Vosk) ya Google ----------------
    private fun startEngine() {
        if (!alive) return
        if (chatterRun == null) scheduleChatter(true)
        JarvisNotif.sink = { line -> main.post { onNotifLine(line) } }
        val eng = getSharedPreferences("cfg", MODE_PRIVATE).getString("engine", "vosk") ?: "vosk"
        if (eng == "vosk") {
            useVosk = true
            setStatus("Offline engine load ho raha hai...")
            say("ऑफलाइन इंजन तैयार हो रहा है, थोड़ा रुकिए बॉस", null)
            val o = Offline(this, this)
            offline = o
            o.start()
        } else {
            useVosk = false
            setStatus("Google engine chalu (isme mic baar-baar on/off hota hai)")
            again(100L)
        }
    }

    override fun onReady() {
        if (!alive) return
        setStatus("Offline engine chalu hai (mic lagatar on rehta hai)")
        vHold()
        say("मैं तैयार हूँ बॉस") {
            vState = 0
            vResume()
        }
    }

    override fun onFail(msg: String) {
        if (!alive) return
        Toast.makeText(this, "Offline engine error: $msg", Toast.LENGTH_LONG).show()
        if (useVosk) {
            useVosk = false
            setStatus("Offline fail hua ($msg). Google engine chal raha hai")
            try { offline?.stop() } catch (e: Exception) {}
            offline = null
            say("ऑफलाइन इंजन नहीं चला, गूगल इंजन चालू कर रहा हूँ") { again(100L) }
        }
    }

    override fun onPartial(text: String) {
        if (!useVosk || vBusy || vState != 0 || text.isBlank()) return
        debug("सुना: $text")
        val after = afterWake(norm(text)) ?: return
        cancelWake()
        if (after.length <= 2) {
            val r = Runnable { if (!vBusy && vState == 0) wakeNowV() }
            wakeRun = r
            main.postDelayed(r, 250L)
        }
    }

    override fun onFinal(text: String) {
        if (!useVosk || vBusy || text.isBlank()) return
        debug("सुना: $text")
        val low = norm(text)
        cancelWake()
        val after = afterWake(low)
        if (vState == 0) {
            if (after == null) return
            if (after.length > 2) vStart(after, false) else wakeNowV()
        } else {
            if (after != null && after.length <= 2) return
            vStart(after ?: low, vLocalOnly)
        }
    }

    private fun beep() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            main.postDelayed({ tg.release() }, 500L)
        } catch (e: Exception) {
        }
    }

    // mic chalu rehta hai; bas hum sunna rok dete hain
    private fun vHold() {
        resumeToken++
        vBusy = true
        offline?.pause()
    }

    private fun vResume() {
        val tok = ++resumeToken
        main.postDelayed({
            if (alive && tok == resumeToken) {
                offline?.resume()
                vBusy = false
                armFollow()
            }
        }, 350L)
    }

    private fun armFollow() {
        followRun?.let { main.removeCallbacks(it) }
        val r = Runnable {
            if (!vBusy) {
                vState = 0
                Commands.pending = null
            }
        }
        followRun = r
        main.postDelayed(r, 9000L)
    }

    private fun wakeNowV() {
        cancelWake()
        vHold()
        beep()
        vState = 1
        vLocalOnly = false
        say(ack()) { vResume() }
    }

    private fun vStart(cmd: String, localOnly: Boolean) {
        vHold()
        followRun?.let { main.removeCallbacks(it) }
        handle(cmd, localOnly)
    }

    // kaam ke baad agla hukum sunna
    private fun nextCmd(localOnly: Boolean) {
        if (useVosk) {
            vLocalOnly = localOnly
            vState = 1
            vResume()
        } else {
            listen(true, localOnly)
        }
    }

    // wapas wake word ka intezaar
    private fun nextWake() {
        if (useVosk) {
            vState = 0
            vResume()
        } else {
            again(100L)
        }
    }

    // ---------------- Beech-beech me apne aap bolna ----------------
    private fun scheduleChatter(first: Boolean) {
        chatterRun?.let { main.removeCallbacks(it) }
        val r = Runnable {
            chatterNow(false)
            scheduleChatter(false)
        }
        chatterRun = r
        val mins = getSharedPreferences("cfg", MODE_PRIVATE).getInt("chat_min", 8)
        val delay = if (first) 90_000L else mins * 60_000L * (70 + (Math.random() * 61).toInt()) / 100
        main.postDelayed(r, delay)
    }

    private fun chatterNow(force: Boolean) {
        if (!alive) return
        if (!force) {
            if (!getSharedPreferences("cfg", MODE_PRIVATE).getBoolean("chatter", true)) return
            if (Emotion.quietNow()) return
            if (Commands.pending != null) return
            val am = getSystemService(AudioManager::class.java)
            if (am.isMusicActive || am.mode != AudioManager.MODE_NORMAL) return
            if (!isIdle()) return
        }
        speakProactive(Emotion.idleLine(), null)
    }

    // kaam ke baad: bina wake word bole agla hukum sun lo
    private fun afterLocal() {
        val ch = Emotion.chatNext
        Emotion.chatNext = false
     if (Commands.pending != null) nextCmd(false) else nextCmd(!ch)
    }

    // "fir/phir/uske baad/karke" wale kai kaam ek ke baad ek
    private fun runSteps(steps: List<String>, idx: Int) {
        if (!alive) return
        if (idx >= steps.size) {
            nextCmd(true)
            return
        }
        val r = safeRun(steps[idx])
        if (r == null) {
            say("ये काम मुझे समझ नहीं आया") { main.postDelayed({ runSteps(steps, idx + 1) }, 600L) }
            return
        }
        if (r == Commands.STOP) {
            say("ठीक है बॉस, मैं बंद हो रहा हूँ") { stopSelf() }
            return
        }
        if (Commands.pending != null) {
            say(r) { nextCmd(false) }
            return
        }
        say(r) { main.postDelayed({ runSteps(steps, idx + 1) }, 1200L) }
    }

    private fun handle(low: String, localOnly: Boolean) {
        Emotion.observe(low)
        if (Commands.pending == null) {
            if (has(low, "dobara bolo", "दोबारा बोलो", "phir se bolo", "फिर से बोलो", "repeat", "kya bola", "क्या बोला")) {
                say(lastReply) { nextCmd(false) }
                return
            }

            val dp = Docs.parse(low)
            if (dp != null) {
                val kind = dp.first
                val topic = dp.second
                val useLast = dp.third
                if (topic.isBlank() && !useLast) {
                    Commands.pending = { ans ->
                        beginDoc(kind, ans.trim(), false)
                        "ठीक है बॉस, बना रहा हूँ, थोड़ा समय लगेगा"
                    }
                    say("किस विषय पर बनाऊँ बॉस?") { nextCmd(false) }
                    return
                }
                beginDoc(kind, topic, useLast)
                say("ठीक है बॉस, ${Docs.kindHindi(kind)} बना रहा हूँ, थोड़ा समय लगेगा") { afterLocal() }
                return
            }

            val rm = Extra.parseReminder(low)
            if (rm != null) {
                scheduleReminder(rm.first, rm.second)
                say("ठीक है बॉस, ${rm.first} मिनट बाद याद दिलाऊँगा") { afterLocal() }
                return
            }

            val steps = Extra.split(this, low)
            if (steps.size > 1) {
                runSteps(steps, 0)
                return
            }

            val sq = Search.parse(low)
            if (sq != null && (!localOnly || sq.second)) {
                searchAndSpeak(sq.first, low, sq.second)
                return
            }
        }

        val local = safeRun(low)
        if (local == Commands.STOP) {
            say("ठीक है बॉस, मैं बंद हो रहा हूँ") { stopSelf() }
            return
        }
        if (local != null) {
            val shown = Emotion.decorate(local)
            lastReply = shown
            say(shown) { afterLocal() }
            return
        }
        if (localOnly) {
            nextWake()
            return
        }
        val live = has(low, "mausam", "मौसम", "weather", "news", "khabar", "खबर", "samachar", "समाचार", "score",
            "स्कोर", "price", "rate", "bhav", "भाव", "cricket", "क्रिकेट", "match", "मैच", "stock", "latest")
        if (live) chatBlocking(low) else chatStream(low)
    }

    override fun onDestroy() {
        alive = false
        main.removeCallbacksAndMessages(null)
        JarvisNotif.sink = null
        try { offline?.stop() } catch (e: Exception) {}
        try { rec?.destroy() } catch (e: Exception) {}
        try { tts?.shutdown() } catch (e: Exception) {}
        try {
            dot?.let { getSystemService(WindowManager::class.java).removeView(it) }
        } catch (e: Exception) {}
        try { if (wl?.isHeld == true) wl?.release() } catch (e: Exception) {}
        super.onDestroy()
    }
}

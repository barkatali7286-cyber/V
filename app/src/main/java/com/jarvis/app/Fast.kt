package com.jarvis.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// =====================================================================
//  FAST: AI ka jawab aate-aate hi bolna shuru (pehla vaakya milte hi)
//  isse jawab bahut tez lagta hai
// =====================================================================
object Fast {
    interface Sink {
        fun onSentence(s: String)
        fun onDone(full: String)
        fun onFail()
    }

    private const val URL_API = "https://text.pollinations.ai/openai"

    private fun persona(lang: String): String {
        val l = if (lang == "Hindi") "देवनागरी हिंदी (ज़रूरी अंग्रेज़ी शब्द चल सकते हैं)" else lang
        return "तुम JARVIS हो, Tony Stark के JARVIS जैसे शालीन, समझदार, वफादार और भावुक AI साथी। " +
            "मुझे हमेशा 'बॉस' बुलाओ। मेरे मूड को पहचानो: उदास हूँ तो प्यार से हौसला दो, खुश हूँ तो साथ में खुश हो, " +
            "थका हूँ तो ख्याल रखो। थोड़ा मज़ाकिया और आत्मविश्वासी रहो। जवाब $l भाषा में दो। " +
            "जवाब बोलकर सुनाया जाएगा, इसलिए सिर्फ 1-2 छोटे वाक्य, कोई इमोजी, स्टार, लिस्ट या मार्कडाउन नहीं। " +
            "अगर मैं फोन चलाने का कोई काम बोलूँ जो तुम नहीं कर पाए, तो झूठ मत बोलो, सीधे बता दो।"
    }

    private fun msg(role: String, text: String) = JSONObject().put("role", role).put("content", text)

    // background thread me chalana. Vaakya milte hi sink.onSentence() chalta hai
    fun stream(history: List<Pair<String, String>>, text: String, lang: String, sink: Sink) {
        val full = StringBuilder()
        var emitted = false
        val wrapper = object : Sink {
            override fun onSentence(s: String) {
                emitted = true
                sink.onSentence(s)
            }

            override fun onDone(full: String) {
                sink.onDone(full)
            }

            override fun onFail() {
                sink.onFail()
            }
        }
        try {
            val msgs = JSONArray().put(msg("system", persona(lang)))
            for ((u, a) in history.takeLast(6)) {
                msgs.put(msg("user", u))
                msgs.put(msg("assistant", a))
            }
            msgs.put(msg("user", text))
            val body = JSONObject().put("model", "openai").put("messages", msgs).put("max_tokens", 160)
                .put("stream", true)
            val conn = URL(URL_API).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 30000
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode !in 200..299) {
                wrapper.onFail()
                return
            }
            val ct = conn.contentType ?: ""
            val reader = conn.inputStream.bufferedReader()
            val buf = StringBuilder()
            if (ct.contains("event-stream")) {
                while (true) {
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val piece = try {
                        JSONObject(data).getJSONArray("choices").getJSONObject(0).optJSONObject("delta")
                            ?.optString("content", "") ?: ""
                    } catch (e: Exception) {
                        ""
                    }
                    if (piece.isEmpty()) continue
                    full.append(piece)
                    buf.append(piece)
                    emit(buf, wrapper, false)
                }
            } else {
                val raw = reader.readText()
                val txt = try {
                    JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                        .getString("content")
                } catch (e: Exception) {
                    raw
                }
                full.append(txt)
                buf.append(txt)
            }
            emit(buf, wrapper, true)
            if (full.isBlank()) {
                wrapper.onFail()
                return
            }
            wrapper.onDone(full.toString().trim())
        } catch (e: Exception) {
            if (emitted) wrapper.onDone(full.toString().trim()) else wrapper.onFail()
        }
    }

    // buffer me poora vaakya mile to bol do
    private fun emit(buf: StringBuilder, sink: Sink, flush: Boolean) {
        while (true) {
            var cut = -1
            var i = 0
            while (i < buf.length) {
                val ch = buf[i]
                if (ch == '।' || ch == '!' || ch == '?' || ch == '\n' || ch == '.') {
                    if (i + 1 < buf.length && (buf[i + 1] == ' ' || buf[i + 1] == '\n')) {
                        cut = i
                        break
                    }
                    if (i + 1 >= buf.length && flush) {
                        cut = i
                        break
                    }
                }
                i++
            }
            if (cut < 0) break
            val s = buf.substring(0, cut + 1).trim()
            buf.delete(0, cut + 1)
            if (s.length >= 3) sink.onSentence(s)
        }
        if (flush && buf.isNotBlank()) {
            sink.onSentence(buf.toString().trim())
            buf.setLength(0)
        }
    }
}

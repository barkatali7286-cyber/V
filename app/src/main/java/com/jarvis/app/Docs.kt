package com.jarvis.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =====================================================================
//  DOCS: "pdf banao", "notes banao", "assignment likho" - AI se likhwa kar PDF banata hai
//  PDF phone ke Downloads/Jarvis folder me save hota hai
// =====================================================================
object Docs {
    private const val URL_API = "https://text.pollinations.ai/openai"

    private val MAKE = listOf("bana", "बना", "likh", "लिख", "tayyar", "तैयार", "generate", "create", "design")
    private val DROP = setOf(
        "pdf", "पीडीएफ", "notes", "नोट्स", "note", "assignment", "असाइनमेंट", "essay", "निबंध", "report",
        "रिपोर्ट", "letter", "पत्र", "summary", "सारांश", "application", "आवेदन", "par", "पर", "ke", "के",
        "baare", "बारे", "me", "में", "on", "about", "topic", "vishay", "विषय", "ka", "का", "ki", "की", "ek",
        "एक", "mujhe", "मुझे", "please", "jarvis", "boss", "बॉस", "do", "दो", "kar", "karo", "करो", "de", "दे",
        "dena", "देना", "for", "a", "an", "the", "ko", "को", "wala", "वाला", "pe", "bhai", "भाई", "file", "फाइल",
        "ye", "ये", "yeh", "यह", "isko", "इसको", "iska", "इसका", "ise", "इसे", "iski", "इसकी"
    )
    private val LASTREF = setOf("ye", "ये", "yeh", "यह", "isko", "इसको", "iska", "इसका", "ise", "इसे", "iski", "इसकी")

    private fun has(t: String, vararg w: String) = w.any { t.contains(it) }

    // (prakar, vishay, pichla jawab hi use karna hai?)
    fun parse(t: String): Triple<String, String, Boolean>? {
        if (!MAKE.any { t.contains(it) }) return null
        val kind = when {
            has(t, "assignment", "असाइनमेंट") -> "assignment"
            has(t, "notes", "नोट्स") -> "notes"
            has(t, "essay", "निबंध") -> "essay"
            has(t, "report", "रिपोर्ट") -> "report"
            has(t, "letter", "पत्र", "application", "आवेदन") -> "letter"
            has(t, "summary", "सारांश") -> "summary"
            has(t, "pdf", "पीडीएफ") -> "document"
            else -> return null
        }
        val words = t.split(" ").filter { it.isNotBlank() }
        val ref = words.any { it in LASTREF }
        val topic = words.filter { w ->
            w !in DROP && MAKE.none { w.contains(it) }
        }.joinToString(" ").trim()
        return Triple(kind, topic, ref && topic.isBlank())
    }

    fun kindHindi(kind: String): String = when (kind) {
        "assignment" -> "असाइनमेंट"
        "notes" -> "नोट्स"
        "essay" -> "निबंध"
        "report" -> "रिपोर्ट"
        "letter" -> "पत्र"
        "summary" -> "सारांश"
        else -> "डॉक्यूमेंट"
    }

    private fun msg(role: String, text: String) = JSONObject().put("role", role).put("content", text)

    // AI se lamba text likhwata hai (background thread me chalana)
    fun generate(kind: String, topic: String): String? {
        val dev = topic.any { it in '\u0900'..'\u097F' }
        val lang = if (dev) "Hindi (Devanagari script)" else "English"
        val what = when (kind) {
            "assignment" -> "a complete school/college assignment with an Introduction, several main sections and a Conclusion"
            "notes" -> "clear, well organised study notes with key points, definitions and examples"
            "essay" -> "a well written essay with an introduction, body paragraphs and a conclusion"
            "report" -> "a formal report with Introduction, Findings, Analysis and Conclusion"
            "letter" -> "a properly formatted formal letter or application"
            "summary" -> "a clear and concise summary"
            else -> "a clear, well structured document"
        }
        val sys = "You are an expert writer. Write $what in $lang about the given topic. " +
            "Put each numbered section heading on its own line (like '1. Introduction'). Use short paragraphs and " +
            "'-' bullet points where useful. Do not use markdown symbols such as ** or #. " +
            "Write about 600 words. Output only the document text."
        val msgs = JSONArray().put(msg("system", sys)).put(msg("user", "Topic: $topic"))
        val body = JSONObject().put("model", "openai").put("messages", msgs).put("max_tokens", 1500)
        return try {
            val conn = URL(URL_API).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 100000
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode !in 200..299) return null
            val raw = conn.inputStream.bufferedReader().use { it.readText() }
            val txt = try {
                JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            } catch (e: Exception) {
                raw
            }
            if (txt.isBlank()) null else txt.trim()
        } catch (e: Exception) {
            null
        }
    }

    // PDF banata hai aur uska Uri deta hai (khol sakne ke liye)
    fun makePdf(c: Context, title: String, text: String): Uri? {
        val doc = PdfDocument()
        val pw = 595
        val ph = 842
        val margin = 48f
        val maxW = pw - 2 * margin
        val bodyP = Paint()
        bodyP.isAntiAlias = true
        bodyP.textSize = 12f
        bodyP.color = Color.BLACK
        val headP = Paint(bodyP)
        headP.textSize = 14f
        headP.typeface = Typeface.DEFAULT_BOLD
        val titleP = Paint(bodyP)
        titleP.textSize = 20f
        titleP.typeface = Typeface.DEFAULT_BOLD

        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, pageNo).create())
        var canvas = page.canvas
        var y = margin + 20f

        fun ensure(h: Float) {
            if (y + h > ph - margin) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, pageNo).create())
                canvas = page.canvas
                y = margin + 20f
            }
        }

        fun drawWrapped(s: String, p: Paint, lh: Float) {
            var rest = s.trim()
            while (rest.isNotEmpty()) {
                var n = p.breakText(rest, true, maxW, null)
                if (n <= 0) n = 1
                if (n < rest.length) {
                    val sp = rest.lastIndexOf(' ', n)
                    if (sp > 0) n = sp
                }
                val line = rest.substring(0, n).trim()
                ensure(lh)
                canvas.drawText(line, margin, y, p)
                y += lh
                rest = rest.substring(n).trimStart()
            }
        }

        drawWrapped(title, titleP, 28f)
        y += 8f
        for (para in text.split("\n")) {
            val line = para.trim()
            if (line.isEmpty()) {
                y += 8f
                continue
            }
            val isHead = line.length < 70 && (line[0].isDigit() || line.endsWith(":"))
            if (isHead) {
                y += 6f
                drawWrapped(line, headP, 20f)
            } else {
                drawWrapped(line, bodyP, 17f)
            }
        }
        doc.finishPage(page)

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safe = title.replace(Regex("[^\\p{L}\\p{N}]+"), "_").take(25)
        val name = "Jarvis_${safe}_$stamp.pdf"
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues()
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Jarvis")
                val uri = c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri == null) {
                    null
                } else {
                    c.contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
                    uri
                }
            } else {
                val dir = c.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                if (dir == null) {
                    null
                } else {
                    dir.mkdirs()
                    val f = File(dir, name)
                    f.outputStream().use { doc.writeTo(it) }
                    Uri.fromFile(f)
                }
            }
        } catch (e: Exception) {
            null
        } finally {
            doc.close()
        }
    }
}

package com.jarvis.app

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

// =====================================================================
//  NOTIFICATION: nayi notification aate hi Jarvis bata deta hai
//  (Settings me "Notification access" Jarvis ko dena padta hai)
// =====================================================================
class JarvisNotif : NotificationListenerService() {
    companion object {
        @Volatile
        var sink: ((String) -> Unit)? = null

        @Volatile
        var connected: Boolean = false

        val recent = ArrayList<String>()
        private var lastKey = ""
        private var lastAt = 0L

        fun summary(max: Int): String {
            if (!connected) return "नोटिफिकेशन पढ़ने के लिए Notification access चालू कीजिए बॉस"
            val snap: List<String> = synchronized(recent) { recent.take(max) }
            if (snap.isEmpty()) return "कोई नई नोटिफिकेशन नहीं है बॉस"
            return "ताज़ा नोटिफिकेशन: " + snap.joinToString(". ")
        }
    }

    override fun onListenerConnected() {
        connected = true
    }

    override fun onListenerDisconnected() {
        connected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        try {
            if (sbn.packageName == packageName) return
            val n = sbn.notification ?: return
            if (sbn.isOngoing) return
            if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
            val cat = n.category ?: ""
            if (cat == Notification.CATEGORY_TRANSPORT || cat == Notification.CATEGORY_PROGRESS ||
                cat == Notification.CATEGORY_SERVICE || cat == Notification.CATEGORY_SYSTEM ||
                cat == Notification.CATEGORY_STATUS
            ) return

            val ex = n.extras
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))
                ?.toString() ?: ""
            if (title.isBlank() && text.isBlank()) return

            val label = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
            } catch (e: Exception) {
                sbn.packageName
            }

            val key = sbn.packageName + title + text
            val now = System.currentTimeMillis()
            if (key == lastKey && now - lastAt < 8000L) return
            lastKey = key
            lastAt = now

            val detail = getSharedPreferences("cfg", MODE_PRIVATE).getBoolean("notif_detail", true)
            val shortText = if (text.length > 110) text.take(110) else text
            val line = if (detail) {
                if (title.isNotBlank() && shortText.isNotBlank()) "$label पर $title का संदेश: $shortText"
                else if (title.isNotBlank()) "$label पर $title"
                else "$label पर $shortText"
            } else {
                "$label पर नई नोटिफिकेशन आई है"
            }
            synchronized(recent) {
                recent.add(0, line)
                while (recent.size > 15) recent.removeAt(recent.size - 1)
            }
            sink?.invoke("बॉस, $line")
        } catch (e: Exception) {
        }
    }
}

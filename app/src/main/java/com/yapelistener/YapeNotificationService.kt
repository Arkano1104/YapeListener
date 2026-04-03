package com.yapelistener

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.Locale
import java.util.regex.Pattern

class YapeNotificationService : NotificationListenerService() {

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    companion object {
        private const val TAG = "YapeListener"
        private const val CHANNEL_ID = "yape_alerts"
        private const val CHANNEL_NAME = "Alertas Yape"
        private const val NOTIF_ID = 9001

        private val YAPE_PACKAGES = setOf(
            "com.bcp.innovacxion.yapeapp",
            "pe.com.bcp.innovacxion.yapeapp",
            "pe.com.yape",
            "com.yape"
        )
    }

    override fun onCreate() {
        super.onCreate()
        setupTTS()
        createNotificationChannel()
    }

    private fun setupTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                var result = tts?.setLanguage(Locale("es", "PE"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    result = tts?.setLanguage(Locale("es", "ES"))
                }
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
                tts?.setSpeechRate(0.82f)
                tts?.setPitch(1.25f)
                ttsReady = true
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return
        if (!YAPE_PACKAGES.contains(packageName)) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""
        val fullText = "$title $text $bigText"

        val yapeInfo = parseYapeNotification(fullText) ?: return
        speakYape(yapeInfo.person, yapeInfo.amount)
        showHeadsUpNotification(yapeInfo.person, yapeInfo.amount)
    }

    data class YapeInfo(val person: String, val amount: String)

    private fun parseYapeNotification(text: String): YapeInfo? {
        val amountPattern = Pattern.compile("""S/\.?\s*([\d]+(?:[.,]\d{1,2})?)""", Pattern.CASE_INSENSITIVE)
        val amountMatcher = amountPattern.matcher(text)
        val rawAmount = if (amountMatcher.find()) amountMatcher.group(1) else null

        val personPatterns = listOf(
            Pattern.compile("""^(.+?)\s+te\s+envi[oó]\s+un\s+pago""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""([^\s].+?)\s+te\s+envi[oó]\s+un\s+pago""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""^([A-ZÁÉÍÓÚÑ].+?)\s+te\s+yap[eé]""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""([A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\s[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+)*)\s+te\s+yap[eé]""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""recibiste.*?de\s+([A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\s[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+)*)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""([A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\s[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+)*)\s+te\s+env[ií]""", Pattern.CASE_INSENSITIVE),
        )

        var person: String? = null
        for (pattern in personPatterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                person = matcher.group(1)?.trim()
                if (!person.isNullOrBlank()) break
            }
        }

        if (rawAmount == null && person == null) return null

        val formattedAmount = rawAmount?.let {
            val num = it.replace(",", ".").toDoubleOrNull()
            if (num != null) "S/ %.2f".format(num) else "S/ $it"
        } ?: "monto desconocido"

        return YapeInfo(person ?: "alguien", formattedAmount)
    }

    private fun speakYape(person: String, amount: String) {
        if (!ttsReady) return
        val spokenAmount = amount.replace("S/", "").trim().let {
            val num = it.toDoubleOrNull()
            if (num != null) {
                val soles = num.toLong()
                val centimos = Math.round((num - soles) * 100)
                if (centimos > 0) "$soles soles con $centimos céntimos" else "$soles soles"
            } else it
        }
        val firstName = person.split(" ", "*").first().trim()
tts?.setSpeechRate(0.85f)
tts?.setPitch(1.2f)
tts?.speak("¡Yape! De $firstName... $spokenAmount", TextToSpeech.QUEUE_FLUSH, null, "yape_${System.currentTimeMillis()}")
    }

    private fun showHeadsUpNotification(person: String, amount: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("💸 ¡YAPE!")
            .setContentText("$person te envió $amount")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$person te envió $amount"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 300, 100, 300))
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alertas cuando recibes un Yape"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 100, 300)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

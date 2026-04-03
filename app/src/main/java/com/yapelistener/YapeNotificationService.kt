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

        // Yape package names (official app)
        private val YAPE_PACKAGES = setOf(
            "pe.com.bcp.innovacxion.yapeapp",
            "pe.com.yape",
            "com.yape"
        )
    }

    override fun onCreate() {
        super.onCreate()
        setupTTS()
        createNotificationChannel()
        Log.d(TAG, "YapeNotificationService started")
    }

    private fun setupTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Try Peruvian Spanish, fallback to any Spanish
                var result = tts?.setLanguage(Locale("es", "PE"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    result = tts?.setLanguage(Locale("es", "ES"))
                }
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
                tts?.setSpeechRate(0.9f)
                tts?.setPitch(1.1f)
                ttsReady = true
                Log.d(TAG, "TTS ready")
            } else {
                Log.e(TAG, "TTS init failed: $status")
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return

        // Only process Yape notifications
        if (!YAPE_PACKAGES.contains(packageName)) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""

        val fullText = "$title $text $bigText"
        Log.d(TAG, "Yape notification: $fullText")

        val yapeInfo = parseYapeNotification(fullText) ?: return

        // Speak it
        speakYape(yapeInfo.person, yapeInfo.amount)

        // Show heads-up notification
        showHeadsUpNotification(yapeInfo.person, yapeInfo.amount)
    }

    data class YapeInfo(val person: String, val amount: String)

    private fun parseYapeNotification(text: String): YapeInfo? {
        // Amount pattern: S/ or S/. followed by digits
        val amountPattern = Pattern.compile("""S/\.?\s?([\d,]+\.?\d{0,2})""", Pattern.CASE_INSENSITIVE)
        val amountMatcher = amountPattern.matcher(text)
        val rawAmount = if (amountMatcher.find()) amountMatcher.group(1) else null

        // Person patterns — Yape usually says:
        // "Juan te yapeo S/50"
        // "Recibiste S/50 de Juan"
        // "Juan te envio S/50"
        val personPatterns = listOf(
            Pattern.compile("""^([A-ZÁÉÍÓÚÑ][a-záéíóúñA-ZÁÉÍÓÚÑ\s]+?)\s+te\s+yap[eé]o""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""([A-ZÁÉÍÓÚÑ][a-záéíóúñA-ZÁÉÍÓÚÑ\s]+?)\s+te\s+yap[eé]o""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""recibiste.*?de\s+([A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\s[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+)*)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""de:\s*([A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\s[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+)*)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""([A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\s[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+)*)\s+te\s+env[ií]""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""([A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\s[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+)*)\s+yap[eé]""", Pattern.CASE_INSENSITIVE),
        )

        var person: String? = null
        for (pattern in personPatterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                person = matcher.group(1)?.trim()
                break
            }
        }

        // If no amount and no person found, skip
        if (rawAmount == null && person == null) return null

        val formattedAmount = rawAmount?.let {
            val num = it.replace(",", ".").toDoubleOrNull()
            if (num != null) "S/ %.2f".format(num) else "S/ $it"
        } ?: "monto desconocido"

        val finalPerson = person ?: "alguien"

        return YapeInfo(finalPerson, formattedAmount)
    }

    private fun speakYape(person: String, amount: String) {
        if (!ttsReady) {
            Log.w(TAG, "TTS not ready yet")
            return
        }
        // Clean amount for speech: "S/ 50.00" → "50 soles"
        val spokenAmount = amount
            .replace("S/", "")
            .trim()
            .let {
                val num = it.toDoubleOrNull()
                if (num != null) {
                    val soles = num.toLong()
                    val centimos = ((num - soles) * 100).toLong()
                    if (centimos > 0) "$soles soles con $centimos céntimos"
                    else "$soles soles"
                } else it
            }

        val speech = "¡Yape! De $person, $spokenAmount"
        Log.d(TAG, "Speaking: $speech")

        tts?.speak(speech, TextToSpeech.QUEUE_FLUSH, null, "yape_${System.currentTimeMillis()}")
    }

    private fun showHeadsUpNotification(person: String, amount: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("💸 ¡YAPE!")
            .setContentText("$person te yapeo $amount")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$person te yapeo $amount")
                .setBigContentTitle("💸 ¡YAPE!"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 300, 100, 300))
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true) // heads-up
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas cuando recibes un Yape"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 100, 300)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

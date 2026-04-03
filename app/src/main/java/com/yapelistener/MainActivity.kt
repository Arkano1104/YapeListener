package com.yapelistener

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        updateStatus()

        findViewById<Button>(R.id.btnPermission).setOnClickListener {
            if (!isNotificationListenerEnabled()) {
                showPermissionDialog()
            }
        }

        // Request POST_NOTIFICATIONS on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val statusText = findViewById<TextView>(R.id.tvStatus)
        val statusIcon = findViewById<ImageView>(R.id.ivStatus)
        val btnPermission = findViewById<Button>(R.id.btnPermission)

        if (isNotificationListenerEnabled()) {
            statusText.text = "✅ Activo — Escuchando Yapes"
            statusText.setTextColor(0xFF2ECC71.toInt())
            btnPermission.text = "Permiso concedido ✓"
            btnPermission.isEnabled = false
            btnPermission.alpha = 0.5f
        } else {
            statusText.text = "⚠️ Permiso requerido"
            statusText.setTextColor(0xFFE74C3C.toInt())
            btnPermission.text = "Dar permiso de notificaciones"
            btnPermission.isEnabled = true
            btnPermission.alpha = 1f
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val cn = ComponentName(this, YapeNotificationService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return !TextUtils.isEmpty(flat) && flat.contains(cn.flattenToString())
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permiso necesario")
            .setMessage(
                "Para detectar los Yapes, la app necesita acceso a las notificaciones.\n\n" +
                "En la siguiente pantalla, busca \"Yape Listener\" y actívala."
            )
            .setPositiveButton("Ir a configuración") { _, _ ->
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}

package com.example.escoly3

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class LocationForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(1, createNotification())
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "location_channel",
                "Rastreo de Ubicación",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Enviando ubicación en segundo plano"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "location_channel")
            .setContentTitle("Escoly3 - Rastreo Activo")
            .setContentText("Compartiendo ubicación en tiempo real")
            .setSmallIcon(android.R.drawable.ic_dialog_map) // Icono por defecto de Android
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
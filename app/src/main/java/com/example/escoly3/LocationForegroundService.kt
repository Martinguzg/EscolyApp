@file:Suppress("DEPRECATION")

package com.example.escoly3

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class LocationForegroundService : Service() {

    private var deviceId: String? = null
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var locationManager: LocationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Servicio foreground creado")

        // Configurar WakeLock para evitar que el sistema suspenda el servicio
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Escoly3::LocationWakeLock"
        ).apply {
            acquire(10 * 60 * 1000L) // 10 minutos
        }

        locationManager = LocationManager(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_TRACKING_ACTION" -> {
                deviceId = intent.getStringExtra("DEVICE_ID") ?: run {
                    Log.e(TAG, "ID de dispositivo no proporcionado")
                    stopSelf()
                    return START_STICKY
                }
                startForegroundService(deviceId!!)

                locationManager.startLocationUpdates(deviceId!!) { location ->
                    Log.d(
                        TAG,
                        "Ubicación desde ForegroundService: ${location.latitude}, ${location.longitude}"
                    )
                }
            }

            "STOP_TRACKING_ACTION" -> {
                stopForeground(true)
                stopSelf()
            }

            else -> {
                Log.w(TAG, "Acción desconocida recibida: ${intent?.action}")
                stopSelf()
            }
        }
        return START_STICKY or START_REDELIVER_INTENT // Mejor manejo de reinicio
    }

    private fun startForegroundService(deviceId: String) {
        try {
            createNotificationChannel()
            val notification = createNotification(deviceId)

            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "Servicio foreground iniciado para $deviceId")

        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar servicio foreground: ${e.message}")
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Rastreo de Ubicación",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Compartiendo ubicación en tiempo real"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(deviceId: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Escoly3 - Rastreo Activo")
            .setContentText("Dispositivo: $deviceId")
            .setSmallIcon(R.drawable.app_logo)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Servicio foreground detenido")

        // Liberar WakeLock
        if (::wakeLock.isInitialized && wakeLock.isHeld) {
            wakeLock.release()
        }

        // Reiniciar servicio si fue detenido inesperadamente
        if (deviceId != null) {
            val restartIntent = Intent(this, LocationForegroundService::class.java).apply {
                action = "START_TRACKING_ACTION"
                putExtra("DEVICE_ID", deviceId)
            }
            startService(restartIntent)
        }
    }

    companion object {
        private const val TAG = "LocationForegroundSvc"
        private const val CHANNEL_ID = "location_tracking_channel_escoly3"
        private const val NOTIFICATION_ID = 1001

        fun startService(context: Context, deviceId: String) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = "START_TRACKING_ACTION"
                putExtra("DEVICE_ID", deviceId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = "STOP_TRACKING_ACTION"
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
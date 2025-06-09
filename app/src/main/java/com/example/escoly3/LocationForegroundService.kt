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
    private lateinit var locationManager: LocationManager // Cambiado a lateinit

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Servicio foreground creado")

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Escoly3::LocationWakeLock"
        ).apply {
            // Considerar la duración del wakelock. Si el servicio necesita correr indefinidamente,
            // un wakelock sin tiempo límite que se libera en onDestroy es más común.
            // El tiempo actual es de 10 minutos.
            acquire(10 * 60 * 1000L)
        }

        locationManager = LocationManager(applicationContext) // Inicialización aquí
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand recibido con acción: ${intent?.action}")

        // Extraer el deviceId si está disponible.
        intent?.getStringExtra("DEVICE_ID")?.let {
            if (it.isNotBlank()) {
                deviceId = it
            }
        }

        // --- CAMBIO CLAVE: SIEMPRE INICIAR EN FOREGROUND SI LA ACCIÓN ES DE INICIO ---
        // Si la acción es iniciar, SIEMPRE debemos llamar a startForeground PRIMERO.
        if (intent?.action == ACTION_START_TRACKING) {
            if (deviceId.isNullOrBlank()) {
                Log.e(TAG, "ID de dispositivo no proporcionado para iniciar. Deteniendo servicio.")
                stopSelf() // No podemos iniciar sin ID
                return START_NOT_STICKY
            }
            Log.i(TAG, "Iniciando servicio en primer plano para el dispositivo: $deviceId")
            // Esta llamada ahora es lo primero que se hace, cumpliendo la regla de Android.
            startForegroundServiceNotification(deviceId!!)
        }

        // Si el servicio no tiene un deviceId en este punto y la acción no es para detenerse,
        // no tiene sentido que continúe.
        if (deviceId.isNullOrBlank() && intent?.action != ACTION_STOP_TRACKING) {
            Log.e(TAG, "El servicio no puede operar sin un Device ID. Deteniéndose.")
            stopSelf()
            return START_NOT_STICKY
        }

        // Ahora procesamos la acción
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                Log.i(TAG, "Iniciando actualizaciones de ubicación para: $deviceId")
                locationManager.startLocationUpdates(deviceId!!) { location ->
                    Log.d(TAG, "Ubicación (desde Service) para $deviceId: ${location.latitude}")
                }
            }
            ACTION_STOP_TRACKING -> {
                Log.i(TAG, "Deteniendo rastreo para el dispositivo: $deviceId")
                locationManager.stopLocationUpdates()
                stopForeground(STOP_FOREGROUND_REMOVE) // Usar esta constante
                stopSelf()
                deviceId = null
            }
            ACTION_PAUSE_FIREBASE_UPDATES -> {
                Log.i(TAG, "Pausando actualizaciones de ubicación a Firebase para $deviceId")
                locationManager.stopLocationUpdates()
            }
            ACTION_RESUME_FIREBASE_UPDATES -> {
                Log.i(TAG, "Reanudando actualizaciones de ubicación a Firebase para $deviceId")
                locationManager.startLocationUpdates(deviceId!!) { location ->
                    Log.d(TAG, "Ubicación (tras reanudar) para $deviceId: ${location.latitude}")
                }
            }
        }

        return START_REDELIVER_INTENT
    }


    private fun startForegroundServiceNotification(deviceId: String) { // Renombrado
        try {
            createNotificationChannel()
            val notification = createNotification(deviceId)

            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "Servicio foreground iniciado con notificación para $deviceId")

        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar servicio foreground con notificación: ${e.message}", e)
            stopSelf() // Detener si no se puede iniciar en foreground
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Rastreo de Ubicación Escoly3", // Nombre más descriptivo
                NotificationManager.IMPORTANCE_LOW // IMPORTANCE_LOW para que sea menos intrusivo
            ).apply {
                description = "Servicio activo para compartir ubicación en tiempo real."
                setShowBadge(false) // No mostrar punto en el ícono de la app
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE // O VISIBILITY_PUBLIC si es necesario
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            Log.d(TAG, "Canal de notificación creado/actualizado.")
        }
    }

    private fun createNotification(deviceId: String): Notification {
        // Considerar añadir un PendingIntent para abrir la app al tocar la notificación.
        // val pendingIntent: PendingIntent = ...

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Escoly3 Rastreo Activo")
            .setContentText("Monitoreando dispositivo: $deviceId")
            .setSmallIcon(R.drawable.app_logo) // Asegúrate que este drawable existe
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // La notificación no se puede descartar por el usuario
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE) // O VISIBILITY_PUBLIC
            .setOnlyAlertOnce(true) // Solo sonar/vibrar la primera vez
            .setShowWhen(false) // No mostrar la hora de la notificación
            // .setContentIntent(pendingIntent) // Opcional
            .build()
    }

    override fun onDestroy() {
        Log.d(TAG, "Servicio foreground onDestroy llamado para dispositivo: $deviceId")
        // Liberar WakeLock si está inicializado y adquirido
        if (::wakeLock.isInitialized && wakeLock.isHeld) {
            wakeLock.release()
            Log.d(TAG, "WakeLock liberado.")
        }
        // Detener LocationManager si no se hizo antes (por si onDestroy es llamado directamente por el sistema)
        locationManager.stopLocationUpdates()
        locationManager.shutdownExecutorService() // Asegurarse que el executor del locationManager se apague

        Log.d(TAG, "Servicio foreground completamente detenido y limpiado.")
        super.onDestroy() // Llamar al final
    }

    companion object {
        private const val TAG = "LocationForegroundSvc"
        private const val CHANNEL_ID = "location_tracking_channel_escoly3"
        private const val NOTIFICATION_ID = 1001 // Debe ser > 0
        const val ACTION_START_TRACKING = "START_TRACKING_ACTION"
        const val ACTION_STOP_TRACKING = "STOP_TRACKING_ACTION"
        const val ACTION_PAUSE_FIREBASE_UPDATES = "PAUSE_FIREBASE_UPDATES" // <-- NUEVA
        const val ACTION_RESUME_FIREBASE_UPDATES = "RESUME_FIREBASE_UPDATES" // <-- NUEVA

        fun startService(context: Context, deviceId: String) {
            if (deviceId.isBlank()) {
                Log.e(TAG, "Intento de iniciar servicio con deviceId vacío.")
                return
            }
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = "START_TRACKING_ACTION"
                putExtra("DEVICE_ID", deviceId)
            }
            ContextCompat.startForegroundService(context, intent)
            Log.d(TAG, "Solicitud para iniciar servicio enviada para deviceId: $deviceId")
        }

        fun stopService(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = "STOP_TRACKING_ACTION"
            }
            // No es necesario pasar deviceId para detener, el servicio usará el que tenga (si aplica) o simplemente se detendrá.
            ContextCompat.startForegroundService(context, intent) // Correcto para enviar comando a un servicio en foreground
            // O context.stopService(intent) si el servicio no necesita procesar la acción STOP y solo debe detenerse
            Log.d(TAG, "Solicitud para detener servicio enviada.")
        }
    }
}
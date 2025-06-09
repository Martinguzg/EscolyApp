package com.example.escoly3

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LocationManager(private val context: Context) {
    private val database = FirebaseDatabase.getInstance().reference
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private var locationCallback: LocationCallback? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    private var currentDeviceId: String? = null
    private var lastSentTime: Long = 0
    @Volatile // Importante para que sea seguro entre hilos (threads)
    private var isPaused = false

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(deviceId: String, onLocation: (Location) -> Unit) {
        currentDeviceId = deviceId

        if (deviceId.isBlank()) {
            Log.e(TAG, "ID de dispositivo inválido para startLocationUpdates")
            return
        }

        if (this.locationCallback != null) {
            Log.w(TAG, "startLocationUpdates llamado de nuevo para $deviceId. Deteniendo actualizaciones previas.")
            internalStopLocationUpdates(shutdownExecutor = false)
        }

        val request = createLocationRequest()
        this.locationCallback = createLocationCallback(deviceId, onLocation)

        try {
            Log.d(TAG, "Solicitando UNA SOLA actualización de ubicación para $deviceId.")
            fusedClient.requestLocationUpdates(
                request,
                this.locationCallback!!,
                Looper.getMainLooper()
            ).addOnSuccessListener {
                Log.d(TAG, "Solicitud de actualización única registrada exitosamente para $deviceId.")
            }.addOnFailureListener { e ->
                Log.e(TAG, "Error al registrar solicitud de actualización única para $deviceId: ${e.message}")
                if (!executor.isShutdown) {
                    executor.execute {
                        try {
                            Thread.sleep(RETRY_DELAY_MS_FAILURE_REGISTRATION)
                            if (this.currentDeviceId == deviceId && this.locationCallback != null) {
                                Log.d(TAG, "Reintentando registrar startLocationUpdates (única vez) para $deviceId.")
                                startLocationUpdates(deviceId, onLocation)
                            }
                        } catch (ie: InterruptedException) {
                            Thread.currentThread().interrupt()
                            Log.w(TAG, "Reintento de registro (única vez) interrumpido para $deviceId.", ie)
                        }
                    }
                }
            }
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException al solicitar ubicación para $deviceId. ¿Faltan permisos?", se)
        } catch (e: Exception) {
            Log.e(TAG, "Excepción general al solicitar ubicación para $deviceId", e)
        }
    }

    private fun createLocationRequest(): LocationRequest {
        val builder = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_UPDATE_INTERVAL_MS
        )
        builder.setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
        builder.setWaitForAccurateLocation(true)
        builder.setMinUpdateDistanceMeters(MIN_DISPLACEMENT_METERS_SINGLE_UPDATE)
        return builder.build()
    }

    fun pauseFirebaseUploads() {
        Log.d(TAG, "Envío a Firebase PAUSADO.")
        isPaused = true
    }

    fun resumeFirebaseUploads() {
        Log.d(TAG, "Envío a Firebase REANUDADO.")
        isPaused = false
    }

    private fun createLocationCallback(
        deviceId: String,
        onLocation: (Location) -> Unit
    ): LocationCallback {
        return object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                // --- NUEVO ---
                // Si el envío está pausado, ignoramos la ubicación y salimos de la función.
                if (isPaused) {
                    Log.d(TAG, "Recibida ubicación, pero el envío está pausado. Ignorando.")
                    return
                }
                // --- FIN DE LO NUEVO ---

                // El resto del código solo se ejecuta si no está pausado.
                if (executor.isShutdown) {
                    Log.w(TAG, "Executor apagado, no se procesará onLocationResult para $deviceId.")
                    return
                }

                val location = result.locations.lastOrNull()
                if (location == null) {
                    Log.w(TAG, "onLocationResult para $deviceId no contiene ubicaciones.")
                    return
                }

                // Log informativo (he quitado la palabra "única vez" para evitar confusión)
                Log.d(TAG, "Recibida ubicación para $deviceId. Precisión: ${location.accuracy}.")

                executor.execute {
                    if (isLocationAcceptable(location)) {
                        val now = System.currentTimeMillis()
                        if (now - lastSentTime >= MIN_INTERVAL_BETWEEN_SAVES_MS) {
                            lastSentTime = now
                            Log.i(TAG, "Ubicación aceptable para $deviceId. Guardando y notificando.")
                            saveDeviceLocation(deviceId, location)
                            onLocation(location)
                        } else {
                            Log.d(TAG, "Actualización para $deviceId ignorada por tiempo (lastSentTime): ${now - lastSentTime}ms")
                        }
                    } else {
                        Log.w(TAG, "Ubicación recibida para $deviceId NO aceptable. Precisión: ${location.accuracy}, Velocidad: ${location.speed}")
                    }
                }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                Log.d(TAG, "Disponibilidad de ubicación para $deviceId: ${availability.isLocationAvailable}")
                if (!availability.isLocationAvailable) {
                    Log.w(TAG, "Ubicación no disponible temporalmente para $deviceId.")
                }
            }
        }
    }

    private fun isLocationAcceptable(location: Location): Boolean {
        val isAccurate = location.accuracy > 0f && location.accuracy < MAX_ACCEPTABLE_ACCURACY
        val isSpeedOk = !location.hasSpeed() || location.speed < MAX_ACCEPTABLE_SPEED || location.speed == 0.0f
        return isAccurate && isSpeedOk
    }

    // CORREGIDO: Solo guarda la ubicación, NO modifica in_safe_zone
    private fun saveDeviceLocation(deviceId: String, location: Location) {
        if (deviceId.isBlank()) {
            Log.e(TAG, "ID de dispositivo inválido para saveDeviceLocation")
            return
        }
        try {
            val timestampKey = dateFormat.format(Date())
            val updates = hashMapOf<String, Any>(
                // REMOVIDO: "devices/$deviceId/in_safe_zone" to false,
                // Esto causaba que siempre se pusiera false al guardar ubicación

                "devices/$deviceId/location_history/$timestampKey" to hashMapOf(
                    "lat" to location.latitude,
                    "lng" to location.longitude,
                    "dateTime" to ServerValue.TIMESTAMP,
                    "battery" to getBatteryLevel()
                )
            )

            database.updateChildren(updates)
                .addOnSuccessListener {
                    Log.d(TAG, "Ubicación (única) guardada para $deviceId en $timestampKey")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error al guardar ubicación (única) para $deviceId", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error crítico al preparar datos para guardar ubicación (única) de $deviceId", e)
        }
    }

    @SuppressLint("PrivateApi", "BatteryLife")
    private fun getBatteryLevel(): Int {
        return try {
            val bm = context.applicationContext.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener nivel de batería", e)
            -1
        }
    }

    fun stopLocationUpdates() {
        internalStopLocationUpdates(shutdownExecutor = true)
    }

    private fun internalStopLocationUpdates(shutdownExecutor: Boolean) {
        val callbackToStop = this.locationCallback
        if (callbackToStop != null) {
            Log.d(TAG, "Intentando detener actualizaciones de ubicación para el dispositivo: $currentDeviceId")
            try {
                fusedClient.removeLocationUpdates(callbackToStop)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d(TAG, "Callback de ubicación removido exitosamente para $currentDeviceId.")
                        } else {
                            Log.w(TAG, "Falla al remover callback de ubicación para $currentDeviceId.", task.exception)
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Excepción al remover callback de ubicación para $currentDeviceId.", e)
            } finally {
                if (this.locationCallback === callbackToStop) {
                    this.locationCallback = null
                }
            }
        } else {
            Log.d(TAG, "stopLocationUpdates llamado pero no había callback activo.")
        }

        if (shutdownExecutor) {
            shutdownExecutorService()
        }
    }

    fun shutdownExecutorService() {
        if (!executor.isTerminated && !executor.isShutdown) {
            Log.d(TAG, "Solicitando apagado del ExecutorService...")
            executor.shutdown()
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    Log.w(TAG, "ExecutorService no terminó a tiempo, forzando apagado.")
                    executor.shutdownNow()
                } else {
                    Log.d(TAG, "ExecutorService apagado correctamente.")
                }
            } catch (ie: InterruptedException) {
                Log.w(TAG, "Interrupción mientras se esperaba apagado del ExecutorService.")
                executor.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }
    }

    companion object {
        private const val TAG = "LocationManager"
        private const val LOCATION_UPDATE_INTERVAL_MS = 5000L
        private const val MIN_UPDATE_INTERVAL_MS = 2500L
        private const val MAX_ACCEPTABLE_ACCURACY = 50f
        private const val MAX_ACCEPTABLE_SPEED = 50f
        private const val MIN_DISPLACEMENT_METERS_SINGLE_UPDATE = 10f
        private const val RETRY_DELAY_MS_FAILURE_REGISTRATION = 7000L
        private const val MIN_INTERVAL_BETWEEN_SAVES_MS = 2500L
    }
}
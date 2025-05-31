package com.example.escoly3

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
// import android.os.Build // No es estrictamente necesario aquí si la biblioteca de play-services-location es reciente
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
    private var lastSentTime: Long = 0 // Para evitar envíos demasiado rápidos si el callback se dispara varias veces por error

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(deviceId: String, onLocation: (Location) -> Unit) {
        currentDeviceId = deviceId

        if (deviceId.isBlank()) {
            Log.e(TAG, "ID de dispositivo inválido para startLocationUpdates")
            return
        }

        // Si ya hay un callback, detener las actualizaciones anteriores para evitar duplicados.
        if (this.locationCallback != null) {
            Log.w(TAG, "startLocationUpdates llamado de nuevo para $deviceId. Deteniendo actualizaciones previas.")
            internalStopLocationUpdates(shutdownExecutor = false) // No apagar el executor aquí
        }

        val request = createLocationRequest()
        // Es importante asignar a la variable miembro *antes* de usarla en la solicitud.
        this.locationCallback = createLocationCallback(deviceId, onLocation)

        try {
            Log.d(TAG, "Solicitando UNA SOLA actualización de ubicación para $deviceId.")
            fusedClient.requestLocationUpdates(
                request,
                this.locationCallback!!, // Usar la variable miembro
                Looper.getMainLooper()
            ).addOnSuccessListener {
                Log.d(TAG, "Solicitud de actualización única registrada exitosamente para $deviceId.")
            }.addOnFailureListener { e ->
                Log.e(TAG, "Error al registrar solicitud de actualización única para $deviceId: ${e.message}")
                // Lógica de reintento simple si falla el registro de la solicitud
                if (!executor.isShutdown) {
                    executor.execute {
                        try {
                            Thread.sleep(RETRY_DELAY_MS_FAILURE_REGISTRATION)
                            // Reintentar solo si el contexto sigue siendo válido (mismo deviceId y callback)
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
            LOCATION_UPDATE_INTERVAL_MS // Intervalo para obtener esa primera actualización
        )
        builder.setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS) // Intervalo más rápido si es necesario
        builder.setWaitForAccurateLocation(true) // Esperar una ubicación precisa
        builder.setMinUpdateDistanceMeters(MIN_DISPLACEMENT_METERS_SINGLE_UPDATE) // Para una sola actualización, la distancia puede ser 0
        //builder.setMaxUpdates(1) // <<-- ESTO PIDE SOLO UNA ACTUALIZACIÓN

        return builder.build()
    }

    private fun createLocationCallback(
        deviceId: String,
        onLocation: (Location) -> Unit
    ): LocationCallback {
        return object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (executor.isShutdown) {
                    Log.w(TAG, "Executor apagado, no se procesará onLocationResult para $deviceId.")
                    return
                }

                val location = result.locations.lastOrNull()
                if (location == null) {
                    Log.w(TAG, "onLocationResult para $deviceId no contiene ubicaciones.")
                    return
                }

                Log.d(TAG, "onLocationResult (única vez) para $deviceId. Precisión: ${location.accuracy}. Total ubicaciones en resultado: ${result.locations.size}")

                executor.execute {
                    if (isLocationAcceptable(location)) {
                        val now = System.currentTimeMillis()
                        // Salvaguarda por si el callback se dispara muy rápido múltiples veces
                        if (now - lastSentTime >= MIN_INTERVAL_BETWEEN_SAVES_MS) {
                            lastSentTime = now
                            Log.i(TAG, "Ubicación (única) aceptable para $deviceId. Guardando y notificando.")
                            saveDeviceLocation(deviceId, location)
                            onLocation(location) // Notifica al que llamó

                            // Con setMaxUpdates(1), el sistema debería detener las actualizaciones automáticamente.
                            // No es estrictamente necesario llamar a stopLocationUpdates() aquí.
                            // Si se observan problemas, se puede añadir una llamada a internalStopLocationUpdates(false).
                        } else {
                            Log.d(TAG, "Actualización (única) para $deviceId (en onLocationResult) ignorada (lastSentTime): ${now - lastSentTime}ms")
                        }
                    } else {
                        Log.w(TAG, "Ubicación (única) recibida para $deviceId NO aceptable. Precisión: ${location.accuracy}, Velocidad: ${location.speed}")
                        // IMPORTANTE: Si la primera ubicación no es aceptable, y setMaxUpdates(1) está activo,
                        // es posible que no se reciba otra y onLocation nunca se llame.
                        // Considera esto en la lógica de tu app (e.g. timeouts).
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

    // ESTRUCTURA DE FIREBASE RESTAURADA A TU ORIGINAL
    private fun saveDeviceLocation(deviceId: String, location: Location) {
        if (deviceId.isBlank()) {
            Log.e(TAG, "ID de dispositivo inválido para saveDeviceLocation")
            return
        }
        try {
            val timestampKey = dateFormat.format(Date())
            val updates = hashMapOf<String, Any>(
                "devices/$deviceId/in_safe_zone" to false, // Como en tu estructura original

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

    // Método público para detener, intentará apagar el executor.
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
                // Solo poner a null si es el mismo callback, para evitar problemas con llamadas concurrentes
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
        private const val LOCATION_UPDATE_INTERVAL_MS = 5000L  // Intervalo deseado para obtener la primera actualización
        private const val MIN_UPDATE_INTERVAL_MS = 2500L     // Intervalo más rápido si se necesita para esa primera actualización

        private const val MAX_ACCEPTABLE_ACCURACY = 50f      // metros de precisión
        private const val MAX_ACCEPTABLE_SPEED = 50f         // m/s
        private const val MIN_DISPLACEMENT_METERS_SINGLE_UPDATE = 10f // No se necesita desplazamiento para una sola actualización

        private const val RETRY_DELAY_MS_FAILURE_REGISTRATION = 7000L // Reintento si falla el registro de la solicitud
        private const val MIN_INTERVAL_BETWEEN_SAVES_MS = 2500L // Mínimo tiempo entre guardados si el callback se dispara varias veces
    }
}
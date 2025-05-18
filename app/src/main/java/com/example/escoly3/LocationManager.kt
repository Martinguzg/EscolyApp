package com.example.escoly3

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Build
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LocationManager(private val context: Context) {
    private val database = FirebaseDatabase.getInstance().reference
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private var locationCallback: LocationCallback? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    private var currentDeviceId: String? = null

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(deviceId: String, onLocation: (Location) -> Unit) {
        currentDeviceId = deviceId

        if (deviceId.isBlank()) {
            Log.e(TAG, "ID de dispositivo inválido")
            return
        }

        val request = createLocationRequest()
        locationCallback = createLocationCallback(deviceId, onLocation)

        try {
            fusedClient.requestLocationUpdates(
                request,
                locationCallback!!,
                Looper.getMainLooper()
            ).addOnFailureListener { e ->
                Log.e(TAG, "Error al iniciar actualizaciones: ${e.message}")
                // Reintento simplificado sin schedule
                executor.execute {
                    Thread.sleep(RETRY_DELAY_MS)
                    startLocationUpdates(deviceId, onLocation)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fatal al solicitar ubicaciones", e)
        }
    }

    private fun createLocationRequest(): LocationRequest {
        return LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_UPDATE_INTERVAL_MS
        ).apply {
            setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            setWaitForAccurateLocation(true)
            // Alternativa para versiones más nuevas
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setMinUpdateDistanceMeters(MIN_DISPLACEMENT_METERS)
            }
        }.build()
    }

    private fun createLocationCallback(
        deviceId: String,
        onLocation: (Location) -> Unit
    ): LocationCallback {
        return object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                executor.execute {
                    result.locations.lastOrNull()?.let { location ->
                        if (isLocationAcceptable(location)) {
                            saveDeviceLocation(deviceId, location)
                            onLocation(location)
                        }
                    }
                }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) {
                    Log.w(TAG, "Ubicación no disponible temporalmente")
                }
            }
        }
    }

    private fun isLocationAcceptable(location: Location): Boolean {
        return location.accuracy < MAX_ACCEPTABLE_ACCURACY &&
                (location.speed < MAX_ACCEPTABLE_SPEED || location.speed == 0f)
    }

    private fun saveDeviceLocation(deviceId: String, location: Location) {
        try {
            val timestampKey = dateFormat.format(Date())
            val updates = hashMapOf<String, Any>(
                // Actualiza el estado en la raíz del dispositivo
                "devices/$deviceId/in_safe_zone" to false,

                // Guarda la ubicación en el historial
                "devices/$deviceId/location_history/$timestampKey" to hashMapOf(
                    "lat" to location.latitude,
                    "lng" to location.longitude,
                    "dateTime" to ServerValue.TIMESTAMP,
                    "battery" to getBatteryLevel()
                )
            )

            database.updateChildren(updates)
                .addOnSuccessListener {
                    Log.d(TAG, "Ubicación guardada correctamente para $deviceId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error al guardar ubicación", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error crítico al guardar ubicación", e)
        }
    }

    private fun createLocationData(location: Location): Map<String, Any> {
        return hashMapOf(
            "latitud" to location.latitude,
            "longitud" to location.longitude,
            "timestamp" to ServerValue.TIMESTAMP,
            "precision" to location.accuracy,
            "velocidad" to location.speed,
            "fuente" to (location.provider ?: "indeterminada") // Cambiado de "proveedor" a "fuente"
        )
    }

    @SuppressLint("PrivateApi")
    private fun getBatteryLevel(): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener nivel de batería", e)
            -1
        }
    }

    fun stopLocationUpdates() {
        try {
            locationCallback?.let { callback ->
                fusedClient.removeLocationUpdates(callback)
                executor.shutdownNow()
                Log.d(TAG, "Actualizaciones de ubicación detenidas")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al detener actualizaciones", e)
        } finally {
            locationCallback = null
        }
    }

    companion object {
        private const val TAG = "LocationManager"
        private const val LOCATION_UPDATE_INTERVAL_MS = 30000L // 30 segundos
        private const val MIN_UPDATE_INTERVAL_MS = 15000L // 15 segundos
        private const val MAX_ACCEPTABLE_ACCURACY = 50f // 50 metros
        private const val MAX_ACCEPTABLE_SPEED = 50f // 50 m/s (180 km/h)
        private const val MIN_DISPLACEMENT_METERS = 10f // 10 metros
        private const val RETRY_DELAY_MS = 10000L // 10 segundos en milisegundos
    }
}
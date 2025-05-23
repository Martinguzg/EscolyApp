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

class LocationManager(private val context: Context) {
    private val database = FirebaseDatabase.getInstance().reference
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private var locationCallback: LocationCallback? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    private var currentDeviceId: String? = null
    private var lastSentTime: Long = 0

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
                        Log.d(TAG, "onLocationResult recibido con ${result.locations.size} ubicaciones")
                        if (isLocationAcceptable(location)) {
                            val now = System.currentTimeMillis()
                            if(now - lastSentTime >= 4900L){
                                lastSentTime = now
                                saveDeviceLocation(deviceId, location)
                                onLocation(location)
                            }else{
                                Log.d(TAG, "Actualizacion ignorada: ${now - lastSentTime}ms desde la ultima")
                            }
                        }
                    }
                }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                Log.d(TAG, "¿Está disponible la ubicación? ${availability.isLocationAvailable}")
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
        private const val LOCATION_UPDATE_INTERVAL_MS = 5000L // 5 segundos
        private const val MIN_UPDATE_INTERVAL_MS = 2500L // 2.5 segundos
        private const val MAX_ACCEPTABLE_ACCURACY = 50f // 50 metros
        private const val MAX_ACCEPTABLE_SPEED = 50f // 50 m/s (180 km/h)
        private const val MIN_DISPLACEMENT_METERS = 10f // 10 metros
        private const val RETRY_DELAY_MS = 2500L // 2.5 segundos en milisegundos
    }
}
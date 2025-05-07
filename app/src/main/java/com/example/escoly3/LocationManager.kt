package com.example.escoly3

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import com.google.android.gms.location.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*

class LocationManager(private val context: Context) {
    private val database = Firebase.database.reference
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private var locationCallback: LocationCallback? = null
    private var isTracking = false // Nuevo: Evita múltiples registros

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(deviceId: String, onLocation: (Location) -> Unit) {
        if (isTracking) {
            Log.w("LocationManager", "Ya se está rastreando, ignorando nueva solicitud")
            return
        }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            30000 // Intervalo de 30 segundos
        ).apply {
            setMinUpdateIntervalMillis(15000) // Intervalo mínimo de 15 segundos
            setWaitForAccurateLocation(true) // Mejor precisión inicial
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.lastOrNull()?.let { location ->
                    Log.d("LocationManager", "Nueva ubicación: ${location.latitude}, ${location.longitude}")
                    onLocation(location)
                    saveLocation(deviceId, location)
                }
            }
        }

        fusedClient.requestLocationUpdates(
            request,
            locationCallback as LocationCallback,
            context.mainLooper
        ).addOnFailureListener { e ->
            Log.e("LocationManager", "Error al iniciar rastreo: ${e.message}")
        }

        isTracking = true
        Log.d("LocationManager", "Rastreo iniciado para dispositivo: $deviceId")
    }

    private fun saveLocation(deviceId: String, location: Location) {
        try {
            val locationData = hashMapOf(
                "lat" to location.latitude,
                "lng" to location.longitude,
                "time" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                "battery" to 100 // Puedes añadir más datos si necesitas
            )

            database.child("locations").child(deviceId).push()
                .setValue(locationData)
                .addOnSuccessListener {
                    Log.d("LocationManager", "Ubicación guardada en Firebase")
                }
                .addOnFailureListener { e ->
                    Log.e("LocationManager", "Error al guardar ubicación: ${e.message}")
                    // Aquí podrías implementar un caché local para reintentar luego
                }
        } catch (e: Exception) {
            Log.e("LocationManager", "Error al procesar ubicación: ${e.message}")
        }
    }

    fun stopLocationUpdates() {
        locationCallback?.let {
            fusedClient.removeLocationUpdates(it)
            isTracking = false
            Log.d("LocationManager", "Rastreo detenido")
        }
    }

    fun isTracking(): Boolean = isTracking // Para consultar el estado desde fuera
}
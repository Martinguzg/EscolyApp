@file:Suppress("DEPRECATION")

package com.example.escoly3

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


// Enum para saber qué tipo de zona segura es
enum class SafeZoneType {
    SCHOOL, HOUSE
}

// Data class actualizada para incluir el tipo
data class SafeZone(
    val latitude: Double,
    val longitude: Double,
    val radius: Float,
    val type: SafeZoneType // <-- CAMPO NUEVO
)

// Enum para saber el estado actual del dispositivo
private enum class TrackingState {
    OUTSIDE, IN_SCHOOL, IN_HOUSE
}


class LocationViewModel(
    private val locationManager: LocationManager,
    private val context: Context,
    private val idManager: IdManager
) : ViewModel() {

    private var currentDeviceId: String? = idManager.getId()

    private val _uiState = MutableStateFlow<LocationUiState>(LocationUiState.Idle)
    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()

    private var safeZones: List<SafeZone> = emptyList()
    private lateinit var fusedLocationClientInternal: FusedLocationProviderClient
    private var internalLocationCallback: LocationCallback? = null

    // AGREGADO: Variable para trackear el estado actual
    private var currentSafeZoneStatus: Boolean? = null
    // NUEVA VARIABLE: Para saber si estamos en la escuela, casa o afuera
    private var currentTrackingState: TrackingState? = null

    init {
        fusedLocationClientInternal = LocationServices.getFusedLocationProviderClient(context.applicationContext)
        loadSafeZones()
        startInternalLocationUpdates()
        loadSavedId()
    }

    fun generateDeviceId(firebaseUid: String?) {
        viewModelScope.launch {
            _uiState.value = LocationUiState.Loading
            try {
                val id = firebaseUid?.let { uid ->
                    if (uid.length >= 5) uid.takeLast(5).uppercase()
                    else uid.uppercase().padStart(5, '0')
                } ?: generateRandomId()

                currentDeviceId = id
                idManager.saveId(id)
                _uiState.value = LocationUiState.IdGenerated(id)
            } catch (e: Exception) {
                _uiState.value = LocationUiState.Error("Error generando ID")
                Log.e(TAG, "generateDeviceId error", e)
            }
        }
    }

    fun loadSavedId() {
        viewModelScope.launch {
            val savedId = idManager.getId()
            if (savedId != null) {
                currentDeviceId = savedId
                if (_uiState.value is LocationUiState.Idle || _uiState.value is LocationUiState.Error) {
                    _uiState.value = LocationUiState.IdGenerated(savedId)
                }
            } else {
                if (_uiState.value is LocationUiState.Idle) {
                    Log.d(TAG, "No saved ID found, ViewModel remains Idle or awaits ID generation.")
                }
            }
        }
    }

    fun startTrackingWithValidId() {
        viewModelScope.launch {
            val currentId = currentDeviceId
            if (currentId == null) {
                Log.w(TAG, "startTrackingWithValidId called but currentDeviceId is null. Attempting to load or generate.")
                _uiState.value = LocationUiState.Idle
                return@launch
            }

            if (checkOverallLocationPermissions()) {
                startTracking(currentId)
            } else {
                Log.w(TAG, "Permisos de ubicación no concedidos al intentar iniciar rastreo.")
                _uiState.value = LocationUiState.IdGenerated(currentId)
            }
        }
    }

    private fun loadSafeZones() {
        val safeZonesRef = Firebase.database.reference.child("safe_zones")
        safeZonesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val safeZonesList = mutableListOf<SafeZone>()

                // Cargar la escuela
                try {
                    val school = snapshot.child("school")
                    val schoolLat = school.child("lat").getValue(Double::class.java)
                    val schoolLng = school.child("lng").getValue(Double::class.java)
                    val schoolRadius = school.child("radius").getValue(Double::class.java)
                    if (schoolLat != null && schoolLng != null && schoolRadius != null) {
                        safeZonesList.add(SafeZone(schoolLat, schoolLng, schoolRadius.toFloat(), SafeZoneType.SCHOOL)) // Asigna tipo SCHOOL
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Excepción al procesar datos de la escuela", e)
                }

                // Cargar las casas del ID del dispositivo actual
                val deviceId = currentDeviceId
                if (!deviceId.isNullOrBlank() && snapshot.child("houses").hasChild(deviceId)) {
                    val userHousesSnapshot = snapshot.child("houses").child(deviceId)
                    userHousesSnapshot.children.forEach { houseSnapshot ->
                        try {
                            val lat = houseSnapshot.child("lat").getValue(Double::class.java)
                            val lng = houseSnapshot.child("lng").getValue(Double::class.java)
                            val radius = houseSnapshot.child("radius").getValue(Double::class.java)
                            if (lat != null && lng != null && radius != null) {
                                safeZonesList.add(SafeZone(lat, lng, radius.toFloat(), SafeZoneType.HOUSE)) // Asigna tipo HOUSE
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Excepción al procesar casa para $deviceId", e)
                        }
                    }
                }

                safeZones = safeZonesList
                Log.d(TAG, "Zonas seguras cargadas: ${safeZones.size} zonas para el ID: $deviceId")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error al cargar zonas seguras: ${error.message}")
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun startInternalLocationUpdates() {
        if (!checkOverallLocationPermissions()) {
            Log.e(TAG, "Permisos de ubicación no concedidos para actualizaciones internas.")
            return
        }

        if (internalLocationCallback != null) {
            stopInternalLocationUpdates()
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L)
            .setMinUpdateIntervalMillis(5000L)
            .build()

        internalLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.locations.lastOrNull()?.let { location ->
                    checkIfInSafeZone(location)
                }
            }

            override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                if (!locationAvailability.isLocationAvailable) {
                    Log.w(TAG, "Ubicación no disponible para actualizaciones internas.")
                }
            }
        }

        try {
            fusedLocationClientInternal.requestLocationUpdates(
                locationRequest,
                internalLocationCallback!!,
                Looper.getMainLooper()
            )
            Log.d(TAG, "Actualizaciones internas de ubicación iniciadas.")
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de seguridad al solicitar actualizaciones internas de ubicación: ${e.message}")
        }
    }

    private fun checkIfInSafeZone(location: Location) {
        // Si el rastreo no está activo, no hacemos nada.
        if (_uiState.value !is LocationUiState.TrackingActive) {
            return
        }

        val currentLat = location.latitude
        val currentLng = location.longitude
        var foundZone: SafeZone? = null

        for (zone in safeZones) {
            val results = FloatArray(1)
            Location.distanceBetween(currentLat, currentLng, zone.latitude, zone.longitude, results)
            if (results[0] <= zone.radius) {
                foundZone = zone
                break // Encontramos la primera zona, es suficiente
            }
        }

        val newState = when (foundZone?.type) {
            SafeZoneType.HOUSE -> TrackingState.IN_HOUSE
            SafeZoneType.SCHOOL -> TrackingState.IN_SCHOOL
            null -> TrackingState.OUTSIDE
        }

        handleStateChange(newState)
    }

    private fun handleStateChange(newState: TrackingState) {
        if (currentTrackingState == newState) {
            return // Si el estado no ha cambiado, no hacemos nada.
        }

        Log.i(TAG, "Cambio de estado de rastreo: De $currentTrackingState a $newState")
        val previousState = currentTrackingState
        currentTrackingState = newState

        when (newState) {
            TrackingState.OUTSIDE -> {
                // Si antes estábamos en la escuela, ahora reanudamos el envío a Firebase.
                if (previousState == TrackingState.IN_SCHOOL) {
                    Log.d(TAG, "Saliendo de la escuela. Reanudando actualizaciones a Firebase.")
                    sendServiceCommand("RESUME_FIREBASE_UPDATES")
                }
                updateFirebaseSafeZoneStatus(false)
            }
            TrackingState.IN_SCHOOL -> {
                // Al entrar a la escuela, pausamos el envío a Firebase.
                Log.d(TAG, "Entrando a la escuela. Pausando actualizaciones a Firebase.")
                sendServiceCommand("PAUSE_FIREBASE_UPDATES")
                updateFirebaseSafeZoneStatus(true)
            }
            TrackingState.IN_HOUSE -> {
                // Al entrar a una casa, detenemos todo.
                Log.d(TAG, "Entrando a una casa. Deteniendo el rastreo por completo.")
                stopTracking() // Esta función ya detiene el servicio y actualiza la UI
                updateFirebaseSafeZoneStatus(true)
            }
        }
    }

    // Nueva función para enviar comandos al servicio
    private fun sendServiceCommand(action: String) {
        val currentId = currentDeviceId ?: return
        Log.d(TAG, "Enviando comando '$action' al servicio para el dispositivo $currentId")
        val intent = Intent(context, LocationForegroundService::class.java).apply {
            this.action = action
            putExtra("DEVICE_ID", currentId)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    // CORREGIDO: Ahora solo actualiza si el estado realmente cambió
    private fun updateFirebaseSafeZoneStatus(inSafeZone: Boolean) {
        val deviceId = currentDeviceId
        if (deviceId == null || deviceId.isBlank()) {
            Log.e(TAG, "ID del dispositivo no disponible para actualizar estado de zona segura.")
            return
        }

        // Solo actualizar si el estado ha cambiado
        if (currentSafeZoneStatus == inSafeZone) {
            Log.d(TAG, "Estado de zona segura sin cambios para $deviceId: $inSafeZone")
            return
        }

        val deviceRef = Firebase.database.reference.child("devices").child(deviceId)
        deviceRef.child("in_safe_zone").setValue(inSafeZone)
            .addOnSuccessListener {
                currentSafeZoneStatus = inSafeZone // Actualizar el estado local
                Log.d(TAG, "Estado in_safe_zone actualizado a $inSafeZone para $deviceId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al actualizar in_safe_zone para $deviceId", e)
            }
    }

    fun stopInternalLocationUpdates() {
        internalLocationCallback?.let {
            fusedLocationClientInternal.removeLocationUpdates(it)
            Log.d(TAG, "Actualizaciones internas de ubicación detenidas.")
        }
        internalLocationCallback = null
    }

    private fun checkOverallLocationPermissions(): Boolean {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            context.applicationContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineLocationGranted
    }

    private fun startTracking(deviceId: String) {
        viewModelScope.launch {
            if (!checkOverallLocationPermissions()) {
                Log.e(TAG, "startTracking: Permisos insuficientes.")
                _uiState.value = LocationUiState.IdGenerated(deviceId)
                return@launch
            }
            _uiState.value = LocationUiState.Loading
            try {
                startForegroundService(deviceId)
                _uiState.value = LocationUiState.TrackingActive(deviceId, false)
                Log.i(TAG, "Rastreo (servicio foreground) iniciado para $deviceId")
            } catch (e: Exception) {
                Log.e(TAG, "Error en startTracking (al iniciar servicio)", e)
                _uiState.value = LocationUiState.Error("Error al iniciar rastreo: ${e.message}")
            }
        }
    }

    fun stopTracking() {
        viewModelScope.launch {
            val currentId = currentDeviceId
            Log.d(TAG, "Solicitando detener rastreo para el dispositivo: $currentId")
            try {
                locationManager.stopLocationUpdates()

                val stopIntent = Intent(context.applicationContext, LocationForegroundService::class.java).apply {
                    action = "STOP_TRACKING_ACTION"
                }
                ContextCompat.startForegroundService(context.applicationContext, stopIntent)

                stopInternalLocationUpdates()

                _uiState.value = if (currentId != null) LocationUiState.IdGenerated(currentId) else LocationUiState.Idle
                Log.d(TAG, "Rastreo (servicio foreground) detenido solicitado.")
            } catch (e: Exception) {
                Log.e(TAG, "Error al detener rastreo", e)
                _uiState.value = LocationUiState.Error("Error al detener rastreo: ${e.message}")
            }
        }
    }

    private fun startForegroundService(deviceId: String) {
        val intent = Intent(context.applicationContext, LocationForegroundService::class.java).apply {
            putExtra("DEVICE_ID", deviceId)
            action = "START_TRACKING_ACTION"
        }
        ContextCompat.startForegroundService(context.applicationContext, intent)
        Log.d(TAG, "Solicitud para iniciar servicio foreground enviada para $deviceId")
    }

    private fun generateRandomId(): String {
        val chars = ('A'..'Z') + ('0'..'9') - listOf('I', 'O', '0', '1')
        return (1..5).joinToString("") { chars.random().toString() }
    }

    fun setErrorState(message: String) {
        viewModelScope.launch {
            if (_uiState.value !is LocationUiState.Idle && _uiState.value !is LocationUiState.IdGenerated) {
                _uiState.value = LocationUiState.Error(message)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel onCleared. Deteniendo actualizaciones internas.")
        stopInternalLocationUpdates()
    }

    sealed class LocationUiState {
        object Idle : LocationUiState()
        object Loading : LocationUiState()
        data class IdGenerated(val id: String) : LocationUiState()
        data class TrackingActive(val id: String, val inSafeZone: Boolean) : LocationUiState()
        data class Error(val message: String) : LocationUiState()
    }

    companion object {
        private const val TAG = "LocationViewModel"
    }
}
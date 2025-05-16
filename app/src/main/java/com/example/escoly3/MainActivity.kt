package com.example.escoly3

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
// Asegúrate de tener estas importaciones
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val auth by lazy { Firebase.auth }
    private val viewModel: LocationViewModel by viewModels {
        LocationViewModelFactory(
            locationManager = LocationManager(applicationContext),
            context = applicationContext
        )
    }

    // Control de estado
    private var isCheckingPermissions = false
    private var isDialogShowing = false
    private val prefs by lazy { getSharedPreferences("AppPrefs", MODE_PRIVATE) }

    // Permisos
    private val basePermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val backgroundPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else {
        emptyArray()
    }

    private val allRequiredPermissions = basePermissions + backgroundPermission

    // Contracts
    private val basePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { handleBasePermissionResult(it) }

    private val backgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { handleBackgroundPermissionResult(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var showWelcomeScreen by remember { mutableStateOf(true) }

            if (showWelcomeScreen) {
                WelcomeScreen(
                    onTimeout = { showWelcomeScreen = false },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val uiState by viewModel.uiState.collectAsState()
                AppScreen(
                    uiState = uiState,
                    onGenerateId = ::handleGenerateId,
                    onStartTracking = ::startTrackingWithChecks,
                    onStopTracking = viewModel::stopTracking
                )
            }
        }

        checkBatteryOptimization()
        checkManufacturerSettings()
        startOptimizationChecker()
        checkOnePlusOptimization()
    }

    // Mejora: Función más robusta para verificar optimización OnePlus
    private fun checkOnePlusOptimization() {
        if (Build.MANUFACTURER.equals("oneplus", ignoreCase = true)) {
            lifecycleScope.launch {
                delay(3000) // Espera para no saturar al usuario
                if (!isDialogShowing) {
                    isDialogShowing = true
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Configuración Especial para OnePlus")
                        .setMessage("""
                        1. Ve a Ajustes > Batería > Optimización de batería
                        2. Selecciona 'Todas las apps'
                        3. Encuentra esta app y elige 'No optimizar'
                        4. Activa 'Inicio automático' en Ajustes > Apps
                        """.trimIndent())
                        .setPositiveButton("Abrir Ajustes") { _, _ ->
                            try {
                                startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
                            } catch (e: Exception) {
                                openAppSettings()
                            }
                            isDialogShowing = false
                        }
                        .setNegativeButton("Más tarde") { _, _ -> isDialogShowing = false }
                        .setOnDismissListener { isDialogShowing = false }
                        .show()
                }
            }
        }
    }

    // Mejora: Optimizado el checker de optimización
    private fun startOptimizationChecker() {
        lifecycleScope.launch {
            while (true) {
                delay(30_000) // Cada 30 segundos
                if (!isDialogShowing && isAppOptimized() && isTrackingActive()) {
                    showBatteryOptimizationDialog()
                }
            }
        }
    }

    private fun isAppOptimized(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val powerManager = getSystemService(PowerManager::class.java)
                powerManager?.let {
                    !it.isIgnoringBatteryOptimizations(packageName)
                } ?: true // Si powerManager es null, asumimos optimizado por seguridad
            } catch (e: Exception) {
                Log.e(TAG, "Error al verificar optimización", e)
                true // Asume optimizado por seguridad si hay error
            }
        } else {
            false // Dispositivos antiguos no tienen esta optimización
        }
    }
    private fun isTrackingActive(): Boolean {
        return when (viewModel.uiState.value) {
            is LocationViewModel.LocationUiState.TrackingActive -> true
            is LocationViewModel.LocationUiState.LocationUpdated -> true
            else -> false
        }
    }
    // Mejora: Manejo más limpio de configuraciones por fabricante
    private fun checkManufacturerSettings() {
        lifecycleScope.launch {
            delay(2000) // Espera para no saturar al inicio

            when {
                Build.MANUFACTURER.equals("xiaomi", ignoreCase = true) && !isDialogShowing -> {
                    showManufacturerGuideDialog(
                        "Configuración necesaria para Xiaomi",
                        """
                        1. Ve a Ajustes > Apps > Esta app
                        2. Activa 'Autoarranque'
                        3. En 'Batería' selecciona 'Sin restricciones'
                        """.trimIndent()
                    )
                }
                Build.MANUFACTURER.equals("huawei", ignoreCase = true) && !isDialogShowing -> {
                    showManufacturerGuideDialog(
                        "Configuración necesaria para Huawei",
                        """
                        1. Ve a Ajustes > Batería
                        2. Desactiva 'Administración inteligente'
                        3. Marca esta app como 'Protegida'
                        """.trimIndent()
                    )
                }
            }
        }
    }

    private fun showManufacturerGuideDialog(title: String, message: String) {
        isDialogShowing = true
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Entendido") { _, _ -> isDialogShowing = false }
            .setOnDismissListener { isDialogShowing = false }
            .show()
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isAppOptimized()) {
            lifecycleScope.launch {
                delay(3000) // Espera para mejor UX
                if (!isDialogShowing) {
                    showBatteryOptimizationDialog()
                }
            }
        }
    }

    private fun showBatteryOptimizationDialog() {
        isDialogShowing = true
        AlertDialog.Builder(this)
            .setTitle("Optimización de Batería")
            .setMessage("Para funcionamiento continuo, desactiva la optimización de batería para esta app")
            .setPositiveButton("Configurar") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error al abrir ajustes de batería", e)
                    openAppSettings()
                }
                isDialogShowing = false
            }
            .setNegativeButton("Ignorar") { _, _ -> isDialogShowing = false }
            .setOnDismissListener { isDialogShowing = false }
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (!isCheckingPermissions && !isDialogShowing) {
            checkPermissionsAndStart()
        }
    }

    private fun checkPermissionsAndStart() {
        isCheckingPermissions = true

        when {
            hasAllPermissions() -> {
                if (isLocationEnabled()) {
                    safelyStartTracking()
                } else {
                    showEnableLocationDialog()
                }
                isCheckingPermissions = false
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                showRationaleDialog()
            }
            else -> {
                requestBasePermissions()
            }
        }
    }

    private fun safelyStartTracking() {
        try {
            viewModel.startTrackingWithValidId()
        } catch (e: Exception) {
            showError("Error al iniciar rastreo: ${e.localizedMessage}")
        }
    }

    private fun requestBasePermissions() {
        try {
            basePermissionLauncher.launch(basePermissions)
        } catch (e: Exception) {
            Log.e(TAG, "Error al solicitar permisos base", e)
            showError("Error al solicitar permisos")
            isCheckingPermissions = false
        }
    }

    private fun handleBasePermissionResult(permissions: Map<String, Boolean>) {
        if (permissions.all { it.value }) {
            if (backgroundPermission.isNotEmpty() && !hasBackgroundPermission()) {
                requestBackgroundPermission()
            } else {
                if (isLocationEnabled()) {
                    safelyStartTracking()
                } else {
                    showEnableLocationDialog()
                }
            }
        } else {
            showError("Permisos esenciales denegados")
        }
        isCheckingPermissions = false
    }

    private fun requestBackgroundPermission() {
        try {
            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } catch (e: Exception) {
            Log.e(TAG, "Error al solicitar permiso de fondo", e)
            showBackgroundPermissionRationale()
        }
    }

    private fun handleBackgroundPermissionResult(granted: Boolean) {
        if (granted) {
            if (isLocationEnabled()) {
                safelyStartTracking()
            } else {
                showEnableLocationDialog()
            }
        } else {
            showBackgroundPermissionRationale()
        }
    }

    private fun showBackgroundPermissionRationale() {
        if (isDialogShowing) return

        isDialogShowing = true
        AlertDialog.Builder(this).apply {
            setTitle("Permiso de fondo requerido")
            setMessage("Para rastreo continuo, active manualmente 'Permitir todo el tiempo' en Ajustes > Ubicación")
            setPositiveButton("Abrir Ajustes") { _, _ ->
                openAppSettings()
                isDialogShowing = false
            }
            setNegativeButton("Continuar sin fondo") { _, _ ->
                if (isLocationEnabled()) {
                    safelyStartTracking()
                } else {
                    showEnableLocationDialog()
                }
                isDialogShowing = false
            }
            setOnDismissListener { isDialogShowing = false }
            show()
        }
    }

    private fun showRationaleDialog() {
        if (isDialogShowing) return

        isDialogShowing = true
        AlertDialog.Builder(this).apply {
            setTitle("Permisos necesarios")
            setMessage("Esta app necesita acceso a tu ubicación para funcionar correctamente")
            setPositiveButton("Entendido") { _, _ ->
                requestBasePermissions()
                isDialogShowing = false
            }
            setNegativeButton("Cancelar") { _, _ ->
                showError("Funcionalidad limitada sin permisos")
                isDialogShowing = false
            }
            setOnDismissListener { isDialogShowing = false }
            show()
        }
    }

    private fun showEnableLocationDialog() {
        if (isDialogShowing) return

        isDialogShowing = true
        AlertDialog.Builder(this).apply {
            setTitle("Ubicación desactivada")
            setMessage("Active los servicios de ubicación para continuar")
            setPositiveButton("Abrir Configuración") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                isDialogShowing = false
            }
            setNegativeButton("Cancelar") { _, _ ->
                isDialogShowing = false
            }
            setOnDismissListener { isDialogShowing = false }
            show()
        }
    }

    private fun isLocationEnabled(): Boolean {
        return try {
            val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                locationManager.isLocationEnabled
            } else {
                @Suppress("DEPRECATION")
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al verificar estado de ubicación", e)
            false
        }
    }

    private fun openAppSettings() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error al abrir ajustes", e)
            showError("No se pudo abrir la configuración")
        }
    }

    private fun handleGenerateId() {
        auth.currentUser?.let { user ->
            viewModel.generateDeviceId(user.uid)
        } ?: run {
            auth.signInAnonymously().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    task.result.user?.uid?.let { uid ->
                        viewModel.generateDeviceId(uid)
                    } ?: showError("Error al obtener ID de usuario")
                } else {
                    showError("Error de autenticación: ${task.exception?.message ?: "Desconocido"}")
                }
            }
        }
    }

    private fun startTrackingWithChecks() {
        if (hasAllPermissions()) {
            if (isLocationEnabled()) {
                safelyStartTracking()
            } else {
                showEnableLocationDialog()
            }
        } else {
            checkPermissionsAndStart()
        }
    }

    private fun showError(message: String) {
        lifecycleScope.launch {
            viewModel.setErrorState(message)
        }
    }

    private fun hasAllPermissions(): Boolean {
        return allRequiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasBackgroundPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopTracking()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
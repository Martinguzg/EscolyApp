package com.example.escoly3

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Modifier
// Añade estos imports en la sección de imports del MainActivity:
import androidx.compose.foundation.layout.fillMaxSize
// Añade estos imports en la sección de imports del MainActivity:
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color


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

    // Permisos
    private val basePermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val backgroundPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Manifest.permission.ACCESS_BACKGROUND_LOCATION
    } else {
        null
    }

    private val allRequiredPermissions = basePermissions + listOfNotNull(backgroundPermission)

    // Contracts
    private val basePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { handleBasePermissionResult(it) }

    private val backgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { handleBackgroundPermissionResult(it) }

    // Handler para configuraciones específicas
    private val deviceSettingsHandler by lazy {
        DeviceSettingsHandler(
            context = this,
            onShowBatteryDialog = { showBatteryOptimizationDialog() }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()

        setContent {
            var showWelcomeScreen by remember { mutableStateOf(true) }

            if (showWelcomeScreen) {
                WelcomeScreen(
                    onTimeout = {
                        showWelcomeScreen = false
                        viewModel.loadSavedId() // Cargar ID después del splash
                    },
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

        deviceSettingsHandler.apply {
            checkManufacturerSettings()
            checkBatteryOptimization()
        }
        startOptimizationChecker()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "location_channel",
                "Ubicación en tiempo real",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Canal para notificaciones de ubicación"
            }

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startOptimizationChecker() {
        lifecycleScope.launch {
            while (true) {
                delay(30_000)
                if (!isDialogShowing && deviceSettingsHandler.isAppOptimized() && isTrackingActive()) {
                    showBatteryOptimizationDialog()
                }
            }
        }
    }

    private fun isTrackingActive(): Boolean {
        return when (viewModel.uiState.value) {
            is LocationViewModel.LocationUiState.TrackingActive -> true
            is LocationViewModel.LocationUiState.LocationUpdated -> true
            else -> false
        }
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
            if (backgroundPermission != null && !hasBackgroundPermission()) {
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

class DeviceSettingsHandler(
    private val context: Context,
    private val onShowBatteryDialog: () -> Unit
) {
    fun checkManufacturerSettings() {
        when (Build.MANUFACTURER.lowercase()) {
            "xiaomi" -> setupXiaomi()
            "huawei" -> setupHuawei()
            "oppo", "realme", "oneplus" -> setupOppoFamily()
            "vivo" -> setupVivo()
            "samsung" -> setupSamsung()
            else -> checkBatteryOptimization()
        }
    }

    fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isAppOptimized()) {
            onShowBatteryDialog()
        }
    }

    fun isAppOptimized(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val powerManager = context.getSystemService(PowerManager::class.java)
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) != true
            } catch (e: Exception) {
                true
            }
        } else false
    }

    private fun setupXiaomi() {
        try {
            Intent().apply {
                component = ComponentName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                )
                context.startActivity(this)
            }
            showDialog(
                "Configuración Xiaomi",
                "1. Activa 'Autoarranque'\n2. Configura 'Sin restricciones' en Batería"
            )
        } catch (e: Exception) {
            openNormalSettings()
        }
    }

    private fun setupHuawei() {
        showDialog(
            "Configuración Huawei",
            "1. Desactiva 'Administración inteligente'\n2. Marca como 'Protegida'"
        )
    }

    private fun setupOppoFamily() {
        showDialog(
            "Configuración OPPO/Realme/OnePlus",
            "1. Desactiva 'Optimización de batería'\n2. Activa 'Inicio automático'"
        )
    }

    private fun setupVivo() {
        showDialog(
            "Configuración Vivo",
            "1. Desactiva 'Optimización'\n2. Activa 'Ejecución en segundo plano'"
        )
    }

    private fun setupSamsung() {
        showDialog(
            "Configuración Samsung",
            "1. 'Optimización no permitida'\n2. Activa 'Ejecutar en segundo plano'"
        )
    }

    private fun showDialog(title: String, message: String) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Abrir Ajustes") { _, _ -> openNormalSettings() }
            .setNegativeButton("Más tarde", null)
            .show()
    }

    private fun openNormalSettings() {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        })
    }
}
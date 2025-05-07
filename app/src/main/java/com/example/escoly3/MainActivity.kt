package com.example.escoly3

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.example.escoly3.ui.theme.Escoly3Theme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.ktx.Firebase

class MainActivity : ComponentActivity() {
    private val auth: FirebaseAuth by lazy { Firebase.auth } // Instancia FirebaseAuth

    private val viewModel: LocationViewModel by viewModels {
        LocationViewModelFactory(
            locationManager = LocationManager(applicationContext),
            context = applicationContext
        )
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.all { it.value }) startTrackingWithAuth()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Escoly3Theme {
                val uiState by viewModel.uiState.collectAsState()
                AppScreen(
                    uiState = uiState,
                    onGenerateId = ::generateIdWithAuth,
                    onStartTracking = ::checkPermissions,
                    onStopTracking = viewModel::stopTracking
                )
            }
        }

        // 🚀 TEST de Firebase al iniciar
        if (auth.currentUser != null) {
            Log.d("Escoly3", "✅ Ya autenticado: ${auth.currentUser?.uid}")
            testFirebaseConnection()
        } else {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    Log.d("Escoly3", "✅ Autenticado en test: ${it.user?.uid}")
                    testFirebaseConnection()
                }
                .addOnFailureListener {
                    Log.e("Escoly3", "❌ Fallo de autenticación en test", it)
                }
        }
    }

    private fun generateIdWithAuth() {
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    viewModel.generateDeviceId(it.user?.uid)
                }
                .addOnFailureListener { e ->
                    viewModel.setError("Error de autenticación: ${e.message}")
                    Log.e("Auth", "Error en autenticación", e)
                }
        } else {
            viewModel.generateDeviceId(auth.currentUser?.uid)
        }
    }

    private fun startTrackingWithAuth() {
        auth.currentUser?.uid?.let { uid ->
            viewModel.startTracking(uid)
        } ?: run {
            viewModel.setError("Genera un ID primero")
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startTrackingWithAuth()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            )
        }
    }

    // ✅ Función para probar escritura en Firebase
    private fun testFirebaseConnection() {
        val database = FirebaseDatabase.getInstance()
        val ref = database.getReference("test_connection")

        val testData = mapOf(
            "timestamp" to System.currentTimeMillis(),
            "message" to "Conexión Firebase exitosa!"
        )

        ref.push().setValue(testData)
            .addOnSuccessListener {
                Log.d("Escoly3", "✅ Test de escritura exitoso en Firebase")
            }
            .addOnFailureListener {
                Log.e("Escoly3", "❌ Error escribiendo test en Firebase", it)
            }
    }
}

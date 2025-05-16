package com.example.escoly3

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class MainApplication : Application() {

    companion object {
        lateinit var auth: FirebaseAuth
        val database by lazy { Firebase.database.reference } // Añadido para acceso directo
    }

    override fun onCreate() {
        super.onCreate()
        initializeFirebase()
    }

    private fun initializeFirebase() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
                auth = Firebase.auth
                Log.d("Firebase", "Inicialización exitosa")
                attemptAnonymousAuth()
            }
        } catch (e: Exception) {
            Log.e("Firebase", "Error en inicialización", e)
            // Eliminado el reintento automático (manejarlo desde UI)
        }
    }

    private fun attemptAnonymousAuth() {
        auth.signInAnonymously()
            .addOnSuccessListener {
                Log.d("Auth", "✅ Autenticación exitosa. UID: ${it.user?.uid}")
            }
            .addOnFailureListener { e ->
                Log.e("Auth", "❌ Falló autenticación: ${e.message}")
                // Eliminado el reintento automático (manejarlo desde UI)
            }
    }
}
package com.example.escoly3

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class MainApplication : Application() {

    companion object {
        lateinit var auth: FirebaseAuth
    }

    override fun onCreate() {
        super.onCreate()
        initializeFirebase()
    }

    private fun initializeFirebase() {
        try {
            // Verificar si Firebase ya está inicializado
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
                auth = Firebase.auth
                Log.d("Firebase", "Inicialización exitosa")
                attemptAnonymousAuth()
            }
        } catch (e: Exception) {
            Log.e("Firebase", "Error en inicialización", e)
            Handler(Looper.getMainLooper()).postDelayed({
                initializeFirebase() // Reintento después de 3 segundos
            }, 3000)
        }
    }

    private fun attemptAnonymousAuth() {
        auth.signInAnonymously()
            .addOnSuccessListener {
                Log.d("Auth", "✅ Autenticación exitosa. UID: ${it.user?.uid}")
            }
            .addOnFailureListener { e ->
                Log.e("Auth", "❌ Falló autenticación: ${e.message}")
                Handler(Looper.getMainLooper()).postDelayed({
                    attemptAnonymousAuth() // Reintento después de 5 segundos
                }, 5000)
            }
    }
}
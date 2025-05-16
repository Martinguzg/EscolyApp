package com.example.escoly3

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class LocationRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // Aquí deberías reiniciar el servicio si es necesario
                // Puedes usar SharedPreferences para verificar si había un rastreo activo
            }
        }
    }
}
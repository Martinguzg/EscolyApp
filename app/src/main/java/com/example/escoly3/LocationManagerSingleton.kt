package com.example.escoly3

import android.content.Context

object LocationManagerSingleton {
    private var instance: LocationManager? = null

    fun initialize(context: Context) {
        if (instance == null) {
            instance = LocationManager(context.applicationContext)
        }
    }

    fun get(): LocationManager {
        return instance
            ?: throw IllegalStateException("LocationManagerSingleton not initialized. Call initialize(context) first.")
    }
}

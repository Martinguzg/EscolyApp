package com.example.escoly3

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class LocationViewModelFactory(
    private val locationManager: LocationManager,
    private val context: Context
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LocationViewModel::class.java)) {
            return LocationViewModel(
                locationManager = locationManager,
                context = context
            ) as T
        }
        throw IllegalArgumentException("ViewModel class desconocida")
    }
}
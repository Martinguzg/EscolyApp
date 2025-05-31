package com.example.escoly3

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class LocationViewModelFactory(
    private val locationManager: LocationManager,
    private val context: Context, // No idManager here
) : ViewModelProvider.Factory {
    // ...
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return LocationViewModel(
            locationManager = locationManager,
            context = context,
            idManager = IdManager(context) // IdManager is created INSIDE the factory
        ) as T
    }
}
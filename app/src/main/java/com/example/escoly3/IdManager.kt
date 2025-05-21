package com.example.escoly3

import android.content.Context
import android.content.SharedPreferences

class IdManager(context: Context) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("escoly_prefs", Context.MODE_PRIVATE)

    fun getId(): String? = sharedPreferences.getString("id_dispositivo", null)

    fun saveId(id: String) {
        sharedPreferences.edit().putString("id_dispositivo", id).apply()
    }
}

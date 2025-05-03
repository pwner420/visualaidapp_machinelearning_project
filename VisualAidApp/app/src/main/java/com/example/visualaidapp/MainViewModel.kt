package com.example.visualaidapp

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {
    // Backing property for dark theme state
    private val _darkThemeEnabled = MutableLiveData(false)

    // Publicly exposed immutable LiveData
    val darkThemeEnabled: LiveData<Boolean> = _darkThemeEnabled

    // Toggle function for the switch
    fun setDarkTheme(enabled: Boolean) {
        _darkThemeEnabled.value = enabled
    }
}

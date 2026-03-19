package com.memex.app.ui.screens.onboarding

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val sharedPrefs: SharedPreferences
) : ViewModel() {

    fun completeOnboarding(onDone: () -> Unit) {
        sharedPrefs.edit().putBoolean("onboarding_done", true).apply()
        onDone()
    }

    fun isOnboardingDone(): Boolean {
        return sharedPrefs.getBoolean("onboarding_done", false)
    }
}

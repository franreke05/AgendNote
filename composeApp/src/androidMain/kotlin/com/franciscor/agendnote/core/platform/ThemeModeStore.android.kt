package com.franciscor.agendnote.core.platform

import android.content.Context

private const val PREFERENCES_NAME = "agendnote_preferences"
private const val THEME_MODE_KEY = "theme_mode_dark"

private var applicationContext: Context? = null

/** Must be called once by the Android host before Compose creates the app graph. */
fun initializeThemeModeStore(context: Context) {
    applicationContext = context.applicationContext
}

actual fun createThemeModeStore(): ThemeModeStore = object : ThemeModeStore {
    private val preferences
        get() = applicationContext?.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(): Boolean? {
        val prefs = preferences ?: return null
        if (!prefs.contains(THEME_MODE_KEY)) return null
        return prefs.getBoolean(THEME_MODE_KEY, false)
    }

    override fun write(isDark: Boolean) {
        preferences?.edit()?.putBoolean(THEME_MODE_KEY, isDark)?.apply()
    }
}

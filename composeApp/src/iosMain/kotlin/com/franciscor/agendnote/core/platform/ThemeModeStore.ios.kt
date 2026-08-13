package com.franciscor.agendnote.core.platform

import platform.Foundation.NSNumber
import platform.Foundation.NSUserDefaults

private const val THEME_MODE_KEY = "agendnote.theme_mode_dark"

actual fun createThemeModeStore(): ThemeModeStore = object : ThemeModeStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun read(): Boolean? {
        val value = defaults.objectForKey(THEME_MODE_KEY) ?: return null
        return (value as? NSNumber)?.boolValue
    }

    override fun write(isDark: Boolean) {
        defaults.setBool(isDark, forKey = THEME_MODE_KEY)
    }
}

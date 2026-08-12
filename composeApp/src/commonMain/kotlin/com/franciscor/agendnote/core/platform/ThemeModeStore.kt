package com.franciscor.agendnote.core.platform

/**
 * Small platform-backed store for preferences that must survive a process restart.
 * The remote settings repository remains the source of truth when available; this store keeps
 * the last user choice available offline and during the next launch.
 */
interface ThemeModeStore {
    fun read(): Boolean?

    fun write(isDark: Boolean)
}

expect fun createThemeModeStore(): ThemeModeStore

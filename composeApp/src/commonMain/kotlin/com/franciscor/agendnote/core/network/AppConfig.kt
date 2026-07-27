package com.franciscor.agendnote.core.network

object AppConfig {
    const val API_BASE_URL: String = "https://pdcxxhnybykfbbvnnzki.functions.supabase.co"
    val APP_SECRET: String
        get() = AppSecrets.appSecret
    const val BACKGROUND_URL: String = ""
    const val APP_VERSION: String = "1.0"
}

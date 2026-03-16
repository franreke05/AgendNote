package com.franciscor.agendnote.core.network

data class RemoteConfigStatus(
    val isEnabled: Boolean,
    val message: String? = null,
) {
    companion object {
        fun resolve(
            apiBaseUrl: String,
            appSecret: String,
        ): RemoteConfigStatus {
            val hasBaseUrl = apiBaseUrl.isNotBlank()
            val hasAppSecret = appSecret.isNotBlank()
            val isEnabled = hasBaseUrl && hasAppSecret
            val message = when {
                isEnabled -> null
                !hasBaseUrl && !hasAppSecret -> {
                    "Configuracion remota incompleta. Faltan API_BASE_URL y APP_SECRET para conectar con la BD."
                }

                !hasBaseUrl -> {
                    "Configuracion remota incompleta. Falta API_BASE_URL para conectar con la BD."
                }

                else -> {
                    "Configuracion remota incompleta. Falta APP_SECRET para conectar con la BD."
                }
            }
            return RemoteConfigStatus(
                isEnabled = isEnabled,
                message = message,
            )
        }
    }
}

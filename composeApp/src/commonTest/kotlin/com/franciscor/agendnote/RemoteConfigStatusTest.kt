package com.franciscor.agendnote

import com.franciscor.agendnote.core.network.RemoteConfigStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteConfigStatusTest {
    @Test
    fun `resolve enables remote when base url and secret are present`() {
        val status = RemoteConfigStatus.resolve(
            apiBaseUrl = "https://example.supabase.co",
            appSecret = "top-secret",
        )

        assertTrue(status.isEnabled)
        assertNull(status.message)
    }

    @Test
    fun `resolve reports missing app secret`() {
        val status = RemoteConfigStatus.resolve(
            apiBaseUrl = "https://example.supabase.co",
            appSecret = "",
        )

        assertFalse(status.isEnabled)
        assertEquals(
            "Configuración remota incompleta. Falta APP_SECRET para conectar con la BD.",
            status.message,
        )
    }
}

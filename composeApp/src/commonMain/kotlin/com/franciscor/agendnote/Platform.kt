package com.franciscor.agendnote

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
package com.franciscor.agendnote.core.ui.components

import androidx.compose.ui.graphics.Color

fun labelColorPalette(): List<String> {
    return listOf(
        "#3DA9FC",
        "#FF7A59",
        "#39D98A",
        "#A17CFF",
        "#FFC857",
        "#5FD3BC",
        "#FF5D8F",
        "#6C5CE7",
        "#00B8A9",
        "#FDCB6E",
        "#74B9FF",
        "#55EFC4",
        "#FF6B6B",
        "#B388FF",
        "#F78FB3",
        "#4ECDC4",
    )
}

fun labelColorName(colorHex: String): String = when (colorHex.uppercase()) {
    "#3DA9FC" -> "Azul"
    "#FF7A59" -> "Coral"
    "#39D98A" -> "Verde"
    "#A17CFF" -> "Violeta"
    "#FFC857" -> "Amarillo"
    "#5FD3BC" -> "Turquesa"
    "#FF5D8F" -> "Rosa"
    "#6C5CE7" -> "Índigo"
    "#00B8A9" -> "Verde azulado"
    "#FDCB6E" -> "Ámbar"
    "#74B9FF" -> "Azul claro"
    "#55EFC4" -> "Menta"
    "#FF6B6B" -> "Rojo"
    "#B388FF" -> "Lila"
    "#F78FB3" -> "Rosa claro"
    "#4ECDC4" -> "Aguamarina"
    else -> "Color personalizado"
}

fun colorFromHex(hex: String): Color {
    val cleaned = hex.removePrefix("#").trim()
    val value = cleaned.toLongOrNull(16) ?: return Color(0xFF9AA4B2)
    val argb = when (cleaned.length) {
        6 -> 0xFF000000L or value
        8 -> value
        else -> return Color(0xFF9AA4B2)
    }
    return Color(argb)
}

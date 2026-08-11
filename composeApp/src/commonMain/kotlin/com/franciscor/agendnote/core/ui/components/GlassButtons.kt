package com.franciscor.agendnote.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.franciscor.agendnote.core.ui.theme.GlassTheme

/**
 * Named button variants (Operación Aniversario, "PRIORIDAD P0 VISUAL" per the product brief).
 *
 * Deliberately thin wrappers around the existing [GlassActionButton] rather than a new
 * implementation: [GlassActionButton] has 54 call sites across the app today, and rewriting the
 * underlying surface this week - four days from the deadline - is a risk with no product upside.
 * These give call sites intent-revealing names ("this is the primary action" vs "this is a
 * secondary one") without touching anything that already works. See the Glass token contract in
 * docs/OPERATION_ANNIVERSARY_STATUS.md for the full rationale and the states table.
 */
object GlassButton {

    /** The single primary/confirming action on a screen (Guardar, Crear, Confirmar). Same
     * visual as the existing default [GlassActionButton] - already the correct "filled coral"
     * treatment, just named explicitly now. */
    @Composable
    fun Primary(
        text: String,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        onClick: () -> Unit,
    ) {
        GlassActionButton(
            text = text,
            modifier = modifier,
            enabled = enabled,
            onClick = onClick,
        )
    }

    /** A secondary/neutral action (Cancelar, Reintentar, Cerrar) that should not compete
     * visually with a nearby [Primary] button. */
    @Composable
    fun Secondary(
        text: String,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        onClick: () -> Unit,
    ) {
        GlassActionButton(
            text = text,
            modifier = modifier,
            enabled = enabled,
            tint = if (enabled) GlassTheme.tokens.glassFillStrong else GlassTheme.tokens.glassFillDisabled,
            textColor = if (enabled) GlassTheme.tokens.textPrimary else GlassTheme.tokens.textDisabled,
            onClick = onClick,
        )
    }
}

/**
 * A destructive action (Eliminar, Borrar todo). Uses [GlassTokens.error] **opaque**, not
 * translucent like the rest of the Glass system - the one deliberate exception, matching what
 * [GlassConfirmDialog] already does for its confirm button: a destructive action needs maximum
 * clarity, not glass subtlety.
 */
@Composable
fun GlassDestructiveButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    GlassActionButton(
        text = text,
        modifier = modifier,
        enabled = enabled,
        tint = if (enabled) GlassTheme.tokens.error else GlassTheme.tokens.glassFillDisabled,
        textColor = if (enabled) GlassTheme.tokens.onError else GlassTheme.tokens.textDisabled,
        onClick = onClick,
    )
}

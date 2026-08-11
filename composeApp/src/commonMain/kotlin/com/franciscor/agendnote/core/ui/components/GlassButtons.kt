package com.franciscor.agendnote.core.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.franciscor.agendnote.core.ui.theme.ControlHeight
import com.franciscor.agendnote.core.ui.theme.GlassRadius
import com.franciscor.agendnote.core.ui.theme.Spacing
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

/**
 * Shared selectable pill for both true 2-way segmented controls (Claro/Oscuro) and independent
 * option-chip grids (recurrence type, weekday picker, month day picker) - consolidates what used
 * to be two near-duplicate implementations (`ModeToggleButton` in SettingsScreen.kt,
 * `RecurrenceOptionChip` in AgendaOverlays.kt: different corner radius - 16dp/16dp vs 14dp/12dp,
 * neither matching [GlassRadius.s] - different min-height literals, one of which didn't even
 * scale for compact screens, and `RecurrenceOptionChip` used [GlassTheme.tokens.onError] as its
 * selected-text color, an error-state token with no relation to selection). Operación
 * Aniversario, "P0 VISUAL" fix, 2026-08-11.
 *
 * A caller building a true segmented control (fixed 2-4 options that always divide the full
 * width) should give every chip `Modifier.weight(1f)` inside a single `Row`, same as before.
 */
@Composable
fun GlassSelectableChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role = Role.RadioButton,
    // 2, not 1: several recurrence-option chips (e.g. "Días de la semana") are two words that
    // can wrap on the narrowest phones - RecurrenceOptionChip (the implementation this replaces)
    // always allowed 2 lines. Callers with text that never wraps (Claro/Oscuro) are unaffected.
    maxLines: Int = 2,
) {
    val tint = when {
        !enabled -> GlassTheme.tokens.glassFillDisabled
        selected -> GlassTheme.tokens.accentOnLight
        else -> GlassTheme.tokens.glassFillStrong
    }
    // White, not GlassTokens.onError - onError is a semantically unrelated token that only
    // happens to already be white; using it here tied a selection color to error-state theming
    // by accident.
    val textColor = when {
        !enabled -> GlassTheme.tokens.textDisabled
        selected -> Color.White
        else -> GlassTheme.tokens.textPrimary
    }
    GlassSurface(
        modifier = modifier
            .defaultMinSize(minHeight = ControlHeight.standard())
            .clip(RoundedCornerShape(GlassRadius.s()))
            .selectable(
                selected = selected,
                enabled = enabled,
                role = role,
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(GlassRadius.s()),
        tint = tint,
        strokeColor = if (selected) tint.copy(alpha = 0.6f) else GlassTheme.tokens.glassStroke,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.m(), vertical = Spacing.s()),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

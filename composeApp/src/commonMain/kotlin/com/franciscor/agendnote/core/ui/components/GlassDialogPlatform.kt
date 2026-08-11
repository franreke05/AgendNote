package com.franciscor.agendnote.core.ui.components

import androidx.compose.runtime.Composable

/**
 * Disables the host platform's own dim/scrim behind an `androidx.compose.ui.window.Dialog`
 * window, so the ONLY darkening the user sees is [GlassScrim] - drawn explicitly by
 * [GlassScrimLayer] inside the dialog's own content.
 *
 * Root cause this exists for (Operación Aniversario, "P0 VISUAL" fix, 2026-08-11): every Glass
 * presentation (sheet/popover/alert) already draws its own [GlassScrimLayer], but a platform
 * `Dialog` window applies its own default background dim on top of/behind that independently
 * (this is standard platform dialog behavior, not an AgendNote bug) - the two stacked together
 * read as a much darker, harder-edged "rectangle" than either alone, and don't match
 * [GlassScrim]'s color/alpha, which is exactly the "parece un Dialog de Android colocado encima
 * de otra Surface" symptom reported against real screenshots. Call this once, right after
 * `Dialog { ... }` opens, from every Glass presentation.
 */
@Composable
expect fun DisableDialogPlatformDim()

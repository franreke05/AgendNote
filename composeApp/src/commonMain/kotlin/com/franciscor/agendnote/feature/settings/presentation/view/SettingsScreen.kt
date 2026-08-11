package com.franciscor.agendnote.feature.settings.presentation.view

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.franciscor.agendnote.app.navigation.SectionHeader
import com.franciscor.agendnote.core.model.TaskSeries
import com.franciscor.agendnote.core.model.PersonalMessage
import com.franciscor.agendnote.core.network.AppConfig
import com.franciscor.agendnote.core.notifications.NotificationPermissionStatus
import com.franciscor.agendnote.core.notifications.NotificationServiceProvider
import com.franciscor.agendnote.core.notifications.NotificationSoundId
import com.franciscor.agendnote.core.platform.isDebugBuild
import com.franciscor.agendnote.core.ui.components.GlassActionButton
import com.franciscor.agendnote.core.ui.components.GlassConfirmDialog
import com.franciscor.agendnote.core.ui.components.GlassSelectableChip
import com.franciscor.agendnote.core.ui.components.GlassSurface
import com.franciscor.agendnote.core.ui.components.GlassTextField
import com.franciscor.agendnote.core.ui.layout.AppLayout
import com.franciscor.agendnote.core.ui.theme.GlassTheme
import com.franciscor.agendnote.feature.agenda.domain.RecurrenceRule
import com.franciscor.agendnote.feature.settings.presentation.controller.SettingsController
import com.franciscor.agendnote.feature.settings.presentation.model.SettingsAction
import com.franciscor.agendnote.feature.settings.presentation.model.SettingsBulkAction
import com.franciscor.agendnote.feature.settings.presentation.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    controller: SettingsController,
    onExportRequested: () -> String = { "" },
    onDeleteAllNotes: suspend () -> Boolean,
    onDeleteAllLabels: suspend () -> Boolean,
    seriesList: List<TaskSeries>,
    onDeleteSeries: (TaskSeries) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = AppLayout.metrics
    val contentInset = layout.width(16.dp, 14.dp)
    val uiState = viewModel.uiState
    val isEditingEnabled = uiState.isRemoteAvailable
    var seriesPendingDelete by remember { mutableStateOf<TaskSeries?>(null) }
    val clipboardManager = LocalClipboardManager.current
    var exportCopiedMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = contentInset),
        verticalArrangement = Arrangement.spacedBy(layout.height(12.dp, 10.dp)),
        contentPadding = PaddingValues(
            bottom = layout.height(24.dp, 20.dp),
            top = layout.height(12.dp, 10.dp),
        ),
    ) {
        item {
            SectionHeader("Ajustes", "Personaliza la app y gestiona tus datos")
        }

        item {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(layout.size(24.dp, 20.dp)),
            ) {
                Column(
                    modifier = Modifier.padding(layout.size(16.dp, 14.dp)),
                    verticalArrangement = Arrangement.spacedBy(layout.height(12.dp, 10.dp)),
                ) {
                    Text(
                        text = "Modo de color",
                        style = MaterialTheme.typography.titleMedium,
                        color = GlassTheme.tokens.textPrimary,
                    )
                    // Suppressed when the remote is unavailable: that error text is always
                    // identical to the app-wide RemoteStatusBanner already shown above the tab
                    // content (AppNavHost), so repeating it here would just be redundant.
                    if (uiState.errorMessage != null && isEditingEnabled) {
                        Text(
                            text = uiState.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = GlassTheme.tokens.errorContent,
                        )
                    }
                    Row(
                        modifier = Modifier.selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(layout.width(10.dp, 8.dp)),
                    ) {
                        GlassSelectableChip(
                            text = "Claro",
                            selected = !uiState.isDarkMode,
                            enabled = isEditingEnabled,
                            onClick = { controller.handle(SettingsAction.SetTheme(false)) },
                            modifier = Modifier.weight(1f),
                        )
                        GlassSelectableChip(
                            text = "Oscuro",
                            selected = uiState.isDarkMode,
                            enabled = isEditingEnabled,
                            onClick = { controller.handle(SettingsAction.SetTheme(true)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        item {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(layout.size(24.dp, 20.dp)),
            ) {
                Column(
                    modifier = Modifier.padding(layout.size(16.dp, 14.dp)),
                    verticalArrangement = Arrangement.spacedBy(layout.height(12.dp, 10.dp)),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(layout.width(8.dp, 6.dp)),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Wallpaper,
                            contentDescription = null,
                            tint = GlassTheme.tokens.textSecondary,
                            modifier = Modifier.size(layout.size(20.dp, 18.dp)),
                        )
                        Text(
                            text = "Fondo",
                            style = MaterialTheme.typography.titleMedium,
                            color = GlassTheme.tokens.textPrimary,
                        )
                    }
                    Text(
                        text = "Pega la URL de una imagen para personalizar el fondo de la app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTheme.tokens.textSecondary,
                    )
                    var backgroundUrlInput by remember(uiState.backgroundUrl) {
                        mutableStateOf(uiState.backgroundUrl)
                    }
                    val normalizedBackgroundUrl = backgroundUrlInput.trim()
                    val isBackgroundUrlValid = normalizedBackgroundUrl.isBlank() ||
                        normalizedBackgroundUrl.startsWith("https://", ignoreCase = true) ||
                        normalizedBackgroundUrl.startsWith("http://", ignoreCase = true)
                    Text(
                        text = "URL de la imagen",
                        style = MaterialTheme.typography.labelMedium,
                        color = GlassTheme.tokens.textSecondary,
                    )
                    GlassTextField(
                        value = backgroundUrlInput,
                        onValueChange = { backgroundUrlInput = it },
                        placeholder = "https://...",
                        label = "URL de la imagen de fondo",
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                    if (!isBackgroundUrlValid) {
                        Text(
                            text = "Introduce una dirección que empiece por https:// o http://",
                            style = MaterialTheme.typography.bodySmall,
                            color = GlassTheme.tokens.errorContent,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(layout.width(10.dp, 8.dp))) {
                        GlassActionButton(
                            text = "Guardar",
                            enabled = isEditingEnabled &&
                                isBackgroundUrlValid &&
                                normalizedBackgroundUrl != uiState.backgroundUrl,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                controller.handle(SettingsAction.SetBackgroundUrl(normalizedBackgroundUrl))
                            },
                        )
                        val canClearBackground = isEditingEnabled && uiState.backgroundUrl.isNotBlank()
                        GlassActionButton(
                            text = "Quitar",
                            enabled = canClearBackground,
                            tint = if (canClearBackground) GlassTheme.tokens.glassFillStrong else GlassTheme.tokens.glassFill,
                            textColor = if (canClearBackground) GlassTheme.tokens.textPrimary else GlassTheme.tokens.textSecondary,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                backgroundUrlInput = ""
                                controller.handle(SettingsAction.SetBackgroundUrl(""))
                            },
                        )
                    }
                }
            }
        }

        item {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(layout.size(24.dp, 20.dp)),
            ) {
                Column(
                    modifier = Modifier.padding(layout.size(16.dp, 14.dp)),
                    verticalArrangement = Arrangement.spacedBy(layout.height(12.dp, 10.dp)),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(layout.width(8.dp, 6.dp)),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Notifications,
                            contentDescription = null,
                            tint = GlassTheme.tokens.textSecondary,
                            modifier = Modifier.size(layout.size(20.dp, 18.dp)),
                        )
                        Text(
                            text = "Notificaciones",
                            style = MaterialTheme.typography.titleMedium,
                            color = GlassTheme.tokens.textPrimary,
                        )
                    }
                    Text(
                        text = "Concede los permisos de notificación y hora exacta para recibir cada aviso cuando corresponde.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTheme.tokens.textSecondary,
                    )
                    val notificationScope = rememberCoroutineScope()
                    // "no inventes falsas opciones si iOS controla la autorización": this reads
                    // the real OS-level state, never assumes anything from a locally-cached flag.
                    var permissionStatus by remember { mutableStateOf<NotificationPermissionStatus?>(null) }
                    LaunchedEffect(Unit) {
                        permissionStatus = runCatching {
                            NotificationServiceProvider.getNotificationService().checkPermissionStatus()
                        }.getOrNull()
                    }
                    permissionStatus?.let { status ->
                        Text(
                            text = "Estado actual: " + when (status) {
                                NotificationPermissionStatus.AUTHORIZED -> "autorizadas"
                                NotificationPermissionStatus.DENIED ->
                                    "denegadas - actívalas desde los Ajustes del sistema"
                                NotificationPermissionStatus.PROVISIONAL -> "silenciosas (provisional)"
                                NotificationPermissionStatus.NOT_DETERMINED -> "todavía no solicitadas"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (status == NotificationPermissionStatus.DENIED) {
                                GlassTheme.tokens.errorContent
                            } else {
                                GlassTheme.tokens.textSecondary
                            },
                        )
                    }
                    GlassActionButton(
                        text = "Configurar recordatorios",
                        tint = GlassTheme.tokens.glassFillStrong,
                        textColor = GlassTheme.tokens.textPrimary,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            notificationScope.launch {
                                NotificationServiceProvider.getNotificationService().requestPermissions()
                                permissionStatus = NotificationServiceProvider.getNotificationService()
                                    .checkPermissionStatus()
                            }
                        },
                    )
                    // Development-only QA affordance (directive item 13) - never shown in a
                    // release build (isDebugBuild is a compile-time constant per platform, so
                    // this branch doesn't even exist in a release binary, not just hidden by a
                    // runtime check).
                    if (isDebugBuild) {
                        GlassActionButton(
                            text = "Probar notificación (+10s)",
                            tint = GlassTheme.tokens.glassFill,
                            textColor = GlassTheme.tokens.textPrimary,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                notificationScope.launch {
                                    NotificationServiceProvider.getNotificationService()
                                        .schedulePersonalMessageNotification(
                                            PersonalMessage(
                                                id = "debug_test_${Clock.System.now().toEpochMilliseconds()}",
                                                title = "Prueba de notificación",
                                                body = "Si ves esto, las notificaciones funcionan.",
                                                scheduledAt = Clock.System.now() + 10.seconds,
                                                notificationSoundId = NotificationSoundId.REMINDER_GENERAL,
                                            ),
                                        )
                                }
                            },
                        )
                    }
                }
            }
        }

        item {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(layout.size(24.dp, 20.dp)),
            ) {
                Column(
                    modifier = Modifier.padding(layout.size(16.dp, 14.dp)),
                    verticalArrangement = Arrangement.spacedBy(layout.height(12.dp, 10.dp)),
                ) {
                    Text(
                        text = "Datos",
                        style = MaterialTheme.typography.titleMedium,
                        color = GlassTheme.tokens.textPrimary,
                    )
                    Text(
                        text = "Copia tus tareas y etiquetas visibles como JSON al portapapeles. " +
                            "Solo incluye lo que ya está cargado (los días y meses que visitaste), " +
                            "no exporta la nube entera.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTheme.tokens.textSecondary,
                    )
                    GlassActionButton(
                        text = "Exportar mis datos",
                        tint = GlassTheme.tokens.glassFillStrong,
                        textColor = GlassTheme.tokens.textPrimary,
                        onClick = {
                            val json = onExportRequested()
                            clipboardManager.setText(AnnotatedString(json))
                            exportCopiedMessage = "Copiado al portapapeles"
                        },
                    )
                    exportCopiedMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.labelMedium,
                            color = GlassTheme.tokens.textSecondary,
                        )
                    }
                }
            }
        }

        item {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(layout.size(24.dp, 20.dp)),
            ) {
                Column(
                    modifier = Modifier.padding(layout.size(16.dp, 14.dp)),
                    verticalArrangement = Arrangement.spacedBy(layout.height(12.dp, 10.dp)),
                ) {
                    Text(
                        text = "Zona de peligro",
                        style = MaterialTheme.typography.titleMedium,
                        color = GlassTheme.tokens.textPrimary,
                    )
                    Text(
                        text = "Estas acciones borran datos en la nube.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTheme.tokens.textSecondary,
                    )
                    GlassActionButton(
                        text = "Borrar todas las tareas",
                        enabled = isEditingEnabled,
                        tint = GlassTheme.tokens.error.copy(alpha = 0.18f),
                        textColor = GlassTheme.tokens.errorContent,
                        onClick = {
                            controller.handle(
                                SettingsAction.RequestBulkAction(SettingsBulkAction.DELETE_NOTES),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GlassActionButton(
                        text = "Borrar todas las etiquetas",
                        enabled = isEditingEnabled,
                        tint = GlassTheme.tokens.error.copy(alpha = 0.18f),
                        textColor = GlassTheme.tokens.errorContent,
                        onClick = {
                            controller.handle(
                                SettingsAction.RequestBulkAction(SettingsBulkAction.DELETE_LABELS),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (seriesList.isNotEmpty()) {
            item {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(layout.size(24.dp, 20.dp)),
                ) {
                    Column(
                        modifier = Modifier.padding(layout.size(16.dp, 14.dp)),
                        verticalArrangement = Arrangement.spacedBy(layout.height(12.dp, 10.dp)),
                    ) {
                        Text(
                            text = "Tareas recurrentes",
                            style = MaterialTheme.typography.titleMedium,
                            color = GlassTheme.tokens.textPrimary,
                        )
                        seriesList.forEach { series ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = series.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = GlassTheme.tokens.textPrimary,
                                    )
                                    Text(
                                        text = describeRecurrence(series.rule),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GlassTheme.tokens.textSecondary,
                                    )
                                }
                                GlassActionButton(
                                    text = "Borrar",
                                    enabled = isEditingEnabled,
                                    tint = GlassTheme.tokens.error.copy(alpha = 0.18f),
                                    textColor = GlassTheme.tokens.errorContent,
                                    onClick = { seriesPendingDelete = series },
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(layout.size(24.dp, 20.dp)),
            ) {
                Column(
                    modifier = Modifier.padding(layout.size(16.dp, 14.dp)),
                    verticalArrangement = Arrangement.spacedBy(layout.height(6.dp, 5.dp)),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(layout.width(8.dp, 6.dp)),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = GlassTheme.tokens.textSecondary,
                            modifier = Modifier.size(layout.size(20.dp, 18.dp)),
                        )
                        Text(
                            text = "Acerca de",
                            style = MaterialTheme.typography.titleMedium,
                            color = GlassTheme.tokens.textPrimary,
                        )
                    }
                    Text(
                        text = "AgendNote · v${AppConfig.APP_VERSION}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTheme.tokens.textSecondary,
                    )
                    Text(
                        text = "Tu agenda de tareas con etiquetas y series recurrentes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassTheme.tokens.textSecondary.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }

    val pendingBulkAction = uiState.pendingBulkAction
    GlassConfirmDialog(
        visible = pendingBulkAction != null,
        title = if (pendingBulkAction == SettingsBulkAction.DELETE_NOTES) {
            "¿Borrar todas las tareas?"
        } else {
            "¿Borrar todas las etiquetas?"
        },
        message = if (pendingBulkAction == SettingsBulkAction.DELETE_NOTES) {
            "Esta acción elimina todas las tareas guardadas."
        } else {
            "Esta acción elimina todas las etiquetas creadas."
        },
        confirmText = "Borrar",
        onConfirm = {
            if (pendingBulkAction != null) {
                controller.handle(
                    SettingsAction.ConfirmBulkAction {
                        if (pendingBulkAction == SettingsBulkAction.DELETE_NOTES) {
                            onDeleteAllNotes()
                        } else {
                            onDeleteAllLabels()
                        }
                    },
                )
            }
        },
        onDismiss = { controller.handle(SettingsAction.DismissBulkAction) },
    )

    seriesPendingDelete?.let { series ->
        GlassConfirmDialog(
            visible = true,
            title = "¿Borrar serie recurrente?",
            message = "Se eliminarán las apariciones futuras de \"${series.title}\" que no estén completadas. Las pasadas se conservan.",
            confirmText = "Borrar",
            onConfirm = {
                seriesPendingDelete = null
                onDeleteSeries(series)
            },
            onDismiss = { seriesPendingDelete = null },
        )
    }
}

// Claro/Oscuro used to be a local ModeToggleButton here, near-duplicating
// AgendaOverlays.kt's RecurrenceOptionChip - both replaced by the shared
// GlassSelectableChip (core/ui/components/GlassButtons.kt).

private fun describeRecurrence(rule: RecurrenceRule): String {
    return when (rule) {
        is RecurrenceRule.Daily -> "Todos los días"
        is RecurrenceRule.WeeklyDays -> {
            val names = rule.days.sortedBy { it.isoDayNumber }.joinToString(", ") { day ->
                when (day) {
                    DayOfWeek.MONDAY -> "lunes"
                    DayOfWeek.TUESDAY -> "martes"
                    DayOfWeek.WEDNESDAY -> "miércoles"
                    DayOfWeek.THURSDAY -> "jueves"
                    DayOfWeek.FRIDAY -> "viernes"
                    DayOfWeek.SATURDAY -> "sábado"
                    DayOfWeek.SUNDAY -> "domingo"
                }
            }
            "Cada $names"
        }
        is RecurrenceRule.Monthly -> "El día ${rule.dayOfMonth} de cada mes"
    }
}


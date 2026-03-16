package com.franciscor.agendnote.app.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.franciscor.agendnote.core.ui.layout.AppLayout
import com.franciscor.agendnote.core.ui.components.GlassSurface
import com.franciscor.agendnote.core.ui.theme.GlassTheme

@Composable
fun SectionHeader(
    title: String,
    subtitle: String,
) {
    val layout = AppLayout.metrics
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.displayLarge,
            color = GlassTheme.tokens.textPrimary,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = GlassTheme.tokens.textSecondary,
        )
        Spacer(modifier = Modifier.height(layout.height(16.dp, 12.dp)))
    }
}

@Composable
fun BottomBar(
    modifier: Modifier = Modifier,
    selectedTab: MainTab,
    onSelect: (MainTab) -> Unit,
) {
    val layout = AppLayout.metrics
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(layout.height(68.dp, 62.dp)),
        shape = RoundedCornerShape(layout.size(28.dp, 24.dp)),
        tint = GlassTheme.tokens.glassFillStrong,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = layout.width(18.dp, 16.dp)),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomBarItem(
                icon = Icons.Rounded.CalendarToday,
                label = "Agenda",
                selected = selectedTab == MainTab.AGENDA,
                onClick = { onSelect(MainTab.AGENDA) },
            )
            BottomBarItem(
                icon = Icons.Rounded.Label,
                label = "Etiquetas",
                selected = selectedTab == MainTab.LABELS,
                onClick = { onSelect(MainTab.LABELS) },
            )
            BottomBarItem(
                icon = Icons.Rounded.Settings,
                label = "Ajustes",
                selected = selectedTab == MainTab.SETTINGS,
                onClick = { onSelect(MainTab.SETTINGS) },
            )
        }
    }
}

@Composable
fun RemoteStatusBanner(
    message: String,
    modifier: Modifier = Modifier,
) {
    val layout = AppLayout.metrics
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(layout.size(22.dp, 18.dp)),
        tint = Color(0xFFFDE7E7),
        strokeColor = Color(0xFFF2B8B8),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF9C2F2F),
            modifier = Modifier.padding(
                horizontal = layout.width(16.dp, 14.dp),
                vertical = layout.height(12.dp, 10.dp),
            ),
        )
    }
}

@Composable
private fun BottomBarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit = {},
) {
    val layout = AppLayout.metrics
    val color = if (selected) GlassTheme.tokens.textPrimary else GlassTheme.tokens.textSecondary
    Column(
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(layout.height(2.dp, 2.dp)),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(layout.size(26.dp, 22.dp)),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
fun ConfirmBulkDialog(
    visible: Boolean,
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val layout = AppLayout.metrics
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            modifier = Modifier
                .padding(horizontal = layout.width(24.dp, 20.dp))
                .fillMaxWidth(),
            shape = RoundedCornerShape(layout.size(28.dp, 24.dp)),
            tint = GlassTheme.tokens.glassFillStrong,
        ) {
            Column(
                modifier = Modifier.padding(layout.size(20.dp, 16.dp)),
                verticalArrangement = Arrangement.spacedBy(layout.height(14.dp, 12.dp)),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = GlassTheme.tokens.textPrimary,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTheme.tokens.textSecondary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(layout.width(10.dp, 8.dp)),
                ) {
                    ModeDialogButton(
                        text = "Cancelar",
                        tint = GlassTheme.tokens.glassFill,
                        textColor = GlassTheme.tokens.textPrimary,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    ModeDialogButton(
                        text = "Borrar",
                        tint = Color(0xFFE06B6B),
                        textColor = Color.White,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeDialogButton(
    text: String,
    tint: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = AppLayout.metrics
    GlassSurface(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(layout.size(16.dp, 16.dp)),
        tint = tint,
        strokeColor = tint.copy(alpha = 0.5f),
    ) {
        Box(
            modifier = Modifier.padding(vertical = layout.height(10.dp, 10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

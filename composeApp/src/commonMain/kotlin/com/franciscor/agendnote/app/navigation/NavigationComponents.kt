package com.franciscor.agendnote.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
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
        Spacer(modifier = Modifier.height(layout.height(8.dp, 6.dp)))
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
            .height(layout.height(64.dp, 60.dp)),
        shape = RoundedCornerShape(layout.size(28.dp, 24.dp)),
        tint = GlassTheme.tokens.glassFillStrong,
        shadowElevation = 12.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .selectableGroup()
                .padding(horizontal = layout.width(10.dp, 8.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomBarItem(
                icon = Icons.Rounded.Checklist,
                label = "Agenda",
                selected = selectedTab == MainTab.AGENDA,
                onClick = { onSelect(MainTab.AGENDA) },
                modifier = Modifier.weight(1f),
            )
            BottomBarItem(
                icon = Icons.Rounded.Schedule,
                label = "Día",
                selected = selectedTab == MainTab.DAY,
                onClick = { onSelect(MainTab.DAY) },
                modifier = Modifier.weight(1f),
            )
            BottomBarItem(
                icon = Icons.AutoMirrored.Rounded.Label,
                label = "Etiquetas",
                selected = selectedTab == MainTab.LABELS,
                onClick = { onSelect(MainTab.LABELS) },
                modifier = Modifier.weight(1f),
            )
            BottomBarItem(
                icon = Icons.Rounded.Settings,
                label = "Ajustes",
                selected = selectedTab == MainTab.SETTINGS,
                onClick = { onSelect(MainTab.SETTINGS) },
                modifier = Modifier.weight(1f),
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
        tint = GlassTheme.tokens.error.copy(alpha = 0.16f),
        strokeColor = GlassTheme.tokens.error.copy(alpha = 0.45f),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = GlassTheme.tokens.errorContent,
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
    modifier: Modifier = Modifier,
) {
    val layout = AppLayout.metrics
    // Keep inactive tabs readable against the dark glass surface while preserving a clear
    // selected-state contrast.
    val color = if (selected) {
        GlassTheme.tokens.textPrimary
    } else {
        GlassTheme.tokens.textPrimary.copy(alpha = 0.72f)
    }
    // Neutral wash behind the active tab (existing textPrimary token at low alpha) so the
    // selection reads at a glance, without introducing the accent color into navigation.
    val backgroundColor = if (selected) {
        GlassTheme.tokens.textPrimary.copy(alpha = 0.08f)
    } else {
        Color.Transparent
    }
    // Each item gets equal weight from the parent Row (see BottomBar) instead of sizing to its
    // own content, so a longer label on an earlier tab can't shrink the width left for a later
    // one and force it to wrap (that used to happen to "Ajustes", the last/narrowest item).
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(layout.size(16.dp, 14.dp)))
            .background(backgroundColor)
            .semantics(mergeDescendants = true) {}
            .selectable(
                selected = selected,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = layout.height(6.dp, 5.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(layout.height(2.dp, 2.dp)),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(layout.size(26.dp, 22.dp)),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

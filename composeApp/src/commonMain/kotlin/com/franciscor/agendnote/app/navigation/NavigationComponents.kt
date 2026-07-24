package com.franciscor.agendnote.app.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.vector.ImageVector
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

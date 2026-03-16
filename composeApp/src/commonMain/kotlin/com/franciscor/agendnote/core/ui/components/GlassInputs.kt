package com.franciscor.agendnote.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.franciscor.agendnote.core.ui.layout.AppLayout
import com.franciscor.agendnote.core.ui.theme.GlassTheme

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val layout = AppLayout.metrics
    val tokens = GlassTheme.tokens
    BasicTextField(
        modifier = modifier,
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        maxLines = maxLines,
        textStyle = textStyle.copy(color = tokens.textPrimary),
        cursorBrush = SolidColor(tokens.accent),
        decorationBox = { innerTextField ->
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = layout.height(58.dp, 52.dp)),
                shape = RoundedCornerShape(layout.size(18.dp, 16.dp)),
                tint = tokens.glassFill,
                strokeColor = tokens.glassStroke,
            ) {
                Box(
                    modifier = Modifier.padding(
                        horizontal = layout.width(16.dp, 14.dp),
                        vertical = layout.height(12.dp, 10.dp),
                    ),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isBlank()) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = tokens.textSecondary,
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}

@Composable
fun GlassActionButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = if (enabled) GlassTheme.tokens.accent else GlassTheme.tokens.glassFillStrong,
    textColor: Color = if (enabled) Color.White else GlassTheme.tokens.textSecondary,
    onClick: () -> Unit,
) {
    val layout = AppLayout.metrics
    val radius = layout.size(18.dp, 16.dp)
    GlassSurface(
        modifier = modifier
            .defaultMinSize(
                minWidth = layout.width(84.dp, 76.dp),
                minHeight = layout.height(52.dp, 46.dp),
            )
            .clip(RoundedCornerShape(radius))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(radius),
        tint = tint,
        strokeColor = tint.copy(alpha = 0.5f),
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = layout.width(18.dp, 14.dp),
                vertical = layout.height(12.dp, 10.dp),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = layout.text(15.sp, 14.sp),
                ),
                color = textColor,
            )
        }
    }
}

@Composable
fun GlassSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "Buscar",
    modifier: Modifier = Modifier,
) {
    val layout = AppLayout.metrics
    val tokens = GlassTheme.tokens
    GlassSurface(
        modifier = modifier.defaultMinSize(minHeight = layout.height(56.dp, 50.dp)),
        shape = RoundedCornerShape(layout.size(18.dp, 16.dp)),
        tint = tokens.glassFill,
        strokeColor = tokens.glassStroke,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = layout.width(16.dp, 14.dp),
                vertical = layout.height(12.dp, 10.dp),
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(layout.width(10.dp, 8.dp)),
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = tokens.textSecondary,
                modifier = Modifier.size(layout.size(24.dp, 20.dp)),
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = tokens.textPrimary),
                cursorBrush = SolidColor(tokens.accent),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (value.isBlank()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = tokens.textSecondary,
                        )
                    }
                    innerTextField()
                },
            )
            if (value.isNotBlank()) {
                Spacer(modifier = Modifier.width(layout.width(4.dp, 4.dp)))
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Limpiar",
                    tint = tokens.textSecondary,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onValueChange("") },
                    ),
                )
            }
        }
    }
}

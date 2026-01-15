package com.franciscor.agendnote.ui.components

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.franciscor.agendnote.ui.theme.GlassTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search

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
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                tint = tokens.glassFill,
                strokeColor = tokens.glassStroke,
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isBlank()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
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
    GlassSurface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(16.dp),
        tint = tint,
        strokeColor = tint.copy(alpha = 0.5f),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
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
    val tokens = GlassTheme.tokens
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        tint = tokens.glassFill,
        strokeColor = tokens.glassStroke,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = tokens.textSecondary,
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
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.textSecondary,
                        )
                    }
                    innerTextField()
                },
            )
            if (value.isNotBlank()) {
                Spacer(modifier = Modifier.width(4.dp))
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

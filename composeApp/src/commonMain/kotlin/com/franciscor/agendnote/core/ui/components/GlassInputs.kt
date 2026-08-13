package com.franciscor.agendnote.core.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.franciscor.agendnote.core.ui.layout.AppLayout
import com.franciscor.agendnote.core.ui.theme.ControlHeight
import com.franciscor.agendnote.core.ui.theme.GlassElevation
import com.franciscor.agendnote.core.ui.theme.GlassRadius
import com.franciscor.agendnote.core.ui.theme.GlassTheme

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    label: String = placeholder,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    minHeight: Dp = ControlHeight.large(),
) {
    val layout = AppLayout.metrics
    val tokens = GlassTheme.tokens
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val containerTint = if (enabled) tokens.glassFill else tokens.glassFillDisabled
    // Focus is the only state communicated with a color shift here (no border-width change:
    // GlassSurface doesn't expose stroke width as a parameter, and widening it is a separate,
    // larger change - see docs/OPERATION_ANNIVERSARY_STATUS.md, Glass design spec).
    val strokeColor = when {
        !enabled -> tokens.glassStroke
        isFocused -> tokens.focusStroke
        else -> tokens.glassStroke
    }
    BasicTextField(
        // REVIEW: BasicTextField exposes editing semantics but no stable field name. Supplying
        // one here makes every glass input understandable to screen readers.
        modifier = modifier
            .semantics { contentDescription = label }
            .alpha(if (enabled) 1f else 0.6f),
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        textStyle = textStyle.copy(color = tokens.textPrimary),
        cursorBrush = SolidColor(tokens.accent),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = minHeight),
                shape = RoundedCornerShape(GlassRadius.s()),
                tint = containerTint,
                strokeColor = strokeColor,
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
                            color = if (enabled) tokens.textSecondary else tokens.textDisabled,
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
    // Uses accentOnLight (not accent) as the fill: white text on the raw #FF8A5B accent only
    // reaches ~2.3:1 contrast, well below WCAG AA's 4.5:1. accentOnLight is a darkened variant
    // of the same hue that keeps white text legible.
    tint: Color = if (enabled) GlassTheme.tokens.accentOnLight else GlassTheme.tokens.glassFillStrong,
    textColor: Color = if (enabled) Color.White else GlassTheme.tokens.textSecondary,
    onClick: () -> Unit,
) {
    val layout = AppLayout.metrics
    val radius = GlassRadius.s()
    val indication = LocalIndication.current
    GlassSurface(
        modifier = modifier
            .defaultMinSize(
                minWidth = layout.width(84.dp, 76.dp),
                minHeight = ControlHeight.standard(),
            )
            .clip(RoundedCornerShape(radius))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = indication,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(radius),
        tint = tint,
        strokeColor = tint.copy(alpha = 0.5f),
        // Was missing before (defaulted to 0.dp, GlassSurface's fused/background level) even
        // though this is the primary CTA - it should float like GlassIconButton/cards, not fuse
        // with the background like an input field. See Glass design spec, §2.3.
        shadowElevation = GlassElevation.floating,
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val layout = AppLayout.metrics
    val tokens = GlassTheme.tokens
    GlassSurface(
        modifier = modifier.defaultMinSize(minHeight = ControlHeight.large()),
        shape = RoundedCornerShape(GlassRadius.s()),
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
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = placeholder },
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
                Box(
                    modifier = Modifier
                        .size(ControlHeight.standard())
                        .clip(RoundedCornerShape(layout.size(14.dp, 12.dp)))
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Limpiar búsqueda",
                            interactionSource = remember { MutableInteractionSource() },
                            indication = LocalIndication.current,
                            onClick = { onValueChange("") },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = tokens.textSecondary,
                        modifier = Modifier.size(layout.size(22.dp, 20.dp)),
                    )
                }
            }
            trailingContent?.invoke()
        }
    }
}

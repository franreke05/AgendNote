package com.franciscor.agendnote.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.franciscor.agendnote.core.ui.layout.AppLayout
import com.franciscor.agendnote.core.ui.theme.GlassTheme
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import kotlin.random.Random

@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
) {
    val layout = AppLayout.metrics
    val tokens = GlassTheme.tokens
    val imageAlpha by animateFloatAsState(
        targetValue = if (imageUrl.isNullOrBlank()) 0f else 1f,
        animationSpec = tween(durationMillis = 700),
        label = "backgroundImageAlpha",
    )
    Box(
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(tokens.backgroundTop, tokens.backgroundBottom),
                    ),
                ),
        )

        if (!imageUrl.isNullOrBlank()) {
            val painterResource = asyncPainterResource(data = imageUrl)
            KamelImage(
                resource = painterResource,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(layout.size(28.dp, 20.dp))
                    .alpha(imageAlpha),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                tokens.backgroundTop.copy(alpha = 0.6f),
                                tokens.backgroundBottom.copy(alpha = 0.8f),
                            ),
                        ),
                    )
                    .alpha(imageAlpha),
            )
        }

        Box(
            modifier = Modifier
                .offset(
                    x = -layout.width(80.dp, 60.dp),
                    y = -layout.height(120.dp, 90.dp),
                )
                .size(layout.size(280.dp, 220.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x66FFFFFF), Color.Transparent),
                    ),
                    shape = CircleShape,
                )
                .blur(layout.size(90.dp, 70.dp)),
        )
        Box(
            modifier = Modifier
                .offset(
                    x = layout.width(220.dp, 180.dp),
                    y = layout.height(80.dp, 60.dp),
                )
                .size(layout.size(220.dp, 180.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x33FFFFFF), Color.Transparent),
                    ),
                    shape = CircleShape,
                )
                .blur(layout.size(80.dp, 64.dp)),
        )
        Box(
            modifier = Modifier
                .offset(
                    x = layout.width(40.dp, 30.dp),
                    y = layout.height(420.dp, 300.dp),
                )
                .size(layout.size(320.dp, 240.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x55C7E6FF), Color.Transparent),
                    ),
                    shape = CircleShape,
                )
                .blur(layout.size(120.dp, 90.dp)),
        )

        GrainOverlay(
            modifier = Modifier.fillMaxSize(),
            intensity = 0.06f,
        )
    }
}

@Composable
private fun GrainOverlay(
    modifier: Modifier = Modifier,
    intensity: Float = 0.06f,
) {
    Box(
        modifier = modifier.drawWithCache {
            val seed = 42
            val random = Random(seed)
            val count = ((size.width * size.height) / 6000f)
                .coerceIn(140f, 360f)
                .toInt()
            val points = List(count) {
                Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
            }
            val radii = List(count) { 0.4f + random.nextFloat() * 1.2f }
            val white = Color.White.copy(alpha = intensity)
            val black = Color.Black.copy(alpha = intensity * 0.45f)

            onDrawBehind {
                points.forEachIndexed { index, offset ->
                    val color = if (index % 3 == 0) black else white
                    drawCircle(color = color, radius = radii[index], center = offset)
                }
            }
        },
    )
}

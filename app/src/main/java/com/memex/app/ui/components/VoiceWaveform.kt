package com.memex.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.memex.app.ui.theme.MemexPurple
import com.memex.app.ui.theme.MemexTeal
import kotlin.math.abs
import kotlin.math.sin

/**
 * Animated waveform visualiser.
 *
 * Renders [barCount] vertical bars driven by [amplitudes]:
 * - Bar colour: teal when amplitude > 0.5, purple otherwise — smoothly blended via lerp.
 * - Bar height: driven by amplitude × sinusoidal envelope (taller in the centre).
 * - Idle animation: subtle sine-wave ripple plays when [isActive] is false.
 * - Active mode: bars scale up and colour brightens.
 *
 * @param amplitudes  Rolling window of [0,1] amplitude samples (any length — padded/clipped).
 * @param isActive    True during LISTENING — enables the amplitude-driven heights.
 */
@Composable
fun VoiceWaveform(
    amplitudes: List<Float>,
    isActive  : Boolean,
    modifier  : Modifier = Modifier
        .fillMaxWidth()
        .height(80.dp)
) {
    val barCount = 30

    // Idle sine-wave phase animation
    val infiniteTransition = rememberInfiniteTransition(label = "waveformIdle")
    val idlePhase by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation  = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "idlePhase"
    )

    // Brightness pulse when active
    val brightnessPulse by infiniteTransition.animateFloat(
        initialValue  = 0.7f,
        targetValue   = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "brightness"
    )

    Canvas(modifier = modifier) {
        val barSpacing = size.width / (barCount * 2f)
        val barWidth   = barSpacing * 1.4f
        val centerY    = size.height / 2f
        val minBarH    = 4.dp.toPx()

        for (i in 0 until barCount) {
            val t         = i.toFloat() / (barCount - 1)
            // Centre bias — bars are tallest in the middle
            val envBias   = 1f - abs(t - 0.5f) * 1.6f

            val amplitude = if (isActive) {
                amplitudes.getOrElse(i) { 0f }.coerceIn(0f, 1f)
            } else {
                // Idle sine ripple
                (0.12f + 0.10f * sin(idlePhase + t * 8f)).toFloat()
            }

            val barHeight = (amplitude * envBias * size.height * 0.82f)
                .coerceAtLeast(minBarH)

            val x = i * (barSpacing * 2f) + barSpacing * 0.3f

            // Colour: lerp purple → teal based on amplitude
            val blend    = (amplitude * 2f).coerceIn(0f, 1f)
            val alpha    = if (isActive) brightnessPulse else 0.55f
            val barColor = lerpColor(
                MemexPurple.copy(alpha = alpha),
                MemexTeal.copy(alpha   = alpha),
                blend
            )

            // Mirror bar above and below centre line
            drawRoundRect(
                color        = barColor,
                topLeft      = Offset(x, centerY - barHeight / 2f),
                size         = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
        }

        // Centre divider line (very subtle)
        drawLine(
            color       = Color.White.copy(alpha = 0.05f),
            start       = Offset(0f, centerY),
            end         = Offset(size.width, centerY),
            strokeWidth = 1f
        )
    }
}

/** Linear interpolation between two [Color] values. */
private fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red   = a.red   + (b.red   - a.red)   * t,
    green = a.green + (b.green - a.green) * t,
    blue  = a.blue  + (b.blue  - a.blue)  * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t
)

package com.memex.app.ui.screens.resurrection

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memex.app.domain.model.Memory
import com.memex.app.domain.model.MemoryType
import com.memex.app.ui.components.typeAccentColor
import com.memex.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
// ResurrectionAnimation
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Cinematic convergence animation:
 *
 *  Phase 0 → cards start spread in a circle
 *  Phase 1 → cards spring-fly toward centre (after 200 ms)
 *  Phase 2 → merged pulse glow + scale swell (after 1 400 ms)
 *  Phase 3 → fade everything to black (after 2 200 ms)
 *  → [onAnimationComplete] fires (after 2 500 ms)
 */
@Composable
fun ResurrectionAnimation(
    memories           : List<Memory>,
    onAnimationComplete: () -> Unit
) {
    var phase by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        delay(200);  phase = 1   // spread → converge
        delay(1200); phase = 2   // merged glow pulse
        delay(800);  phase = 3   // dissolve
        delay(300);  onAnimationComplete()
    }

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(MemexBlack),
        contentAlignment = Alignment.Center
    ) {

        // ── Memory cards flying inward ────────────────────────────────────────
        memories.forEachIndexed { index, memory ->
            val spread = calculateSpreadOffset(index, memories.size)

            val targetX by animateFloatAsState(
                targetValue   = if (phase >= 1) 0f else spread.first,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessMediumLow
                ),
                label = "cardX_$index"
            )
            val targetY by animateFloatAsState(
                targetValue   = if (phase >= 1) 0f else spread.second,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessMediumLow
                ),
                label = "cardY_$index"
            )
            val scale by animateFloatAsState(
                targetValue   = when (phase) {
                    0    -> 1.0f
                    1    -> 0.65f
                    2    -> 1.15f
                    else -> 0f
                },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessMedium
                ),
                label = "cardScale_$index"
            )
            val alpha by animateFloatAsState(
                targetValue   = if (phase == 3) 0f else 1f,
                animationSpec = tween(400),
                label         = "cardAlpha_$index"
            )
            val rotation by animateFloatAsState(
                targetValue   = if (phase >= 1) 0f else (index * 360f / memories.size),
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label         = "cardRotation_$index"
            )

            Box(
                modifier = Modifier
                    .offset { IntOffset(targetX.roundToInt(), targetY.roundToInt()) }
                    .scale(scale)
                    .alpha(alpha)
            ) {
                MiniMemoryCard(memory = memory)
            }
        }

        // ── Purple glow burst (phase 2) ───────────────────────────────────────
        AnimatedVisibility(
            visible = phase == 2,
            enter   = fadeIn(tween(200)),
            exit    = fadeOut(tween(350))
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "glowPulse")
            val glowAlpha by infiniteTransition.animateFloat(
                initialValue  = 0.25f,
                targetValue   = 0.85f,
                animationSpec = infiniteRepeatable(
                    animation  = tween(350, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "glowAlpha"
            )
            val glowScale by infiniteTransition.animateFloat(
                initialValue  = 0.8f,
                targetValue   = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation  = tween(500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "glowScale"
            )

            Box(
                modifier = Modifier
                    .size(220.dp)
                    .scale(glowScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MemexPurple.copy(alpha = glowAlpha),
                                MemexPurpleDim.copy(alpha = glowAlpha * 0.6f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // ── Synthesizing label ────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = phase == 1 || phase == 2,
            enter    = fadeIn(tween(400)),
            exit     = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier              = Modifier.padding(bottom = 64.dp),
                horizontalAlignment   = Alignment.CenterHorizontally
            ) {
                // Bouncing dots row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    repeat(3) { i ->
                        val infiniteTransition = rememberInfiniteTransition(label = "synthDot_$i")
                        val dotY by infiniteTransition.animateFloat(
                            initialValue  = 0f,
                            targetValue   = -7f,
                            animationSpec = infiniteRepeatable(
                                animation  = tween(400, delayMillis = i * 130, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "dotY_$i"
                        )
                        Box(
                            modifier = Modifier
                                .offset(y = dotY.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(MemexPurpleLight)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text      = "Synthesizing memories…",
                    color     = MemexPurpleLight,
                    fontSize  = 14.sp,
                    style     = MemexBodyStyle.copy(fontSize = 14.sp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MiniMemoryCard
// ─────────────────────────────────────────────────────────────────────────────

/** Compact card shown during the convergence animation. */
@Composable
fun MiniMemoryCard(memory: Memory) {
    val accent = typeAccentColor(memory.type)
    val emoji  = when (memory.type) {
        MemoryType.CAMERA -> "📷"
        MemoryType.VOICE  -> "🎙️"
        MemoryType.TEXT   -> "📝"
    }

    Box(
        modifier = Modifier
            .width(160.dp)
            .background(MemexCard, RoundedCornerShape(12.dp))
            .padding(1.dp)                            // border space
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.6f), MemexCard)
                    ),
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(emoji, fontSize = 13.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text     = memory.type.name,
                        style    = MemexCaptionStyle.copy(
                            color    = accent,
                            fontSize = 10.sp
                        )
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text     = memory.summary.take(60).ifBlank { memory.rawContent.take(60) },
                    style    = MemexBodyStyle.copy(color = MemexWhite, fontSize = 11.sp),
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── Geometry helper ───────────────────────────────────────────────────────────

/**
 * Distribute cards evenly on an ellipse.
 * Returns (offsetX, offsetY) in pixels for a given card [index].
 */
private fun calculateSpreadOffset(index: Int, total: Int): Pair<Float, Float> {
    if (total == 0) return 0f to 0f
    val angle   = (2.0 * PI * index / total) - PI / 2   // start from top
    val radiusX = 260f
    val radiusY = 200f
    return (cos(angle) * radiusX).toFloat() to (sin(angle) * radiusY).toFloat()
}

package com.memex.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.offset
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memex.app.ai.VoiceAgentService
import com.memex.app.ui.theme.*

// ── Data ──────────────────────────────────────────────────────────────────────

private data class StageInfo(
    val shortLabel: String,
    val label     : String,
    val icon      : ImageVector
)

private val pipelineStages = listOf(
    StageInfo("STT", "Listening", Icons.Rounded.Mic),
    StageInfo("LLM", "Thinking",  Icons.Rounded.Psychology),
    StageInfo("TTS", "Speaking",  Icons.Rounded.VolumeUp)
)

// ── PipelineIndicator ─────────────────────────────────────────────────────────

/**
 * Horizontal three-stage visualiser:
 *
 *   [STT] ──▶ [LLM] ──▶ [TTS]
 *
 * - **Active** stage: purple background + glowing border + [PulsingDots]
 * - **Done** stage: teal background + check-like static icon
 * - **Inactive** stage: dim background + grey icon
 * - Connector arrows colour-animate when their left stage becomes done
 */
@Composable
fun PipelineIndicator(
    stage   : VoiceAgentService.PipelineStage,
    modifier: Modifier = Modifier
) {
    Row(
        modifier              = modifier,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        pipelineStages.forEachIndexed { index, info ->

            val isActive = when (stage) {
                VoiceAgentService.PipelineStage.LISTENING   -> index == 0
                VoiceAgentService.PipelineStage.PROCESSING  -> index == 1
                VoiceAgentService.PipelineStage.SPEAKING    -> index == 2
                VoiceAgentService.PipelineStage.IDLE        -> false
            }
            val isDone = when (stage) {
                VoiceAgentService.PipelineStage.PROCESSING  -> index == 0
                VoiceAgentService.PipelineStage.SPEAKING    -> index <= 1
                else                                         -> false
            }

            // ── Stage box ─────────────────────────────────────────────────────
            val boxColor by animateColorAsState(
                targetValue   = when {
                    isActive -> MemexPurple
                    isDone   -> MemexTeal
                    else     -> MemexGrayDim
                },
                animationSpec = tween(500),
                label         = "stageBox_$index"
            )

            val labelColor by animateColorAsState(
                targetValue   = when {
                    isActive -> MemexWhite
                    isDone   -> MemexTeal
                    else     -> MemexGray
                },
                animationSpec = tween(300),
                label         = "stageLabel_$index"
            )

            // Pulse scale for active box
            val infiniteTransition = rememberInfiniteTransition(label = "stagePulse_$index")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue  = 1f,
                targetValue   = if (isActive) 1.06f else 1f,
                animationSpec = infiniteRepeatable(
                    animation  = tween(700, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseScale_$index"
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier            = Modifier.width(72.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .scale(pulseScale)
                        .clip(RoundedCornerShape(16.dp))
                        .background(boxColor)
                        .then(
                            if (isActive) Modifier.border(
                                width = 2.dp,
                                color = MemexPurpleLight,
                                shape = RoundedCornerShape(16.dp)
                            ) else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isActive) {
                        PulsingDots()
                    } else {
                        Icon(
                            imageVector        = info.icon,
                            contentDescription = info.label,
                            tint               = if (isDone) MemexWhite else MemexGray,
                            modifier           = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    text     = if (isActive) info.label else info.shortLabel,
                    color    = labelColor,
                    fontSize = 11.sp,
                    style    = MemexCaptionStyle.copy(fontSize = 11.sp, color = labelColor)
                )
            }

            // ── Connector arrow ───────────────────────────────────────────────
            if (index < pipelineStages.size - 1) {
                val arrowColor by animateColorAsState(
                    targetValue   = when {
                        isDone || isActive -> MemexPurpleLight
                        else               -> MemexGrayDim
                    },
                    animationSpec = tween(500),
                    label         = "arrowColor_$index"
                )
                Icon(
                    imageVector        = Icons.Rounded.ArrowForward,
                    contentDescription = null,
                    tint               = arrowColor,
                    modifier           = Modifier
                        .size(20.dp)
                        .padding(bottom = 16.dp) // align with box centre
                )
            }
        }
    }
}

// ── PulsingDots ───────────────────────────────────────────────────────────────

/**
 * Three white dots that bounce vertically with a 150 ms stagger.
 * Used inside the active pipeline stage box.
 */
@Composable
fun PulsingDots() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "dot_$index")
            val offsetY by infiniteTransition.animateFloat(
                initialValue  = 0f,
                targetValue   = -8f,
                animationSpec = infiniteRepeatable(
                    animation  = tween(
                        durationMillis = 400,
                        delayMillis    = index * 150,
                        easing         = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dotY_$index"
            )
            Box(
                modifier = Modifier
                    .offset(y = offsetY.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

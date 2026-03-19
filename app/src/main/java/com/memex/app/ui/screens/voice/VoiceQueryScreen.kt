package com.memex.app.ui.screens.voice

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.memex.app.ai.VoiceAgentService.PipelineStage
import com.memex.app.ui.components.PipelineIndicator
import com.memex.app.ui.components.VoiceWaveform
import com.memex.app.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
// VoiceQueryScreen — the demo money shot
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun VoiceQueryScreen(
    onNavigateUp: () -> Unit = {},
    viewModel   : VoiceQueryViewModel = hiltViewModel()
) {
    val uiState   by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val haptics = LocalHapticFeedback.current

    val stage = uiState.stage

    // Ambient background glow colour: purple when active, teal when speaking, dim when idle
    val glowColor by animateColorAsState(
        targetValue   = when (stage) {
            PipelineStage.LISTENING  -> MemexPurple.copy(alpha = 0.18f)
            PipelineStage.PROCESSING -> MemexAmber.copy(alpha = 0.12f)
            PipelineStage.SPEAKING   -> MemexTeal.copy(alpha = 0.14f)
            PipelineStage.IDLE       -> Color.Transparent
        },
        animationSpec = tween(800),
        label         = "bgGlow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MemexBlack)
            .drawBehind { drawAmbientGlow(glowColor) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp),
            horizontalAlignment  = Alignment.CenterHorizontally,
            verticalArrangement  = Arrangement.spacedBy(0.dp)
        ) {

            Spacer(Modifier.height(24.dp))

            // ── Title ─────────────────────────────────────────────────────────
            Text(
                text     = "Ask MEMEX",
                style    = MemexDisplayStyle.copy(fontSize = 26.sp),
                color    = MemexWhite
            )
            Text(
                text     = "Your private AI memory assistant",
                style    = MemexCaptionStyle.copy(fontSize = 13.sp),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            // ── Language toggle ───────────────────────────────────────────────
            LanguageToggle(
                language        = uiState.language,
                onToggle        = { viewModel.toggleLanguage() }
            )

            Spacer(Modifier.height(36.dp))

            // ── Pipeline visualiser ───────────────────────────────────────────
            PipelineIndicator(
                stage    = stage,
                modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(40.dp))

            // ── Waveform (only during LISTENING) ─────────────────────────────
            AnimatedVisibility(
                visible = stage == PipelineStage.LISTENING || stage == PipelineStage.IDLE,
                enter   = fadeIn(tween(400)) + expandVertically(),
                exit    = fadeOut(tween(300)) + shrinkVertically()
            ) {
                VoiceWaveform(
                    amplitudes = List(30) { uiState.audioAmplitude },
                    isActive   = stage == PipelineStage.LISTENING,
                    modifier   = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                )
            }

            Spacer(Modifier.height(28.dp))

            // ── Main mic button ───────────────────────────────────────────────
            MicButton(
                stage   = stage,
                onClick = { 
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.startQuery() 
                }
            )

            Spacer(Modifier.height(12.dp))

            // Stage label
            Text(
                text = when (stage) {
                    PipelineStage.IDLE       -> "Tap to ask a question"
                    PipelineStage.LISTENING  -> "Listening… tap to stop"
                    PipelineStage.PROCESSING -> "Thinking through your memories…"
                    PipelineStage.SPEAKING   -> "Speaking answer aloud…"
                },
                style     = MemexCaptionStyle.copy(fontSize = 13.sp, color = MemexGray),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            // ── Transcript box ────────────────────────────────────────────────
            AnimatedVisibility(
                visible = uiState.transcript.isNotEmpty(),
                enter   = slideInVertically { it / 2 } + fadeIn(),
                exit    = slideOutVertically { it / 2 } + fadeOut()
            ) {
                TranscriptBox(
                    text     = uiState.transcript,
                    isActive = stage == PipelineStage.LISTENING
                )
            }

            if (uiState.transcript.isNotEmpty()) Spacer(Modifier.height(16.dp))

            // ── Answer box ────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = uiState.answer.isNotEmpty(),
                enter   = slideInVertically { it / 2 } + fadeIn(),
                exit    = fadeOut()
            ) {
                AnswerBox(
                    answer   = uiState.answer,
                    language = uiState.language,
                    stage    = stage
                )
            }

            Spacer(Modifier.weight(1f))

            // ── Cost counter ──────────────────────────────────────────────────
            CostFooter(memoryCount = uiState.memoryCount)

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Language Toggle
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LanguageToggle(
    language: String,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MemexGrayDim)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        LanguageChip(label = "EN",  selected = language == "en", onClick = onToggle)
        LanguageChip(label = "हिं", selected = language == "hi", onClick = onToggle)
    }
}

@Composable
private fun LanguageChip(
    label   : String,
    selected: Boolean,
    onClick : () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue   = if (selected) MemexPurple else Color.Transparent,
        animationSpec = tween(250),
        label         = "chipBg_$label"
    )
    val textColor by animateColorAsState(
        targetValue   = if (selected) MemexWhite else MemexGray,
        animationSpec = tween(250),
        label         = "chipText_$label"
    )

    Box(
        modifier         = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text  = label,
            color = textColor,
            style = MemexBodyStyle.copy(fontSize = 14.sp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mic Button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MicButton(
    stage  : PipelineStage,
    onClick: () -> Unit
) {
    val isListening  = stage == PipelineStage.LISTENING
    val isBusy       = stage == PipelineStage.PROCESSING || stage == PipelineStage.SPEAKING

    // Ripple ring animation during listening
    val infiniteTransition = rememberInfiniteTransition(label = "ripple")
    val rippleScale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = if (isListening) 1.65f else 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleScale"
    )
    val rippleAlpha by infiniteTransition.animateFloat(
        initialValue  = if (isListening) 0.4f else 0f,
        targetValue   = 0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleAlpha"
    )

    // Button scale on press feedback
    val buttonColor by animateColorAsState(
        targetValue   = when {
            isListening -> MemexRed
            isBusy      -> MemexGrayDim
            else        -> MemexPurple
        },
        animationSpec = tween(400),
        label         = "btnColor"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier.size(110.dp)
    ) {
        // Outer ripple ring (listening only)
        if (isListening) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(rippleScale)
                    .background(MemexRed.copy(alpha = rippleAlpha), CircleShape)
            )
            // Second ring with phase offset
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale((rippleScale * 0.75f).coerceAtMost(1.4f))
                    .background(MemexPurple.copy(alpha = rippleAlpha * 0.6f), CircleShape)
            )
        }

        // Core button
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = when {
                            isListening -> listOf(MemexRed, Color(0xFF7F1111))
                            isBusy      -> listOf(MemexGrayDim, MemexGrayDim)
                            else        -> listOf(MemexPurple, MemexPurpleDim)
                        }
                    ),
                    shape = CircleShape
                )
                .clip(CircleShape)
                .clickable(enabled = !isBusy, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    color       = MemexWhite,
                    strokeWidth = 2.5.dp,
                    modifier    = Modifier.size(32.dp)
                )
            } else {
                Icon(
                    imageVector        = if (isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                    contentDescription = if (isListening) "Stop" else "Start Query",
                    tint               = MemexWhite,
                    modifier           = Modifier.size(34.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Transcript Box
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TranscriptBox(
    text    : String,
    isActive: Boolean
) {
    // Blinking cursor animation
    val cursorVisible by rememberInfiniteTransition(label = "cursor")
        .animateFloat(
            initialValue  = 1f,
            targetValue   = 0f,
            animationSpec = infiniteRepeatable(
                animation  = tween(600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "cursorAlpha"
        )

    val borderColor by animateColorAsState(
        targetValue   = if (isActive) MemexTeal else MemexCardBorder,
        animationSpec = tween(400),
        label         = "transcriptBorder"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = MemexCard,
        shape    = RoundedCornerShape(14.dp),
        border   = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text  = "You said",
                style = MemexCaptionStyle.copy(
                    fontSize      = 10.sp,
                    letterSpacing = 1.2.sp,
                    color         = MemexTeal
                )
            )
            Spacer(Modifier.height(6.dp))

            // Text + blinking cursor
            val displayText = buildAnnotatedString {
                withStyle(SpanStyle(color = MemexWhite)) { append(text) }
                if (isActive) {
                    withStyle(SpanStyle(color = MemexPurple.copy(alpha = cursorVisible))) {
                        append("▋")
                    }
                }
            }
            Text(
                text     = displayText,
                style    = MemexBodyStyle.copy(fontSize = 15.sp),
                lineHeight = 22.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Answer Box
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AnswerBox(
    answer  : String,
    language: String,
    stage   : PipelineStage
) {
    val isSpeaking = stage == PipelineStage.SPEAKING

    // Pulsing purple border glow while speaking
    val infiniteTransition = rememberInfiniteTransition(label = "answerGlow")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.4f,
        targetValue   = if (isSpeaking) 1.0f else 0.4f,
        animationSpec = infiniteRepeatable(
            animation  = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "borderAlpha"
    )

    val borderColor = if (isSpeaking)
        MemexPurple.copy(alpha = borderAlpha)
    else MemexCardBorder

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = MemexDeepNavy,
        shape    = RoundedCornerShape(14.dp),
        border   = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header row
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text  = "MEMEX Answer",
                    style = MemexCaptionStyle.copy(
                        fontSize      = 10.sp,
                        letterSpacing = 1.2.sp,
                        color         = MemexPurpleLight
                    )
                )
                Spacer(Modifier.weight(1f))

                // Hindi badge
                if (language == "hi") {
                    Surface(
                        color = MemexTealDim,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text     = "🇮🇳 Hindi",
                            fontSize = 10.sp,
                            color    = MemexTeal,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                // Streaming indicator
                if (stage == PipelineStage.PROCESSING) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(
                        color       = MemexPurple,
                        strokeWidth = 2.dp,
                        modifier    = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text      = answer,
                style     = MemexBodyStyle.copy(
                    color      = MemexWhite,
                    fontSize   = 15.sp,
                    lineHeight = 24.sp
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Cost Footer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CostFooter(memoryCount: Int) {
    val countText = if (memoryCount == 0) "no memories yet" else "$memoryCount memories indexed"

    Text(
        text      = "₹0 inference cost • $countText • 100% offline",
        color     = MemexGray.copy(alpha = 0.6f),
        fontSize  = 11.sp,
        textAlign = TextAlign.Center,
        style     = MemexCaptionStyle.copy(fontSize = 11.sp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Drawing helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawAmbientGlow(color: Color) {
    if (color == Color.Transparent) return
    drawCircle(
        brush  = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = Offset(size.width / 2f, size.height * 0.35f),
            radius = size.width * 0.85f
        ),
        radius = size.width,
        center = Offset(size.width / 2f, size.height * 0.35f)
    )
}

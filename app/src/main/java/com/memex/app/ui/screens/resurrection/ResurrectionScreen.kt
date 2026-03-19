package com.memex.app.ui.screens.resurrection

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.memex.app.domain.model.Memory
import com.memex.app.domain.model.MemoryType
import com.memex.app.ui.components.AnimatedTagPill
import com.memex.app.ui.components.typeAccentColor
import com.memex.app.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
// ResurrectionScreen entry point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ResurrectionScreen(
    onNavigateUp: () -> Unit = {},
    viewModel   : ResurrectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context  = LocalContext.current

    // Snackbar state for copy/save confirmations
    val snackbarState = remember { SnackbarHostState() }

    Scaffold(
        containerColor   = MemexBlack,
        snackbarHost     = { SnackbarHost(snackbarState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (uiState.stage) {

                // ── Phase 1: memory selection ─────────────────────────────────
                ResurrectionStage.SELECTING -> {
                    SelectionView(
                        uiState      = uiState,
                        onBack       = onNavigateUp,
                        onToggle     = { viewModel.toggleMemorySelection(it) },
                        onResurrect  = { viewModel.resurrect() }
                    )
                }

                // ── Phase 2: cinematic animation ──────────────────────────────
                ResurrectionStage.ANIMATING,
                ResurrectionStage.SYNTHESIZING -> {
                    ResurrectionAnimation(
                        memories            = uiState.selectedMemories,
                        onAnimationComplete = { viewModel.onAnimationComplete() }
                    )
                }

                // ── Phase 3: narrative display ────────────────────────────────
                ResurrectionStage.DONE -> {
                    NarrativeView(
                        uiState    = uiState,
                        onBack     = {
                            viewModel.reset()
                            onNavigateUp()
                        },
                        onSpeak    = {
                            if (uiState.isSpeaking) viewModel.stopSpeaking()
                            else viewModel.speakNarrative()
                        },
                        onSave     = {
                            viewModel.saveNarrativeAsMemory()
                        },
                        onCopy     = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("MEMEX Narrative", uiState.narrativeText))
                        },
                        onReset    = { viewModel.reset() }
                    )
                }
            }

            // Global error snackbar
            uiState.errorMessage?.let { err ->
                LaunchedEffect(err) {
                    snackbarState.showSnackbar(err)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Phase 1: SelectionView
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SelectionView(
    uiState    : ResurrectionUiState,
    onBack     : () -> Unit,
    onToggle   : (String) -> Unit,
    onResurrect: () -> Unit
) {
    val selectedCount = uiState.selectedIds.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MemexBlack)
            .statusBarsPadding()
    ) {
        // ── Top bar ───────────────────────────────────────────────────────────
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = onBack,
                colors  = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MemexCard
                )
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = MemexWhite)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text  = "Resurrect Context",
                    style = MemexTitleStyle.copy(fontSize = 18.sp),
                    color = MemexWhite
                )
                Text(
                    text  = "Select 2–10 memories to weave together",
                    style = MemexCaptionStyle.copy(fontSize = 12.sp)
                )
            }
        }

        HorizontalDivider(color = MemexCardBorder, thickness = 0.5.dp)

        // ── Selection counter chip ────────────────────────────────────────────
        AnimatedVisibility(
            visible = selectedCount > 0,
            enter   = expandVertically() + fadeIn(),
            exit    = shrinkVertically() + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = MemexPurpleDim,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text     = "$selectedCount selected",
                        color    = MemexPurpleLight,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                if (selectedCount >= 10) {
                    Surface(color = MemexRed.copy(alpha = 0.15f), shape = RoundedCornerShape(20.dp)) {
                        Text(
                            text     = "Max reached",
                            color    = MemexRed,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // ── Memory list ───────────────────────────────────────────────────────
        LazyColumn(
            modifier       = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (uiState.allMemories.isEmpty()) {
                item {
                    Box(
                        modifier         = Modifier.fillMaxWidth().height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🧠", fontSize = 40.sp)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text  = "No memories yet",
                                style = MemexBodyStyle.copy(color = MemexGray)
                            )
                        }
                    }
                }
            }
            items(uiState.allMemories, key = { it.id }) { memory ->
                SelectableMemoryCard(
                    memory     = memory,
                    isSelected = memory.id in uiState.selectedIds,
                    onToggle   = { onToggle(memory.id) }
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        // ── Resurrect button ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MemexBlack)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            val canResurrect = selectedCount >= 2

            // Glow effect behind button
            val infiniteTransition = rememberInfiniteTransition(label = "btnGlow")
            val glowAlpha by infiniteTransition.animateFloat(
                initialValue  = 0.3f,
                targetValue   = if (canResurrect) 0.7f else 0f,
                animationSpec = infiniteRepeatable(
                    animation  = tween(900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "btnGlowAlpha"
            )

            Button(
                onClick  = onResurrect,
                enabled  = canResurrect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .drawBehind {
                        if (canResurrect) {
                            drawCircle(
                                brush  = Brush.radialGradient(
                                    listOf(
                                        MemexPurple.copy(alpha = glowAlpha),
                                        Color.Transparent
                                    )
                                ),
                                radius = size.width * 0.6f,
                                center = Offset(size.width / 2, size.height / 2)
                            )
                        }
                    },
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = MemexPurple,
                    disabledContainerColor = MemexGrayDim
                ),
                shape    = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = MemexWhite, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text  = if (canResurrect) "Resurrect $selectedCount Memories" else "Select at least 2 memories",
                    color = MemexWhite,
                    style = MemexBodyStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Selectable Memory Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SelectableMemoryCard(
    memory    : Memory,
    isSelected: Boolean,
    onToggle  : () -> Unit
) {
    val accent = typeAccentColor(memory.type)
    val borderColor by animateColorAsState(
        targetValue   = if (isSelected) MemexPurple else MemexCardBorder,
        animationSpec = tween(250),
        label         = "selectBorder_${memory.id}"
    )
    val bgColor by animateColorAsState(
        targetValue   = if (isSelected) MemexPurpleDim.copy(alpha = 0.35f) else MemexCard,
        animationSpec = tween(250),
        label         = "selectBg_${memory.id}"
    )
    val scale by animateFloatAsState(
        targetValue   = if (isSelected) 1.02f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label         = "selectScale_${memory.id}"
    )

    Card(
        onClick   = onToggle,
        modifier  = Modifier
            .fillMaxWidth()
            .scale(scale),
        colors    = CardDefaults.cardColors(containerColor = bgColor),
        border    = BorderStroke(if (isSelected) 1.5.dp else 0.5.dp, borderColor),
        shape     = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier          = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: type accent stripe + check
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier            = Modifier.width(36.dp)
            ) {
                AnimatedContent(
                    targetState   = isSelected,
                    transitionSpec = { scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut() },
                    label         = "checkAnim_${memory.id}"
                ) { selected ->
                    if (selected) {
                        Box(
                            modifier         = Modifier
                                .size(28.dp)
                                .background(MemexPurple, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Check,
                                null,
                                tint     = MemexWhite,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .border(1.5.dp, MemexGray, CircleShape)
                        )
                    }
                }
            }

            Spacer(Modifier.width(10.dp))

            // Right: content
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text  = when (memory.type) { MemoryType.CAMERA -> "📷"; MemoryType.VOICE -> "🎙️"; else -> "📝" },
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text  = memory.type.name,
                        style = MemexCaptionStyle.copy(color = accent, fontSize = 10.sp)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text  = formatShortDate(memory.createdAt),
                        style = MemexCaptionStyle.copy(fontSize = 10.sp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text     = memory.summary.ifBlank { memory.rawContent },
                    style    = MemexBodyStyle.copy(color = MemexWhite, fontSize = 13.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (memory.tags.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        memory.tags.take(3).forEach { tag ->
                            Text(
                                text     = "#$tag",
                                style    = MemexCaptionStyle.copy(color = MemexPurpleLight, fontSize = 10.sp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Phase 3: NarrativeView
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NarrativeView(
    uiState: ResurrectionUiState,
    onBack : () -> Unit,
    onSpeak: () -> Unit,
    onSave : () -> Unit,
    onCopy : () -> Unit,
    onReset: () -> Unit
) {
    val scrollState = rememberScrollState()

    // Characters visible so far via typewriter
    val visibleText = uiState.narrativeText.take(uiState.typedChars)
    val isTyping    = uiState.typedChars < uiState.narrativeText.length

    // Blinking cursor
    val cursorAlpha by rememberInfiniteTransition(label = "cursor")
        .animateFloat(
            initialValue  = 1f,
            targetValue   = 0f,
            animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
            label         = "cursorAlpha"
        )

    // Ambient glow
    val infiniteTransition = rememberInfiniteTransition(label = "narrativeGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.08f,
        targetValue   = 0.18f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "narrativeGlow"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MemexBlack)
            .drawBehind {
                drawCircle(
                    brush  = Brush.radialGradient(
                        listOf(MemexPurple.copy(glowAlpha), Color.Transparent)
                    ),
                    radius = size.width * 0.9f,
                    center = Offset(size.width / 2, size.height * 0.25f)
                )
            }
            .statusBarsPadding()
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = onBack,
                colors  = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MemexCard)
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = MemexWhite)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✨", fontSize = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text  = "Memory Resurrected",
                        style = MemexTitleStyle.copy(fontSize = 18.sp),
                        color = MemexPurpleLight
                    )
                }
                Text(
                    text  = "${uiState.selectedMemories.size} memories woven together",
                    style = MemexCaptionStyle.copy(fontSize = 11.sp)
                )
            }
            // Quick-reset button
            IconButton(onClick = onReset) {
                Icon(Icons.Rounded.Refresh, "New resurrection", tint = MemexGray, modifier = Modifier.size(20.dp))
            }
        }

        HorizontalDivider(color = MemexCardBorder, thickness = 0.5.dp)

        // ── Memory source chips ───────────────────────────────────────────────
        Row(
            modifier            = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            uiState.selectedMemories.forEach { memory ->
                val accent = typeAccentColor(memory.type)
                Surface(
                    color = accent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, accent.copy(alpha = 0.4f))
                ) {
                    Text(
                        text     = when(memory.type) { MemoryType.CAMERA -> "📷"; MemoryType.VOICE -> "🎙️"; else -> "📝" } +
                                " " + memory.summary.take(20),
                        color    = accent,
                        fontSize = 10.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // ── Narrative text ────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Surface(
                color  = MemexCard,
                shape  = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (uiState.isSpeaking) MemexTeal else MemexCardBorder
                )
            ) {
                Box(modifier = Modifier.padding(18.dp)) {
                    // Typewriter text with blinking cursor
                    val displayText = buildAnnotatedString {
                        withStyle(SpanStyle(color = MemexWhite)) { append(visibleText) }
                        if (isTyping) {
                            withStyle(SpanStyle(color = MemexPurple.copy(alpha = cursorAlpha))) {
                                append("▋")
                            }
                        }
                    }
                    Text(
                        text       = displayText,
                        style      = MemexBodyStyle.copy(fontSize = 16.sp, lineHeight = 26.sp),
                        textAlign  = TextAlign.Start
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Speaking animation bar (only while TTS active)
            AnimatedVisibility(visible = uiState.isSpeaking) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    repeat(8) { i ->
                        val barAlpha by rememberInfiniteTransition(label = "bar_$i").animateFloat(
                            initialValue  = 0.3f,
                            targetValue   = 1.0f,
                            animationSpec = infiniteRepeatable(
                                tween(300, delayMillis = i * 80),
                                RepeatMode.Reverse
                            ),
                            label = "barAlpha_$i"
                        )
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height((8 + i * 3).dp)
                                .alpha(barAlpha)
                                .background(MemexTeal, RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(3.dp))
                    }
                    Text(
                        " Speaking…",
                        style = MemexCaptionStyle.copy(color = MemexTeal, fontSize = 12.sp)
                    )
                }
            }
        }

        // ── Action buttons ────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MemexBlack)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Speak / Stop button
            Button(
                onClick  = onSpeak,
                enabled  = !isTyping,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isSpeaking) MemexRed else MemexTeal
                ),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    if (uiState.isSpeaking) Icons.Rounded.Stop else Icons.Rounded.VolumeUp,
                    null,
                    tint     = MemexBlack,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text  = if (uiState.isSpeaking) "Stop Speaking" else "Speak Narrative",
                    color = MemexBlack,
                    style = MemexBodyStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Save as Memory
                OutlinedButton(
                    onClick  = onSave,
                    enabled  = !uiState.isSaved,
                    modifier = Modifier.weight(1f).height(48.dp),
                    border   = BorderStroke(1.dp, if (uiState.isSaved) MemexTeal else MemexPurple),
                    shape    = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        if (uiState.isSaved) Icons.Rounded.CheckCircle else Icons.Rounded.Save,
                        null,
                        tint     = if (uiState.isSaved) MemexTeal else MemexPurple,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text  = if (uiState.isSaved) "Saved ✓" else "Save Memory",
                        color = if (uiState.isSaved) MemexTeal else MemexPurple,
                        style = MemexBodyStyle.copy(fontSize = 13.sp)
                    )
                }

                // Copy to clipboard
                OutlinedButton(
                    onClick  = onCopy,
                    modifier = Modifier.weight(1f).height(48.dp),
                    border   = BorderStroke(1.dp, MemexCardBorder),
                    shape    = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, tint = MemexGray, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy", color = MemexGray, style = MemexBodyStyle.copy(fontSize = 13.sp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun formatShortDate(epochMs: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(epochMs))
}

package com.memex.app.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.memex.app.domain.model.Memory
import com.memex.app.ui.components.MemoryCard
import com.memex.app.ui.components.formatRelativeTime
import com.memex.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────────────────────
// HomeScreen
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Main screen — full-screen dark memory timeline.
 *
 * Layout (top → bottom):
 *   1. Status-bar safe area + MEMEX header with glow title + settings icon
 *   2. AI loading banner (animated shimmer, only while SDK is initialising)
 *   3. Animated search bar
 *   4. Memory timeline (LazyColumn, date-group headers)
 *   5. Empty / no-results state
 */
@Composable
fun HomeScreen(
    viewModel       : HomeViewModel = hiltViewModel(),
    onMemoryClick   : (String) -> Unit = {},
    onSettingsClick : () -> Unit       = {},
    onResurrectClick: () -> Unit       = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MemexBlack)
    ) {

        // ── 1. Header ─────────────────────────────────────────────────────────
        var showDeleteDialog by remember { mutableStateOf(false) }
        
        MemexHeader(
            memoryCount      = uiState.totalCount,
            onSettingsClick  = onSettingsClick,
            onResurrectClick = onResurrectClick,
            onDeleteAllClick = { showDeleteDialog = true }
        )
        
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Purge Vault?", style = MemexTitleStyle, color = MemexRed) },
                text = { Text("This will permanently delete ALL your memories. This action cannot be undone.", style = MemexBodyStyle) },
                confirmButton = {
                    TextButton(onClick = { 
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.deleteAllMemories()
                        showDeleteDialog = false 
                    }) {
                        Text("PURGE ALL", color = MemexRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("CANCEL", color = MemexWhite)
                    }
                },
                containerColor = MemexDeepNavy,
                textContentColor = MemexWhite,
                titleContentColor = MemexRed
            )
        }

        // ── 2. AI loading banner ──────────────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.aiLoadingMessage != null,
            enter   = expandVertically() + fadeIn(),
            exit    = shrinkVertically() + fadeOut()
        ) {
            uiState.aiLoadingMessage?.let { msg ->
                AiLoadingBanner(message = msg)
            }
        }

        // ── 3. Search bar ─────────────────────────────────────────────────────
        SearchBar(
            query    = uiState.searchQuery,
            onQuery  = { viewModel.searchMemories(it) },
            onClear  = { viewModel.searchMemories(""); focusManager.clearFocus() },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )

        // ── 4. Content ────────────────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            when {
                uiState.isLoading -> {
                    LoadingShimmer()
                }
                uiState.memories.isEmpty() && uiState.searchQuery.isNotEmpty() -> {
                    NoSearchResults(query = uiState.searchQuery) {
                        viewModel.searchMemories("")
                        focusManager.clearFocus()
                    }
                }
                uiState.memories.isEmpty() -> {
                    EmptyVaultState()
                }
                else -> {
                    MemoryTimeline(
                        memories   = uiState.memories,
                        listState  = listState,
                        onCardClick = { id -> onMemoryClick(id) },
                        onDelete   = { id -> 
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.deleteMemory(id) 
                        }
                    )
                }
            }
        }

        // ── 5. Inference Cost Banner (persistent at bottom) ───────────────────
        if (uiState.totalCount > 0) {
            InferenceCostBanner(memoriesCount = uiState.totalCount)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MemexHeader(
    memoryCount     : Int,
    onSettingsClick : () -> Unit,
    onResurrectClick: () -> Unit = {},
    onDeleteAllClick: () -> Unit = {}
) {
    // Animated purple glow behind the title
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue   = 0.3f,
        targetValue    = 0.7f,
        animationSpec  = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column {
            // MEMEX title with purple glow effect via drawBehind
            Text(
                text     = "MEMEX",
                style    = MemexDisplayStyle,
                color    = MemexPurple,
                modifier = Modifier.drawBehind {
                    drawGlow(MemexPurple.copy(alpha = glowAlpha), 40f)
                }
            )
            Text(
                text  = if (memoryCount == 0) "Encrypted memory vault"
                        else "$memoryCount ${if (memoryCount == 1) "memory" else "memories"} stored",
                style = MemexCaptionStyle.copy(fontSize = 12.sp)
            )
        }

        // Action buttons row (Resurrect + Delete + Settings)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // ✨ Resurrect shortcut
            FilledTonalIconButton(
                onClick  = onResurrectClick,
                colors   = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MemexPurpleDim
                ),
                modifier = Modifier.size(42.dp)
            ) {
                Text("✨", fontSize = 18.sp)
            }

            // 🗑️ Nuclear Delete (only if memories exist)
            if (memoryCount > 0) {
                FilledTonalIconButton(
                    onClick  = onDeleteAllClick,
                    colors   = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MemexGrayDim
                    ),
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Rounded.DeleteForever,
                        contentDescription = "Nuclear Delete",
                        tint               = MemexRed.copy(alpha = 0.8f),
                        modifier           = Modifier.size(20.dp)
                    )
                }
            }

            // ⚙️ Settings
            FilledTonalIconButton(
                onClick  = onSettingsClick,
                colors   = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MemexCard
                ),
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    imageVector        = Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    tint               = MemexGray,
                    modifier           = Modifier.size(20.dp)
                )
            }
        }
    }

    HorizontalDivider(color = MemexCardBorder, thickness = 0.5.dp)
}

// ─────────────────────────────────────────────────────────────────────────────
// AI Loading Banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AiLoadingBanner(message: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerX by infiniteTransition.animateFloat(
        initialValue  = -1f,
        targetValue   = 2f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label         = "shimmerX"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MemexDeepNavy)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Shimmer highlight bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.horizontalGradient(
                        colors      = listOf(
                            MemexPurpleDim.copy(alpha = 0f),
                            MemexPurple.copy(alpha = 0.25f),
                            MemexPurpleDim.copy(alpha = 0f)
                        ),
                        startX = shimmerX * 500f,
                        endX   = shimmerX * 500f + 300f
                    )
                )
        )

        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .height(34.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier  = Modifier.size(14.dp),
                color     = MemexPurple,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text  = message,
                style = MemexCaptionStyle.copy(
                    color    = MemexPurpleLight,
                    fontSize = 12.sp
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Search Bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SearchBar(
    query   : String,
    onQuery : (String) -> Unit,
    onClear : () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue   = if (isFocused) MemexPurple else MemexCardBorder,
        animationSpec = tween(200),
        label         = "searchBorder"
    )

    Surface(
        modifier  = modifier.fillMaxWidth(),
        color     = MemexCard,
        shape     = RoundedCornerShape(14.dp),
        border    = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector        = Icons.Rounded.Search,
                contentDescription = "Search",
                tint               = if (isFocused) MemexPurple else MemexGray,
                modifier           = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))

            TextField(
                value         = query,
                onValueChange = onQuery,
                placeholder   = {
                    Text(
                        "Search memories…",
                        style = MemexBodyStyle.copy(color = MemexGray, fontSize = 14.sp)
                    )
                },
                singleLine    = true,
                colors        = TextFieldDefaults.colors(
                    focusedContainerColor   = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor  = Color.Transparent,
                    focusedTextColor        = MemexWhite,
                    unfocusedTextColor      = MemexWhite,
                    cursorColor             = MemexPurple,
                    focusedIndicatorColor   = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle     = MemexBodyStyle.copy(fontSize = 14.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { /* query already live */ }),
                modifier      = Modifier
                    .weight(1f)
                    .onFocusChanged { isFocused = it.isFocused }
            )

            AnimatedVisibility(visible = query.isNotEmpty()) {
                IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector        = Icons.Rounded.Clear,
                        contentDescription = "Clear",
                        tint               = MemexGray,
                        modifier           = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Memory Timeline
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Groups [memories] by calendar day and renders date-section headers
 * between groups, followed by [MemoryCard] items.
 */
@Composable
private fun MemoryTimeline(
    memories   : List<Memory>,
    listState  : LazyListState,
    onCardClick: (String) -> Unit,
    onDelete   : (String) -> Unit
) {
    // Group by day string
    val grouped: Map<String, List<Memory>> = remember(memories) {
        memories.groupBy { dayKey(it.createdAt) }
    }

    LazyColumn(
        state          = listState,
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        grouped.forEach { (dayLabel, dayMemories) ->
            item(key = "header_$dayLabel") {
                DateSectionHeader(label = dayLabel)
            }
            items(
                items = dayMemories,
                key   = { it.id }
            ) { memory ->
                MemoryCard(
                    memory  = memory,
                    onClick = { onCardClick(memory.id) },
                    onDelete = { onDelete(memory.id) }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Date Section Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DateSectionHeader(label: String) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text     = label,
            style    = MemexCaptionStyle.copy(
                fontSize      = 11.sp,
                letterSpacing = 1.5.sp,
                fontWeight    = FontWeight.SemiBold,
                color         = MemexGray
            )
        )
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(
            modifier  = Modifier.weight(1f),
            color     = MemexCardBorder,
            thickness = 0.5.dp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty / Loading states
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyVaultState() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue  = 0.85f,
        targetValue   = 1.05f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier             = Modifier.fillMaxSize(),
        horizontalAlignment  = Alignment.CenterHorizontally,
        verticalArrangement  = Arrangement.Center
    ) {
        // Pulsing brain emoji in a glowing circle
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier
                .size(110.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors  = listOf(
                            MemexPurple.copy(alpha = 0.25f * pulse),
                            Color.Transparent
                        )
                    ),
                    shape = androidx.compose.foundation.shape.CircleShape
                )
        ) {
            Text(
                text     = "🧠",
                fontSize = (48 * pulse).sp
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text      = "Your vault is empty",
            style     = MemexTitleStyle.copy(fontSize = 20.sp),
            color     = MemexWhite,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text      = "Tap ＋ below to start capturing memories",
            style     = MemexCaptionStyle.copy(fontSize = 13.sp),
            textAlign = TextAlign.Center,
            modifier  = Modifier.padding(horizontal = 40.dp)
        )

        Spacer(Modifier.height(32.dp))

        // Hint chips
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("📷 Scan", "🎙️ Voice", "📝 Type").forEach { hint ->
                Surface(
                    color = MemexCard,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, MemexCardBorder)
                ) {
                    Text(
                        text     = hint,
                        style    = MemexCaptionStyle.copy(color = MemexWhite),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NoSearchResults(query: String, onClear: () -> Unit) {
    Column(
        modifier             = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment  = Alignment.CenterHorizontally,
        verticalArrangement  = Arrangement.Center
    ) {
        Text("🔍", fontSize = 48.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            text      = "No memories matching",
            style     = MemexTitleStyle.copy(fontSize = 18.sp),
            color     = MemexWhite,
            textAlign = TextAlign.Center
        )
        Text(
            text      = "\"$query\"",
            style     = MemexBodyStyle.copy(color = MemexPurple, fontSize = 15.sp),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        OutlinedButton(
            onClick = onClear,
            border  = androidx.compose.foundation.BorderStroke(1.dp, MemexPurple),
            shape   = RoundedCornerShape(12.dp)
        ) {
            Text("Clear search", color = MemexPurple)
        }
    }
}

@Composable
private fun LoadingShimmer() {
    val infiniteTransition = rememberInfiniteTransition(label = "loadShimmer")
    val shimmerX by infiniteTransition.animateFloat(
        initialValue  = -500f,
        targetValue   = 1500f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label         = "shimmerX"
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        repeat(4) {
            Spacer(Modifier.height(if (it == 0) 8.dp else 16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MemexCard,
                                MemexGrayDim,
                                MemexCard
                            ),
                            startX = shimmerX,
                            endX   = shimmerX + 600f
                        )
                    )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

// ── Inference Cost Banner ────────────────────────────────────────────────────

/**
 * Persistently shows the "₹0 cost" value proposition at the bottom of Home.
 */
@Composable
private fun InferenceCostBanner(memoriesCount: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MemexGrayDim.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MemexCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Cloud inference equivalent",
                    color = MemexGray,
                    fontSize = 11.sp,
                    style = MemexCaptionStyle
                )
                Text(
                    "₹0.00 spent",
                    color = MemexTeal,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    style = MemexDisplayStyle
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$memoriesCount memories",
                    color = MemexGray,
                    fontSize = 11.sp,
                    style = MemexCaptionStyle
                )
                Text(
                    "100% private",
                    color = MemexPurpleLight,
                    fontSize = 13.sp,
                    style = MemexBodyStyle,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/** Returns "TODAY", "YESTERDAY", or "Mar 19" */
private fun dayKey(epochMs: Long): String {
    val cal    = Calendar.getInstance()
    val today  = cal.get(Calendar.DAY_OF_YEAR)
    val year   = cal.get(Calendar.YEAR)
    cal.timeInMillis = epochMs
    val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
    val targetYear = cal.get(Calendar.YEAR)
    return when {
        targetYear == year && dayOfYear == today     -> "TODAY"
        targetYear == year && dayOfYear == today - 1 -> "YESTERDAY"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMs)).uppercase()
    }
}

/** Draws a radial glow centred on the composable using drawBehind. */
private fun DrawScope.drawGlow(color: Color, radius: Float) {
    drawCircle(
        brush  = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = Offset(size.width / 2, size.height / 2),
            radius = radius
        ),
        radius = radius,
        center = Offset(size.width / 2, size.height / 2)
    )
}

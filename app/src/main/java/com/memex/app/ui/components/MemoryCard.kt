package com.memex.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.memex.app.domain.model.Memory
import com.memex.app.domain.model.MemoryType
import com.memex.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

// ── Public entry point ────────────────────────────────────────────────────────

/**
 * A rich, dark memory card with:
 *  - Colour-coded left border by capture type (Purple/Teal/Amber)
 *  - Type indicator circle + icon
 *  - Summary text + scrollable tag pills
 *  - Timestamp + SHA-256 hash badge
 *  - Thumbnail strip for CAMERA memories
 *  - Long-press → delete confirmation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryCard(
    memory   : Memory,
    onClick  : () -> Unit,
    onDelete : () -> Unit = {}
) {
    val borderColor = typeAccentColor(memory.type)

    // Long-press delete state
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Scale animation on press
    var pressed by remember { mutableStateOf(false) }
    val cardScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cardScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .scale(cardScale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap        = { onClick() },
                    onLongPress  = { showDeleteConfirm = true }
                )
            },
        colors = CardDefaults.cardColors(containerColor = MemexCard),
        border = BorderStroke(0.5.dp, MemexCardBorder),
        shape  = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        // Coloured left accent line
        Row {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(borderColor, borderColor.copy(alpha = 0.3f))
                        ),
                        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                    )
            )

            Column(modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {

                // ── Header row: type indicator + timestamp ────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TypeIndicator(type = memory.type)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text  = typeLabel(memory.type),
                        style = MemexCaptionStyle.copy(color = borderColor, fontSize = 11.sp),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text  = formatRelativeTime(memory.createdAt),
                        style = MemexCaptionStyle.copy(fontSize = 11.sp)
                    )
                }

                Spacer(Modifier.height(10.dp))

                // ── Thumbnail (camera only) ───────────────────────────────────
                memory.thumbnailPath?.let { path ->
                    AsyncImage(
                        model             = path,
                        contentDescription = "Memory thumbnail",
                        contentScale      = ContentScale.Crop,
                        modifier          = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.height(10.dp))
                }

                // ── Summary text ──────────────────────────────────────────────
                Text(
                    text     = memory.summary.ifBlank { memory.rawContent },
                    style    = MemexBodyStyle.copy(color = MemexWhite, fontSize = 15.sp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                // ── Tags ──────────────────────────────────────────────────────
                if (memory.tags.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(memory.tags) { tag ->
                            AnimatedTagPill(tag = tag)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ── Footer: language badge + hash ─────────────────────────────
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Language badge
                    if (memory.language == "hi") {
                        Surface(
                            color  = MemexTealDim,
                            shape  = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text     = "हि",
                                fontSize = 10.sp,
                                color    = MemexTeal,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }

                    // Hash proof badge
                    Text(
                        text       = "# ${memory.sha256Hash.take(8)}",
                        style      = MemexCaptionStyle.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 11.sp,
                            color      = MemexPurpleDim
                        )
                    )
                }
            }
        }
    }

    // ── Delete confirmation dialog ────────────────────────────────────────────
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest  = { showDeleteConfirm = false },
            containerColor    = MemexCard,
            titleContentColor = MemexWhite,
            textContentColor  = MemexGray,
            title  = { Text("Delete memory?", style = MemexTitleStyle.copy(fontSize = 18.sp)) },
            text   = { Text("This cannot be undone. The encrypted record will be permanently erased.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Delete", color = MemexRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = MemexGray)
                }
            }
        )
    }
}

// ── Sub-components ────────────────────────────────────────────────────────────

/** Coloured circular dot + capture-type icon. */
@Composable
fun TypeIndicator(type: MemoryType) {
    Box(
        modifier         = Modifier
            .size(28.dp)
            .background(typeAccentColor(type).copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector        = typeIcon(type),
            contentDescription = type.name,
            tint               = typeAccentColor(type),
            modifier           = Modifier.size(14.dp)
        )
    }
}

/** Spring-scale-in tag pill. */
@Composable
fun AnimatedTagPill(tag: String) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val scale by animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessLow
        ),
        label = "tagScale"
    )

    Surface(
        color  = MemexGrayDim,
        shape  = RoundedCornerShape(20.dp),
        modifier = Modifier.scale(scale)
    ) {
        Text(
            text     = "#$tag",
            color    = MemexPurpleLight,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

fun typeAccentColor(type: MemoryType): Color = when (type) {
    MemoryType.CAMERA -> MemexPurple
    MemoryType.VOICE  -> MemexTeal
    MemoryType.TEXT   -> MemexAmber
}

private fun typeIcon(type: MemoryType): ImageVector = when (type) {
    MemoryType.CAMERA -> Icons.Rounded.CameraAlt
    MemoryType.VOICE  -> Icons.Rounded.Mic
    MemoryType.TEXT   -> Icons.Rounded.Keyboard
}

private fun typeLabel(type: MemoryType): String = when (type) {
    MemoryType.CAMERA -> "Camera"
    MemoryType.VOICE  -> "Voice"
    MemoryType.TEXT   -> "Text"
}

/**
 * Format epoch millis as a human-friendly relative string.
 * e.g. "just now", "5m ago", "2h ago", "Mar 19"
 */
fun formatRelativeTime(epochMs: Long): String {
    val diff = System.currentTimeMillis() - epochMs
    val sec  = diff / 1_000
    val min  = sec  / 60
    val hour = min  / 60
    val day  = hour / 24
    return when {
        sec  < 60   -> "just now"
        min  < 60   -> "${min}m ago"
        hour < 24   -> "${hour}h ago"
        day  == 1L  -> "yesterday"
        day  <  7   -> "${day}d ago"
        else        -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMs))
    }
}

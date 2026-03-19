package com.memex.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp

/**
 * Full-screen scan overlay drawn on a [Canvas] using pure draw calls.
 *
 * Renders:
 *  1. Four pulsing L-shaped corner brackets (purple).
 *  2. A teal scan line that sweeps top → bottom with a multi-layer glow.
 *
 * When [isScanning] is false the canvas is empty (transparent overlay only).
 */
@Composable
fun ScanOverlay(
    isScanning: Boolean,
    modifier  : Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanOverlay")

    // Scan line Y progress: 0f (top of scan zone) → 1f (bottom)
    val scanLineY by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanLineY"
    )

    // Corner bracket alpha pulse: 0.45 ↔ 1.0
    val cornerAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.45f,
        targetValue   = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cornerAlpha"
    )

    // Scan line glow pulse (secondary pulse on the line brightness)
    val lineGlow by infiniteTransition.animateFloat(
        initialValue  = 0.6f,
        targetValue   = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lineGlow"
    )

    Canvas(modifier = modifier) {
        if (!isScanning) return@Canvas

        val w             = size.width
        val h             = size.height
        val bracketSize   = 60.dp.toPx()
        val bracketThick  = 3.dp.toPx()
        val margin        = 60.dp.toPx()
        val purple        = Color(0xFF7B5CF0)   // MemexPurple
        val teal          = Color(0xFF00D4AA)   // MemexTeal

        // ── Dark vignette outside the scan zone ───────────────────────────────
        // (subtle — keeps focus on the bracket area)
        drawRect(
            color      = Color.Black.copy(alpha = 0.30f),
            size       = size
        )
        // Clear the inner scan rectangle (transparent window)
        drawRect(
            color      = Color.Transparent,
            topLeft    = Offset(margin, margin),
            size       = androidx.compose.ui.geometry.Size(
                width  = w - 2 * margin,
                height = h - 2 * margin
            )
        )

        // ── Corner brackets ───────────────────────────────────────────────────
        // Four corners: (topLeft, topRight, bottomLeft, bottomRight)
        // hDir: +1 = extends right, -1 = extends left
        // vDir: +1 = extends down,  -1 = extends up
        data class CornerDef(val anchor: Offset, val hDir: Float, val vDir: Float)

        val corners = listOf(
            CornerDef(Offset(margin, margin),             +1f, +1f),  // top-left
            CornerDef(Offset(w - margin, margin),         -1f, +1f),  // top-right
            CornerDef(Offset(margin, h - margin),         +1f, -1f),  // bottom-left
            CornerDef(Offset(w - margin, h - margin),     -1f, -1f)   // bottom-right
        )

        val bracketPaint = Paint().apply {
            color       = purple.copy(alpha = cornerAlpha)
            strokeWidth = bracketThick
            style       = PaintingStyle.Stroke
            strokeCap   = androidx.compose.ui.graphics.StrokeCap.Round
        }

        corners.forEach { (anchor, hDir, vDir) ->
            drawIntoCanvas { canvas ->
                // Horizontal arm
                canvas.drawLine(
                    p1    = anchor,
                    p2    = Offset(anchor.x + hDir * bracketSize, anchor.y),
                    paint = bracketPaint
                )
                // Vertical arm
                canvas.drawLine(
                    p1    = anchor,
                    p2    = Offset(anchor.x, anchor.y + vDir * bracketSize),
                    paint = bracketPaint
                )
                // Corner dot accent
                canvas.drawCircle(
                    center = anchor,
                    radius = bracketThick * 1.5f,
                    paint  = Paint().apply {
                        color = purple.copy(alpha = cornerAlpha)
                        style = PaintingStyle.Fill
                    }
                )
            }
        }

        // ── Animated scan line ────────────────────────────────────────────────
        val lineY = margin + (h - 2f * margin) * scanLineY

        // Outer soft glow layers (widest → narrowest, progressively more opaque)
        val glowLayers = listOf(
            Pair(12f, 0.04f),
            Pair(8f,  0.07f),
            Pair(5f,  0.12f),
            Pair(3f,  0.20f),
        )
        glowLayers.forEach { (halfWidth, alpha) ->
            for (offset in listOf(-halfWidth, halfWidth)) {
                drawLine(
                    color       = teal.copy(alpha = alpha * lineGlow),
                    start       = Offset(margin, lineY + offset),
                    end         = Offset(w - margin, lineY + offset),
                    strokeWidth = 1.5f
                )
            }
        }

        // Core scan line
        drawLine(
            color       = teal.copy(alpha = 0.85f * lineGlow),
            start       = Offset(margin, lineY),
            end         = Offset(w - margin, lineY),
            strokeWidth = 2.dp.toPx()
        )

        // Trailing "comet tail" — fades out above the scan line
        val tailHeight = (h - 2 * margin) * 0.08f
        val tailSteps  = 6
        repeat(tailSteps) { step ->
            val stepFraction = step.toFloat() / tailSteps
            val tailY        = lineY - tailHeight * stepFraction
            if (tailY >= margin) {
                drawLine(
                    color       = teal.copy(alpha = 0.25f * (1f - stepFraction) * lineGlow),
                    start       = Offset(margin + 10, tailY),
                    end         = Offset(w - margin - 10, tailY),
                    strokeWidth = 1f
                )
            }
        }
    }
}

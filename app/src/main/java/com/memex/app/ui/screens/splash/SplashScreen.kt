package com.memex.app.ui.screens.splash

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.memex.app.ui.theme.*
import com.memex.app.util.MemexBiometricManager
import kotlinx.coroutines.delay

/**
 * SplashScreen — full-black canvas with staggered letter animation + glow pulse.
 *
 * Biometric gate:
 *   If the user has enabled "biometric_lock" in SharedPreferences, the screen
 *   triggers [MemexBiometricManager.authenticate] before calling [onReady].
 *   If biometric is unavailable or not enrolled, auth is skipped (fallback path).
 *   If the user cancels/fails authentication, a retry prompt is shown.
 *
 * Animation timeline (no-auth path):
 *   0ms   — screen shown, letters invisible
 *   100ms — M appears
 *   200ms — E appears
 *   300ms — M appears
 *   400ms — E appears
 *   500ms — X appears
 *   700ms — purple glow pulse begins (infinite)
 *   1_000ms — tagline fades in
 *   2_000ms — biometric check / onReady()
 */
@Composable
fun SplashScreen(onReady: () -> Unit) {

    val context  = LocalContext.current
    val activity = context as? FragmentActivity

    // Biometric pref
    val prefs: SharedPreferences = remember {
        context.getSharedPreferences("memex_prefs", Context.MODE_PRIVATE)
    }
    val biometricEnabled = remember { prefs.getBoolean("biometric_lock", false) }

    // ── Letter visibility states ──────────────────────────────────────────────
    val letters        = "MEMEX".toList()
    val letterVisible  = remember { letters.map { mutableStateOf(false) } }
    var taglineVisible by remember { mutableStateOf(false) }
    var authState      by remember { mutableStateOf(AuthState.WAITING) }

    // ── Glow pulse animation ──────────────────────────────────────────────────
    val glowAlpha by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue  = 0.4f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // Helper: trigger biometric then navigate
    fun doAuth() {
        if (!biometricEnabled || activity == null) {
            onReady()
            return
        }
        authState = AuthState.AUTHENTICATING
        MemexBiometricManager(activity).authenticate(
            onSuccess  = { authState = AuthState.SUCCESS; onReady() },
            onError    = { authState = AuthState.FAILED },
            onFallback = { authState = AuthState.SUCCESS; onReady() }  // no hardware → skip lock
        )
    }

    // ── Sequenced launch effects ──────────────────────────────────────────────
    LaunchedEffect(Unit) {
        letters.indices.forEach { i ->
            delay(100L * (i + 1))
            letterVisible[i].value = true
        }
        delay(200)
        taglineVisible = true
        delay(1_300)
        doAuth()
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(MemexBlack),
        contentAlignment = Alignment.Center
    ) {

        // Radial background glow behind the logo
        Box(
            modifier = Modifier
                .size(280.dp)
                .align(Alignment.Center)
                .blur(80.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MemexPurple.copy(alpha = glowAlpha * 0.35f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // ── Animated logo letters ─────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.Center) {
                letters.forEachIndexed { index, letter ->
                    AnimatedVisibility(
                        visible = letterVisible[index].value,
                        enter   = fadeIn(tween(300)) + scaleIn(
                            initialScale  = 0.6f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness    = Spring.StiffnessMedium
                            )
                        )
                    ) {
                        Text(
                            text  = letter.toString(),
                            style = MemexDisplayStyle.copy(
                                fontSize      = 52.sp,
                                color         = MemexPurple.copy(alpha = glowAlpha),
                                letterSpacing = 6.sp
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Tagline ───────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = taglineVisible,
                enter   = fadeIn(tween(600)) + expandVertically(tween(500))
            ) {
                Text(
                    text      = "Your private AI memory",
                    style     = MemexCaptionStyle.copy(
                        fontSize = 15.sp,
                        color    = MemexGray
                    ),
                    textAlign = TextAlign.Center
                )
            }

            // ── Auth status ───────────────────────────────────────────────────
            Spacer(Modifier.height(32.dp))
            AnimatedVisibility(
                visible = authState == AuthState.FAILED,
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    color  = MemexRed.copy(alpha = 0.12f),
                    shape  = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier              = Modifier.padding(16.dp),
                        horizontalAlignment   = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "🔒  Authentication cancelled",
                            style = MemexBodyStyle.copy(color = MemexRed, fontSize = 13.sp)
                        )
                        Spacer(Modifier.height(8.dp))
                        RetryButton(onClick = { doAuth() })
                    }
                }
            }
        }

        // ── Version watermark ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = taglineVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
            enter    = fadeIn(tween(800))
        ) {
            Text(
                text  = "v1.0 • on-device • encrypted",
                style = MemexCaptionStyle.copy(
                    fontSize = 10.sp,
                    color    = MemexGrayDim.copy(alpha = 0.7f)
                )
            )
        }

        // Auth status indicator (subtle, bottom-right)
        AnimatedVisibility(
            visible  = authState == AuthState.AUTHENTICATING,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 70.dp),
            enter    = fadeIn(),
            exit     = fadeOut()
        ) {
            Text(
                text  = "🔐  Verifying identity…",
                style = MemexCaptionStyle.copy(
                    fontSize = 12.sp,
                    color    = MemexPurpleLight.copy(alpha = 0.8f)
                )
            )
        }
    }
}

// ── Retry button (shown when auth fails) ──────────────────────────────────────

@Composable
private fun RetryButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color   = MemexPurpleDim,
        shape   = RoundedCornerShape(10.dp)
    ) {
        Text(
            text     = "Try again",
            color    = MemexPurpleLight,
            style    = MemexBodyStyle.copy(fontSize = 13.sp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

// ── Auth state machine ────────────────────────────────────────────────────────

private enum class AuthState { WAITING, AUTHENTICATING, SUCCESS, FAILED }

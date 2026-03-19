package com.memex.app.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.memex.app.ui.screens.capture.CaptureScreen
import com.memex.app.ui.screens.home.HomeScreen
import com.memex.app.ui.screens.memory.MemoryDetailScreen
import com.memex.app.ui.screens.settings.SettingsScreen
import com.memex.app.ui.screens.splash.SplashScreen
import com.memex.app.ui.screens.resurrection.ResurrectionScreen
import com.memex.app.ui.screens.voice.VoiceQueryScreen
import com.memex.app.ui.screens.onboarding.OnboardingScreen
import com.memex.app.ui.screens.onboarding.OnboardingViewModel
import com.memex.app.ui.screens.loading.LoadingScreen
import com.memex.app.ui.theme.*

// ── Route constants ───────────────────────────────────────────────────────────
object MemexRoutes {
    const val ONBOARDING   = "onboarding"
    const val LOADING      = "loading"
    const val HOME         = "home"
    const val VOICE_QUERY  = "voice_query?memoryId={memoryId}"
    const val SETTINGS     = "settings"
    const val RESURRECTION = "resurrection"
    const val CAPTURE      = "capture/{type}"        // type = camera|voice|text
    const val MEMORY_DETAIL= "memory/{memoryId}"

    fun capture(type: String)    = "capture/$type"
    fun memoryDetail(id: String) = "memory/$id"
    fun voiceQuery(memoryId: String? = null) = if (memoryId != null) "voice_query?memoryId=$memoryId" else "voice_query"
}

// ── Helpers ───────────────────────────────────────────────────────────────────
private const val ANIM_DURATION = 300

private fun enterTransition(): EnterTransition =
    slideInHorizontally(tween(ANIM_DURATION)) { it / 4 } +
    fadeIn(tween(ANIM_DURATION))

private fun exitTransition(): ExitTransition =
    slideOutHorizontally(tween(ANIM_DURATION)) { -it / 4 } +
    fadeOut(tween(ANIM_DURATION))

private fun popEnterTransition(): EnterTransition =
    slideInHorizontally(tween(ANIM_DURATION)) { -it / 4 } +
    fadeIn(tween(ANIM_DURATION))

private fun popExitTransition(): ExitTransition =
    slideOutHorizontally(tween(ANIM_DURATION)) { it / 4 } +
    fadeOut(tween(ANIM_DURATION))

// ── Root nav composable ───────────────────────────────────────────────────────
@Composable
fun MemexNavGraph(
    navController: NavHostController = rememberNavController(),
    onboardingViewModel: OnboardingViewModel = hiltViewModel()
) {

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // Which routes show the bottom bar
    val bottomBarRoutes = setOf(MemexRoutes.HOME, MemexRoutes.VOICE_QUERY)
    val showBottomBar = currentRoute in bottomBarRoutes

    // Capture bottom-sheet visibility
    var showCaptureSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MemexBlack,
        bottomBar = {
            if (showBottomBar) {
                MemexBottomBar(
                    currentRoute   = currentRoute,
                    onHomeClick    = {
                        navController.navigate(MemexRoutes.HOME) {
                            popUpTo(MemexRoutes.HOME) { inclusive = true }
                        }
                    },
                    onCaptureClick = { showCaptureSheet = true },
                    onQueryClick   = {
                        navController.navigate(MemexRoutes.voiceQuery()) {
                            popUpTo(MemexRoutes.HOME)
                        }
                    }
                )
            }
        }
    ) { paddingValues ->

        NavHost(
            navController    = navController,
            startDestination = MemexRoutes.SPLASH,
            modifier         = Modifier.padding(paddingValues),
            enterTransition  = { enterTransition() },
            exitTransition   = { exitTransition() },
            popEnterTransition  = { popEnterTransition() },
            popExitTransition   = { popExitTransition() }
        ) {

            // ── Splash ────────────────────────────────────────────────────────
            composable(MemexRoutes.SPLASH) {
                SplashScreen(
                    onReady = {
                        if (!onboardingViewModel.isOnboardingDone()) {
                            navController.navigate(MemexRoutes.ONBOARDING) {
                                popUpTo(MemexRoutes.SPLASH) { inclusive = true }
                            }
                        } else {
                            // If they've onboarded, go to Loading if SDK isn't ready, or Home
                            navController.navigate(MemexRoutes.LOADING) {
                                popUpTo(MemexRoutes.SPLASH) { inclusive = true }
                            }
                        }
                    }
                )
            }

            // ── Onboarding ────────────────────────────────────────────────────
            composable(MemexRoutes.ONBOARDING) {
                OnboardingScreen(
                    onComplete = {
                        navController.navigate(MemexRoutes.LOADING) {
                            popUpTo(MemexRoutes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }

            // ── Model Loading ─────────────────────────────────────────────────
            composable(MemexRoutes.LOADING) {
                LoadingScreen(
                    onReady = {
                        navController.navigate(MemexRoutes.HOME) {
                            popUpTo(MemexRoutes.LOADING) { inclusive = true }
                        }
                    }
                )
            }

            // ── Home ──────────────────────────────────────────────────────────
            composable(MemexRoutes.HOME) {
                HomeScreen(
                    onMemoryClick = { id ->
                        navController.navigate(MemexRoutes.memoryDetail(id))
                    },
                    onSettingsClick = {
                        navController.navigate(MemexRoutes.SETTINGS)
                    },
                    onResurrectClick = {
                        navController.navigate(MemexRoutes.RESURRECTION)
                    }
                )
            }

            // ── Resurrection ──────────────────────────────────────────────────
            composable(MemexRoutes.RESURRECTION) {
                ResurrectionScreen(onNavigateUp = { navController.popBackStack() })
            }

            // ── Capture (camera | voice | text) ───────────────────────────────
            composable(
                route     = MemexRoutes.CAPTURE,
                arguments = listOf(navArgument("type") { type = NavType.StringType })
            ) { backStackEntry ->
                val type = backStackEntry.arguments?.getString("type") ?: "text"
                CaptureScreen(
                    captureType   = type,
                    onNavigateUp  = { navController.popBackStack() }
                )
            }

            // ── Voice Query ───────────────────────────────────────────────────
            composable(
                route     = MemexRoutes.VOICE_QUERY,
                arguments = listOf(navArgument("memoryId") { defaultValue = ""; nullable = true })
            ) { backStackEntry ->
                val memoryId = backStackEntry.arguments?.getString("memoryId") ?: ""
                VoiceQueryScreen(
                    onNavigateUp    = { navController.popBackStack() },
                    initialMemoryId = memoryId
                )
            }

            // ── Memory Detail ─────────────────────────────────────────────────
            composable(
                route     = MemexRoutes.MEMORY_DETAIL,
                arguments = listOf(navArgument("memoryId") { type = NavType.StringType })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("memoryId") ?: return@composable
                MemoryDetailScreen(
                    memoryId     = id,
                    onNavigateUp = { navController.popBackStack() },
                    onAskAboutMemory = { memId ->
                        navController.navigate(MemexRoutes.voiceQuery(memId))
                    }
                )
            }

            // ── Settings ──────────────────────────────────────────────────────
            composable(MemexRoutes.SETTINGS) {
                SettingsScreen(
                    onNavigateUp  = { navController.popBackStack() },
                    onNuclearDone = {
                        // After wiping the vault, restart from Splash with a clean stack
                        navController.navigate(MemexRoutes.SPLASH) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
    }

    // ── Capture bottom sheet ──────────────────────────────────────────────────
    if (showCaptureSheet) {
        CaptureOptionsSheet(
            onDismiss = { showCaptureSheet = false },
            onCapture = { type ->
                showCaptureSheet = false
                navController.navigate(MemexRoutes.capture(type))
            }
        )
    }
}

// ── Bottom navigation bar ─────────────────────────────────────────────────────
@Composable
private fun MemexBottomBar(
    currentRoute  : String?,
    onHomeClick   : () -> Unit,
    onCaptureClick: () -> Unit,
    onQueryClick  : () -> Unit
) {
    // Frosted glass bar
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MemexDeepNavy.copy(alpha = 0.95f))
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment     = Alignment.CenterVertically
    ) {

        // Home button
        NavIconButton(
            emoji     = "🏠",
            label     = "Home",
            selected  = currentRoute == MemexRoutes.HOME,
            onClick   = onHomeClick
        )

        // Centre Capture FAB
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(MemexPurple, MemexPurpleDim)
                    )
                )
                .clickable(onClick = onCaptureClick),
            contentAlignment = Alignment.Center
        ) {
            Text("＋", fontSize = 26.sp, color = MemexWhite)
        }

        // Query button
        NavIconButton(
            emoji    = "🎙️",
            label    = "Query",
            selected = currentRoute == MemexRoutes.VOICE_QUERY,
            onClick  = onQueryClick
        )
    }
}

@Composable
private fun NavIconButton(
    emoji   : String,
    label   : String,
    selected: Boolean,
    onClick : () -> Unit
) {
    val tint   = if (selected) MemexPurple else MemexGray
    val bgAlpha = if (selected) 0.15f else 0f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MemexPurple.copy(alpha = bgAlpha))
            .clickable(
                indication            = null,
                interactionSource     = remember { MutableInteractionSource() },
                onClick               = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(emoji, fontSize = 22.sp)
    }
}

// ── Capture options bottom sheet ──────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptureOptionsSheet(
    onDismiss: () -> Unit,
    onCapture: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest  = onDismiss,
        containerColor    = MemexDeepNavy,
        scrimColor        = Color.Black.copy(alpha = 0.6f),
        shape             = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text(
                text      = "New Memory",
                style     = MemexTitleStyle,
                color     = MemexWhite,
                modifier  = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Text(
                text      = "Choose how to capture",
                style     = MemexCaptionStyle,
                modifier  = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 20.dp),
                textAlign = TextAlign.Center
            )

            CaptureOptionRow("📷", "Camera", "Scan documents, whiteboards, receipts") {
                onCapture("camera")
            }
            Spacer(Modifier.height(12.dp))
            CaptureOptionRow("🎙️", "Voice", "Speak your thoughts in English or Hindi") {
                onCapture("voice")
            }
            Spacer(Modifier.height(12.dp))
            CaptureOptionRow("📝", "Text", "Type or paste any text") {
                onCapture("text")
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CaptureOptionRow(
    emoji      : String,
    title      : String,
    subtitle   : String,
    onClick    : () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MemexCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 28.sp)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title,    style = MemexBodyStyle.copy(color = MemexWhite, fontSize = 16.sp))
            Text(subtitle, style = MemexCaptionStyle)
        }
    }
}

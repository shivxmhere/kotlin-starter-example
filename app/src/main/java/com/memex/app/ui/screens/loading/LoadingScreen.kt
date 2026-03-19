package com.memex.app.ui.screens.loading

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.memex.app.ui.theme.*

@Composable
fun LoadingScreen(
    onReady: () -> Unit,
    viewModel: LoadingViewModel = hiltViewModel()
) {
    val isReady by viewModel.isReady.collectAsState()
    val loadingStatus by viewModel.loadingStatus.collectAsState()
    val downloadStatus by viewModel.downloadStatus.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val areModelsDownloaded = viewModel.areModelsDownloaded

    // When the AI is ready, notify the caller to navigate Home
    LaunchedEffect(isReady) {
        if (isReady) {
            onReady()
        }
    }

    // Logo pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MemexBlack),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo with glow
        Box(
            modifier = Modifier.size(140.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(MemexPurple.copy(alpha = 0.3f), Color.Transparent),
                                radius = size.width * scale * 0.8f
                            ),
                            radius = size.width * scale * 0.8f,
                            center = center
                        )
                    }
            )
            Text(
                text = "🧠",
                fontSize = (60 * scale).sp
            )
        }

        Spacer(Modifier.height(40.dp))

        Text(
            text = "MEMEX AI",
            style = MemexDisplayStyle.copy(fontSize = 32.sp, color = MemexPurple)
        )

        Spacer(Modifier.height(60.dp))

        // Progress Area
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Determine which progress to show
            val currentProgress = if (!areModelsDownloaded) downloadProgress else 1f
            val currentMessage = if (!areModelsDownloaded) downloadStatus else loadingStatus

            LinearProgressIndicator(
                progress = currentProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MemexPurple,
                trackColor = MemexGrayDim
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = currentMessage,
                style = MemexCaptionStyle,
                color = MemexPurpleLight,
                textAlign = TextAlign.Center
            )

            if (!areModelsDownloaded) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "This only happens once. Your data stays private forever after.",
                    style = MemexCaptionStyle.copy(fontSize = 11.sp, color = MemexGray),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(80.dp))

            Text(
                text = "Running entirely on your device",
                style = MemexBodyStyle.copy(fontSize = 13.sp, color = MemexTeal),
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Secure • Offline • Free",
                style = MemexCaptionStyle.copy(fontSize = 11.sp),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

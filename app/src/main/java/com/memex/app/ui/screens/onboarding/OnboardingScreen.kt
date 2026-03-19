package com.memex.app.ui.screens.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.memex.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MemexBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                OnboardingPage(page)
            }

            // Indicator and Button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Dot indicator
                Row(
                    modifier = Modifier.padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(3) { index ->
                        Box(
                            modifier = Modifier
                                .size(if (pagerState.currentPage == index) 12.dp else 8.dp)
                                .clip(CircleShape)
                                .background(if (pagerState.currentPage == index) MemexPurple else MemexGrayDim)
                        )
                    }
                }

                // Button
                Button(
                    onClick = {
                        if (pagerState.currentPage == 2) {
                            viewModel.completeOnboarding(onComplete)
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MemexPurple),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = if (pagerState.currentPage == 2) "Get Started" else "Next",
                        style = MemexBodyStyle,
                        fontWeight = FontWeight.Bold,
                        color = MemexWhite
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(page: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (page) {
            0 -> {
                OnboardingIllustration("🔒", MemexPurple)
                Spacer(Modifier.height(40.dp))
                Text("Your memories, truly private", style = MemexDisplayStyle, color = MemexWhite, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Text("MEMEX uses on-device encryption. Your bank doesn't know what you remember. Neither do we.", style = MemexBodyStyle, color = MemexGray, textAlign = TextAlign.Center)
            }
            1 -> {
                OnboardingIllustration("✈️", MemexTeal)
                Spacer(Modifier.height(40.dp))
                Text("Works offline, forever", style = MemexDisplayStyle, color = MemexWhite, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Text("Capture thoughts in the middle of a flight, or deep in a forest. AI that never needs an internet connection.", style = MemexBodyStyle, color = MemexGray, textAlign = TextAlign.Center)
            }
            2 -> {
                OnboardingIllustration("₹0", MemexAmber)
                Spacer(Modifier.height(40.dp))
                Text("Zero cloud costs", style = MemexDisplayStyle, color = MemexWhite, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Text("No subscriptions. No token limits. 100% free AI inference forever, powered by your phone's chip.", style = MemexBodyStyle, color = MemexGray, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun OnboardingIllustration(emoji: String, glowColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .size(160.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(glowColor.copy(alpha = 0.2f), Color.Transparent),
                    radius = 200f * scale
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = (70 * scale).sp)
    }
}

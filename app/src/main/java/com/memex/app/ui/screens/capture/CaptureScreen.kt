package com.memex.app.ui.screens.capture

import android.content.Context
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.memex.app.domain.model.MemoryType
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.memex.app.ui.components.AnimatedTagPill
import com.memex.app.ui.components.ScanOverlay
import com.memex.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

// ─────────────────────────────────────────────────────────────────────────────
// Entry point — routes to Camera / Voice / Text sub-screens
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CaptureScreen(
    captureType : String,
    onNavigateUp: () -> Unit = {},
    viewModel   : CaptureViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptics = LocalHapticFeedback.current

    // Navigate back automatically once saved
    LaunchedEffect(uiState.status) {
        if (uiState.status == CaptureStatus.SAVED) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(600)         // let the "saved" animation play
            onNavigateUp()
        }
    }

    when (captureType.lowercase()) {
        "camera" -> CameraCaptureScreen(viewModel = viewModel, uiState = uiState, onNavigateUp = onNavigateUp)
        "voice"  -> VoiceCaptureScreen(viewModel = viewModel, uiState = uiState, onNavigateUp = onNavigateUp)
        else     -> TextCaptureScreen(viewModel = viewModel, uiState = uiState, onNavigateUp = onNavigateUp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CAMERA CAPTURE SCREEN
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraCaptureScreen(
    viewModel   : CaptureViewModel,
    uiState     : CaptureUiState,
    onNavigateUp: () -> Unit
) {
    val context       = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // CameraX image capture use-case
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // Show result panel once extractedText is populated and status returned to IDLE
    val showResult = uiState.extractedText.isNotEmpty() &&
            uiState.status != CaptureStatus.SCANNING &&
            uiState.status != CaptureStatus.PROCESSING

    val isActive = uiState.status == CaptureStatus.SCANNING ||
            uiState.status == CaptureStatus.PROCESSING

    // Saved flash animation
    val savedScale by animateFloatAsState(
        targetValue   = if (uiState.status == CaptureStatus.SAVED) 0f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label         = "savedScale"
    )

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Full-screen camera preview ────────────────────────────────────────
        CameraXPreview(
            context        = context,
            lifecycleOwner = lifecycleOwner,
            onImageCaptureReady = { ic -> imageCapture = ic },
            modifier       = Modifier.fillMaxSize()
        )

        // ── Scan overlay (corner brackets + scan line) ────────────────────────
        ScanOverlay(
            isScanning = isActive,
            modifier   = Modifier.fillMaxSize()
        )

        // ── Top bar ───────────────────────────────────────────────────────────
        CaptureTopBar(
            title       = "Scan Document",
            onBack      = onNavigateUp,
            modifier    = Modifier.align(Alignment.TopCenter)
        )

        // ── Processing spinner overlay ────────────────────────────────────────
        AnimatedVisibility(
            visible = isActive,
            enter   = fadeIn(),
            exit    = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            ProcessingIndicator(
                message = if (uiState.status == CaptureStatus.SCANNING)
                    "Analysing with Vision AI…"
                else "Generating summary & tags…"
            )
        }

        // ── Capture FAB ───────────────────────────────────────────────────────
        if (!showResult && !isActive) {
            CaptureFab(
                icon     = Icons.Rounded.CameraAlt,
                onClick  = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    val file = viewModel.cacheImageFile()
                    takePicture(imageCapture, file, cameraExecutor, context) { savedFile ->
                        viewModel.captureFromCamera(savedFile.absolutePath)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 40.dp)
            )
        }

        // ── Status label ──────────────────────────────────────────────────────
        if (!isActive && !showResult) {
            Text(
                text     = "Position document within the frame",
                style    = MemexCaptionStyle.copy(color = MemexWhite.copy(alpha = 0.7f)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 120.dp)
            )
        }

        // ── Result bottom sheet ───────────────────────────────────────────────
        if (showResult) {
            ResultBottomSheet(
                uiState      = uiState,
                onSave       = { viewModel.saveToVault(MemoryType.CAMERA) },
                onRetake     = { viewModel.reset() },
                savedScale   = savedScale
            )
        }

        // ── Error snackbar ────────────────────────────────────────────────────
        uiState.errorMessage?.let { err ->
            Snackbar(
                modifier          = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                containerColor    = MemexRed.copy(alpha = 0.9f),
                contentColor      = MemexWhite
            ) { Text(err) }
        }
    }

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// VOICE CAPTURE SCREEN
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VoiceCaptureScreen(
    viewModel   : CaptureViewModel,
    uiState     : CaptureUiState,
    onNavigateUp: () -> Unit
) {
    val scope      = rememberCoroutineScope()
    val showResult = uiState.extractedText.isNotEmpty() &&
            uiState.status != CaptureStatus.SCANNING &&
            uiState.status != CaptureStatus.PROCESSING

    val savedScale by animateFloatAsState(
        targetValue   = if (uiState.status == CaptureStatus.SAVED) 0f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label         = "savedScale"
    )

    // Waveform pulse when listening
    val infiniteTransition = rememberInfiniteTransition(label = "voicePulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue  = 0.85f,
        targetValue   = 1.15f,
        animationSpec = infiniteRepeatable(
            animation  = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MemexBlack)
    ) {
        CaptureTopBar("Voice Note", onNavigateUp, Modifier.align(Alignment.TopCenter))

        Column(
            modifier             = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 64.dp),
            horizontalAlignment  = Alignment.CenterHorizontally,
            verticalArrangement  = Arrangement.Center
        ) {
            // Mic visualiser
            Box(contentAlignment = Alignment.Center) {
                if (uiState.isListening) {
                    // Glowing rings
                    repeat(3) { ring ->
                        Box(
                            modifier = Modifier
                                .size((100 + ring * 32).dp)
                                .scale(if (ring == 0) pulse else 1f)
                                .background(
                                    MemexTeal.copy(alpha = 0.08f - ring * 0.02f),
                                    CircleShape
                                )
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .scale(if (uiState.isListening) pulse else 1f)
                        .background(
                            brush = Brush.radialGradient(
                                listOf(MemexTeal, MemexPurple)
                            ),
                            shape = CircleShape
                        )
                        .clip(CircleShape)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (uiState.isListening) viewModel.stopVoiceCapture()
                            else viewModel.startVoiceCapture()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = if (uiState.isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                        contentDescription = if (uiState.isListening) "Stop" else "Record",
                        tint               = MemexWhite,
                        modifier           = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text  = when {
                    uiState.status == CaptureStatus.PROCESSING -> "Processing transcript…"
                    uiState.isListening                        -> "Listening… tap to stop"
                    else                                       -> "Tap to start recording"
                },
                style = MemexBodyStyle.copy(color = MemexWhite, fontSize = 16.sp),
                textAlign = TextAlign.Center
            )

            if (uiState.isListening) {
                Spacer(Modifier.height(12.dp))
                // Amplitude bar
                VoiceAmplitudeBar(amplitude = uiState.audioAmplitude)
            }

            if (uiState.status == CaptureStatus.PROCESSING) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator(color = MemexPurple, modifier = Modifier.size(32.dp))
            }
        }

        if (showResult) {
            ResultBottomSheet(
                uiState    = uiState,
                onSave     = { viewModel.saveToVault(MemoryType.VOICE) },
                onRetake   = { viewModel.reset() },
                savedScale = savedScale
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TEXT CAPTURE SCREEN
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextCaptureScreen(
    viewModel   : CaptureViewModel,
    uiState     : CaptureUiState,
    onNavigateUp: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    val showResult = uiState.extractedText.isNotEmpty() &&
            uiState.status != CaptureStatus.PROCESSING

    val savedScale by animateFloatAsState(
        targetValue   = if (uiState.status == CaptureStatus.SAVED) 0f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label         = "savedScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MemexBlack)
            .statusBarsPadding()
    ) {
        CaptureTopBar("Type Note", onNavigateUp)

        Spacer(Modifier.height(16.dp))

        TextField(
            value         = inputText,
            onValueChange = { inputText = it },
            placeholder   = {
                Text(
                    "Type or paste your note here…\n\nSupports English and हिंदी",
                    style = MemexBodyStyle.copy(color = MemexGray)
                )
            },
            modifier      = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
                .background(MemexCard, RoundedCornerShape(16.dp)),
            colors        = TextFieldDefaults.colors(
                focusedContainerColor   = MemexCard,
                unfocusedContainerColor = MemexCard,
                focusedTextColor        = MemexWhite,
                unfocusedTextColor      = MemexWhite,
                cursorColor             = MemexPurple,
                focusedIndicatorColor   = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            textStyle     = MemexBodyStyle.copy(fontSize = 15.sp),
            minLines      = 6
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick  = { 
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.captureFromText(inputText) 
            },
            enabled  = inputText.isNotBlank() && uiState.status != CaptureStatus.PROCESSING,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(52.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = MemexPurple,
                disabledContainerColor = MemexGrayDim
            ),
            shape    = RoundedCornerShape(14.dp)
        ) {
            if (uiState.status == CaptureStatus.PROCESSING) {
                CircularProgressIndicator(color = MemexWhite, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Processing…", color = MemexWhite)
            } else {
                Icon(Icons.Rounded.AutoAwesome, null, tint = MemexWhite, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Analyse with AI", color = MemexWhite, style = MemexBodyStyle.copy(fontSize = 15.sp))
            }
        }

        Spacer(Modifier.height(16.dp).navigationBarsPadding())

        if (showResult) {
            ResultBottomSheet(
                uiState    = uiState,
                onSave     = { viewModel.saveToVault(MemoryType.TEXT) },
                onRetake   = { viewModel.reset() },
                savedScale = savedScale
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RESULT BOTTOM SHEET
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultBottomSheet(
    uiState   : CaptureUiState,
    onSave    : () -> Unit,
    onRetake  : () -> Unit,
    savedScale: Float
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onRetake,
        sheetState       = sheetState,
        containerColor   = MemexDeepNavy,
        scrimColor       = Color.Black.copy(alpha = 0.6f),
        shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .scale(savedScale)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .navigationBarsPadding()
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint               = MemexTeal,
                    modifier           = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text  = "Memory Captured",
                    style = MemexTitleStyle.copy(fontSize = 20.sp),
                    color = MemexTeal
                )
            }

            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = MemexCardBorder, thickness = 0.5.dp)
            Spacer(Modifier.height(14.dp))

            // ── Extracted text ────────────────────────────────────────────────
            if (uiState.extractedText.isNotEmpty()) {
                SectionLabel("Extracted Text")
                Surface(
                    color  = MemexCard,
                    shape  = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.5.dp, MemexCardBorder)
                ) {
                    Text(
                        text     = uiState.extractedText,
                        style    = MemexBodyStyle.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 13.sp,
                            color      = MemexWhite.copy(alpha = 0.85f)
                        ),
                        modifier = Modifier.padding(12.dp),
                        maxLines = 8,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            // ── AI Summary ────────────────────────────────────────────────────
            if (uiState.summary.isNotEmpty()) {
                SectionLabel("AI Summary")
                Text(
                    text  = uiState.summary,
                    style = MemexBodyStyle.copy(color = MemexWhite, fontSize = 15.sp),
                    lineHeight = 22.sp
                )
                Spacer(Modifier.height(14.dp))
            }

            // ── Tags ──────────────────────────────────────────────────────────
            if (uiState.tags.isNotEmpty()) {
                SectionLabel("Tags")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(uiState.tags) { index, tag ->
                        // Stagger entry animation per tag
                        var visible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            delay(index * 100L)
                            visible = true
                        }
                        AnimatedVisibility(
                            visible = visible,
                            enter   = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn()
                        ) {
                            AnimatedTagPill(tag = tag)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // ── SHA-256 hash badge ────────────────────────────────────────────
            if (uiState.sha256Hash.isNotEmpty()) {
                SectionLabel("Integrity Hash")
                Surface(
                    color  = MemexGrayDim,
                    shape  = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier          = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = null,
                            tint               = MemexPurpleDim,
                            modifier           = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text  = "SHA-256: ${uiState.sha256Hash.take(16)}…",
                            style = MemexBodyStyle.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize   = 12.sp,
                                color      = MemexPurple
                            )
                        )
                    }
                }
                Spacer(Modifier.height(22.dp))
            }

            // ── Action buttons ────────────────────────────────────────────────
            Button(
                onClick  = onSave,
                enabled  = uiState.status != CaptureStatus.SAVED,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = MemexPurple),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.Save, null, tint = MemexWhite, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text  = if (uiState.status == CaptureStatus.SAVED) "Saved ✓" else "Save to Vault",
                    color = MemexWhite,
                    style = MemexBodyStyle.copy(fontSize = 16.sp)
                )
            }

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick  = onRetake,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                border   = BorderStroke(1.dp, MemexCardBorder),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text  = "Retake",
                    color = MemexGray,
                    style = MemexBodyStyle.copy(fontSize = 15.sp)
                )
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// REUSABLE SUB-COMPONENTS
// ─────────────────────────────────────────────────────────────────────────────

/** Top action bar used by all capture sub-screens. */
@Composable
private fun CaptureTopBar(
    title   : String,
    onBack  : () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            onClick = onBack,
            colors  = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.5f)
            )
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = MemexWhite
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text  = title,
            style = MemexTitleStyle.copy(fontSize = 18.sp),
            color = MemexWhite
        )
    }
}

/** Large circular shutter / action button. */
@Composable
private fun CaptureFab(
    icon    : ImageVector,
    onClick : () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier         = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Outer ring
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(MemexWhite.copy(alpha = 0.15f), CircleShape)
        )
        // Inner button
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(
                    Brush.radialGradient(listOf(MemexPurple, MemexPurpleDim)),
                    CircleShape
                )
                .clip(CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MemexWhite, modifier = Modifier.size(28.dp))
        }
    }
}

/** Centred spinner + message shown while AI is working. */
@Composable
private fun ProcessingIndicator(message: String) {
    Surface(
        color  = Color.Black.copy(alpha = 0.75f),
        shape  = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                color       = MemexTeal,
                strokeWidth = 2.5.dp,
                modifier    = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text  = message,
                style = MemexBodyStyle.copy(color = MemexWhite, fontSize = 14.sp)
            )
        }
    }
}

/** Animated amplitude bar for the voice screen. */
@Composable
private fun VoiceAmplitudeBar(amplitude: Float) {
    val barCount = 20
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment     = Alignment.CenterVertically,
        modifier              = Modifier.height(48.dp)
    ) {
        repeat(barCount) { i ->
            val t      = (i.toFloat() / barCount)
            val height = ((0.15f + amplitude * 0.85f) * (1f - kotlin.math.abs(t - 0.5f))).coerceIn(0.05f, 1f)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(height)
                    .background(
                        Brush.verticalGradient(listOf(MemexTeal, MemexPurple)),
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text     = text.uppercase(),
        style    = MemexCaptionStyle.copy(
            fontSize      = 10.sp,
            letterSpacing = 1.2.sp,
            color         = MemexGray
        ),
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// CameraX helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CameraXPreview(
    context              : Context,
    lifecycleOwner       : androidx.lifecycle.LifecycleOwner,
    onImageCaptureReady  : (ImageCapture) -> Unit,
    modifier             : Modifier = Modifier
) {
    AndroidView(
        factory  = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()

                onImageCaptureReady(imageCapture)

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                    )
                } catch (e: Exception) {
                    // Camera not available — handled gracefully
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier
    )
}

private fun takePicture(
    imageCapture  : ImageCapture?,
    outputFile    : File,
    executor      : java.util.concurrent.Executor,
    context       : Context,
    onSaved       : (File) -> Unit
) {
    val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
    imageCapture?.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                ContextCompat.getMainExecutor(context).execute { onSaved(outputFile) }
            }
            override fun onError(exc: ImageCaptureException) {
                // Swallowed — ViewModel will show error state
            }
        }
    )
}

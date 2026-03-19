package com.memex.app.ui.screens.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.memex.app.util.MemexBiometricManager
import com.memex.app.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
// SettingsScreen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateUp : () -> Unit = {},
    onNuclearDone: () -> Unit = {},   // navigate back to splash after delete
    viewModel    : SettingsViewModel = hiltViewModel()
) {
    val uiState  by viewModel.uiState.collectAsState()
    val context   = LocalContext.current
    val activity  = context as? FragmentActivity
    val scrollState = rememberScrollState()

    // Check real biometric availability once
    val bioAvailable = remember(activity) {
        activity?.let { MemexBiometricManager.isAvailable(it) } ?: false
    }

    Scaffold(
        containerColor = MemexBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text  = "Settings",
                        style = MemexTitleStyle.copy(fontSize = 18.sp),
                        color = MemexWhite
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MemexWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MemexBlack
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── ₹0 Cost card (hero element) ───────────────────────────────────
            ZeroCostCard()

            // ── VAULT section ─────────────────────────────────────────────────
            SettingsSectionHeader("🗄️  Vault")

            SettingsCard {
                SettingsInfoRow(
                    icon  = Icons.Rounded.Memory,
                    label = "Memories stored",
                    value = "${uiState.memoryCount}"
                )
                HorizontalDivider(color = MemexCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                SettingsInfoRow(
                    icon  = Icons.Rounded.Storage,
                    label = "Vault size",
                    value = uiState.vaultSizeMb
                )
                HorizontalDivider(color = MemexCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

                // Nuclear delete
                NuclearDeleteRow(
                    memoryCount = uiState.memoryCount,
                    isDeleting  = uiState.isDeleting,
                    onClick     = { viewModel.showDeleteDialog() }
                )
            }

            // ── PRIVACY section ───────────────────────────────────────────────
            SettingsSectionHeader("🔒  Privacy & Security")

            SettingsCard {
                // Biometric toggle
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Fingerprint,
                        contentDescription = null,
                        tint     = if (bioAvailable) MemexPurple else MemexGray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Biometric lock",
                            style = MemexBodyStyle.copy(color = MemexWhite, fontSize = 15.sp)
                        )
                        Text(
                            text  = if (bioAvailable) "Require fingerprint/face on launch"
                                    else "Not available on this device",
                            style = MemexCaptionStyle.copy(fontSize = 11.sp)
                        )
                    }
                    Switch(
                        checked  = uiState.biometricEnabled,
                        onCheckedChange = {
                            if (bioAvailable) viewModel.setBiometricEnabled(it)
                        },
                        enabled  = bioAvailable,
                        colors   = SwitchDefaults.colors(
                            checkedThumbColor       = MemexWhite,
                            checkedTrackColor       = MemexPurple,
                            uncheckedTrackColor     = MemexGrayDim
                        )
                    )
                }

                HorizontalDivider(color = MemexCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

                // View encryption info
                SettingsActionRow(
                    icon    = Icons.Rounded.Lock,
                    label   = "View vault encryption",
                    subtext = "AES-256 + SHA-256 integrity proofs",
                    tint    = MemexTeal,
                    onClick = { viewModel.showEncryptionInfo() }
                )

                HorizontalDivider(color = MemexCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

                // Export hashes
                SettingsActionRow(
                    icon    = Icons.Rounded.ContentCopy,
                    label   = if (uiState.hashExported) "Hashes copied ✓" else "Export proof hashes",
                    subtext = "Copy all SHA-256 integrity hashes to clipboard",
                    tint    = if (uiState.hashExported) MemexTeal else MemexPurpleLight,
                    onClick = { viewModel.exportProofHashes() }
                )
            }

            // ── AI MODELS section ─────────────────────────────────────────────
            SettingsSectionHeader("🤖  AI Models")

            SettingsCard {
                ModelStatusRow("LLM (SmolLM2-1.7B)", "Language model", uiState.llmLoaded)
                HorizontalDivider(color = MemexCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                ModelStatusRow("STT (Whisper)", "Speech-to-text", uiState.sttLoaded)
                HorizontalDivider(color = MemexCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                ModelStatusRow("TTS (Piper)", "Text-to-speech", uiState.ttsLoaded)
                HorizontalDivider(color = MemexCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                ModelStatusRow("VLM (MobileVLM)", "Vision model", uiState.vlmLoaded)
                if (!uiState.allModelsReady) {
                    HorizontalDivider(color = MemexCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                    Row(
                        modifier          = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color       = MemexPurple,
                            strokeWidth = 2.dp,
                            modifier    = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text  = uiState.aiLoadingStatus,
                            style = MemexCaptionStyle.copy(color = MemexPurpleLight, fontSize = 12.sp)
                        )
                    }
                }
            }

            // ── ABOUT section ─────────────────────────────────────────────────
            SettingsSectionHeader("ℹ️  About")

            SettingsCard {
                SettingsInfoRow(icon = Icons.Rounded.Info,  label = "Version",      value = "1.0.0")
                HorizontalDivider(color = MemexCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                SettingsInfoRow(icon = Icons.Rounded.Code,  label = "Powered by",   value = "RunAnywhere SDK")
                HorizontalDivider(color = MemexCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                SettingsInfoRow(icon = Icons.Rounded.WifiOff, label = "Connectivity", value = "100% Offline")
                HorizontalDivider(color = MemexCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                SettingsInfoRow(icon = Icons.Rounded.Shield, label = "Data leaves device", value = "Never")
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // ── Nuclear delete dialog ─────────────────────────────────────────────────
    if (uiState.showDeleteDialog) {
        NuclearDeleteDialog(
            memoryCount = uiState.memoryCount,
            onConfirm   = { viewModel.deleteAllMemories(onNuclearDone) },
            onDismiss   = { viewModel.dismissDeleteDialog() }
        )
    }

    // ── Encryption info dialog ────────────────────────────────────────────────
    if (uiState.showEncryptionInfo) {
        EncryptionInfoDialog(onDismiss = { viewModel.dismissEncryptionInfo() })
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Zero Cost Hero Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ZeroCostCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "costGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.3f,
        targetValue   = 0.7f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "costGlowAlpha"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        color    = MemexTealDim
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawCircle(
                        brush  = Brush.radialGradient(
                            listOf(MemexTeal.copy(alpha = glowAlpha * 0.4f), Color.Transparent)
                        ),
                        radius = size.width * 0.7f,
                        center = Offset(size.width * 0.15f, size.height / 2)
                    )
                }
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text  = "Inference Cost",
                        style = MemexCaptionStyle.copy(color = MemexTeal.copy(alpha = 0.8f), fontSize = 11.sp)
                    )
                    Text(
                        text  = "₹0.00",
                        style = MemexDisplayStyle.copy(color = MemexTeal, fontSize = 36.sp),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text  = "100% on-device · zero cloud calls · zero cost",
                        style = MemexCaptionStyle.copy(color = MemexTeal.copy(alpha = 0.7f), fontSize = 11.sp)
                    )
                }
                Text("🤖", fontSize = 40.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text     = title,
        style    = MemexCaptionStyle.copy(
            fontSize      = 11.sp,
            letterSpacing = 0.8.sp,
            color         = MemexGray,
            fontWeight    = FontWeight.SemiBold
        ),
        modifier = Modifier.padding(start = 4.dp, bottom = 0.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Settings card container
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = MemexCard,
        shape    = RoundedCornerShape(14.dp),
        border   = BorderStroke(0.5.dp, MemexCardBorder)
    ) {
        Column(content = content)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Row components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsInfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MemexGray, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text     = label,
            style    = MemexBodyStyle.copy(color = MemexWhite.copy(alpha = 0.85f), fontSize = 15.sp),
            modifier = Modifier.weight(1f)
        )
        Text(
            text  = value,
            style = MemexBodyStyle.copy(color = MemexGray, fontSize = 14.sp)
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon   : ImageVector,
    label  : String,
    subtext: String,
    tint   : Color = MemexPurpleLight,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label,   style = MemexBodyStyle.copy(color = MemexWhite, fontSize = 15.sp))
            Text(subtext, style = MemexCaptionStyle.copy(fontSize = 11.sp))
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MemexGrayDim, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ModelStatusRow(name: String, description: String, isLoaded: Boolean) {
    val dotColor by animateColorAsState(
        targetValue   = if (isLoaded) MemexTeal else MemexAmber,
        animationSpec = tween(500),
        label         = "modelDot_$name"
    )
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name,        style = MemexBodyStyle.copy(color = MemexWhite, fontSize = 14.sp))
            Text(description, style = MemexCaptionStyle.copy(fontSize = 11.sp))
        }
        Text(
            text  = if (isLoaded) "Ready" else "Loading…",
            style = MemexCaptionStyle.copy(
                color    = if (isLoaded) MemexTeal else MemexAmber,
                fontSize = 11.sp
            )
        )
    }
}

@Composable
private fun NuclearDeleteRow(
    memoryCount: Int,
    isDeleting : Boolean,
    onClick    : () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isDeleting, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isDeleting) {
            CircularProgressIndicator(
                color       = MemexRed,
                strokeWidth = 2.dp,
                modifier    = Modifier.size(18.dp)
            )
        } else {
            Icon(Icons.Rounded.DeleteForever, null, tint = MemexRed, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = if (isDeleting) "Destroying vault…" else "Nuclear Delete",
                style = MemexBodyStyle.copy(color = MemexRed, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            )
            Text(
                text  = "Permanently destroy all $memoryCount memories",
                style = MemexCaptionStyle.copy(fontSize = 11.sp, color = MemexRed.copy(alpha = 0.7f))
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dialogs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NuclearDeleteDialog(
    memoryCount: Int,
    onConfirm  : () -> Unit,
    onDismiss  : () -> Unit
) {
    var confirmText by remember { mutableStateOf("") }
    val isEnabled = confirmText == "DELETE"

    // Shake animation on wrong input attempt
    val infiniteTransition = rememberInfiniteTransition(label = "shake")
    val shakeOffset by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = if (confirmText.isNotEmpty() && !isEnabled) 4f else 0f,
        animationSpec = infiniteRepeatable(
            tween(80, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "shakeX"
    )

    AlertDialog(
        containerColor   = MemexCard,
        shape            = RoundedCornerShape(20.dp),
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, null, tint = MemexRed, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text       = "Nuclear Delete",
                    color      = MemexRed,
                    fontWeight = FontWeight.Bold,
                    style      = MemexTitleStyle.copy(fontSize = 18.sp)
                )
            }
        },
        text = {
            Column {
                Surface(
                    color = MemexRed.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text      = "This will permanently destroy ALL $memoryCount memories.\nThis action cannot be undone.",
                        color     = MemexWhite,
                        style     = MemexBodyStyle.copy(fontSize = 14.sp, lineHeight = 22.sp),
                        modifier  = Modifier.padding(12.dp),
                        textAlign = TextAlign.Start
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Type DELETE to enable the destroy button:",
                    style = MemexCaptionStyle.copy(fontSize = 12.sp, color = MemexGray)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value         = confirmText,
                    onValueChange = { confirmText = it.uppercase().take(6) },
                    placeholder   = {
                        Text("DELETE", color = MemexGray, style = MemexBodyStyle.copy(
                            fontFamily = FontFamily.Monospace))
                    },
                    textStyle     = MemexBodyStyle.copy(
                        fontFamily = FontFamily.Monospace,
                        color      = if (isEnabled) MemexRed else MemexWhite
                    ),
                    singleLine    = true,
                    modifier      = Modifier
                        .fillMaxWidth()
                        .offset(x = shakeOffset.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor    = if (isEnabled) MemexRed else MemexGray,
                        unfocusedBorderColor  = MemexCardBorder,
                        focusedTextColor      = MemexWhite,
                        unfocusedTextColor    = MemexWhite,
                        cursorColor           = MemexRed,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick  = onConfirm,
                enabled  = isEnabled,
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = MemexRed,
                    disabledContainerColor = MemexRed.copy(alpha = 0.25f)
                ),
                shape    = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Rounded.DeleteForever, null, tint = MemexWhite, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("DESTROY VAULT", color = MemexWhite, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MemexGray)
            }
        }
    )
}

@Composable
private fun EncryptionInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        containerColor   = MemexCard,
        shape            = RoundedCornerShape(20.dp),
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = MemexTeal, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Vault Encryption", style = MemexTitleStyle.copy(fontSize = 17.sp), color = MemexTeal)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EncryptionBullet(
                    icon  = "🔑",
                    title = "AES-256 Encryption",
                    body  = "All memories are encrypted with AES-256 GCM using a key stored in Android Keystore. The key never leaves secure hardware."
                )
                EncryptionBullet(
                    icon  = "🔒",
                    title = "SHA-256 Integrity Proofs",
                    body  = "Every memory stores a SHA-256 hash of its content. This proves the memory has never been tampered with."
                )
                EncryptionBullet(
                    icon  = "📴",
                    title = "Zero Network Access",
                    body  = "MEMEX never sends your data to any server. All AI inference runs locally via the RunAnywhere SDK."
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it", color = MemexTeal)
            }
        }
    )
}

@Composable
private fun EncryptionBullet(icon: String, title: String, body: String) {
    Surface(
        color = MemexDeepNavy,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier          = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(icon, fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MemexBodyStyle.copy(color = MemexWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(2.dp))
                Text(body,  style = MemexCaptionStyle.copy(fontSize = 11.sp, lineHeight = 16.sp))
            }
        }
    }
}

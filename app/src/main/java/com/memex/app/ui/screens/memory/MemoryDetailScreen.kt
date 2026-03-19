package com.memex.app.ui.screens.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.memex.app.ui.components.formatRelativeTime
import com.memex.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryDetailScreen(
    memoryId         : String,
    onNavigateUp     : () -> Unit,
    onAskAboutMemory : (String) -> Unit,
    viewModel        : MemoryDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(memoryId) {
        viewModel.loadMemory(memoryId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Memory Detail", style = MemexTitleStyle) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = MemexWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MemexBlack,
                    titleContentColor = MemexWhite
                )
            )
        },
        containerColor = MemexBlack
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val memory = uiState.memory
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MemexPurple
                    )
                }
                memory != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(20.dp)
                    ) {
                        // ── Summary Card ──────────────────────────────────────
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MemexDeepNavy),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MemexCardBorder)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("SUMMARY", style = MemexCaptionStyle, color = MemexPurpleLight)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text  = memory.summary,
                                    style = MemexBodyStyle,
                                    color = MemexWhite
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        // ── Tags ──────────────────────────────────────────────
                        if (memory.tags.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                memory.tags.forEach { tag ->
                                    Surface(
                                        color = MemexPurpleDim.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(20.dp),
                                        border = androidx.compose.foundation.BorderStroke(0.5.dp, MemexPurpleDim)
                                    ) {
                                        Text(
                                            text = "#$tag",
                                            color = MemexPurpleLight,
                                            style = MemexCaptionStyle,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }

                        // ── Raw Content ───────────────────────────────────────
                        Text("EXTRACTED CONTENT", style = MemexCaptionStyle, color = MemexGray)
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MemexCard.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, MemexCardBorder)
                        ) {
                            Text(
                                text = memory.rawContent,
                                style = MemexBodyStyle,
                                color = MemexWhite.copy(alpha = 0.9f),
                                modifier = Modifier.padding(16.dp)
                            )
                        }

                        Spacer(Modifier.height(32.dp))

                        // ── Cryptographic Proof ───────────────────────────────
                        Text("CRYPTOGRAPHIC PROOF", style = MemexCaptionStyle, color = MemexGray)
                        Spacer(Modifier.height(10.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MemexCard),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, MemexCardBorder)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.CheckCircle, null, tint = MemexTeal, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("SHA-256 Hash Verified", color = MemexTeal, style = MemexCaptionStyle, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(12.dp))
                                SelectionContainer {
                                    Text(
                                        text = memory.sha256Hash,
                                        style = MemexMonoStyle.copy(fontSize = 11.sp, color = MemexGray),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "Captured: ${SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()).format(Date(memory.createdAt))}",
                                    style = MemexCaptionStyle,
                                    color = MemexGray
                                )
                            }
                        }

                        Spacer(Modifier.height(40.dp))

                        // ── Actions ───────────────────────────────────────────
                        Button(
                            onClick  = { onAskAboutMemory(memoryId) },
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = MemexPurple),
                            shape    = RoundedCornerShape(14.dp)
                        ) {
                            Text("Ask about this memory", style = MemexBodyStyle, color = MemexWhite, fontWeight = FontWeight.Bold)
                        }

                        Spacer(Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = { viewModel.deleteMemory(onNavigateUp) },
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MemexRed),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MemexRed),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Delete from Vault", style = MemexBodyStyle, fontWeight = FontWeight.Bold)
                        }

                        Spacer(Modifier.height(40.dp))
                    }
                }
                else -> {
                    Text(
                        text = uiState.error ?: "Unknown error",
                        color = MemexRed,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

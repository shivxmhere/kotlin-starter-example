package com.memex.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.memex.app.navigation.MemexNavGraph
import com.memex.app.ui.theme.MemexTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host for the entire MEMEX Compose UI.
 *
 * Design decisions:
 *   - [enableEdgeToEdge] lets the app draw under status/nav bars for the
 *     full-immersive black splash and nav bar effect.
 *   - [MemexNavGraph] owns all navigation logic; MainActivity stays thin.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw content edge-to-edge (status bar + nav bar transparent)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            MemexTheme {
                MemexNavGraph()
            }
        }
    }
}

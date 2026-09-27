package com.isedalabs.stickler

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.isedalabs.stickler.debug.DebugScreen
import com.isedalabs.stickler.debug.DebugViewModel
import com.isedalabs.stickler.ui.theme.SticklerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Phase 1 shows the reminder debug screen; the Today and Now screens replace it later.
    private val debugViewModel: DebugViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SticklerTheme {
                DebugScreen(debugViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Permissions can change in system settings while the app is in the background.
        debugViewModel.refreshPermissions()
    }
}

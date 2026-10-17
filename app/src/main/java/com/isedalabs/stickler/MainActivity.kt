package com.isedalabs.stickler

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.isedalabs.stickler.debug.DebugScreen
import com.isedalabs.stickler.debug.DebugViewModel
import com.isedalabs.stickler.today.TodayScreen
import com.isedalabs.stickler.today.TodayViewModel
import com.isedalabs.stickler.ui.theme.SticklerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val todayViewModel: TodayViewModel by viewModels()

    // The Phase 1 reminder debug screen stays reachable from Today for on-device testing.
    private val debugViewModel: DebugViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SticklerTheme {
                var showDebug by rememberSaveable { mutableStateOf(false) }
                if (showDebug) {
                    BackHandler { showDebug = false }
                    DebugScreen(debugViewModel)
                } else {
                    TodayScreen(todayViewModel, onOpenDebug = { showDebug = true })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The date, time zone and permissions can all change while the app is in the background.
        todayViewModel.refresh()
        debugViewModel.refreshPermissions()
    }
}

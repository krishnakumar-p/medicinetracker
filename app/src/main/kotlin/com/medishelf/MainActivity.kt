package com.medishelf

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medishelf.core.designsystem.theme.MediShelfTheme
import com.medishelf.core.model.ThemeMode
import com.medishelf.core.notifications.ReminderNotifier
import com.medishelf.ui.MediShelfRoot
import dagger.hilt.android.AndroidEntryPoint

/** FragmentActivity because BiometricPrompt needs one. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.onNotificationTarget(intent.getStringExtra(ReminderNotifier.EXTRA_OPEN_LIST))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Keep the splash up until we know whether to show the disclaimer, lock or app.
        splash.setKeepOnScreenCondition { viewModel.uiState.value.gate == AppGate.LOADING }
        enableEdgeToEdge()
        if (savedInstanceState == null) viewModel.onNotificationTarget(intent.getStringExtra(ReminderNotifier.EXTRA_OPEN_LIST))
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(state.secureWindow) {
                if (state.secureWindow) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            val dark = when (state.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            MediShelfTheme(darkTheme = dark) {
                MediShelfRoot(state = state, onStartFresh = viewModel::onStartFresh, onOpenListHandled = viewModel::onOpenListHandled)
            }
        }
    }
}

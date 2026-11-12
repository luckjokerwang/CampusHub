package com.campushub.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.campushub.app.core.settings.ThemeMode
import com.campushub.app.ui.app.CampusApp
import com.campushub.app.ui.app.ThemeReveal
import com.campushub.app.ui.dashboard.DashboardViewModel
import com.campushub.app.ui.theme.CampusHubTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as? CampusApplication
        val settings = app?.settings

        setContent {
            val themeMode = settings?.themeMode
                ?.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
                ?.value ?: ThemeMode.SYSTEM
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            val themeOrigin = remember { mutableStateOf<Offset?>(null) }

            ThemeReveal(darkTheme = darkTheme, origin = themeOrigin.value) { renderedDark ->
                CampusHubTheme(darkTheme = renderedDark) {
                    val dashboardVm: DashboardViewModel = viewModel(
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                return DashboardViewModel(settings = settings) as T
                            }
                        }
                    )

                    Box(modifier = Modifier.fillMaxSize()) {
                        Surface(modifier = Modifier.fillMaxSize()) {
                            if (settings != null) {
                                CampusApp(
                                    dashboardVm = dashboardVm,
                                    settings = settings,
                                    onThemeButtonPosition = { themeOrigin.value = it }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

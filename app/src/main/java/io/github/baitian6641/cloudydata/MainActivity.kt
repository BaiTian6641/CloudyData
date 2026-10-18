package io.github.baitian6641.cloudydata

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.baitian6641.cloudydata.design.CloudyTheme
import io.github.baitian6641.cloudydata.ui.HomeScreen
import io.github.baitian6641.cloudydata.ui.SettingsScreen
import io.github.baitian6641.cloudydata.ui.WorkbookScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as CloudyDataApp
        val store = app.workbookStore
        val requestedWorkbookId = intent.getStringExtra(EXTRA_WORKBOOK_ID)
        setContent {
            val preferences by app.displayPreferences.state.collectAsStateWithLifecycle()
            CloudyTheme(
                appearance = preferences.appearance,
                density = preferences.density,
            ) {
                var openWorkbookId by rememberSaveable {
                    mutableStateOf<String?>(requestedWorkbookId)
                }
                var showSettings by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = openWorkbookId != null || showSettings) {
                    if (openWorkbookId != null) openWorkbookId = null else showSettings = false
                }
                when {
                    showSettings -> SettingsScreen(
                        store = app.displayPreferences,
                        onBack = { showSettings = false },
                    )
                    openWorkbookId != null -> WorkbookScreen(
                        store = store,
                        workbookId = openWorkbookId!!,
                        onBack = { openWorkbookId = null },
                    )
                    else -> HomeScreen(
                        store = store,
                        onOpenWorkbook = { openWorkbookId = it },
                        onSettings = { showSettings = true },
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_WORKBOOK_ID = "io.github.baitian6641.cloudydata.extra.WORKBOOK_ID"
    }
}

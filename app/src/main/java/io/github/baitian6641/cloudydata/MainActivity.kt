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
import io.github.baitian6641.cloudydata.design.CloudyAppearance
import io.github.baitian6641.cloudydata.design.CloudyDensity
import io.github.baitian6641.cloudydata.design.CloudyTheme
import io.github.baitian6641.cloudydata.ui.HomeScreen
import io.github.baitian6641.cloudydata.ui.WorkbookScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = (application as CloudyDataApp).workbookStore
        setContent {
            CloudyTheme(
                appearance = CloudyAppearance.System,
                density = CloudyDensity.Comfortable,
            ) {
                var openWorkbookId by rememberSaveable { mutableStateOf<String?>(null) }
                BackHandler(enabled = openWorkbookId != null) {
                    openWorkbookId = null
                }
                val currentId = openWorkbookId
                if (currentId == null) {
                    HomeScreen(
                        store = store,
                        onOpenWorkbook = { openWorkbookId = it },
                    )
                } else {
                    WorkbookScreen(
                        store = store,
                        workbookId = currentId,
                        onBack = { openWorkbookId = null },
                    )
                }
            }
        }
    }
}

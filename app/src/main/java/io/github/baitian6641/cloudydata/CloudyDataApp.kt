package io.github.baitian6641.cloudydata

import android.app.Application
import io.github.baitian6641.cloudydata.data.DisplayPreferencesStore
import io.github.baitian6641.cloudydata.data.WorkbookStore

class CloudyDataApp : Application() {
    val workbookStore: WorkbookStore by lazy { WorkbookStore(filesDir) }
    val displayPreferences: DisplayPreferencesStore by lazy { DisplayPreferencesStore(this) }
}

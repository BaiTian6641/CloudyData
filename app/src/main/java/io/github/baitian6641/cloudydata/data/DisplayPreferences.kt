package io.github.baitian6641.cloudydata.data

import android.content.Context
import android.content.SharedPreferences
import io.github.baitian6641.cloudydata.design.CloudyAppearance
import io.github.baitian6641.cloudydata.design.CloudyDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class DisplayPreferences(
    val appearance: CloudyAppearance = CloudyAppearance.System,
    val density: CloudyDensity = CloudyDensity.Comfortable,
)

/**
 * App-owned presentation preferences. A successful write is reported only after
 * SharedPreferences.commit() returns true. Invalid stored values fall back
 * safely without deleting the stored text, so a future corrected build can still
 * inspect it.
 */
class DisplayPreferencesStore(
    context: Context,
    preferenceName: String = DEFAULT_PREFERENCE_NAME,
) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<DisplayPreferences> = _state.asStateFlow()

    suspend fun setAppearance(value: CloudyAppearance): Result<Unit> =
        commit(KEY_APPEARANCE, value.name) { copy(appearance = value) }

    suspend fun setDensity(value: CloudyDensity): Result<Unit> =
        commit(KEY_DENSITY, value.name) { copy(density = value) }

    private suspend fun commit(
        key: String,
        value: String,
        update: DisplayPreferences.() -> DisplayPreferences,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val written = preferences.edit().putString(key, value).commit()
            if (!written) {
                Result.failure(IllegalStateException("The preference store did not confirm the write."))
            } else {
                _state.value = _state.value.update()
                Result.success(Unit)
            }
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    private fun load(): DisplayPreferences {
        val appearance = preferences.getString(KEY_APPEARANCE, null)
            ?.let { stored -> runCatching { CloudyAppearance.valueOf(stored) }.getOrNull() }
            ?: CloudyAppearance.System
        val density = preferences.getString(KEY_DENSITY, null)
            ?.let { stored -> runCatching { CloudyDensity.valueOf(stored) }.getOrNull() }
            ?: CloudyDensity.Comfortable
        return DisplayPreferences(appearance, density)
    }

    companion object {
        const val DEFAULT_PREFERENCE_NAME = "cloudy-display"
        private const val KEY_APPEARANCE = "appearance"
        private const val KEY_DENSITY = "density"
    }
}

package io.github.baitian6641.cloudydata

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudydata.data.DisplayPreferencesStore
import io.github.baitian6641.cloudydata.design.CloudyAppearance
import io.github.baitian6641.cloudydata.design.CloudyDensity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DisplayPreferencesTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "display-test-${System.nanoTime()}"

    @After
    fun tearDown() {
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun explicitChoicesPersistAcrossStoreInstances() {
        val first = DisplayPreferencesStore(context, name)
        assertEquals(CloudyAppearance.System, first.state.value.appearance)
        assertEquals(CloudyDensity.Comfortable, first.state.value.density)
        assertTrue(runBlocking { first.setAppearance(CloudyAppearance.Dark) }.isSuccess)
        assertTrue(runBlocking { first.setDensity(CloudyDensity.CompactPointer) }.isSuccess)

        val second = DisplayPreferencesStore(context, name)
        assertEquals(CloudyAppearance.Dark, second.state.value.appearance)
        assertEquals(CloudyDensity.CompactPointer, second.state.value.density)
    }

    @Test
    fun invalidStoredValuesFallBackWithoutDeletingEvidence() {
        val shared = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        shared.edit().putString("appearance", "BROKEN").putString("density", "BROKEN").commit()

        val store = DisplayPreferencesStore(context, name)
        assertEquals(CloudyAppearance.System, store.state.value.appearance)
        assertEquals(CloudyDensity.Comfortable, store.state.value.density)
        assertEquals("BROKEN", shared.getString("appearance", null))
        assertEquals("BROKEN", shared.getString("density", null))
    }
}

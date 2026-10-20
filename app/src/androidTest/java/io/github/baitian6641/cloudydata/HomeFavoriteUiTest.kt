package io.github.baitian6641.cloudydata

import android.content.Context
import android.graphics.BitmapFactory
import android.system.Os
import android.util.Log
import android.util.Xml
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import io.github.baitian6641.cloudydata.data.WorkbookStore
import io.github.baitian6641.cloudydata.model.WorkbookCommand
import io.github.baitian6641.cloudydata.model.WorkbookDocument
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/** S03.3 / F01,F64 / bounded Q19,Q21,Q34 through the actual native Home. */
@RunWith(AndroidJUnit4::class)
class HomeFavoriteUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var context: Context
    private lateinit var device: UiDevice
    private lateinit var store: WorkbookStore
    private lateinit var fixture: WorkbookDocument
    private lateinit var sibling: WorkbookDocument
    private lateinit var directory: File
    private lateinit var searchPrefix: String
    private var originalMode: Int? = null
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        context = instrumentation.targetContext
        device = UiDevice.getInstance(instrumentation)
        store = (context.applicationContext as CloudyDataApp).workbookStore
        searchPrefix = "Favorite UI ${UUID.randomUUID().toString().take(8)}"
        fixture = store.create("$searchPrefix target")
        sibling = store.create("$searchPrefix reload sibling")
        directory = store.workbookDir(fixture.id)
        originalMode = Os.stat(directory.absolutePath).st_mode and 0x1ff
    }

    @After
    fun tearDown() {
        try {
            if (::directory.isInitialized) originalMode?.let { Os.chmod(directory.absolutePath, it) }
        } finally {
            scenario?.close()
            instrumentation.waitForIdleSync()
            if (::fixture.isInitialized) store.workbookDir(fixture.id).deleteRecursively()
            if (::sibling.isInitialized) store.workbookDir(sibling.id).deleteRecursively()
        }
    }

    @Test
    fun actualHomeKeepsFailedIntentAcrossReloadThenRetriesOnceAndKeyboardRemovesIt() {
        val baselineBytes = metadata(fixture.id).readBytes()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        instrumentation.waitForIdleSync()
        setSearch(searchPrefix)
        waitObject(By.desc(addDescription(fixture.name)))
        waitObject(By.desc(addDescription(sibling.name)))
        assertHomeStillOpen(searchPrefix)

        Os.chmod(directory.absolutePath, 0x16d) // 0555: actual app-private directory write denial.
        try {
            waitObject(By.desc(addDescription(fixture.name))).click()
            waitObject(By.desc(retryDescription()))
            waitObject(By.text("Retry favorite change"))
            waitObject(By.text("Dismiss and reload"))
            waitObject(By.desc("Dismiss favorite change for ${fixture.name} and reload"))
            assertHomeStillOpen(searchPrefix)
            assertFalse(store.load(fixture.id).favorite)
            assertEquals(fixture.revision, store.load(fixture.id).revision)
            assertTrue(store.loadCheckpoint(fixture.id).receipts.isEmpty())
            assertArrayEquals(baselineBytes, metadata(fixture.id).readBytes())

            val disabledSemanticsPublished = device.wait(
                Until.hasObject(By.desc(addDescription(fixture.name)).enabled(false)), TIMEOUT_MS,
            )
            if (!disabledSemanticsPublished) {
                runCatching {
                    val hierarchy = File(context.filesDir, "home-favorite-disabled-failure.xml")
                    device.dumpWindowHierarchy(hierarchy)
                    Log.e(TAG, "DISABLED_FAILURE hierarchy=${hierarchy.absolutePath} target=${fixture.name}")
                    capture("home-favorite-disabled-failure.png")
                }.onFailure { Log.e(TAG, "Could not preserve native disabled-star evidence", it) }
            }
            assertTrue("The failed star's exact native accessibility node must publish disabled state", disabledSemanticsPublished)
            val disabledStar = waitObject(By.desc(addDescription(fixture.name)))
            assertFalse("A failed favorite intent must disable its row's star", disabledStar.isEnabled)
            val disabledStarCenter = disabledStar.visibleCenter
            assertTrue(device.click(disabledStarCenter.x, disabledStarCenter.y))
            instrumentation.waitForIdleSync()
            assertHomeStillOpen(searchPrefix)
            waitObject(By.desc(retryDescription()))
            assertArrayEquals("Pointer activation of a disabled nested star cannot change the workbook", baselineBytes, metadata(fixture.id).readBytes())
            assertTrue(store.loadCheckpoint(fixture.id).receipts.isEmpty())

            // A successful action in another own workbook performs a real list
            // reload. It must not erase or replay the first workbook's failure.
            waitObject(By.desc(addDescription(sibling.name))).click()
            waitObject(By.desc(removeDescription(sibling.name)))
            waitObject(By.desc(retryDescription()))
            assertTrue(store.load(sibling.id).favorite)
            assertEquals(sibling.revision + 1, store.load(sibling.id).revision)
            assertEquals(1, store.loadCheckpoint(sibling.id).receipts.size)
            assertArrayEquals(baselineBytes, metadata(fixture.id).readBytes())

            selectSection("Favorites", "Workbooks marked for quick access")
            waitObject(By.desc(removeDescription(sibling.name)))
            waitObject(By.desc(retryDescription()))
            assertTrue(device.wait(Until.gone(By.desc(addDescription(fixture.name))), TIMEOUT_MS))
            assertHomeStillOpen(searchPrefix)
            selectSection("All workbooks", "Every workbook in the local library")
            waitObject(By.desc(addDescription(fixture.name)))
            waitObject(By.desc(retryDescription()))
            setSearch(fixture.name)
            waitObject(By.desc(addDescription(fixture.name)))
            waitObject(By.desc(retryDescription()))
            waitObject(By.text("Favorite change needs attention"))
            assertHomeStillOpen(fixture.name)
            assertFalse(store.load(fixture.id).favorite)
            assertTrue(store.loadCheckpoint(fixture.id).receipts.isEmpty())
            capture("home-favorite-failed.png")
        } finally {
            Os.chmod(directory.absolutePath, originalMode!!)
        }

        waitObject(By.desc(retryDescription())).click()
        waitObject(By.desc(removeDescription(fixture.name)))
        assertTrue(device.wait(Until.gone(By.desc(retryDescription())), TIMEOUT_MS))
        assertHomeStillOpen(fixture.name)
        val accepted = store.loadCheckpoint(fixture.id)
        assertTrue(accepted.document.favorite)
        assertEquals(fixture.revision + 1, accepted.document.revision)
        assertEquals(fixture.worksheets, accepted.document.worksheets)
        assertEquals(fixture.activeWorksheetId, accepted.document.activeWorksheetId)
        assertEquals(fixture.snapshot, accepted.document.snapshot)
        assertEquals(1, accepted.receipts.size)
        val addReceipt = accepted.receipts.single()
        assertTrue(addReceipt.command is WorkbookCommand.SetFavorite)
        assertTrue((addReceipt.command as WorkbookCommand.SetFavorite).value)
        assertEquals(fixture.id, addReceipt.command.workbookId)
        assertEquals(fixture.revision, addReceipt.command.expectedRevision)
        assertEquals(fixture.revision + 1, addReceipt.acceptedRevision)

        selectSection("Favorites", "Workbooks marked for quick access")
        waitObject(By.desc(removeDescription(fixture.name)))
        assertHomeStillOpen(fixture.name)
        assertTrue(store.list().single { it.id == fixture.id }.favorite)
        capture("home-favorite-saved.png")

        focusStarWithTab(removeDescription(fixture.name))
        assertTrue(device.pressKeyCode(KeyEvent.KEYCODE_ENTER))
        waitObject(By.text("No matching workbooks"))
        assertTrue(device.wait(Until.gone(By.desc(removeDescription(fixture.name))), TIMEOUT_MS))
        assertHomeStillOpen(fixture.name)
        val removed = store.loadCheckpoint(fixture.id)
        assertFalse(removed.document.favorite)
        assertEquals(fixture.revision + 2, removed.document.revision)
        assertEquals(fixture.worksheets, removed.document.worksheets)
        assertEquals(fixture.activeWorksheetId, removed.document.activeWorksheetId)
        assertEquals(2, removed.receipts.size)
        assertEquals(addReceipt, removed.receipts.first())
        val removeReceipt = removed.receipts.last()
        assertTrue(removeReceipt.command is WorkbookCommand.SetFavorite)
        assertFalse((removeReceipt.command as WorkbookCommand.SetFavorite).value)
        assertNotEquals(addReceipt.command.commandId, removeReceipt.command.commandId)
        assertEquals(fixture.id, removeReceipt.command.workbookId)
        assertEquals(fixture.revision + 1, removeReceipt.command.expectedRevision)
        assertEquals(fixture.revision + 2, removeReceipt.acceptedRevision)
        assertFalse(store.list().single { it.id == fixture.id }.favorite)
        assertTrue("The other workbook's accepted favorite must remain independent", store.load(sibling.id).favorite)
        assertEquals(sibling.revision + 1, store.load(sibling.id).revision)
        Log.i(TAG, "NATIVE_HOME_PASS target=${fixture.id} baseRevision=${fixture.revision} " +
            "acceptedRevision=${accepted.document.revision} removedRevision=${removed.document.revision} " +
            "receipts=${removed.receipts.size} keyboard=Tab+Enter")
    }

    private fun setSearch(value: String) {
        waitObject(By.text("Search workbooks"))
        waitObject(By.clazz("android.widget.EditText")).text = value
        instrumentation.waitForIdleSync()
        val exactTextPublished = device.wait(
            Until.hasObject(By.clazz("android.widget.EditText").text(value)), TIMEOUT_MS,
        )
        if (!exactTextPublished) {
            runCatching {
                val hierarchy = File(context.filesDir, "home-favorite-search-failure.xml")
                device.dumpWindowHierarchy(hierarchy)
                Log.e(TAG, "SEARCH_FAILURE hierarchy=${hierarchy.absolutePath} expected=$value")
                capture("home-favorite-search-failure.png")
            }.onFailure { Log.e(TAG, "Could not preserve native search failure evidence", it) }
        }
        assertTrue("The native search EditText must publish the exact requested text", exactTextPublished)
        assertEquals(value, waitObject(By.clazz("android.widget.EditText")).text)
    }

    private fun selectSection(label: String, subtitle: String) {
        if (device.findObject(By.text(label)) == null) {
            waitObject(By.desc("Open navigation")).click()
        }
        waitObject(By.text(label)).click()
        waitObject(By.text(subtitle))
        instrumentation.waitForIdleSync()
    }

    private fun assertHomeStillOpen(search: String) {
        waitObject(By.text("Home"))
        waitObject(By.text("Search workbooks"))
        assertEquals(search, waitObject(By.clazz("android.widget.EditText")).text)
        assertEquals(context.packageName, device.currentPackageName)
    }

    private fun focusStarWithTab(description: String) {
        val trace = File(context.filesDir, "home-favorite-focus-trace.txt").apply {
            writeText("Native Tab focus probe target=$description maximumTabPresses=24\n")
        }
        repeat(24) { index ->
            recordFocusObservation("beforeTab=${index + 1}", description, trace)
            if (device.hasObject(By.desc(description).focused(true))) return
            assertTrue(device.pressKeyCode(KeyEvent.KEYCODE_TAB))
            instrumentation.waitForIdleSync()
            // Main-loop idle does not prove Compose accessibility publication.
            // Wait only for the original exact target; diagnostics below decide
            // whether any failure is publication, traversal or event handling.
            val targetFocused = device.wait(
                Until.hasObject(By.desc(description).focused(true)), FOCUS_PUBLICATION_TIMEOUT_MS,
            )
            recordFocusObservation("afterTab=${index + 1} exactTargetFocused=$targetFocused", description, trace)
            if (targetFocused) return
        }
        if (device.hasObject(By.desc(description).focused(true))) return
        runCatching {
            val hierarchy = File(context.filesDir, "home-favorite-focus-failure.xml")
            device.dumpWindowHierarchy(hierarchy)
            Log.e(TAG, "FOCUS_FAILURE hierarchy=${hierarchy.absolutePath} trace=${trace.absolutePath} target=$description")
            capture("home-favorite-focus-failure.png")
        }.onFailure { Log.e(TAG, "Could not preserve native keyboard focus failure evidence", it) }
        assertNotNull("The existing native star must be reachable within a bounded Tab traversal",
            device.findObject(By.desc(description).focused(true)))
    }

    private fun recordFocusObservation(stage: String, targetDescription: String, trace: File) {
        val hierarchy = ByteArrayOutputStream().also { device.dumpWindowHierarchy(it) }.toByteArray()
        val parser = Xml.newPullParser().apply { setInput(hierarchy.inputStream(), Charsets.UTF_8.name()) }
        val focused = mutableListOf<String>()
        val targets = mutableListOf<String>()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "node") {
                fun attribute(name: String) = parser.getAttributeValue(null, name).orEmpty()
                val description = attribute("content-desc")
                if (attribute("focused") == "true" || description == targetDescription) {
                    val observation = "class=${attribute("class")} package=${attribute("package")} " +
                        "description=$description text=${attribute("text")} bounds=${attribute("bounds")} " +
                        "focused=${attribute("focused")} focusable=${attribute("focusable")} " +
                        "enabled=${attribute("enabled")} clickable=${attribute("clickable")}"
                    if (attribute("focused") == "true") focused += observation
                    if (description == targetDescription) targets += observation
                }
            }
            parser.next()
        }
        val record = "$stage\nfocused=${focused.ifEmpty { listOf("<none>") }.joinToString(" || ")}\n" +
            "target=${targets.ifEmpty { listOf("<missing>") }.joinToString(" || ")}\n"
        trace.appendText(record)
        Log.i(TAG, "FOCUS_OBSERVATION $record")
    }

    private fun waitObject(selector: BySelector): UiObject2 {
        val found = device.wait(Until.findObject(selector), TIMEOUT_MS)
        assertNotNull("Native Home element was not available: $selector", found)
        return found!!
    }

    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        val file = File(context.filesDir, name)
        assertTrue("Native screenshot capture failed", device.takeScreenshot(file))
        assertTrue(file.length() > 0)
        val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, dimensions)
        assertTrue(dimensions.outWidth > 0 && dimensions.outHeight > 0)
        val hash = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }
        Log.i(TAG, "CAPTURE path=${file.absolutePath} sha256=$hash pixels=${dimensions.outWidth}x${dimensions.outHeight}")
    }

    private fun metadata(id: String): File = File(store.workbookDir(id), "workbook.json")
    private fun addDescription(name: String) = "Add $name to favorites"
    private fun removeDescription(name: String) = "Remove $name from favorites"
    private fun retryDescription() = "Retry favorite change for ${fixture.name}"

    companion object {
        private const val TAG = "HomeFavoriteUiTest"
        private const val TIMEOUT_MS = 5_000L
        private const val FOCUS_PUBLICATION_TIMEOUT_MS = 500L
    }
}

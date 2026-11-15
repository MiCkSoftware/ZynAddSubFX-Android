package com.mick.zynaddsubfx

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mick.zynaddsubfx.ui.theme.ZynAddSubFXTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FxEditorInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sameEditorOpensOffSystemSlotsAndPreservesPartEq() {
        NativeSynthBridge.nativeStopAudio()
        assertTrue(NativeSynthBridge.nativeInit(48_000, 256))
        val engine = SynthEngine(InstrumentationRegistry.getInstrumentation().targetContext)
        val part = FxAddress()
        val snapshot = engine.fxSnapshot(part)!!
        assertTrue(engine.writeFx(snapshot, snapshot.parameter("fx/type")!!, 7.0))
        compose.setContent {
            ZynAddSubFXTheme { MasterFxScreen(engine, 0, emptySet(), 0, {}, {}, {}) }
        }
        compose.onNodeWithText("MASTER / SYSTEM").assertExists()
        compose.onNodeWithText("INSERT").assertExists()
        compose.onNodeWithContentDescription("Master / System 1 · Off").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Choose an effect").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Scope").assertDoesNotExist()
        compose.onNodeWithText("FX 1").assertDoesNotExist()
        compose.onNodeWithText("FX 4").assertDoesNotExist()
        compose.onNodeWithContentDescription("Type").performScrollTo().performTouchInput { longClick() }
        compose.onNodeWithText("Reverse").performClick()
        compose.runOnIdle {
            assertEquals(7, engine.fxSnapshot(part)!!.type)
            assertEquals(10, engine.fxSnapshot(FxAddress(FxScope.SYSTEM))!!.type)
        }
        compose.onNodeWithText("‹ FX").performClick()
        compose.onNodeWithContentDescription("Master / Insert 8 · Off").performScrollTo().performClick()
        compose.onNodeWithText("Choose an effect").assertExists()

    }
}

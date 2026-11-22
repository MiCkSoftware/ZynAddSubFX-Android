package com.mick.zynaddsubfx

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mick.zynaddsubfx.ui.theme.ZynAddSubFXTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyboardInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun slidesAcrossHexesAndPianoKeysWithOneTouch() {
        val events = mutableListOf<Int>()
        compose.setContent {
            ZynAddSubFXTheme {
                Column {
                    JankoHexKeyboard(emptySet(), 0, { events.add(it) }, { events.add(-it) }, Modifier.testTag("hex"))
                    ZynClassicKeyboardStrip(emptySet(), 0, { events.add(it) }, { events.add(-it) }, Modifier.testTag("piano"))
                }
            }
        }
        compose.onNodeWithTag("hex").performTouchInput {
            val first = Offset(width * .066f, height * .103f)
            down(first)
            moveTo(first.copy(x = width * .20f))
            moveTo(first.copy(x = width * .333f))
            up()
        }
        compose.runOnIdle {
            assertEquals(listOf(47, -47, 49, -49, 51, -51), events)
            events.clear()
        }
        compose.onNodeWithTag("piano").performTouchInput {
            down(Offset(width * .072f, height * .90f))
            moveTo(Offset(width * .145f, height * .40f))
            moveTo(Offset(width * .215f, height * .90f))
            moveTo(Offset(width * .215f, height + 5f))
            up()
        }
        compose.runOnIdle { assertEquals(listOf(60, -60, 61, -61, 62, -62), events) }
    }
}

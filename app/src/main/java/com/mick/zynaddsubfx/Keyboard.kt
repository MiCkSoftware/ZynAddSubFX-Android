package com.mick.zynaddsubfx

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun JankoHexKeyboard(
    heldNotes: Set<Int>,
    octaveShift: Int,
    onPressKeyboardNote: (Int) -> Unit,
    onReleaseKeyboardNote: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val topPattern = listOf(0, 2, 4, 6, 8, 10, 12)
    val midPattern = listOf(1, 3, 5, 7, 9, 11, 13)
    val topNotes = topPattern.map { 47 + it }
    val midNotes = midPattern.map { 47 + it }
    val rows = listOf(topNotes, midNotes, topNotes, midNotes, topNotes, midNotes)
    val hGap = 2.dp
    val vGap = 2.dp
    val hexRatio = 1.1547f

    BoxWithConstraints(modifier = modifier) {
        // The interleaved rows occupy 7 full keys plus a half-step horizontal offset.
        val keyWidth = (maxWidth - hGap * 6.5f) / 7.5f
        val keyHeight = keyWidth * hexRatio
        val xOffsetMid = (keyWidth + hGap) / 2f
        val xOffsetEven = 0.dp
        val xOffsetOdd = xOffsetMid
        val rowStride = keyHeight * 0.75f + vGap
        val totalHeight = keyHeight + rowStride * (rows.size - 1)

        Box(modifier = Modifier.fillMaxWidth().height(totalHeight)) {
            rows.forEachIndexed { rowIndex, notes ->
                val isOffsetRow = rowIndex % 2 == 1
                JankoRow(
                    notes = notes,
                    heldNotes = heldNotes,
                    octaveShift = octaveShift,
                    keyWidth = keyWidth,
                    keyHeight = keyHeight,
                    hGap = hGap,
                    xOffset = if (isOffsetRow) xOffsetOdd else xOffsetEven,
                    yOffset = rowStride * rowIndex,
                    onPressKeyboardNote = onPressKeyboardNote,
                    onReleaseKeyboardNote = onReleaseKeyboardNote
                )
            }
        }
    }
}

@Composable
private fun JankoRow(
    notes: List<Int>,
    heldNotes: Set<Int>,
    octaveShift: Int,
    keyWidth: Dp,
    keyHeight: Dp,
    hGap: Dp,
    xOffset: Dp,
    yOffset: Dp,
    onPressKeyboardNote: (Int) -> Unit,
    onReleaseKeyboardNote: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.offset(x = xOffset, y = yOffset),
        horizontalArrangement = Arrangement.spacedBy(hGap)
    ) {
        notes.forEach { note ->
            val effective = (note + (octaveShift * 12)).coerceIn(0, 127)
            TactileKey(
                note = note,
                labelNote = effective,
                active = heldNotes.contains(effective),
                onPress = { onPressKeyboardNote(note) },
                onRelease = { onReleaseKeyboardNote(note) },
                modifier = Modifier.width(keyWidth).height(keyHeight)
            )
        }
    }
}

private val HexKeyShape = GenericShape { size, _ ->
    moveTo(size.width * 0.5f, 0f)
    lineTo(size.width, size.height * 0.25f)
    lineTo(size.width, size.height * 0.75f)
    lineTo(size.width * 0.5f, size.height)
    lineTo(0f, size.height * 0.75f)
    lineTo(0f, size.height * 0.25f)
    close()
}

@Composable
fun TactileKey(
    note: Int,
    labelNote: Int = note,
    active: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier.width(44.dp).height(84.dp),
) {
    val bg = if (active) Color(0xFF1E6C73) else Color(0xFF22343A)
    val fg = if (active) Color(0xFFE8FFFF) else Color(0xFFBFD3D8)

    Surface(
        shape = HexKeyShape,
        tonalElevation = 2.dp,
        modifier = modifier.pointerInput(note) {
            detectTapGestures(
                onPress = {
                    onPress()
                    tryAwaitRelease()
                    onRelease()
                }
            )
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(bg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = noteName(labelNote),
                color = fg,
                textAlign = TextAlign.Center
            )
        }
    }
}

internal fun noteName(note: Int): String {
    val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val clamped = note.coerceIn(0, 127)
    return "${names[clamped % 12]}${clamped / 12 - 1}"
}

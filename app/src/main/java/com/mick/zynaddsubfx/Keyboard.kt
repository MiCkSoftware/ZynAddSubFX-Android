package com.mick.zynaddsubfx

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
    val rowCount = 6
    val keyCount = 7
    val hGap = 2.dp
    val vGap = 2.dp
    val hexRatio = 1.1547f

    BoxWithConstraints(modifier = modifier) {
        // The interleaved rows occupy 7 full keys plus a half-step horizontal offset.
        val keyWidth = (maxWidth - hGap * 6.5f) / 7.5f
        val keyHeight = keyWidth * hexRatio
        val xOffsetMid = (keyWidth + hGap) / 2f
        val rowStride = keyHeight * 0.75f + vGap
        val totalHeight = keyHeight + rowStride * (rowCount - 1)
        val density = LocalDensity.current

        Box(modifier = Modifier.fillMaxWidth().height(totalHeight)
            .keyboardGlissando(onPressKeyboardNote, onReleaseKeyboardNote) { position ->
                with(density) {
                    val width = keyWidth.roundToPx()
                    val height = keyHeight.roundToPx()
                    val stride = width + hGap.roundToPx()
                    for (row in 0 until rowCount) {
                        val x = position.x - if (row % 2 == 1) xOffsetMid.roundToPx() else 0
                        if (x < 0f || stride <= 0 || height <= 0 || width <= 0) continue
                        val column = (x / stride).toInt()
                        if (column >= keyCount) continue
                        val localY = (position.y - (rowStride * row).roundToPx()) / height
                        val localX = (x - column * stride) / width
                        if (localY !in 0f..1f || localX !in 0f..1f) continue
                        val inset = maxOf(0f, .5f - 2f * minOf(localY, 1f - localY))
                        if (localX in inset..(1f - inset)) return@keyboardGlissando 47 + row % 2 + 2 * column
                    }
                    null
                }
            }) {
            repeat(rowCount) { row ->
                Row(
                    modifier = Modifier.offset(
                        x = if (row % 2 == 1) xOffsetMid else 0.dp,
                        y = rowStride * row,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(hGap),
                ) {
                    repeat(keyCount) { column ->
                        val note = 47 + row % 2 + 2 * column
                        val effective = (note + octaveShift * 12).coerceIn(0, 127)
                        TactileKey(
                            note,
                            labelNote = effective,
                            active = effective in heldNotes,
                            modifier = Modifier.width(keyWidth).height(keyHeight),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun Modifier.keyboardGlissando(
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
    noteAt: (Offset) -> Int?,
): Modifier {
    val press = rememberUpdatedState(onPress)
    val release = rememberUpdatedState(onRelease)
    val hitTest = rememberUpdatedState(noteAt)
    return pointerInput(Unit) {
        val notes = mutableMapOf<PointerId, Int>()
        fun move(id: PointerId, note: Int?) {
            if (notes[id] == note) return
            notes.remove(id)?.let { previous ->
                if (previous !in notes.values) release.value(previous)
            }
            if (note != null) {
                val alreadyHeld = note in notes.values
                notes[id] = note
                if (!alreadyHeld) press.value(note)
            }
        }

        awaitEachGesture {
            try {
                val down = awaitFirstDown()
                down.consume()
                if (down.position.x >= 0f && down.position.x < size.width &&
                    down.position.y >= 0f && down.position.y < size.height
                ) move(down.id, hitTest.value(down.position))
                do {
                    val event = awaitPointerEvent()
                    for (change in event.changes) {
                        if (change.isConsumed) {
                            move(change.id, null)
                            continue
                        }
                        change.consume()
                        val position = change.position
                        val inside = position.x >= 0f && position.x < size.width &&
                            position.y >= 0f && position.y < size.height
                        move(change.id, if (change.pressed && inside) hitTest.value(position) else null)
                    }
                } while (event.changes.any { it.pressed })
            } finally {
                val remaining = notes.values.toSet()
                notes.clear()
                remaining.forEach { release.value(it) }
            }
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
private fun TactileKey(
    note: Int,
    labelNote: Int = note,
    active: Boolean,
    modifier: Modifier = Modifier.width(44.dp).height(84.dp),
) {
    val bg = if (active) Color(0xFF1E6C73) else Color(0xFF22343A)
    val fg = if (active) Color(0xFFE8FFFF) else Color(0xFFBFD3D8)

    Surface(
        shape = HexKeyShape,
        tonalElevation = 2.dp,
        modifier = modifier,
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

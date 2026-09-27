package com.mick.zynaddsubfx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

data class PlayUiState(
    val audioRunning: Boolean,
    val actionStatus: String,
    val currentPresetName: String?,
    val currentPresetRating: Int,
    val masterVolume: Float,
    val keyboardVelocity: Int,
    val keyboardOctaveShift: Int,
    val heldNote: Int?,
    val heldNotes: Set<Int>,
    val presetLoading: Boolean,
    val parts: List<SynthEngine.PartInspector>,
    val selectedPartIndex: Int,
)

@Composable
fun PlayScreen(
    uiState: PlayUiState,
    onOpenBanks: () -> Unit,
    onPressKeyboardNote: (Int) -> Unit,
    onReleaseKeyboardNote: (Int) -> Unit,
    onAllNotesOff: () -> Unit,
    onPanic: () -> Unit,
    onSetCurrentPresetRating: (Int) -> Unit,
    onMasterVolumeChange: (Float) -> Unit,
    onKeyboardVelocityChange: (Int) -> Unit,
    onKeyboardOctaveShiftChange: (Int) -> Unit,
    onPartTapped: (Int, Boolean) -> Unit,
    onSetSelectedPartChannel: (Int) -> Unit,
    onSetSelectedPartVolume: (Int) -> Unit,
    onSetSelectedPartPan: (Int) -> Unit,
    onSetSelectedPartKeyShift: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val partsByIndex = remember(uiState.parts) { uiState.parts.associateBy { it.partIndex } }
    val selectedPart = partsByIndex[uiState.selectedPartIndex]
    val presetDisplayName = uiState.currentPresetName ?: "none"
    val heldVoices = uiState.heldNotes.size

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 10.dp, horizontal=2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F7681))
            ) {
                Text(
                    text = presetDisplayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF66F0E9),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clickable { onOpenBanks() }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
            PresetStarRating(
                rating = uiState.currentPresetRating.coerceIn(0, 5),
                onSetRating = onSetCurrentPresetRating
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ) {
            Column(modifier = Modifier.padding(0.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0..1).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            (0..7).forEach { col ->
                                val partIndex = row * 8 + col
                                val part = partsByIndex[partIndex]
                                val active = part?.enabled == true
                                val selected = uiState.selectedPartIndex == partIndex
                                val buttonColor = if (active) Color(0xFF0A2D33) else Color(0xFF1D2B30)
                                val glowColor = if (active) Color(0xFF33C8C8) else Color(0xFF4F6469)
                                val textColor = if (active) Color(0xFFE6FFFF) else Color(0xFFB0BEC5)
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(
                                            width = if (selected) 2.dp else 0.dp,
                                            color = if (selected) Color(0xFF33C8C8) else Color.Transparent,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable { onPartTapped(partIndex, selected) },
                                    shape = RoundedCornerShape(6.dp),
                                    color = buttonColor,
                                    shadowElevation = if (active) 8.dp else 1.dp
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = (partIndex + 1).toString(),
                                            color = textColor,
                                            modifier = Modifier
                                                .background(
                                                    color = glowColor.copy(alpha = if (active) 0.20f else 0.06f),
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                EditorSectionHeader("Part controls")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.weight(1f)) { DenseKnobCard("Channel", ((selectedPart?.receiveChannel ?: 0) + 1).toFloat(), 1f, 16f,
                        { onSetSelectedPartChannel(it.toInt().coerceIn(1, 16)) }) }
                    Box(Modifier.weight(1f)) { DenseKnobCard("Volume", (selectedPart?.volume ?: 96).toFloat(), 0f, 127f,
                        { onSetSelectedPartVolume(it.toInt().coerceIn(0, 127)) }) }
                    Box(Modifier.weight(1f)) { DenseKnobCard("Panning", (selectedPart?.panning ?: 64).toFloat(), 0f, 127f,
                        { onSetSelectedPartPan(it.toInt().coerceIn(0, 127)) }) }
                    Box(Modifier.weight(1f)) { DenseKnobCard("Key shift", (selectedPart?.keyShift ?: 0).toFloat(), -64f, 63f,
                        { onSetSelectedPartKeyShift(it.toInt().coerceIn(-64, 63)) }) }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp, horizontal=2.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                EditorSectionHeader("Performance")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(Modifier.weight(1f)) { DenseKnobCard("Master", uiState.masterVolume * 127f, 0f, 127f,
                        { onMasterVolumeChange((it / 127f).coerceIn(0f, 1f)) }) }
                    Box(Modifier.weight(1f)) { DenseKnobCard("Velocity", uiState.keyboardVelocity.toFloat(), 1f, 127f,
                        { onKeyboardVelocityChange(it.roundToInt().coerceIn(1, 127)) }) }
                    Box(Modifier.weight(1f)) { DenseKnobCard("Octave", uiState.keyboardOctaveShift.toFloat(), -2f, 2f,
                        { onKeyboardOctaveShiftChange(it.roundToInt().coerceIn(-2, 2)) }) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LuminousActionButton(
                        label = "ALL OFF",
                        onClick = onAllNotesOff,
                        modifier = Modifier.weight(1f),
                        accent = Color(0xFF33C8C8)
                    )
                    LuminousActionButton(
                        label = "PANIC",
                        onClick = onPanic,
                        modifier = Modifier.weight(1f),
                        accent = Color(0xFFFF815E)
                    )
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (uiState.heldNotes.isNotEmpty()) Color(0xFF33C8C8) else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (uiState.heldNotes.isNotEmpty()) Color(0xFF33C8C8) else Color(0xFF667A82),
                                        CircleShape
                                    )
                            )
                            Text(
                                text = "Held",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF0D2026)) {
                                Text(
                                    text = heldVoices.toString(),
                                    color = Color(0xFFA7F4F0),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier
                                        .width(28.dp)
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ) {
            JankoHexKeyboard(
                heldNotes = uiState.heldNotes,
                octaveShift = uiState.keyboardOctaveShift,
                onPressKeyboardNote = onPressKeyboardNote,
                onReleaseKeyboardNote = onReleaseKeyboardNote,
                modifier = Modifier.padding(4.dp).fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

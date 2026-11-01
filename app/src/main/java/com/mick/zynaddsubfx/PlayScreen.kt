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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

data class PlayUiState(
    val audioRunning: Boolean,
    val actionStatus: String,
    val currentPresetName: String?,
    val currentBankName: String? = null,
    val currentPresetRating: Int,
    val masterVolume: Float,
    val keyboardVelocity: Int,
    val keyboardChannel: Int,
    val keyboardOctaveShift: Int,
    val heldNote: Int?,
    val heldNotes: Set<Int>,
    val presetLoading: Boolean,
    val parts: List<SynthEngine.PartInspector>,
    val selectedPartIndex: Int,
    val loadedPartIndices: Set<Int> = emptySet(),
)

@Composable
private fun PlayChoiceKnob(
    label: String,
    title: String,
    value: Int,
    range: IntRange,
    context: Any? = null,
    labelFor: (Int) -> String = { it.toString() },
    knobLabel: (Int) -> String = labelFor,
    onSelect: (Int) -> Unit,
) {
    var open by remember(context) { mutableStateOf(false) }
    Box {
        DenseControlCard(label, Color(0xFF1C474B), onLongClick = { open = true },
            longClickLabel = "Choose $title") {
            TinyKnob("", value.toFloat(), range.first.toFloat(), range.last.toFloat(),
                valueText = { knobLabel(it.roundToInt()) },
                onValueChange = { onSelect(it.roundToInt().coerceIn(range)) })
        }
        EnumChoiceMenu(open, title, range, value, labelFor, { open = false }) {
            onSelect(it)
            open = false
        }
    }
}

@Composable
fun PlayScreen(
    uiState: PlayUiState,
    onOpenBanks: () -> Unit,
    onPreviousPreset: () -> Unit,
    onNextPreset: () -> Unit,
    canPreviousPreset: Boolean,
    canNextPreset: Boolean,
    onPressKeyboardNote: (Int) -> Unit,
    onReleaseKeyboardNote: (Int) -> Unit,
    onAllNotesOff: () -> Unit,
    onPanic: () -> Unit,
    onRestartAudio: () -> Unit,
    onSetCurrentPresetRating: (Int) -> Unit,
    onMasterVolumeChange: (Float) -> Unit,
    onKeyboardVelocityChange: (Int) -> Unit,
    onKeyboardChannelChange: (Int) -> Unit,
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFF111A1B),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF815E)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.width(38.dp).height(40.dp).alpha(if (canPreviousPreset) 1f else .35f)
                        .semantics { contentDescription = "Previous instrument" }
                        .clickable(enabled = canPreviousPreset, onClick = onPreviousPreset),
                    shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp), color = Color(0xFFFF815E),
                ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = Color(0xFF301B16), modifier = Modifier.padding(4.dp)) }
                Row(
                    modifier = Modifier.weight(1f).clickable(onClick = onOpenBanks).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "Open instrument library", tint = Color(0xFFFFAD91), modifier = Modifier.size(32.dp))
                    Column {
                        uiState.currentBankName?.let { bank ->
                            Text(bank, style = MaterialTheme.typography.labelSmall, color = Color(0xFFC98B77),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(presetDisplayName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFFFFAD91), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                PresetStarRating(rating = uiState.currentPresetRating.coerceIn(0, 5), onSetRating = onSetCurrentPresetRating)
                Spacer(Modifier.width(5.dp))
                Surface(
                    modifier = Modifier.width(38.dp).height(40.dp).alpha(if (canNextPreset) 1f else .35f)
                        .semantics { contentDescription = "Next instrument" }
                        .clickable(enabled = canNextPreset, onClick = onNextPreset),
                    shape = RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp), color = Color(0xFFFF815E),
                ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFF301B16), modifier = Modifier.padding(4.dp)) }
            }
        }
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
                                val loaded = partIndex in uiState.loadedPartIndices || !part?.name.isNullOrBlank()
                                Box(
                                    Modifier.weight(1f).border(
                                        width = if (selected) 2.dp else 0.dp,
                                        color = if (selected) Color(0xFF8EF5EE) else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp),
                                    ).padding(2.dp),
                                ) {
                                    LedButton(
                                        label = "Part ${partIndex + 1}: ${if (active) "enabled" else if (loaded) "instrument loaded, disabled" else "empty, disabled"}${if (selected) ", selected" else ""}",
                                        lit = active,
                                        onClick = { onPartTapped(partIndex, selected) },
                                        modifier = Modifier.fillMaxWidth(),
                                        hue = if (active) 125f else 2f,
                                        action = !active && !loaded,
                                        displayLabel = (partIndex + 1).toString(),
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                EditorSectionHeader("Part controls")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.weight(1f)) { PlayChoiceKnob("Channel", "Receive channel", (selectedPart?.receiveChannel ?: 0) + 1,
                        1..16, uiState.selectedPartIndex, onSelect = onSetSelectedPartChannel) }
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
                    Box(Modifier.weight(1f)) {
                        PlayChoiceKnob("Channel", "Keyboard channel", uiState.keyboardChannel + 1, 0..16,
                            uiState.selectedPartIndex,
                            labelFor = { if (it == 0) "Auto (channel ${(selectedPart?.receiveChannel ?: 0) + 1})" else it.toString() },
                            knobLabel = { if (it == 0) "A${(selectedPart?.receiveChannel ?: 0) + 1}" else it.toString() },
                            onSelect = { onKeyboardChannelChange(it - 1) })
                    }
                    Box(Modifier.weight(1f)) { DenseKnobCard("Velocity", uiState.keyboardVelocity.toFloat(), 1f, 127f,
                        { onKeyboardVelocityChange(it.roundToInt().coerceIn(1, 127)) }) }
                    Box(Modifier.weight(1f)) { PlayChoiceKnob("Octave", "Octave", uiState.keyboardOctaveShift,
                        -2..2, onSelect = onKeyboardOctaveShiftChange) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LedButton("All notes off", false, onAllNotesOff, Modifier.weight(1f), action = true, displayLabel = "ALL OFF")
                    LedButton("Panic", false, onPanic, Modifier.weight(1f), hue = 2f, action = true, displayLabel = "PANIC")
                    LedButton("Restart audio", false, onRestartAudio, Modifier.weight(1f), hue = 28f, action = true, displayLabel = "RESTART")
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

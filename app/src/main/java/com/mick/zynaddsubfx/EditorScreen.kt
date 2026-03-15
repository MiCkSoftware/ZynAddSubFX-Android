package com.mick.zynaddsubfx

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import java.util.Locale

data class EditorUiState(
    val parts: List<SynthEngine.PartInspector>,
    val selectedPartIndex: Int,
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PresetEditorScreen(
    uiState: EditorUiState,
    heldNote: Int?,
    heldNotes: Set<Int>,
    keyboardOctaveShift: Int,
    onPressKeyboardNote: (Int) -> Unit,
    onReleaseKeyboardNote: (Int) -> Unit,
    onSetPartEnabled: (Int, Boolean) -> Unit,
    onSetPartAddEnabled: (Int, Boolean) -> Unit,
    onSetPartSubEnabled: (Int, Boolean) -> Unit,
    onSetPartPadEnabled: (Int, Boolean) -> Unit,
    onSoloPart: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val partExpanded = remember { mutableStateMapOf<Int, Boolean>() }
    var keyboardVisible by rememberSaveable { mutableStateOf(false) }
    val keyboardSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val selectedPart = remember(uiState.parts, uiState.selectedPartIndex) {
        uiState.parts.firstOrNull { it.partIndex == uiState.selectedPartIndex }
            ?: uiState.parts.firstOrNull()
    }

    LaunchedEffect(uiState.parts) {
        uiState.parts.forEach { part ->
            if (partExpanded[part.partIndex] == null) {
                partExpanded[part.partIndex] = part.enabled
            }
        }
    }

    Box(modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (selectedPart != null) {
                PartEditorCard(
                    part = selectedPart,
                    expanded = partExpanded[selectedPart.partIndex] ?: selectedPart.enabled,
                    onToggleExpanded = {
                        val current = partExpanded[selectedPart.partIndex] ?: selectedPart.enabled
                        partExpanded[selectedPart.partIndex] = !current
                    },
                    onSetPartEnabled = onSetPartEnabled,
                    onSetPartAddEnabled = onSetPartAddEnabled,
                    onSetPartSubEnabled = onSetPartSubEnabled,
                    onSetPartPadEnabled = onSetPartPadEnabled,
                    onSoloPart = onSoloPart
                )
            } else {
                Text(
                    text = "No part available",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(68.dp))
        }

        if (keyboardVisible) {
            ModalBottomSheet(
                onDismissRequest = { keyboardVisible = false },
                sheetState = keyboardSheetState,
            ) {
                KeyboardOverlayCard(
                    heldNote = heldNote,
                    heldNotes = heldNotes,
                    keyboardOctaveShift = keyboardOctaveShift,
                    onPressKeyboardNote = onPressKeyboardNote,
                    onReleaseKeyboardNote = onReleaseKeyboardNote
                )
            }
        } else {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .clickable { keyboardVisible = true },
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            ) {
                Spacer(
                    modifier = Modifier
                        .width(52.dp)
                        .height(6.dp)
                        .pointerInput(Unit) {
                            var totalDrag = 0f
                            detectVerticalDragGestures(
                                onDragStart = { totalDrag = 0f },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    totalDrag += dragAmount
                                    if (totalDrag < -18f) {
                                        keyboardVisible = true
                                    }
                                }
                            )
                        }
                )
            }
        }
    }
}

@Composable
private fun KeyboardOverlayCard(
    heldNote: Int?,
    heldNotes: Set<Int>,
    keyboardOctaveShift: Int,
    onPressKeyboardNote: (Int) -> Unit,
    onReleaseKeyboardNote: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(60, 62, 64, 65, 67, 69, 71).forEach { note ->
                val effective = (note + (keyboardOctaveShift * 12)).coerceIn(0, 127)
                TactileKey(
                    note = note,
                    labelNote = effective,
                    active = heldNotes.contains(effective),
                    onPress = { onPressKeyboardNote(note) },
                    onRelease = { onReleaseKeyboardNote(note) }
                )
            }
        }
    }
}

@Composable
private fun PartEditorCard(
    part: SynthEngine.PartInspector,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSetPartEnabled: (Int, Boolean) -> Unit,
    onSetPartAddEnabled: (Int, Boolean) -> Unit,
    onSetPartSubEnabled: (Int, Boolean) -> Unit,
    onSetPartPadEnabled: (Int, Boolean) -> Unit,
    onSoloPart: (Int) -> Unit,
) {
    var addSectionExpanded by rememberSaveable(part.partIndex) { mutableStateOf(false) }
    var subSectionExpanded by rememberSaveable(part.partIndex) { mutableStateOf(false) }
    var padSectionExpanded by rememberSaveable(part.partIndex) { mutableStateOf(false) }
    var fxSectionExpanded by rememberSaveable(part.partIndex) { mutableStateOf(false) }
    val partTitle = if (part.name.isBlank()) {
        "Part ${part.partIndex + 1}"
    } else {
        "Part ${part.partIndex + 1} - ${part.name.take(20)}"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = partTitle, color = MaterialTheme.colorScheme.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                TinyStateToggle("0", Color(0xFFB71C1C), !part.enabled) { onSetPartEnabled(part.partIndex, false) }
                TinyStateToggle("1", Color(0xFF1B5E20), part.enabled) { onSetPartEnabled(part.partIndex, true) }
                TinyStateToggle("S", Color(0xFFE65100), false) { onSoloPart(part.partIndex) }
                Text(
                    text = if (expanded) "▾" else "▸",
                    modifier = Modifier.clickable { onToggleExpanded() },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!expanded) return

        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF14262C),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E5F68))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Peak",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = String.format(Locale.US, "%.3f", part.outputPeak),
                        color = Color(0xFFA7F4F0),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            EditorStatChip("Ch", (part.receiveChannel + 1).toString())
            EditorStatChip("Keys", "${part.minKey}..${part.maxKey}")
            EditorStatChip("Mode", if (part.poly) "POLY" else "MONO")
            EditorStatChip("Stereo", if (part.stereoEnabled) "ON" else "OFF")
            EditorStatChip("RndGrp", if (part.rndGroupingEnabled) "ON" else "OFF")
            EditorStatChip("VolRaw", String.format(Locale.US, "%.3f", part.volumeRaw))
            EditorStatChip("Gain", String.format(Locale.US, "%.3f", part.gainRaw))
        }
        Spacer(modifier = Modifier.height(6.dp))
        PartModuleSection(
            title = "ADD",
            count = part.addEnabledCount,
            expanded = addSectionExpanded,
            onToggle = { addSectionExpanded = !addSectionExpanded }
        ) {
            val enabled = part.addEnabledCount > 0
            LuminousToggleButton(
                label = if (enabled) "ADD ON" else "ADD OFF",
                enabled = enabled,
                onToggle = { onSetPartAddEnabled(part.partIndex, !enabled) }
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EditorStatChip("Ops", part.addEnabledCount.toString())
                EditorStatChip("Part", (part.partIndex + 1).toString())
                EditorStatChip("NoteOn", if (part.noteOn) "YES" else "NO")
            }
            Text(
                text = "Additive engine active operators for this part.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        PartModuleSection(
            title = "SUB",
            count = part.subEnabledCount,
            expanded = subSectionExpanded,
            onToggle = { subSectionExpanded = !subSectionExpanded }
        ) {
            val enabled = part.subEnabledCount > 0
            LuminousToggleButton(
                label = if (enabled) "SUB ON" else "SUB OFF",
                enabled = enabled,
                onToggle = { onSetPartSubEnabled(part.partIndex, !enabled) }
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EditorStatChip("Ops", part.subEnabledCount.toString())
                EditorStatChip("Range", "${part.minKey}..${part.maxKey}")
                EditorStatChip("Mode", if (part.poly) "POLY" else "MONO")
            }
            Text(
                text = "Subtractive block enabled operators for this part.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        PartModuleSection(
            title = "PAD",
            count = part.padEnabledCount,
            expanded = padSectionExpanded,
            onToggle = { padSectionExpanded = !padSectionExpanded }
        ) {
            val enabled = part.padEnabledCount > 0
            LuminousToggleButton(
                label = if (enabled) "PAD ON" else "PAD OFF",
                enabled = enabled,
                onToggle = { onSetPartPadEnabled(part.partIndex, !enabled) }
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EditorStatChip("Enabled", part.padEnabledCount.toString())
                EditorStatChip("ActiveKit", part.activeKitItems.toString())
                EditorStatChip("MutedKit", part.mutedKitItems.toString())
                EditorStatChip("KitMode", part.kitMode.toString())
            }
            Text(
                text = "PAD synth / sample-based layer status.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        PartModuleSection(
            title = "FX",
            count = part.partFxActiveCount,
            expanded = fxSectionExpanded,
            onToggle = { fxSectionExpanded = !fxSectionExpanded }
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EditorStatChip("Slots", part.partFxActiveCount.toString())
                EditorStatChip("Stereo", if (part.stereoEnabled) "ON" else "OFF")
                EditorStatChip("Peak", String.format(Locale.US, "%.3f", part.outputPeak))
            }
            Text(
                text = "Routing and effect types available in Debug > Inspector.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun EditorStatChip(
    label: String,
    value: String,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFF1A353D),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E5F68))
    ) {
        Text(
            text = "$label $value",
            color = Color(0xFFA7F4F0),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun TinyStateToggle(
    label: String,
    activeColor: Color,
    active: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = Color.White,
        modifier = Modifier
            .background(if (active) activeColor else activeColor.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun PartModuleSection(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF122229),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF234A53))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    color = Color(0xFF66F0E9),
                    style = MaterialTheme.typography.labelLarge
                )
                Surface(shape = RoundedCornerShape(999.dp), color = Color(0xFF1A353D)) {
                    Text(
                        text = count.toString(),
                        color = Color(0xFFA7F4F0),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (expanded) "▾" else "▸",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            if (expanded) {
                content()
            }
        }
    }
}

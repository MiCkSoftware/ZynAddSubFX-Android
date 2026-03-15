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
    onSetSelectedPartSense: (Int) -> Unit,
    onSetSelectedPartStrength: (Int) -> Unit,
    onSetSelectedPartTime: (Int) -> Unit,
    onSetSelectedPartStretch: (Int) -> Unit,
    onSetSelectedPartStereo: (Boolean) -> Unit,
    onSetSelectedPartRndGrp: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val partsByIndex = remember(uiState.parts) { uiState.parts.associateBy { it.partIndex } }
    val selectedPart = partsByIndex[uiState.selectedPartIndex]
    val selectedStereo = selectedPart?.stereoEnabled ?: true
    val selectedRndGrp = selectedPart?.rndGroupingEnabled ?: false
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        TinyKnob(
                            label = "Sens",
                            value = (selectedPart?.velocitySense ?: 64).toFloat(),
                            min = 0f,
                            max = 127f,
                            onValueChange = { onSetSelectedPartSense(it.toInt().coerceIn(0, 127)) }
                        )
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        TinyKnob(
                            label = "Pan",
                            value = (selectedPart?.panning ?: 64).toFloat(),
                            min = 0f,
                            max = 127f,
                            onValueChange = { onSetSelectedPartPan(it.toInt().coerceIn(0, 127)) }
                        )
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        TinyKnob(
                            label = "Stretch",
                            value = (selectedPart?.portamentoStretch ?: 64).toFloat(),
                            min = 0f,
                            max = 127f,
                            onValueChange = { onSetSelectedPartStretch(it.toInt().coerceIn(0, 127)) }
                        )
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        TinyKnob(
                            label = "Strenght",
                            value = (selectedPart?.velocityOffset ?: 64).toFloat(),
                            min = 0f,
                            max = 127f,
                            onValueChange = { onSetSelectedPartStrength(it.toInt().coerceIn(0, 127)) }
                        )
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        TinyKnob(
                            label = "Tim",
                            value = (selectedPart?.portamentoTime ?: 64).toFloat(),
                            min = 0f,
                            max = 127f,
                            onValueChange = { onSetSelectedPartTime(it.toInt().coerceIn(0, 127)) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        TinyKnob(
                            label = "Ch",
                            value = ((selectedPart?.receiveChannel ?: 0) + 1).toFloat(),
                            min = 1f,
                            max = 16f,
                            onValueChange = { onSetSelectedPartChannel(it.toInt().coerceIn(1, 16)) }
                        )
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        TinyKnob(
                            label = "Vol",
                            value = (selectedPart?.volume ?: 96).toFloat(),
                            min = 0f,
                            max = 127f,
                            onValueChange = { onSetSelectedPartVolume(it.toInt().coerceIn(0, 127)) }
                        )
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        LuminousToggleButton(
                            label = "STEREO",
                            enabled = selectedStereo,
                            onToggle = { onSetSelectedPartStereo(!selectedStereo) }
                        )
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        LuminousToggleButton(
                            label = "RND GRP",
                            enabled = selectedRndGrp,
                            onToggle = { onSetSelectedPartRndGrp(!selectedRndGrp) }
                        )
                    }
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        TinyKnob(
                            label = "Master",
                            value = uiState.masterVolume * 127f,
                            min = 0f,
                            max = 127f,
                            onValueChange = { onMasterVolumeChange((it / 127f).coerceIn(0f, 1f)) }
                        )
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        TinyKnob(
                            label = "Velocity",
                            value = uiState.keyboardVelocity.toFloat(),
                            min = 1f,
                            max = 127f,
                            onValueChange = { onKeyboardVelocityChange(it.roundToInt().coerceIn(1, 127)) }
                        )
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        TinyKnob(
                            label = "Octave",
                            value = uiState.keyboardOctaveShift.toFloat(),
                            min = -2f,
                            max = 2f,
                            onValueChange = { onKeyboardOctaveShiftChange(it.roundToInt().coerceIn(-2, 2)) }
                        )
                    }
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
                JankoHexKeyboard(
                    heldNotes = uiState.heldNotes,
                    octaveShift = uiState.keyboardOctaveShift,
                    onPressKeyboardNote = onPressKeyboardNote,
                    onReleaseKeyboardNote = onReleaseKeyboardNote,
                    modifier = Modifier.padding(0.dp).fillMaxWidth()
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

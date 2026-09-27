package com.mick.zynaddsubfx

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.delay
import java.util.Locale

data class EditorUiState(
    val parts: List<SynthEngine.PartInspector>,
    val selectedPartIndex: Int,
    val activeFxSlots: List<SynthEngine.ActiveFxSlot>,
    val mixer: SynthEngine.MixerInspector,
)

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
fun PresetEditorScreen(
    engine: SynthEngine,
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
    onPartChanged: () -> Unit,
    onAllNotesOffPart: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val partExpanded = remember { mutableStateMapOf<Int, Boolean>() }
    var selectedModule by rememberSaveable(uiState.selectedPartIndex) { mutableStateOf<String?>(null) }
    var selectedKitIndex by rememberSaveable(uiState.selectedPartIndex) { mutableStateOf(0) }
    var keyboardVisible by rememberSaveable { mutableStateOf(false) }
    val keyboardSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val selectedPart = remember(uiState.parts, uiState.selectedPartIndex) {
        uiState.parts.firstOrNull { it.partIndex == uiState.selectedPartIndex }
            ?: uiState.parts.firstOrNull()
    }
    BackHandler(enabled = selectedModule != null) { selectedModule = null }

    LaunchedEffect(uiState.parts) {
        uiState.parts.forEach { part ->
            if (partExpanded[part.partIndex] == null) {
                partExpanded[part.partIndex] = part.enabled
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        if (selectedModule != null && selectedPart != null) {
            FullInstrumentEditor(
                engine = engine,
                partIndex = selectedPart.partIndex,
                kitIndex = selectedKitIndex,
                module = selectedModule!!,
                heldNotes = heldNotes,
                keyboardOctaveShift = keyboardOctaveShift,
                onBack = { selectedModule = null },
                onOpenModule = { selectedModule = it },
                onPressKeyboardNote = onPressKeyboardNote,
                onReleaseKeyboardNote = onReleaseKeyboardNote,
                modifier = Modifier.fillMaxSize(),
            )
            return@Box
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (selectedPart != null) {
                PartEditorCard(
                    engine = engine,
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
                    onSoloPart = onSoloPart,
                    onPartChanged = onPartChanged,
                    onAllNotesOffPart = onAllNotesOffPart,
                    heldNote = heldNote,
                    onOpenModule = { module, kit ->
                        selectedKitIndex = kit
                        selectedModule = module
                    },
                    activeFxSlots = uiState.activeFxSlots,
                    mixer = uiState.mixer
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
@OptIn(ExperimentalLayoutApi::class)
private fun PartEditorCard(
    engine: SynthEngine,
    part: SynthEngine.PartInspector,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSetPartEnabled: (Int, Boolean) -> Unit,
    onSetPartAddEnabled: (Int, Boolean) -> Unit,
    onSetPartSubEnabled: (Int, Boolean) -> Unit,
    onSetPartPadEnabled: (Int, Boolean) -> Unit,
    onSoloPart: (Int) -> Unit,
    onPartChanged: () -> Unit,
    onAllNotesOffPart: (Int) -> Unit,
    heldNote: Int?,
    onOpenModule: (String, Int) -> Unit,
    activeFxSlots: List<SynthEngine.ActiveFxSlot>,
    mixer: SynthEngine.MixerInspector,
) {
    var structure by remember(part.partIndex) {
        mutableStateOf(engine.parameterSnapshot(part.partIndex, 0))
    }
    var kitSnapshots by remember(part.partIndex) {
        mutableStateOf((0 until 16).map { engine.parameterSnapshot(part.partIndex, it) })
    }
    var editedStructuralParameter by remember { mutableStateOf<SynthEngine.ParameterValue?>(null) }
    var currentPeak by remember(part.partIndex) { mutableStateOf(0f) }
    var heldPeak by remember(part.partIndex) { mutableStateOf(0f) }
    LaunchedEffect(part.partIndex, expanded) {
        while (expanded) {
            currentPeak = engine.inspectParts().firstOrNull { it.partIndex == part.partIndex }?.outputPeak ?: 0f
            heldPeak = maxOf(heldPeak, currentPeak)
            delay(200)
        }
    }
    LaunchedEffect(part) {
        structure = engine.parameterSnapshot(part.partIndex, 0)
        kitSnapshots = (0 until 16).map { engine.parameterSnapshot(part.partIndex, it) }
    }
    fun structural(path: String, fallback: String): String =
        structure.values.firstOrNull { it.descriptor.path == path }?.let { value ->
            when (value.descriptor.type) {
                SynthEngine.ParameterType.BOOLEAN -> if (value.value >= .5) "ON" else "OFF"
                SynthEngine.ParameterType.ENUM -> value.descriptor.options.getOrNull(value.value.toInt())
                    ?: value.value.toInt().toString()
                SynthEngine.ParameterType.INTEGER -> value.value.toInt().toString()
            }
        } ?: fallback
    fun edit(path: String) {
        editedStructuralParameter = structure.values.firstOrNull { it.descriptor.path == path }
    }
    fun bool(snapshot: SynthEngine.ParameterSnapshot, path: String): Boolean =
        snapshot.values.firstOrNull { it.descriptor.path == path }?.value?.let { it >= .5 } == true
    fun refreshKits() {
        kitSnapshots = (0 until 16).map { engine.parameterSnapshot(part.partIndex, it) }
    }
    fun writeKit(kit: Int, path: String, value: Boolean) {
        if (engine.writeParameter(part.partIndex, kit, SynthEngine.ParameterWrite(path, if (value) 1.0 else 0.0))) {
            refreshKits()
            onPartChanged()
        }
    }
    fun addKitEngine(path: String) {
        val kit = (1 until 16).firstOrNull { !bool(kitSnapshots[it], "kit/enabled") } ?: return
        engine.writeParameter(part.partIndex, kit, SynthEngine.ParameterWrite("kit/enabled", 1.0))
        engine.writeParameter(part.partIndex, kit, SynthEngine.ParameterWrite("kit/addEnabled", if (path == "kit/addEnabled") 1.0 else 0.0))
        engine.writeParameter(part.partIndex, kit, SynthEngine.ParameterWrite("kit/subEnabled", if (path == "kit/subEnabled") 1.0 else 0.0))
        engine.writeParameter(part.partIndex, kit, SynthEngine.ParameterWrite("kit/padEnabled", if (path == "kit/padEnabled") 1.0 else 0.0))
        refreshKits()
    }
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
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                LedButton("Disable part", !part.enabled, { onSetPartEnabled(part.partIndex, false) }, Modifier.width(LedSlotWidth.dp), 2f, displayLabel = "0")
                LedButton("Enable part", part.enabled, { onSetPartEnabled(part.partIndex, true) }, Modifier.width(LedSlotWidth.dp), 125f, displayLabel = "1")
                LedButton("Solo part", false, { onSoloPart(part.partIndex) }, Modifier.width(LedSlotWidth.dp), 28f, action = true, displayLabel = "S")
                Text(
                    text = if (expanded) "▾" else "▸",
                    modifier = Modifier.width(22.dp).clickable { onToggleExpanded() },
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
                        text = String.format(Locale.US, "%.3f / %.3f", currentPeak, heldPeak),
                        color = Color(0xFFA7F4F0),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text("↺", color = Color(0xFFA7F4F0), modifier = Modifier.clickable { heldPeak = 0f })
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        EditorSectionHeader("Part parameters")
        DenseParameterGrid(
            structure.values.filter { it.descriptor.path in setOf(
                "part/volume", "part/panning", "part/velocitySense", "part/velocityOffset",
                "part/keyShift", "part/channel", "part/keyLimit", "part/portamentoTime",
                "part/portamentoStretch") },
            onWrite = { parameter, value ->
                if (engine.writeParameter(part.partIndex, 0,
                        SynthEngine.ParameterWrite(parameter.descriptor.path, value))) {
                    structure = engine.parameterSnapshot(part.partIndex, 0)
                }
            },
            verticalLabels = true,
            valueText = { parameter ->
                val raw = parameter.value.toInt()
                if (parameter.descriptor.path == "part/channel") (raw + 1).toString() else raw.toString()
            },
            onLongPress = { editedStructuralParameter = it },
            onCommit = onPartChanged,
        )
        Spacer(Modifier.height(12.dp))
        EditorSectionHeader("Playing")
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(
                Triple("Note on", "part/noteOn", "NOTE ON"),
                Triple("Portamento", "part/portamento", "PORT"),
                Triple("Mode", "part/polyMode", "MODE"),
            ).forEach { (label, path, shortLabel) ->
                val active = structure.values.firstOrNull { it.descriptor.path == path }?.value?.let { it >= .5 }
                    ?: (path == "part/noteOn" && part.noteOn)
                Box(Modifier.weight(1f)) {
                    LedButton(label, active, {
                        if (engine.writeParameter(part.partIndex, 0,
                                SynthEngine.ParameterWrite(path, if (active) 0.0 else 1.0))) {
                            structure = engine.parameterSnapshot(part.partIndex, 0)
                            onPartChanged()
                        }
                    }, Modifier.fillMaxWidth(), displayLabel = if (path == "part/polyMode") {
                        if (active) "POLY" else "MONO"
                    } else shortLabel)
                }
            }
            Box(Modifier.weight(1f)) {
                LedButton("All notes off", false, { onAllNotesOffPart(part.partIndex) },
                    Modifier.fillMaxWidth(), 28f, action = true, displayLabel = "ALL OFF")
            }
        }
        Spacer(Modifier.height(12.dp))
        EditorSectionHeader("Key range")
        DenseParameterGrid(
            structure.values.filter { it.descriptor.path in setOf("part/minKey", "part/maxKey") },
            onWrite = { parameter, value ->
                if (engine.writeParameter(part.partIndex, 0,
                        SynthEngine.ParameterWrite(parameter.descriptor.path, value))) {
                    structure = engine.parameterSnapshot(part.partIndex, 0)
                }
            },
            verticalLabels = true,
            onLongPress = { editedStructuralParameter = it },
            onCommit = onPartChanged,
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Last note →", color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall)
            listOf("MIN" to "part/minKey", "RESET" to "reset", "MAX" to "part/maxKey").forEach { (label, path) ->
                LedButton(label, false, {
                    val writes = if (path == "reset") listOf("part/minKey" to 0.0, "part/maxKey" to 127.0)
                        else heldNote?.let { note ->
                            val min = structural("part/minKey", "0").toIntOrNull() ?: 0
                            val max = structural("part/maxKey", "127").toIntOrNull() ?: 127
                            listOf(path to (if (path == "part/minKey") minOf(note, max) else maxOf(note, min)).toDouble())
                        } ?: emptyList()
                    if (writes.isNotEmpty() && writes.all { (key, value) ->
                            engine.writeParameter(part.partIndex, 0, SynthEngine.ParameterWrite(key, value))
                        }) { structure = engine.parameterSnapshot(part.partIndex, 0); onPartChanged() }
                }, Modifier.width(62.dp), action = true, displayLabel = label)
            }
        }
        Spacer(Modifier.height(12.dp))
        EditorSectionHeader("Instrument kit")
        EngineKitSection(
            "ADD",
            "kit/addEnabled",
            kitSnapshots,
            ::bool,
            { onOpenModule("ADD", it) },
            ::writeKit,
            kitControl = { kit ->
                val stereo = bool(kit, "add/stereo")
                StudioStereoSelector(
                    stereo = stereo,
                    onStereoChange = { writeKit(kit.kitIndex, "add/stereo", it) },
                )
                val rnd = bool(kit, "add/randomGrouping")
                LedButton("Kit ${kit.kitIndex + 1} random grouping", rnd,
                    { writeKit(kit.kitIndex, "add/randomGrouping", !rnd) },
                    Modifier.width(LedSlotWidth.dp), displayLabel = "RND")
            },
        ) {
            addKitEngine("kit/addEnabled")
        }
        editedStructuralParameter?.let { parameter ->
            ParameterEditorDialog(
                parameter = parameter,
                onDismiss = { editedStructuralParameter = null },
                onApply = { value ->
                    if (engine.writeParameter(
                            part.partIndex,
                            0,
                            SynthEngine.ParameterWrite(parameter.descriptor.path, value)
                        )
                    ) {
                        structure = engine.parameterSnapshot(part.partIndex, 0)
                        onPartChanged()
                    }
                    editedStructuralParameter = null
                },
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        EngineKitSection("SUB", "kit/subEnabled", kitSnapshots, ::bool, { onOpenModule("SUB", it) }, ::writeKit) {
            addKitEngine("kit/subEnabled")
        }
        Spacer(modifier = Modifier.height(4.dp))
        EngineKitSection("PAD", "kit/padEnabled", kitSnapshots, ::bool, { onOpenModule("PAD", it) }, ::writeKit) {
            addKitEngine("kit/padEnabled")
        }
        Spacer(modifier = Modifier.height(4.dp))
        ZynModuleRow(
            title = "FX",
            count = part.partFxActiveCount,
            active = part.partFxActiveCount > 0,
            onOpen = { onOpenModule("FX", 0) },
            onToggle = { onOpenModule("FX", 0) },
        )
        Spacer(Modifier.height(12.dp))
        EditorSectionHeader("To system FX")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(4) { fx ->
                val amount = mixer.systemSends.firstOrNull { it.partIndex == part.partIndex && it.systemFxSlot == fx }?.sendValue ?: 0
                var sendValue by remember(part.partIndex, fx, amount) { mutableStateOf(amount) }
                Box(Modifier.weight(1f)) {
                    DenseKnobCard("FX ${fx + 1}", sendValue.toFloat(), 0f, 127f,
                        onValueChange = { value ->
                            val next = value.toInt().coerceIn(0, 127)
                            if (engine.setSystemFxSend(part.partIndex, fx, next)) sendValue = next
                        }, labelColor = Color(0xFF274354), onValueChangeFinished = onPartChanged)
                }
            }
        }
    }
}

@Composable
private fun EngineKitSection(
    title: String,
    enginePath: String,
    kits: List<SynthEngine.ParameterSnapshot>,
    bool: (SynthEngine.ParameterSnapshot, String) -> Boolean,
    onOpen: (Int) -> Unit,
    onWrite: (Int, String, Boolean) -> Unit,
    kitControl: (@Composable (SynthEngine.ParameterSnapshot) -> Unit)? = null,
    onAdd: () -> Unit,
) {
    val active = kits.filter { bool(it, "kit/enabled") && bool(it, enginePath) }
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF122229),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF234A53)),
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color(0xFF66F0E9), modifier = Modifier.weight(1f))
                ZynValueChip("", active.size.toString())
                Text("+", color = Color(0xFF66F0E9), modifier = Modifier.clickable(onClick = onAdd).padding(8.dp))
            }
            active.forEach { kit ->
                val muted = bool(kit, "kit/muted")
                ZynKitItemRow(
                    label = "Kit ${kit.kitIndex + 1}",
                    muted = muted,
                    onOpen = { onOpen(kit.kitIndex) },
                    onMute = { onWrite(kit.kitIndex, "kit/muted", !muted) },
                    middleContent = kitControl?.let { content -> { content(kit) } },
                )
            }
        }
    }
}

@Composable
private fun ParameterEditorDialog(
    parameter: SynthEngine.ParameterValue,
    onDismiss: () -> Unit,
    onApply: (Double) -> Unit,
) {
    var value by remember(parameter.descriptor.path) { mutableStateOf(parameter.value) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(parameter.descriptor.label) },
        text = {
            Column {
                Text((value.toInt() + if (parameter.descriptor.path == "part/channel") 1 else 0).toString())
                androidx.compose.material3.Slider(
                    value = value.toFloat(),
                    onValueChange = { value = it.toDouble() },
                    valueRange = parameter.descriptor.minimum.toFloat()..parameter.descriptor.maximum.toFloat(),
                )
            }
        },
        confirmButton = { Text("Apply", modifier = Modifier.clickable { onApply(value) }.padding(8.dp)) },
        dismissButton = { Text("Cancel", modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp)) },
    )
}

@Composable
private fun PartFxRouting(
    part: SynthEngine.PartInspector,
    activeFxSlots: List<SynthEngine.ActiveFxSlot>,
    mixer: SynthEngine.MixerInspector,
) {
    val fxByScopeAndSlot = remember(activeFxSlots) {
        activeFxSlots.associateBy { it.scope.lowercase(Locale.US) to it.slotId }
    }
    val insertRoutes = remember(mixer.insertRoutings, part.partIndex) {
        mixer.insertRoutings.filter { it.assignedPart == part.partIndex }
    }
    val systemSends = remember(mixer.systemSends, part.partIndex) {
        mixer.systemSends.filter { it.partIndex == part.partIndex && it.sendValue > 0 }
    }
    val instrumentFx = remember(activeFxSlots, part.partFxActiveCount) {
        if (part.partFxActiveCount <= 0) {
            emptyList()
        } else {
            activeFxSlots.filter { it.scope.equals("Instrument", ignoreCase = true) }
                .take(part.partFxActiveCount)
        }
    }

    FxRouteGroup(title = "INSERT", empty = insertRoutes.isEmpty()) {
        insertRoutes.forEach { route ->
            FxRouteRow(
                slot = route.slotId,
                name = route.typeName,
                detail = "direct"
            )
        }
    }
    FxRouteGroup(title = "SYSTEM SEND", empty = systemSends.isEmpty()) {
        systemSends.forEach { send ->
            val fx = fxByScopeAndSlot["system" to send.systemFxSlot]
            FxRouteRow(
                slot = send.systemFxSlot,
                name = fx?.typeName ?: "System FX",
                detail = "send ${send.sendValue}"
            )
        }
    }
    FxRouteGroup(
        title = "PART FX",
        empty = part.partFxActiveCount <= 0
    ) {
        if (instrumentFx.isEmpty()) {
            repeat(part.partFxActiveCount) { slot ->
                FxRouteRow(slot = slot, name = "Active FX", detail = "part")
            }
        } else {
            instrumentFx.forEach { fx ->
                FxRouteRow(slot = fx.slotId, name = fx.typeName, detail = "part")
            }
        }
    }
}

@Composable
private fun FxRouteGroup(
    title: String,
    empty: Boolean,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            color = Color(0xFF66F0E9),
            style = MaterialTheme.typography.labelSmall
        )
        if (empty) {
            Text(
                text = "No active routing",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            content()
        }
    }
}

@Composable
private fun FxRouteRow(
    slot: Int,
    name: String,
    detail: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(7.dp),
        color = Color(0xFF172B31),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF274B54))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "#${slot + 1}",
                color = Color(0xFF66F0E9),
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = name,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
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
                    text = if (expanded) "▾" else "›",
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

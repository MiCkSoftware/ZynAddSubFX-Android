@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.mick.zynaddsubfx

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.pow
import kotlinx.coroutines.launch

private enum class OscillatorEditorKind { Voice, Modulator }

private data class OscillatorEditorTarget(
    val kind: OscillatorEditorKind,
    val ownerVoice: Int,
)

private sealed interface InstrumentEditorDestination {
    data object VoiceDetail : InstrumentEditorDestination
    data object Resonance : InstrumentEditorDestination
    data class Oscillator(val target: OscillatorEditorTarget) : InstrumentEditorDestination
}

private val voiceEditorSections =
    listOf("Voice", "Oscillator", "Amplitude", "Frequency", "Filter", "Modulation", "Unison")
private val oscillatorEditorSections =
    listOf("Preview", "Output", "Base function", "Shape & filter", "Harmonics")
private val resonanceEditorSections = listOf("Curve", "Parameters")

@Composable
private fun SynthEditorScaffold(
    title: String,
    navigationLabel: String,
    onNavigateUp: () -> Unit,
    tabs: List<String>,
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    dirty: Boolean,
    onActions: (() -> Unit)?,
    heldNotes: Set<Int>,
    keyboardOctaveShift: Int,
    onPressKeyboardNote: (Int) -> Unit,
    onReleaseKeyboardNote: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        Surface(color = Color(0xFF102329), shadowElevation = 5.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onNavigateUp) { Text(navigationLabel) }
                    Text(
                        title,
                        color = Color(0xFF8EF5EE),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (dirty) Text("●", color = Color(0xFFFFC857))
                    onActions?.let { action ->
                        TextButton(onClick = action) { Text("⋮") }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    tabs.forEach { tab ->
                        Surface(
                            color = if (selectedTab == tab) Color(0xFF23616A) else Color(0xFF182F35),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clickable { onTabSelected(tab) },
                        ) {
                            Text(tab, modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp))
                        }
                    }
                }
            }
        }
        content(Modifier.weight(1f).fillMaxWidth())
        ZynClassicKeyboardStrip(
            heldNotes = heldNotes,
            octaveShift = keyboardOctaveShift,
            onPress = onPressKeyboardNote,
            onRelease = onReleaseKeyboardNote,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 4.dp),
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
fun FullInstrumentEditor(
    engine: SynthEngine,
    partIndex: Int,
    kitIndex: Int,
    module: String,
    heldNotes: Set<Int>,
    keyboardOctaveShift: Int,
    onBack: () -> Unit,
    onOpenModule: (String) -> Unit,
    onPressKeyboardNote: (Int) -> Unit,
    onReleaseKeyboardNote: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val model = remember(engine) { InstrumentEditorViewModel(engine) }
    val state = model.state
    var selector by remember { mutableStateOf<String?>(null) }
    var destination by remember { mutableStateOf<InstrumentEditorDestination?>(null) }
    var editedParameter by remember { mutableStateOf<SynthEngine.ParameterValue?>(null) }
    var exportedFile by remember { mutableStateOf<File?>(null) }
    var exportedRevision by remember { mutableStateOf(0L) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val contentScroll = rememberScrollState()
    val sectionOffsets = remember(module, state.kitIndex) { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val voiceDetailScroll = rememberScrollState()
    val voiceSectionOffsets = remember(state.selectedVoice) { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val oscillatorScroll = rememberScrollState()
    val oscillatorSectionOffsets = remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val oscillatorPreviewOcclusion = with(density) { 112.dp.roundToPx() }
    val resonanceScroll = rememberScrollState()
    val resonanceSectionOffsets = remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val source = exportedFile
        val ok = uri != null && source != null && runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { it.copyTo(output) }
            } != null
        }.getOrDefault(false)
        model.finishExport(ok, exportedRevision)
        source?.delete()
        exportedFile = null
    }

    LaunchedEffect(partIndex, kitIndex, module) {
        model.open(partIndex, kitIndex)
        model.selectEngine(module)
    }

    val sectionNames = populatedSections(state.snapshot?.values.orEmpty(), module)
    val openSection: (String) -> Unit = { tab ->
        destination = null
        model.selectTab(tab)
        scope.launch {
            contentScroll.animateScrollTo(sectionOffsets.value[tab] ?: 0)
        }
    }
    val currentDestination = destination
    if (currentDestination is InstrumentEditorDestination.Oscillator) {
        val oscillatorTitle = if (currentDestination.target.kind == OscillatorEditorKind.Modulator) {
            "Modulator oscillator"
        } else "Voice oscillator"
        SynthEditorScaffold(
            title = oscillatorTitle,
            navigationLabel = "‹ Voice ${state.selectedVoice + 1}",
            onNavigateUp = { destination = InstrumentEditorDestination.VoiceDetail },
            tabs = oscillatorEditorSections,
            selectedTab = activeAnchor(
                oscillatorScroll.value +
                    if (oscillatorScroll.value > 0) oscillatorPreviewOcclusion else 0,
                oscillatorEditorSections,
                oscillatorSectionOffsets.value,
            ),
            onTabSelected = { section ->
                scope.launch {
                    val sectionOffset = oscillatorSectionOffsets.value[section] ?: 0
                    oscillatorScroll.animateScrollTo(
                        if (section == "Preview") 0 else {
                            (sectionOffset - oscillatorPreviewOcclusion).coerceAtLeast(0)
                        }
                    )
                }
            },
            dirty = state.dirty,
            onActions = null,
            heldNotes = heldNotes,
            keyboardOctaveShift = keyboardOctaveShift,
            onPressKeyboardNote = onPressKeyboardNote,
            onReleaseKeyboardNote = onReleaseKeyboardNote,
            modifier = modifier,
        ) { contentModifier ->
            AddOscillatorEditorScreen(
                model = model,
                target = currentDestination.target,
                modifier = contentModifier,
                scrollState = oscillatorScroll,
                onSectionPosition = { section, offset ->
                    if (oscillatorSectionOffsets.value[section] != offset) {
                        oscillatorSectionOffsets.value =
                            oscillatorSectionOffsets.value + (section to offset)
                    }
                },
            )
        }
        return
    }
    if (currentDestination == InstrumentEditorDestination.Resonance) {
        SynthEditorScaffold(
            title = "ADsynth Resonance",
            navigationLabel = "‹ ADD",
            onNavigateUp = { destination = null },
            tabs = resonanceEditorSections,
            selectedTab = activeAnchor(
                resonanceScroll.value,
                resonanceEditorSections,
                resonanceSectionOffsets.value,
            ),
            onTabSelected = { section ->
                scope.launch {
                    resonanceScroll.animateScrollTo(resonanceSectionOffsets.value[section] ?: 0)
                }
            },
            dirty = state.dirty,
            onActions = null,
            heldNotes = heldNotes,
            keyboardOctaveShift = keyboardOctaveShift,
            onPressKeyboardNote = onPressKeyboardNote,
            onReleaseKeyboardNote = onReleaseKeyboardNote,
            modifier = modifier,
        ) { contentModifier ->
            AddResonanceEditorScreen(
                model = model,
                modifier = contentModifier,
                scrollState = resonanceScroll,
                onSectionPosition = { section, offset ->
                    if (resonanceSectionOffsets.value[section] != offset) {
                        resonanceSectionOffsets.value = resonanceSectionOffsets.value + (section to offset)
                    }
                },
            )
        }
        return
    }
    if (currentDestination == InstrumentEditorDestination.VoiceDetail) {
        SynthEditorScaffold(
            title = "Voice ${state.selectedVoice + 1}",
            navigationLabel = "‹ Voices",
            onNavigateUp = { destination = null },
            tabs = voiceEditorSections,
            selectedTab = activeAnchor(
                voiceDetailScroll.value,
                voiceEditorSections,
                voiceSectionOffsets.value,
            ),
            onTabSelected = { section ->
                scope.launch {
                    voiceDetailScroll.animateScrollTo(voiceSectionOffsets.value[section] ?: 0)
                }
            },
            dirty = state.dirty,
            onActions = null,
            heldNotes = heldNotes,
            keyboardOctaveShift = keyboardOctaveShift,
            onPressKeyboardNote = onPressKeyboardNote,
            onReleaseKeyboardNote = onReleaseKeyboardNote,
            modifier = modifier,
        ) { contentModifier ->
            AddVoiceDetailScreen(
                model = model,
                modifier = contentModifier,
                scrollState = voiceDetailScroll,
                onSectionPosition = { section, offset ->
                    if (voiceSectionOffsets.value[section] != offset) {
                        voiceSectionOffsets.value = voiceSectionOffsets.value + (section to offset)
                    }
                },
                onOpenOscillator = {
                    destination = InstrumentEditorDestination.Oscillator(it)
                },
            )
        }
        return
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) {
                Surface(
                    modifier = Modifier.width(148.dp).fillMaxSize(),
                    color = Color(0xFF0C1D22),
                    border = BorderStroke(1.dp, Color(0xFF234A53)),
                ) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Part ${state.partIndex + 1}", color = Color(0xFF8EF5EE))
                        listOf("ADD", "SUB", "PAD", "FX").forEach { item ->
                            Surface(
                                color = if (item == module) Color(0xFF23616A) else Color(0xFF162D33),
                                shape = RoundedCornerShape(7.dp),
                                modifier = Modifier.fillMaxWidth().combinedClickable(
                                    onClick = { onOpenModule(item) }
                                ),
                            ) {
                                Text(item, modifier = Modifier.padding(10.dp))
                            }
                        }
                        CompactSelector(
                            label = "Kit",
                            value = (state.kitIndex + 1).toString(),
                            onClick = { selector = "kit" },
                        )
                    }
                }
            }
            SynthEditorScaffold(
                title = "$module · P${state.partIndex + 1} K${state.kitIndex + 1}",
                navigationLabel = "‹ Part",
                onNavigateUp = onBack,
                tabs = sectionNames,
                selectedTab = activeAnchor(contentScroll.value, sectionNames, sectionOffsets.value),
                onTabSelected = openSection,
                dirty = state.dirty,
                onActions = { selector = "actions" },
                heldNotes = heldNotes,
                keyboardOctaveShift = keyboardOctaveShift,
                onPressKeyboardNote = onPressKeyboardNote,
                onReleaseKeyboardNote = onReleaseKeyboardNote,
                modifier = Modifier.weight(1f),
            ) { contentModifier ->
                Column(
                    contentModifier.verticalScroll(contentScroll).padding(7.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    populatedSections(state.snapshot?.values.orEmpty(), module).forEach { section ->
                        val parameters = parametersFor(
                            state.snapshot?.values.orEmpty(),
                            module,
                            section,
                            state.selectedVoice,
                        ).let { if (section == "Voices") emptyList() else it }
                        ZynEditorSection(
                            title = section,
                            parameters = parameters,
                            modifier = Modifier.onGloballyPositioned {
                                val offset = it.positionInParent().y.roundToInt()
                                if (sectionOffsets.value[section] != offset) {
                                    sectionOffsets.value = sectionOffsets.value + (section to offset)
                                }
                            },
                            complex = section == "Spectrum" ||
                                (module == "FX" && section != "Routing"),
                            onComplex = {
                                model.selectTab(section)
                                destination = if (section == "Resonance") {
                                    InstrumentEditorDestination.Resonance
                                } else null
                                if (destination == null) selector = "complex"
                            },
                            onWrite = model::write,
                            onEdit = { editedParameter = it },
                            verticalLabels = module == "ADD",
                            leadingContent = when (section) {
                                "Voices" -> ({
                                    VoiceMatrix(
                                        values = state.snapshot?.values.orEmpty(),
                                        onWrite = model::write,
                                        onOpenDetail = {
                                            model.selectVoice(it)
                                            destination = InstrumentEditorDestination.VoiceDetail
                                        },
                                    )
                                })
                                "Resonance" -> ({
                                    Box(
                                        Modifier.fillMaxWidth().height(90.dp).clickable {
                                            model.selectTab(section)
                                            destination = InstrumentEditorDestination.Resonance
                                        },
                                    ) {
                                        val resonanceEnabled = state.snapshot?.values.orEmpty().firstOrNull {
                                            it.descriptor.path == "add/resonance/enabled"
                                        }?.value?.let { it >= .5 } == true
                                        ResonanceCurve(
                                            points = state.snapshot?.values.orEmpty().filter {
                                                it.descriptor.group == "ADD / Resonance points"
                                            },
                                            enabled = resonanceEnabled,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                        Surface(
                                            modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
                                            color = Color(0xCC18383E),
                                            shape = RoundedCornerShape(5.dp),
                                        ) {
                                            Text("↗", color = Color(0xFF8EF5EE), modifier = Modifier.padding(6.dp))
                                        }
                                    }
                                })
                                else -> null
                            },
                        )
                    }
                }
            }
        }
    }

    editedParameter?.let { parameter ->
        ParameterEditSheet(
            parameter = parameter,
            onDismiss = { editedParameter = null },
            onValue = { model.write(parameter, it) },
            onReset = { model.reset(parameter) },
        )
    }
    selector?.let { kind ->
        ModalBottomSheet(onDismissRequest = { selector = null }, sheetState = sheetState) {
            when (kind) {
                "voice" -> SelectorSheet("Voice", 8, state.selectedVoice) {
                    model.selectVoice(it)
                    selector = null
                }
                "actions" -> Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Instrument", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = {
                        selector = null
                        model.beginExport()
                        val file = File(context.cacheDir, "zyn-export-${System.nanoTime()}.xiz")
                        exportedRevision = state.revision
                        if (engine.exportInstrument(state.partIndex, file)) {
                            exportedFile = file
                            exportLauncher.launch("instrument-part-${state.partIndex + 1}.xiz")
                        } else model.finishExport(false, exportedRevision)
                    }) { Text("Export XIZ") }
                    OutlinedButton(onClick = { selector = "kit" }) { Text("Select kit item") }
                }
                "kit" -> SelectorSheet("Kit item", 16, state.kitIndex) {
                    model.selectKit(it)
                    selector = null
                }
                else -> ComplexEditorSheet(state.selectedTab)
            }
        }
    }
    if (state.operation is SynthEngine.OperationState.Failed) {
        AlertDialog(
            onDismissRequest = { model.open(state.partIndex, state.kitIndex) },
            title = { Text("Operation failed") },
            text = { Text((state.operation as SynthEngine.OperationState.Failed).message) },
            confirmButton = {
                TextButton(onClick = { model.open(state.partIndex, state.kitIndex) }) { Text("OK") }
            },
        )
    }
}

@Composable
private fun VoiceMatrix(
    values: List<SynthEngine.ParameterValue>,
    onWrite: (SynthEngine.ParameterValue, Double) -> Unit,
    onOpenDetail: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
            Text("No.", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(34.dp))
            Text("Vol", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text("Pan", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text("Res", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(48.dp))
            Text("Detune", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text("Vibrato", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(28.dp))
        }
        repeat(8) { voice ->
            val voiceValues = values.filter {
                it.descriptor.path.startsWith("add/voice/$voice/") &&
                    !it.descriptor.path.contains("/osc/")
            }
            fun value(suffix: String) = voiceValues.firstOrNull { it.descriptor.path.endsWith("/$suffix") }
            val enabled = value("enabled")
            val resonance = value("resonance")
            Surface(
                color = Color(0xFF10262C),
                border = BorderStroke(1.dp, Color(0xFF2E6973)),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("${voice + 1}", modifier = Modifier.width(24.dp))
                    enabled?.let { parameter ->
                        androidx.compose.material3.Checkbox(
                            checked = parameter.value >= .5,
                            onCheckedChange = { onWrite(parameter, if (it) 1.0 else 0.0) },
                            modifier = Modifier.width(34.dp),
                        )
                    }
                    VoiceMatrixKnob(value("volume"), onWrite, Modifier.weight(1f))
                    VoiceMatrixKnob(value("panning"), onWrite, Modifier.weight(1f))
                    resonance?.let { parameter ->
                        androidx.compose.material3.Checkbox(
                            checked = parameter.value >= .5,
                            onCheckedChange = { onWrite(parameter, if (it) 1.0 else 0.0) },
                            modifier = Modifier.width(48.dp),
                        )
                    } ?: Spacer(Modifier.width(48.dp))
                    VoiceMatrixKnob(value("detune"), onWrite, Modifier.weight(1f))
                    VoiceMatrixKnob(value("vibrato"), onWrite, Modifier.weight(1f))
                    Text(
                        "›",
                        color = Color(0xFF66F0E9),
                        modifier = Modifier.width(28.dp).clickable { onOpenDetail(voice) }.padding(5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceMatrixKnob(
    parameter: SynthEngine.ParameterValue?,
    onWrite: (SynthEngine.ParameterValue, Double) -> Unit,
    modifier: Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (parameter != null) {
                TinyKnob(
                label = "",
                value = parameter.value.toFloat(),
                min = parameter.descriptor.minimum.toFloat(),
                max = parameter.descriptor.maximum.toFloat(),
                onValueChange = { onWrite(parameter, it.toDouble()) },
            )
        }
    }
}

@Composable
private fun AddVoiceDetailScreen(
    model: InstrumentEditorViewModel,
    modifier: Modifier = Modifier,
    scrollState: ScrollState,
    onSectionPosition: (String, Int) -> Unit,
    onOpenOscillator: (OscillatorEditorTarget) -> Unit,
) {
    val state = model.state
    val snapshotValues = state.snapshot?.values.orEmpty()
    val values = snapshotValues.filter {
        it.descriptor.path.startsWith("add/voice/${state.selectedVoice}/") &&
            !it.descriptor.path.contains("/osc/") &&
            !it.descriptor.path.contains("/modOsc/")
    }
    val voiceRoot = "ADD / Voice ${state.selectedVoice + 1}"
    val oscillatorGroup = "ADD / Voice ${state.selectedVoice + 1} / Oscillator"
    val oscillatorMagnitudes = snapshotValues.filter {
        it.descriptor.group == "$oscillatorGroup harmonics"
    }
    val oscillatorPhases = snapshotValues.filter {
        it.descriptor.group == "$oscillatorGroup phases"
    }
    val oscillatorMagnitudeType = snapshotValues.firstOrNull {
        it.descriptor.group == oscillatorGroup &&
            it.descriptor.path.endsWith("/magnitudeType")
    }?.value?.roundToInt() ?: 0
    val externalModulator = values.firstOrNull {
        it.descriptor.path.endsWith("/externalModulator")
    }?.value?.roundToInt() ?: 0
    val externalModOscillator = values.firstOrNull {
        it.descriptor.path.endsWith("/externalModOscillator")
    }?.value?.roundToInt() ?: 0
    val modulatorOscillatorOwner = if (externalModOscillator > 0) {
        externalModOscillator - 1
    } else state.selectedVoice
    val modulatorOscillatorGroup =
        "ADD / Voice ${modulatorOscillatorOwner + 1} / Modulator oscillator"
    val modulatorMagnitudes = snapshotValues.filter {
        it.descriptor.group == "$modulatorOscillatorGroup harmonics"
    }
    val modulatorPhases = snapshotValues.filter {
        it.descriptor.group == "$modulatorOscillatorGroup phases"
    }
    val modulatorMagnitudeType = snapshotValues.firstOrNull {
        it.descriptor.group == modulatorOscillatorGroup &&
            it.descriptor.path.endsWith("/magnitudeType")
    }?.value?.roundToInt() ?: 0
    val modulatorPhase = values.firstOrNull {
        it.descriptor.path.endsWith("/modPhase")
    }?.value?.roundToInt() ?: 0
    Column(
        modifier.verticalScroll(scrollState).padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
            voiceEditorSections.forEach { section ->
                val selected = values.filter { value ->
                    val group = value.descriptor.group
                    when (section) {
                        "Voice" -> group.endsWith("/ Voice") &&
                            !value.descriptor.path.endsWith("/resonance")
                        "Oscillator" -> group.endsWith("/ Voice Oscillator") &&
                            (state.selectedVoice > 0 ||
                                !value.descriptor.path.endsWith("/externalOscillator"))
                        "Amplitude" -> group == "$voiceRoot / Amplitude" ||
                            group == "$voiceRoot / Amplitude / Envelope" ||
                            group == "$voiceRoot / Amplitude / LFO"
                        "Frequency" -> when (group) {
                            "$voiceRoot / Frequency" ->
                                value.descriptor.path.substringAfterLast('/') in setOf(
                                    "detune", "fixedFreq", "fixedFreqEt", "octave",
                                    "detuneType", "coarse"
                                )
                            "$voiceRoot / Frequency / Envelope",
                            "$voiceRoot / Frequency / LFO" -> true
                            else -> false
                        }
                        "Filter" -> group == "$voiceRoot / Filter" ||
                            group == "$voiceRoot / Filter / Envelope" ||
                            group == "$voiceRoot / Filter / LFO"
                        "Modulation" -> group.startsWith("$voiceRoot / Modulation")
                        else -> value.descriptor.path.substringAfterLast('/') in setOf(
                            "unison", "spread", "stereoSpread",
                            "vibrato", "vibratoSpeed", "unisonInvert"
                        )
                    }
                }.distinctBy { it.descriptor.path }
                if (selected.isNotEmpty()) {
                    ZynVoiceSection(
                        title = section,
                        modifier = Modifier.onGloballyPositioned {
                            onSectionPosition(section, it.positionInParent().y.roundToInt())
                        },
                    ) {
                        if (section == "Oscillator") {
                            Surface(
                                modifier = Modifier.fillMaxWidth().height(118.dp)
                                    .clickable {
                                        onOpenOscillator(
                                            OscillatorEditorTarget(
                                                OscillatorEditorKind.Voice,
                                                state.selectedVoice,
                                            )
                                        )
                                    },
                                color = Color(0xFF10262C),
                                border = BorderStroke(1.dp, Color(0xFF2E6973)),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Box(Modifier.fillMaxSize()) {
                                    OscillatorPreview(
                                        oscillatorMagnitudes,
                                        oscillatorPhases,
                                        oscillatorMagnitudeType,
                                        Modifier.fillMaxSize(),
                                    )
                                    Surface(
                                        modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
                                        color = Color(0xCC18383E),
                                        shape = RoundedCornerShape(5.dp),
                                    ) {
                                        Text(
                                            "Edit voice oscillator ↗",
                                            color = Color(0xFF8EF5EE),
                                            modifier = Modifier.padding(6.dp),
                                        )
                                    }
                                }
                            }
                        }
                        if (section == "Modulation") {
                            if (externalModulator > 0) {
                                Text(
                                    "External modulator: Voice $externalModulator · internal frequency and oscillator disabled",
                                    color = Color(0xFFFFB74D),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 3.dp),
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    if (externalModOscillator > 0) {
                                        "Oscillator shared from Voice $externalModOscillator"
                                    } else "Internal modulator oscillator",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Text(
                                    "Phase $modulatorPhase",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                            Box(
                                Modifier.fillMaxWidth().height(118.dp)
                                    .alpha(if (externalModulator > 0) .45f else 1f)
                                    .clickable(enabled = externalModulator == 0) {
                                        onOpenOscillator(
                                            OscillatorEditorTarget(
                                                OscillatorEditorKind.Modulator,
                                                modulatorOscillatorOwner,
                                            )
                                        )
                                    },
                            ) {
                                OscillatorPreview(
                                    modulatorMagnitudes,
                                    modulatorPhases,
                                    modulatorMagnitudeType,
                                    Modifier.fillMaxSize(),
                                )
                                Surface(
                                    modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
                                    color = Color(0xCC18383E),
                                    shape = RoundedCornerShape(5.dp),
                                ) {
                                    Text(
                                        if (externalModulator > 0) "Oscillator inactive" else "Edit modulator oscillator ↗",
                                        color = Color(0xFF8EF5EE),
                                        modifier = Modifier.padding(6.dp),
                                    )
                                }
                            }
                        }
                        if (section == "Unison") {
                            val spread = selected.firstOrNull { it.descriptor.path.endsWith("/spread") }
                            val size = selected.firstOrNull { it.descriptor.path.endsWith("/unison") }
                            if (spread != null) {
                                val cents = (spread.value / 127.0 * 2.0).pow(2.0) * 50.0
                                Text(
                                    if ((size?.value ?: 1.0) <= 1.0)
                                        "OFF · %.1f cents".format(cents)
                                    else
                                        "${size?.value?.roundToInt()} voices · %.1f cents".format(cents),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 3.dp),
                                )
                            }
                        }
                        selected.groupBy { it.descriptor.group }.entries
                            .sortedBy { voiceGroupOrder(section, it.key) }
                            .forEach { (group, parameters) ->
                            val subsection = group.substringAfterLast('/').trim()
                            if (subsection != section && subsection !in setOf("Voice", "Amplitude", "Frequency", "Filter", "Modulation")) {
                                Text(
                                    subsection,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(start = 3.dp, top = 5.dp),
                                )
                            }
                            DenseParameterGrid(
                                parameters = parameters.sortedBy {
                                    voiceParameterOrder(section, group, it.descriptor.path)
                                },
                                onWrite = model::write,
                                verticalLabels = true,
                                onLongPress = { model.reset(it) },
                                enabled = { parameter ->
                                    externalModulator == 0 || section != "Modulation" ||
                                        !isInternalModulatorFrequencyParameter(parameter.descriptor.path)
                                },
                                valueText = {
                                    if (it.descriptor.path.endsWith("/detune")) {
                                        "%.2f".format((it.value - 8192.0) / 8192.0 * 100.0)
                                    } else {
                                        it.value.roundToInt().toString()
                                    }
                                },
                            )
                        }
                    }
                }
            }
    }
}

private fun voiceGroupOrder(section: String, group: String): Int = when (section) {
    "Amplitude" -> when {
        group.endsWith("/ Amplitude") -> 0
        group.endsWith("/ Envelope") -> 1
        else -> 2
    }
    "Frequency" -> when {
        group.endsWith("/ Frequency") -> 0
        group.endsWith("/ Envelope") -> 1
        else -> 2
    }
    "Filter" -> when {
        group.endsWith("/ Filter") -> 0
        group.endsWith("/ Envelope") -> 1
        else -> 2
    }
    "Modulation" -> when {
        group.endsWith("/ Modulation") -> 0
        group.endsWith("/ Amplitude envelope") -> 1
        else -> 2
    }
    else -> 0
}

private fun isInternalModulatorFrequencyParameter(path: String): Boolean {
    val field = path.substringAfterLast('/')
    return path.contains("/modFreqEnvelope/") || field in setOf(
        "externalModOscillator", "modPhase", "modFixedFreq", "modDetune",
        "modOctave", "modCoarse", "modDetuneType", "modFreqEnvelopeEnabled",
    )
}

private fun voiceParameterOrder(section: String, group: String, path: String): Int {
    val field = path.substringAfterLast('/')
    val order = when {
        section == "Amplitude" && group.endsWith("/ Amplitude") -> listOf(
            "volumeMinus", "volume", "velocity", "panning"
        )
        section == "Amplitude" && group.endsWith("/ Envelope") -> listOf(
            "ampEnvelopeEnabled", "attackTime", "decayTime", "sustain",
            "releaseTime", "stretch", "forceRelease", "loop"
        )
        section == "Amplitude" && group.endsWith("/ LFO") -> listOf(
            "ampLfoEnabled", "frequency", "depth", "start", "delay", "stretch", "continuous",
            "amplitudeRandom", "frequencyRandom", "waveform"
        )
        section == "Frequency" && group.endsWith("/ Frequency") -> listOf(
            "detune", "fixedFreq", "fixedFreqEt", "octave", "detuneType", "coarse"
        )
        section == "Frequency" && group.endsWith("/ Envelope") -> listOf(
            "freqEnvelopeEnabled", "attackValue", "attackTime", "releaseTime",
            "releaseValue", "stretch", "forceRelease"
        )
        section == "Frequency" && group.endsWith("/ LFO") -> listOf(
            "freqLfoEnabled", "frequency", "depth", "start", "delay", "stretch", "continuous",
            "amplitudeRandom", "frequencyRandom", "waveform"
        )
        section == "Filter" && group.endsWith("/ Filter") -> listOf(
            "filter", "bypassGlobalFilter", "filterCategory", "filterType", "filterCutoff",
            "filterQ", "filterStages", "filterTracking", "filterGain", "filterCombHpf",
            "filterCombLpf", "filterVelocityAmount", "filterVelocity",
        )
        section == "Filter" && group.endsWith("/ Envelope") -> listOf(
            "filterEnvelopeEnabled", "attackValue", "attackTime", "decayValue", "decayTime",
            "releaseTime", "releaseValue", "stretch", "forceRelease",
        )
        section == "Filter" && group.endsWith("/ LFO") -> listOf(
            "filterLfoEnabled", "frequency", "depth", "start", "delay", "stretch", "continuous",
            "amplitudeRandom", "frequencyRandom", "waveform",
        )
        section == "Modulation" && group.endsWith("/ Modulation") -> listOf(
            "fmType", "sync", "externalModulator", "externalModOscillator", "modPhase",
            "modVolume", "modVelocity", "modDamping", "modFixedFreq", "modDetune",
            "modOctave", "modCoarse", "modDetuneType", "modAmpEnvelopeEnabled",
            "modFreqEnvelopeEnabled",
        )
        section == "Modulation" && group.endsWith("/ Amplitude envelope") -> listOf(
            "attackValue", "attackTime", "decayValue", "decayTime", "sustain",
            "releaseTime", "releaseValue", "stretch", "forceRelease",
        )
        section == "Modulation" && group.endsWith("/ Frequency envelope") -> listOf(
            "attackValue", "attackTime", "releaseTime", "releaseValue", "stretch", "forceRelease",
        )
        else -> emptyList()
    }
    val index = order.indexOf(field)
    return if (index >= 0) index else order.size
}

@Composable
private fun ZynVoiceSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title.uppercase(), color = Color(0xFF66F0E9), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(7.dp))
            Surface(
                modifier = Modifier.weight(1f),
                color = Color(0xFF234A53),
                shape = RoundedCornerShape(99.dp),
            ) { Spacer(Modifier.fillMaxWidth().height(1.dp)) }
        }
        content()
    }
}

@Composable
private fun AddOscillatorEditorScreen(
    model: InstrumentEditorViewModel,
    target: OscillatorEditorTarget,
    modifier: Modifier = Modifier,
    scrollState: ScrollState,
    onSectionPosition: (String, Int) -> Unit,
) {
    val values = model.state.snapshot?.values.orEmpty()
    val modulator = target.kind == OscillatorEditorKind.Modulator
    val group = "ADD / Voice ${target.ownerVoice + 1} / " +
        if (modulator) "Modulator oscillator" else "Oscillator"
    val controls = values.filter { it.descriptor.group == group }
    val magnitudes = values.filter { it.descriptor.group == "$group harmonics" }
    val phases = values.filter { it.descriptor.group == "$group phases" }
    var selectedHarmonic by remember(target) { mutableStateOf(0) }
    var editedParameter by remember { mutableStateOf<SynthEngine.ParameterValue?>(null) }
    val magnitude = magnitudes.getOrNull(selectedHarmonic)
    val phase = phases.getOrNull(selectedHarmonic)
    val field: (SynthEngine.ParameterValue) -> String = {
        it.descriptor.path.substringAfterLast('/')
    }
    val outputControls = controls.filter { field(it) in setOf("magnitudeType", "randomness") }
    val baseControls = controls.filter { field(it) in setOf("baseFunction", "baseShape") }
    val shapingControls = controls.filter { field(it) == "waveshape" }
    val filterControls = controls.filter { field(it) in setOf("filterType", "filter1", "filter2") }
    val positionSection: (String) -> Modifier = { section ->
        Modifier.onGloballyPositioned {
            onSectionPosition(section, it.positionInParent().y.roundToInt())
        }
    }

    BoxWithConstraints(modifier) {
        val scrollBottomPadding = (maxHeight - 180.dp).coerceAtLeast(112.dp)
        val previewTransition = (scrollState.value / 360f).coerceIn(0f, 1f)
        val expandedPreviewHeight = 210.dp
        val compactPreviewHeight = 104.dp
        val previewHeight = expandedPreviewHeight +
            (compactPreviewHeight - expandedPreviewHeight) * previewTransition
        val expandedPreviewTop = 30.dp
        val compactPreviewTop = 4.dp
        val previewTop = expandedPreviewTop +
            (compactPreviewTop - expandedPreviewTop) * previewTransition
        Column(
            Modifier.fillMaxSize().verticalScroll(scrollState).padding(vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (target.ownerVoice != model.state.selectedVoice) {
                Text(
                    "Shared from Voice ${target.ownerVoice + 1}",
                    color = Color(0xFFFFB74D),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 7.dp),
                )
            }
            ZynVoiceSection(
                "Base + output preview",
                modifier = positionSection("Preview").padding(horizontal = 7.dp),
            ) {
                Spacer(Modifier.fillMaxWidth().height(expandedPreviewHeight))
            }

            listOf(
                "Output" to outputControls,
                "Base function" to baseControls,
                "Shape & filter" to (shapingControls + filterControls),
            ).forEach { (title, parameters) ->
                if (parameters.isNotEmpty()) {
                    ZynVoiceSection(
                        title,
                        modifier = positionSection(title).padding(horizontal = 7.dp),
                    ) {
                        DenseParameterGrid(
                            parameters = parameters,
                            onWrite = model::write,
                            onLongPress = { editedParameter = it },
                        )
                    }
                }
            }

            ZynVoiceSection("Harmonics", modifier = positionSection("Harmonics")) {
                if (magnitude != null && phase != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        HarmonicKnobCard(
                            label = "Harmonic #",
                            value = (selectedHarmonic + 1).toFloat(),
                            minimum = 1f,
                            maximum = magnitudes.size.coerceAtLeast(1).toFloat(),
                            onValueChange = {
                                selectedHarmonic = it.roundToInt().minus(1)
                                    .coerceIn(magnitudes.indices)
                            },
                            modifier = Modifier.weight(1f),
                        )
                        HarmonicKnobCard(
                            label = "Magnitude",
                            value = magnitude.value.toFloat(),
                            minimum = magnitude.descriptor.minimum.toFloat(),
                            maximum = magnitude.descriptor.maximum.toFloat(),
                            onValueChange = { model.write(magnitude, it.roundToInt().toDouble()) },
                            modifier = Modifier.weight(1f),
                        )
                        HarmonicKnobCard(
                            label = "Phase",
                            value = phase.value.toFloat(),
                            minimum = phase.descriptor.minimum.toFloat(),
                            maximum = phase.descriptor.maximum.toFloat(),
                            onValueChange = { model.write(phase, it.roundToInt().toDouble()) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Text(
                    "Swipe ↔ harmonic · ↕ magnitude · all ${magnitudes.size}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                )
                HarmonicOverview(
                    magnitudes = magnitudes,
                    selected = selectedHarmonic,
                    onSelect = { selectedHarmonic = it },
                    onMagnitudeChange = { index, value ->
                        magnitudes.getOrNull(index)?.let { model.write(it, value) }
                    },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                )
                if (magnitude != null && phase != null) {
                    OutlinedButton(
                        onClick = {
                            model.reset(magnitude)
                            model.reset(phase)
                        },
                        modifier = Modifier.padding(horizontal = 7.dp),
                    ) { Text("Reset harmonic ${selectedHarmonic + 1}") }
                }
            }
            Spacer(Modifier.height(scrollBottomPadding))
        }
        OscillatorPreviewPanel(
            magnitudes = magnitudes,
            phases = phases,
            controls = controls,
            modifier = Modifier.align(Alignment.TopCenter)
                .offset(y = previewTop)
                .fillMaxWidth()
                .height(previewHeight)
                .padding(horizontal = 7.dp),
        )
    }

    editedParameter?.let { parameter ->
        ParameterEditSheet(
            parameter = parameter,
            onDismiss = { editedParameter = null },
            onValue = { model.write(parameter, it) },
            onReset = { model.reset(parameter) },
        )
    }
}

@Composable
private fun HarmonicKnobCard(
    label: String,
    value: Float,
    minimum: Float,
    maximum: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF162D33),
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.dp, Color(0xFF274B54)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TinyKnob(
                label = "",
                value = value,
                min = minimum,
                max = maximum,
                sensitivity = KnobSensitivity.Adjust,
                onValueChange = onValueChange,
            )
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun OscillatorPreviewPanel(
    magnitudes: List<SynthEngine.ParameterValue>,
    phases: List<SynthEngine.ParameterValue>,
    controls: List<SynthEngine.ParameterValue>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.Black,
        border = BorderStroke(1.dp, Color(0xFF2E6973)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            CombinedOscillatorPreview(
                magnitudes = magnitudes,
                phases = phases,
                controls = controls,
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(7.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Base", color = Color(0xFF65F55D), style = MaterialTheme.typography.labelSmall)
                Text("Output", color = Color(0xFFFF4D57), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun HarmonicOverview(
    magnitudes: List<SynthEngine.ParameterValue>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onMagnitudeChange: (Int, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val currentSelected by rememberUpdatedState(selected)
    val currentMagnitudes by rememberUpdatedState(magnitudes)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnMagnitudeChange by rememberUpdatedState(onMagnitudeChange)
    Canvas(
        modifier.onSizeChanged { size = it }.pointerInput(magnitudes.size, size) {
            var accumulatedHorizontalDrag = 0f
            var dragSelection = currentSelected
            var dragMagnitude = currentMagnitudes.getOrNull(dragSelection)?.value ?: 64.0
            detectDragGestures(
                onDragStart = {
                    accumulatedHorizontalDrag = 0f
                    dragSelection = currentSelected
                    dragMagnitude = currentMagnitudes.getOrNull(dragSelection)?.value ?: 64.0
                },
                onDrag = { change, dragAmount ->
                    if (magnitudes.isNotEmpty() && size.width > 0) {
                        change.consume()
                        accumulatedHorizontalDrag += dragAmount.x
                        val stepWidth = (size.width / 24f).coerceAtLeast(8f)
                        val steps = (accumulatedHorizontalDrag / stepWidth).toInt()
                        if (steps != 0) {
                            dragSelection = (dragSelection + steps).coerceIn(magnitudes.indices)
                            currentOnSelect(dragSelection)
                            dragMagnitude = currentMagnitudes.getOrNull(dragSelection)?.value ?: 64.0
                            accumulatedHorizontalDrag -= steps * stepWidth
                        }
                        if (size.height > 0 && dragAmount.y != 0f) {
                            val descriptor = currentMagnitudes.getOrNull(dragSelection)?.descriptor
                            val minimum = descriptor?.minimum ?: 0.0
                            val maximum = descriptor?.maximum ?: 127.0
                            dragMagnitude = (dragMagnitude - dragAmount.y / size.height * (maximum - minimum))
                                .coerceIn(minimum, maximum)
                            currentOnMagnitudeChange(dragSelection, dragMagnitude.roundToInt().toDouble())
                        }
                    }
                },
                onDragEnd = { accumulatedHorizontalDrag = 0f },
                onDragCancel = { accumulatedHorizontalDrag = 0f },
            )
        }
    ) {
        drawRect(Color(0xFF050A0C))
        val centerY = size.height / 2f
        drawLine(
            Color(0xFF31515A),
            start = androidx.compose.ui.geometry.Offset(0f, centerY),
            end = androidx.compose.ui.geometry.Offset(size.width.toFloat(), centerY),
            strokeWidth = 1f,
        )
        if (magnitudes.isNotEmpty()) {
            val slot = size.width / magnitudes.size
            magnitudes.forEachIndexed { index, harmonic ->
                val normalized = ((harmonic.value - 64.0) / 63.0).coerceIn(-1.0, 1.0).toFloat()
                val x = slot * (index + .5f)
                val y = centerY - normalized * centerY * .88f
                drawLine(
                    color = Color(0xFF23434B),
                    start = androidx.compose.ui.geometry.Offset(x, centerY * .12f),
                    end = androidx.compose.ui.geometry.Offset(x, centerY * 1.88f),
                    strokeWidth = maxOf(1f, slot * .16f),
                )
                drawLine(
                    color = if (index == selected) Color(0xFFFFC857) else Color(0xFF36C5C9),
                    start = androidx.compose.ui.geometry.Offset(x, centerY),
                    end = androidx.compose.ui.geometry.Offset(x, y),
                    strokeWidth = if (index == selected) maxOf(3f, slot * .8f) else maxOf(1f, slot * .55f),
                )
                drawCircle(
                    color = if (index == selected) Color(0xFFFFC857) else Color(0xFF36C5C9),
                    radius = if (index == selected) maxOf(2.5f, slot * .42f) else maxOf(1.2f, slot * .2f),
                    center = androidx.compose.ui.geometry.Offset(x, y),
                )
            }
        }
    }
}

@Composable
private fun AddResonanceEditorScreen(
    model: InstrumentEditorViewModel,
    modifier: Modifier = Modifier,
    scrollState: ScrollState,
    onSectionPosition: (String, Int) -> Unit,
) {
    val values = model.state.snapshot?.values.orEmpty()
    val controls = values.filter { it.descriptor.group == "ADD / Resonance" }
    val points = values.filter { it.descriptor.group == "ADD / Resonance points" }
    val enabled = controls.firstOrNull { it.descriptor.path.endsWith("/enabled") }
    val maxDb = controls.firstOrNull { it.descriptor.path.endsWith("/maxDb") }
    val center = controls.firstOrNull { it.descriptor.path.endsWith("/center") }
    val octaves = controls.firstOrNull { it.descriptor.path.endsWith("/octaves") }
    val protect = controls.firstOrNull { it.descriptor.path.endsWith("/protectFundamental") }
    var graphSize by remember { mutableStateOf(IntSize.Zero) }

    Column(
        modifier.verticalScroll(scrollState).padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
            ResonanceCurve(
                points = points,
                enabled = enabled?.value?.let { it >= .5 } == true,
                modifier = Modifier.fillMaxWidth().height(260.dp)
                    .onGloballyPositioned {
                        onSectionPosition("Curve", it.positionInParent().y.roundToInt())
                    }
                    .onSizeChanged { graphSize = it }.pointerInput(graphSize) {
                    fun write(positionX: Float, positionY: Float) {
                        if (graphSize.width <= 0 || graphSize.height <= 0 || points.isEmpty()) return
                        val index = ((positionX / graphSize.width) * (points.size - 1))
                            .roundToInt().coerceIn(points.indices)
                        val value = (127f * (1f - positionY / graphSize.height))
                            .roundToInt().coerceIn(0, 127)
                        model.writePath("add/resonance/point/$index", value.toDouble())
                    }
                    detectDragGestures(
                        onDragStart = { write(it.x, it.y) },
                        onDrag = { change, _ ->
                            write(change.position.x, change.position.y)
                            change.consume()
                        },
                    )
                },
            )
            val curveActions = listOf(
                "Zero" to "add/resonance/zero",
                "Smooth" to "add/resonance/smooth",
                "Interp." to "add/resonance/interpolateSmooth",
                "Linear" to "add/resonance/interpolateLinear",
                "R1" to "add/resonance/random/0",
                "R2" to "add/resonance/random/1",
                "R3" to "add/resonance/random/2",
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                curveActions.forEach { (label, path) ->
                    OutlinedButton(
                        onClick = { model.writePath(path, 1.0) },
                        modifier = Modifier.weight(1f),
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFFF4D57),
                        ),
                        border = BorderStroke(1.dp, Color(0xFFFF4D57)),
                    ) {
                        Text(label, fontSize = 9.sp, maxLines = 1)
                    }
                }
            }
            val resonanceControls = listOfNotNull(enabled, maxDb, center, octaves, protect)
            Box(
                Modifier.fillMaxWidth().onGloballyPositioned {
                    onSectionPosition("Parameters", it.positionInParent().y.roundToInt())
                },
            ) {
                DenseParameterGrid(
                    parameters = resonanceControls,
                    onWrite = model::write,
                    verticalLabels = true,
                    onLongPress = { model.reset(it) },
                    valueText = { parameter ->
                        when {
                            parameter.descriptor.path.endsWith("/maxDb") ->
                                parameter.value.roundToInt().toString()
                            parameter.descriptor.path.endsWith("/center") -> {
                                val hz = 10000.0 * 10.0.pow(-(1.0 - parameter.value / 127.0) * 2.0)
                                if (hz >= 1000) "%.1fk".format(hz / 1000) else "%.0f".format(hz)
                            }
                            parameter.descriptor.path.endsWith("/octaves") ->
                                "%.1f".format(0.25 + 10.0 * parameter.value / 127.0)
                            else -> parameter.value.roundToInt().toString()
                        }
                    },
                )
            }
    }
}

@Composable
private fun ResonanceCurve(
    points: List<SynthEngine.ParameterValue>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val gridColor = if (enabled) Color(0xFF31515A) else Color(0xFF30383B)
        val curveColor = if (enabled) Color(0xFFFF4D57) else Color(0xFF747B7E)
        drawRect(Color(0xFF050A0C))
        repeat(9) { line ->
            val x = size.width * line / 8f
            drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(x, 0f),
                end = androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = 1f)
        }
        repeat(5) { line ->
            val y = size.height * line / 4f
            drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1f)
        }
        if (points.isNotEmpty()) {
            val path = Path()
            points.forEachIndexed { index, point ->
                val x = size.width * index / (points.size - 1).coerceAtLeast(1)
                val y = size.height * (1f - point.value.toFloat() / 127f)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, curveColor, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5f))
        }
    }
}

@Composable
private fun VoiceMiniWave(seed: Int, modifier: Modifier) {
    Canvas(modifier) {
        val path = Path()
        repeat(32) { x ->
            val px = size.width * x / 31f
            val py = size.height * (.5f + .35f * kotlin.math.sin((x + seed * 3) * .55f))
            if (x == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, Color(0xFF65F55D), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
    }
}

@Composable
private fun OscillatorPreview(
    magnitudes: List<SynthEngine.ParameterValue>,
    phases: List<SynthEngine.ParameterValue>,
    magnitudeType: Int,
    modifier: Modifier,
) {
    Canvas(modifier) {
        val gridColor = Color(0xFF31515A)
        val curveColor = Color(0xFFFF4D57)
        drawRect(Color(0xFF050A0C))
        repeat(9) { line ->
            val x = size.width * line / 8f
            drawLine(
                gridColor,
                start = androidx.compose.ui.geometry.Offset(x, 0f),
                end = androidx.compose.ui.geometry.Offset(x, size.height),
                strokeWidth = 1f,
            )
        }
        repeat(5) { line ->
            val y = size.height * line / 4f
            drawLine(
                gridColor,
                start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(size.width, y),
                strokeWidth = 1f,
            )
        }
        val samples = DoubleArray(256)
        var peak = 0.0
        samples.indices.forEach { x ->
            val angle = x.toDouble() / (samples.size - 1) * Math.PI * 2.0
            var sample = 0.0
            magnitudes.forEachIndexed { harmonic, value ->
                val rawMagnitude = value.value
                if (rawMagnitude == 64.0) return@forEachIndexed
                val normalizedDistance = 1.0 - kotlin.math.abs(rawMagnitude / 64.0 - 1.0)
                val amplitude = when (magnitudeType) {
                    1 -> kotlin.math.exp(normalizedDistance * kotlin.math.ln(0.01))
                    2 -> kotlin.math.exp(normalizedDistance * kotlin.math.ln(0.001))
                    3 -> kotlin.math.exp(normalizedDistance * kotlin.math.ln(0.0001))
                    4 -> kotlin.math.exp(normalizedDistance * kotlin.math.ln(0.00001))
                    else -> 1.0 - normalizedDistance
                } * if (rawMagnitude < 64.0) -1.0 else 1.0
                val phase = ((phases.getOrNull(harmonic)?.value ?: 64.0) - 64.0) /
                    64.0 * Math.PI
                sample += amplitude * kotlin.math.sin((harmonic + 1) * angle + phase)
            }
            samples[x] = sample
            peak = maxOf(peak, kotlin.math.abs(sample))
        }
        val path = Path()
        samples.forEachIndexed { x, sample ->
            val px = size.width * x / (samples.size - 1)
            val normalized = if (peak > 0.0) sample / peak else 0.0
            val py = size.height * (.5f - normalized.toFloat() * .42f)
            if (x == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, curveColor, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5f))
    }
}

@Composable
private fun CombinedOscillatorPreview(
    magnitudes: List<SynthEngine.ParameterValue>,
    phases: List<SynthEngine.ParameterValue>,
    controls: List<SynthEngine.ParameterValue>,
    modifier: Modifier,
) {
    val control: (String, Double) -> Double = { field, fallback ->
        controls.firstOrNull { it.descriptor.path.substringAfterLast('/') == field }?.value ?: fallback
    }
    val baseFunction = control("baseFunction", 0.0).roundToInt()
    val baseShape = control("baseShape", 64.0)
    val magnitudeType = control("magnitudeType", 0.0).roundToInt()
    val waveshape = control("waveshape", 64.0)
    val filterType = control("filterType", 0.0).roundToInt()
    val filter1 = control("filter1", 64.0)
    val filter2 = control("filter2", 64.0)
    Canvas(modifier) {
        drawRect(Color(0xFF050A0C))
        repeat(9) { line ->
            val x = size.width * line / 8f
            drawLine(
                Color(0xFF31515A),
                start = androidx.compose.ui.geometry.Offset(x, 0f),
                end = androidx.compose.ui.geometry.Offset(x, size.height),
                strokeWidth = 1f,
            )
        }
        repeat(5) { line ->
            val y = size.height * line / 4f
            drawLine(
                Color(0xFF31515A),
                start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(size.width, y),
                strokeWidth = 1f,
            )
        }
        val sampleCount = 256
        val baseSamples = DoubleArray(sampleCount) { index ->
            oscillatorBaseSample(
                type = baseFunction,
                shape = baseShape,
                phase = index.toDouble() / (sampleCount - 1),
            )
        }
        val outputSamples = DoubleArray(sampleCount)
        outputSamples.indices.forEach { index ->
            val cycle = index.toDouble() / (sampleCount - 1)
            var sample = 0.0
            magnitudes.forEachIndexed { harmonic, parameter ->
                val amplitude = harmonicAmplitude(parameter.value, magnitudeType)
                if (amplitude == 0.0) return@forEachIndexed
                val phase = ((phases.getOrNull(harmonic)?.value ?: 64.0) - 64.0) / 64.0
                sample += amplitude * oscillatorFilterGain(
                    type = filterType,
                    harmonic = harmonic,
                    harmonicCount = magnitudes.size,
                    parameter1 = filter1,
                    parameter2 = filter2,
                ) * oscillatorBaseSample(
                    type = baseFunction,
                    shape = baseShape,
                    phase = (harmonic + 1) * cycle + phase,
                )
            }
            outputSamples[index] = sample
        }
        val peak = outputSamples.maxOfOrNull { kotlin.math.abs(it) }?.coerceAtLeast(.00001) ?: 1.0
        outputSamples.indices.forEach { index ->
            val normalized = outputSamples[index] / peak
            outputSamples[index] = when {
                waveshape > 64.0 -> {
                    val drive = 1.0 + (waveshape - 64.0) / 10.0
                    kotlin.math.tanh(normalized * drive) / kotlin.math.tanh(drive)
                }
                waveshape < 64.0 -> kotlin.math.sign(normalized) *
                    kotlin.math.abs(normalized).pow(1.0 + (64.0 - waveshape) / 32.0)
                else -> normalized
            }
        }
        fun drawSamples(samples: DoubleArray, color: Color, strokeWidth: Float) {
            val path = Path()
            samples.forEachIndexed { index, sample ->
                val px = size.width * index / (samples.size - 1)
                val py = size.height * (.5f - sample.coerceIn(-1.0, 1.0).toFloat() * .42f)
                if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth))
        }
        drawSamples(baseSamples, Color(0xFF65F55D), 5f)
        drawSamples(outputSamples, Color(0xFFFF4D57), 2.5f)
    }
}

private fun harmonicAmplitude(rawMagnitude: Double, magnitudeType: Int): Double {
    if (rawMagnitude == 64.0) return 0.0
    val normalizedDistance = 1.0 - kotlin.math.abs(rawMagnitude / 64.0 - 1.0)
    val amplitude = when (magnitudeType) {
        1 -> kotlin.math.exp(normalizedDistance * kotlin.math.ln(0.01))
        2 -> kotlin.math.exp(normalizedDistance * kotlin.math.ln(0.001))
        3 -> kotlin.math.exp(normalizedDistance * kotlin.math.ln(0.0001))
        4 -> kotlin.math.exp(normalizedDistance * kotlin.math.ln(0.00001))
        else -> 1.0 - normalizedDistance
    }
    return amplitude * if (rawMagnitude < 64.0) -1.0 else 1.0
}

private fun oscillatorBaseSample(type: Int, shape: Double, phase: Double): Double {
    val cycle = phase - kotlin.math.floor(phase)
    val amount = ((shape + .5) / 128.0).coerceIn(.01, .99)
    val sine = kotlin.math.sin(cycle * Math.PI * 2.0)
    return when (type) {
        1 -> 1.0 - 4.0 * kotlin.math.abs(cycle - .5)
        2 -> if (cycle < amount) 1.0 else -1.0
        3 -> cycle * 2.0 - 1.0
        4 -> kotlin.math.sign(sine) * kotlin.math.abs(sine).pow(.15 + amount * 3.5)
        5 -> 2.0 * kotlin.math.exp(-(((cycle - .5) / (.08 + amount * .32)).pow(2.0))) - 1.0
        6 -> (sine + (amount * 2.0 - 1.0)).coerceAtLeast(0.0) * 2.0 - 1.0
        7 -> kotlin.math.abs(sine) * 2.0 - 1.0
        8 -> sine * if (cycle < amount) 1.0 else -1.0
        9 -> kotlin.math.sin(cycle.pow(.35 + amount * 2.5) * Math.PI * 2.0)
        10 -> kotlin.math.sin((cycle + cycle * cycle * amount * 5.0) * Math.PI * 2.0)
        11 -> kotlin.math.abs(kotlin.math.sin(cycle.pow(.35 + amount * 2.5) * Math.PI * 2.0)) * 2.0 - 1.0
        12 -> kotlin.math.cos((1 + (amount * 10).roundToInt()) * kotlin.math.acos(sine.coerceIn(-1.0, 1.0)))
        13 -> if (sine >= 0.0) 1.0 else -1.0
        14 -> kotlin.math.sign(sine) * kotlin.math.exp(-(1.0 - kotlin.math.abs(sine)) * (4.0 + amount * 24.0))
        15 -> {
            val x = cycle * 2.0 - 1.0
            kotlin.math.sign(sine) * kotlin.math.sqrt((1.0 - x * x).coerceAtLeast(0.0))
        }
        else -> sine
    }
}

private fun oscillatorFilterGain(
    type: Int,
    harmonic: Int,
    harmonicCount: Int,
    parameter1: Double,
    parameter2: Double,
): Double {
    if (type == 0) return 1.0
    val frequency = (harmonic + 1).toDouble() / harmonicCount.coerceAtLeast(1)
    val center = (1.0 - parameter1 / 127.0).coerceIn(.01, .99)
    val width = (.03 + parameter2 / 127.0 * .45).coerceAtMost(.5)
    val lowPass = 1.0 / (1.0 + (frequency / center).pow(4.0))
    val highPass = 1.0 - lowPass
    val bandPass = kotlin.math.exp(-(((frequency - center) / width).pow(2.0)))
    return when (type) {
        1 -> kotlin.math.sqrt(lowPass)
        2, 13 -> lowPass
        3 -> kotlin.math.sqrt(highPass)
        4 -> highPass
        5 -> kotlin.math.sqrt(bandPass)
        6 -> bandPass
        7 -> kotlin.math.abs(kotlin.math.cos(frequency * Math.PI * (1.0 + parameter2 / 16.0)))
        8 -> kotlin.math.abs(kotlin.math.sin(frequency * Math.PI * (1.0 + parameter2 / 16.0)))
        9 -> .35 + .65 * lowPass
        10 -> .2 + .8 / (1.0 + kotlin.math.exp((frequency - center) / width * 8.0))
        11 -> .25 + .75 * kotlin.math.abs(kotlin.math.cos(frequency * Math.PI / width))
        12 -> .15 + .85 * bandPass
        else -> 1.0
    }
}

private fun populatedSections(
    all: List<SynthEngine.ParameterValue>,
    module: String,
): List<String> {
    val declared = InstrumentEditorViewModel.tabsFor(module)
    return declared.filter { section ->
        parametersFor(all, module, section, 0).isNotEmpty() ||
            section in setOf("Resonance", "Spectrum") ||
            (module == "FX" && section != "Routing")
    }
}

private fun activeAnchor(scroll: Int, sections: List<String>, offsets: Map<String, Int>): String =
    sections.lastOrNull { (offsets[it] ?: Int.MAX_VALUE) <= scroll + 12 }
        ?: sections.firstOrNull().orEmpty()

@Composable
private fun ZynEditorSection(
    title: String,
    parameters: List<SynthEngine.ParameterValue>,
    modifier: Modifier = Modifier,
    complex: Boolean,
    onComplex: () -> Unit,
    onWrite: (SynthEngine.ParameterValue, Double) -> Unit,
    onEdit: (SynthEngine.ParameterValue) -> Unit,
    verticalLabels: Boolean = false,
    leadingContent: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title.uppercase(),
                color = Color(0xFF66F0E9),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(vertical = 2.dp),
            )
            Spacer(Modifier.width(7.dp))
            Surface(
                modifier = Modifier.weight(1f),
                color = Color(0xFF234A53),
                shape = RoundedCornerShape(99.dp),
            ) { Spacer(Modifier.fillMaxWidth().height(1.dp)) }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
            leadingContent?.invoke()
            if (complex) ComplexEditorLauncher(title, onComplex)
            if (parameters.isNotEmpty()) {
                if (verticalLabels) {
                    parameters.groupBy { it.descriptor.group }.forEach { (group, groupedParameters) ->
                        Text(
                            addSubsectionLabel(group),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 4.dp, top = 5.dp, bottom = 2.dp),
                        )
                        DenseParameterGrid(
                            groupedParameters,
                            onWrite,
                            verticalLabels = true,
                            onLongPress = onEdit,
                        )
                    }
                } else {
                    DenseParameterGrid(
                        parameters,
                        onWrite,
                        verticalLabels = false,
                        onLongPress = onEdit,
                    )
                }
            }
        }
    }
}

private fun addSubsectionLabel(group: String): String = when {
    group.endsWith("/ Global") -> "Global parameters"
    group.endsWith("/ Punch") -> "Punch"
    group.endsWith("/ Envelope") -> when {
        group.contains("Amplitude") -> "Amplitude Envelope"
        group.contains("Frequency") -> "Frequency Envelope"
        else -> "Filter Envelope"
    }
    group.endsWith("/ LFO") -> when {
        group.contains("Amplitude") -> "Amplitude LFO"
        group.contains("Frequency") -> "Frequency LFO"
        else -> "Filter LFO"
    }
    group.endsWith("/ Parameters") -> "Filter Parameters"
    else -> group.substringAfterLast('/')
}

private fun parametersFor(
    all: List<SynthEngine.ParameterValue>,
    module: String,
    tab: String,
    voice: Int,
): List<SynthEngine.ParameterValue> = all.filter { parameter ->
    val group = parameter.descriptor.group
    if (!group.startsWith(module, ignoreCase = true)) return@filter false
    if (module == "ADD" && parameter.descriptor.path == "add/stereo") return@filter false
    when (tab) {
        "Global" -> group.endsWith("Global") || !group.contains("/")
        "Amp" -> group.contains("Amplitude")
        "Frequency" -> group.contains("Frequency")
        "Filter" -> group.contains("Filter")
        "Voices" -> group.endsWith("Voice ${voice + 1}")
        "Harmonics" -> group.contains("Harmonic")
        "Profile" -> group.contains("Profile")
        "Quality" -> group.contains("Quality")
        "Routing" -> group.contains("Routing")
        else -> false
    }
}

private fun emptyMessage(module: String, tab: String): String = when {
    module == "FX" -> "FX $tab is the next native parameter family to connect."
    tab in setOf("Resonance", "Spectrum") -> "Open the terminal editor above."
    else -> "No $tab parameters are available for this kit item."
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DenseParameterGrid(
    parameters: List<SynthEngine.ParameterValue>,
    onWrite: (SynthEngine.ParameterValue, Double) -> Unit,
    verticalLabels: Boolean = false,
    valueText: (SynthEngine.ParameterValue) -> String = { it.value.roundToInt().toString() },
    enabled: (SynthEngine.ParameterValue) -> Boolean = { true },
    onLongPress: (SynthEngine.ParameterValue) -> Unit,
) {
    BoxWithConstraints {
        val columns = when {
            verticalLabels -> 4
            maxWidth >= 900.dp -> 6
            maxWidth >= 650.dp -> 5
            maxWidth >= 420.dp -> 4
            else -> 3
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            parameters.chunked(columns).forEach { rowParameters ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    rowParameters.forEach { parameter ->
                        Box(Modifier.weight(1f)) {
                            DenseParameterControl(
                                parameter,
                                onWrite,
                                onLongPress,
                                verticalLabels,
                                valueText(parameter),
                                enabled(parameter),
                            )
                        }
                    }
                    repeat(columns - rowParameters.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DenseParameterControl(
    parameter: SynthEngine.ParameterValue,
    onWrite: (SynthEngine.ParameterValue, Double) -> Unit,
    onLongPress: (SynthEngine.ParameterValue) -> Unit,
    verticalLabel: Boolean = false,
    valueText: String = parameter.value.roundToInt().toString(),
    enabled: Boolean = true,
) {
    val descriptor = parameter.descriptor
    Surface(
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else .45f).combinedClickable(
            enabled = enabled,
            onClick = {
                if (descriptor.type == SynthEngine.ParameterType.BOOLEAN) {
                    onWrite(parameter, if (parameter.value >= .5) 0.0 else 1.0)
                }
            },
            onLongClick = {
                if (!verticalLabel || descriptor.type != SynthEngine.ParameterType.ENUM) {
                    onLongPress(parameter)
                }
            },
        ),
        color = Color(0xFF162D33),
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.dp, Color(0xFF274B54)),
    ) {
        Box(Modifier.fillMaxWidth().height(if (verticalLabel) 78.dp else 86.dp)) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                if (verticalLabel) {
                    Surface(
                        modifier = Modifier.width(28.dp).fillMaxHeight(),
                        color = addLabelColor(descriptor),
                        shape = RoundedCornerShape(topStart = 7.dp, bottomStart = 7.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                compactAddLabel(descriptor),
                                modifier = Modifier.rotate(-90f).requiredWidth(72.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Column(
                    Modifier.weight(1f).padding(horizontal = 3.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                when (descriptor.type) {
                SynthEngine.ParameterType.BOOLEAN -> Switch(
                    checked = parameter.value >= .5,
                    onCheckedChange = { if (enabled) onWrite(parameter, if (it) 1.0 else 0.0) },
                    enabled = enabled,
                )
                SynthEngine.ParameterType.ENUM -> if (verticalLabel) {
                    TinyKnob(
                        label = "",
                        value = parameter.value.toFloat(),
                        min = descriptor.minimum.toFloat(),
                        max = descriptor.maximum.toFloat(),
                        sensitivity = KnobSensitivity.Adjust,
                        onValueChange = {
                            if (enabled) onWrite(parameter, it.roundToInt().toDouble())
                        },
                    )
                    Text(
                        descriptor.options.getOrNull(parameter.value.roundToInt())
                            ?: parameter.value.roundToInt().toString(),
                        fontSize = 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    CompactSelector(
                        label = "",
                        value = descriptor.options.getOrNull(parameter.value.roundToInt())
                            ?: parameter.value.roundToInt().toString(),
                        onClick = { if (enabled) onLongPress(parameter) },
                    )
                }
                SynthEngine.ParameterType.INTEGER -> TinyKnob(
                    label = "",
                    value = parameter.value.toFloat(),
                    min = descriptor.minimum.toFloat(),
                    max = descriptor.maximum.toFloat(),
                    sensitivity = if (descriptor.path.endsWith("/oscillatorPhase")) {
                        KnobSensitivity.Adjust
                    } else {
                        KnobSensitivity.Default
                    },
                    valueText = valueText,
                    onValueChange = {
                        if (enabled) onWrite(parameter, it.roundToInt().toDouble())
                    },
                )
                }
                    if (!verticalLabel) {
                        Text(
                            descriptor.label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                        )
                    }
                }
            }
            val unit = when {
                descriptor.path.endsWith("/maxDb") -> "dB"
                descriptor.path.endsWith("/center") -> "Hz"
                else -> ""
            }
            if (unit.isNotEmpty()) {
                Text(
                    unit,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 5.dp, bottom = 3.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 8.sp,
                )
            }
        }
    }
}

private fun addLabelColor(descriptor: SynthEngine.ParameterDescriptor): Color {
    val hue = when {
        descriptor.group.endsWith("/ Amplitude envelope") -> 205f
        descriptor.group.endsWith("/ Frequency envelope") -> 165f
        descriptor.group.endsWith("/ Envelope") && descriptor.group.contains("/ Filter") -> 325f
        descriptor.group.endsWith("/ LFO") && descriptor.group.contains("/ Amplitude") -> 270f
        descriptor.group.endsWith("/ LFO") && descriptor.group.contains("/ Frequency") -> 235f
        descriptor.group.endsWith("/ LFO") && descriptor.group.contains("/ Filter") -> 290f
        descriptor.group.endsWith("/ Modulation") -> 286f
        descriptor.group.endsWith("/ Voice Oscillator") -> 32f
        descriptor.group.endsWith("/ Filter") -> 48f
        descriptor.group.endsWith("/ Frequency") -> 140f
        descriptor.group.endsWith("/ Amplitude") -> 205f
        descriptor.group.endsWith("/ Voice") -> 188f
        descriptor.path.startsWith("add/punch") -> 24f
        descriptor.path.startsWith("add/ampEnvelope") -> 205f
        descriptor.path.startsWith("add/ampLfo") -> 270f
        descriptor.path.startsWith("add/freqEnvelope") -> 165f
        descriptor.path.startsWith("add/freqLfo") -> 235f
        descriptor.path.startsWith("add/filterEnvelope") -> 325f
        descriptor.path.startsWith("add/filterLfo") -> 290f
        descriptor.path.startsWith("add/filter/") ||
            descriptor.path.startsWith("add/filterVelocity") -> 48f
        descriptor.path in setOf("add/detune", "add/coarse", "add/octave", "add/detuneType") -> 140f
        else -> 188f
    }
    return Color.hsv(hue, saturation = .52f, value = .22f)
}

private fun compactAddLabel(descriptor: SynthEngine.ParameterDescriptor): String = when {
    descriptor.path == "add/stereo" -> "Stereo"
    descriptor.path == "add/volume" -> "Volume"
    descriptor.path == "add/panning" -> "Panning"
    descriptor.path == "add/velocity" -> "Velocity"
    descriptor.path == "add/punchStrength" -> "Punch Str."
    descriptor.path == "add/punchTime" -> "Punch Time"
    descriptor.path == "add/punchStretch" -> "Punch Stret."
    descriptor.path == "add/punchVelocity" -> "Punch Vel."
    descriptor.path == "add/octave" -> "Octave"
    descriptor.path == "add/coarse" -> "Coarse Det."
    descriptor.path.endsWith("/attackValue") -> "Attack Val."
    descriptor.path.endsWith("/attackTime") -> "Attack Time"
    descriptor.path.endsWith("/decayValue") -> "Decay Val."
    descriptor.path.endsWith("/decayTime") -> "Decay Time"
    descriptor.path.endsWith("/sustain") -> "Sustain"
    descriptor.path.endsWith("/releaseTime") -> "Release Time"
    descriptor.path.endsWith("/releaseValue") -> "Release Val."
    descriptor.path.endsWith("/stretch") -> "Stretch"
    descriptor.path.endsWith("/loop") -> "Loop"
    descriptor.path.endsWith("/forceRelease") -> "Force Rel."
    descriptor.path.endsWith("/amplitudeRandom") -> "A.R."
    descriptor.path.endsWith("/frequencyRandom") -> "F.R."
    descriptor.path.endsWith("/frequency") -> "Frequency"
    descriptor.path.endsWith("/depth") -> "Depth"
    descriptor.path.endsWith("/start") -> "Start"
    descriptor.path.endsWith("/delay") -> "Delay"
    descriptor.path.endsWith("/random") -> "Random"
    descriptor.path.endsWith("/continuous") -> "Continuous"
    descriptor.path.endsWith("/waveform") -> "Waveform"
    descriptor.path.endsWith("/type") -> "Type"
    else -> descriptor.label
}

@Composable
private fun CompactSelector(label: String, value: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.combinedClickable(onClick = onClick),
        color = Color(0xFF1A353D),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, Color(0xFF2E5F68)),
    ) {
        Text("$label $value ▾".trim(), modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
    }
}

@Composable
private fun ComplexEditorLauncher(name: String, onClick: () -> Unit) {
    LuminousActionButton(
        label = "Open $name editor",
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ParameterEditSheet(
    parameter: SynthEngine.ParameterValue,
    onDismiss: () -> Unit,
    onValue: (Double) -> Unit,
    onReset: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(parameter.descriptor.label, style = MaterialTheme.typography.titleMedium)
            if (parameter.descriptor.type == SynthEngine.ParameterType.ENUM) {
                val selected = parameter.value.roundToInt()
                Text(
                    parameter.descriptor.options.getOrNull(selected) ?: "Option ${selected + 1}",
                    color = Color(0xFF8EF5EE),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    parameter.descriptor.options.forEachIndexed { index, option ->
                        if (index == selected) {
                            Button(onClick = {
                                onValue(index.toDouble())
                                onDismiss()
                            }) { Text(option) }
                        } else {
                            OutlinedButton(onClick = {
                                onValue(index.toDouble())
                                onDismiss()
                            }) { Text(option) }
                        }
                    }
                }
                OutlinedButton(onClick = {
                    onReset()
                    onDismiss()
                }) { Text("Reset") }
            } else {
                Text("Current: ${parameter.value.roundToInt()}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onValue(parameter.value - 1) }) { Text("−") }
                    OutlinedButton(onClick = { onValue(parameter.value + 1) }) { Text("+") }
                    Button(onClick = {
                        onReset()
                        onDismiss()
                    }) { Text("Reset") }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SelectorSheet(title: String, count: Int, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(count) { index ->
                if (index == selected) Button(onClick = { onSelect(index) }) { Text("${index + 1}") }
                else OutlinedButton(onClick = { onSelect(index) }) { Text("${index + 1}") }
            }
        }
    }
}

@Composable
private fun ComplexEditorSheet(name: String) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(name, style = MaterialTheme.typography.titleLarge)
        Text(
            "This terminal editor owns its complex visual control and does not create another navigation level.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(1.dp))
    }
}

@Composable
private fun KeyboardOverlay(
    heldNotes: Set<Int>,
    keyboardOctaveShift: Int,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(60, 62, 64, 65, 67, 69, 71).forEach { note ->
            val effective = (note + keyboardOctaveShift * 12).coerceIn(0, 127)
            TactileKey(
                note = note,
                labelNote = effective,
                active = effective in heldNotes,
                onPress = { onPress(note) },
                onRelease = { onRelease(note) },
            )
        }
    }
}

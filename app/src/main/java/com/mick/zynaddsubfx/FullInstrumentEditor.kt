@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.mick.zynaddsubfx

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.pow
import kotlinx.coroutines.launch

private enum class OscillatorEditorKind { Voice, Modulator }
private val LocalEnumContext = compositionLocalOf<Any?> { null }

private data class OscillatorEditorTarget(
    val kind: OscillatorEditorKind,
    val ownerVoice: Int,
)

private sealed interface InstrumentEditorDestination {
    data object VoiceDetail : InstrumentEditorDestination
    data object Resonance : InstrumentEditorDestination
    data class Oscillator(val target: OscillatorEditorTarget) : InstrumentEditorDestination
    data class Envelope(val address: ModuleAddress.Envelope) : InstrumentEditorDestination
    data class Lfo(val address: ModuleAddress.Lfo) : InstrumentEditorDestination
    data class Filter(val address: ModuleAddress.Filter) : InstrumentEditorDestination
}

private fun InstrumentEditorDestination.encode(): String = when (this) {
    InstrumentEditorDestination.VoiceDetail -> "voice"
    InstrumentEditorDestination.Resonance -> "resonance"
    is InstrumentEditorDestination.Oscillator ->
        "osc:${target.kind.name}:${target.ownerVoice}"
    is InstrumentEditorDestination.Envelope ->
        "env:${address.index}:${address.envelopeRole.name}"
    is InstrumentEditorDestination.Lfo -> "lfo:${address.index}:${address.lfoRole.name}"
    is InstrumentEditorDestination.Filter -> "filter:${address.index}:${address.vowel}"
}

private fun decodeDestination(value: String): InstrumentEditorDestination? {
    val fields = value.split(':')
    return when (fields.firstOrNull()) {
        "voice" -> InstrumentEditorDestination.VoiceDetail
        "resonance" -> InstrumentEditorDestination.Resonance
        "osc" -> InstrumentEditorDestination.Oscillator(
            OscillatorEditorTarget(
                runCatching { OscillatorEditorKind.valueOf(fields[1]) }.getOrDefault(OscillatorEditorKind.Voice),
                fields.getOrNull(2)?.toIntOrNull()?.coerceIn(0, 7) ?: 0,
            )
        )
        "env" -> InstrumentEditorDestination.Envelope(
            ModuleAddress.Envelope(
                fields.getOrNull(1)?.toIntOrNull()?.coerceIn(-1, 7) ?: -1,
                runCatching { EnvelopeRole.valueOf(fields[2]) }.getOrDefault(EnvelopeRole.AMPLITUDE),
            )
        )
        "lfo" -> InstrumentEditorDestination.Lfo(
            ModuleAddress.Lfo(
                fields.getOrNull(1)?.toIntOrNull()?.coerceIn(-1, 7) ?: -1,
                runCatching { LfoRole.valueOf(fields[2]) }.getOrDefault(LfoRole.AMPLITUDE),
            )
        )
        "filter" -> InstrumentEditorDestination.Filter(
            ModuleAddress.Filter(
                fields.getOrNull(1)?.toIntOrNull()?.coerceIn(-1, 7) ?: -1,
                fields.getOrNull(2)?.toIntOrNull()?.coerceIn(0, FORMANT_VOWEL_SLOTS - 1) ?: 0,
            )
        )
        "formant" -> InstrumentEditorDestination.Filter(
            ModuleAddress.Filter(
                fields.getOrNull(1)?.toIntOrNull()?.coerceIn(-1, 7) ?: -1,
                fields.getOrNull(2)?.toIntOrNull()?.coerceIn(0, FORMANT_VOWEL_SLOTS - 1) ?: 0,
            )
        )
        else -> null
    }
}

private fun parentDestination(destination: InstrumentEditorDestination): InstrumentEditorDestination? = when (destination) {
    InstrumentEditorDestination.VoiceDetail, InstrumentEditorDestination.Resonance -> null
    is InstrumentEditorDestination.Oscillator -> InstrumentEditorDestination.VoiceDetail
    is InstrumentEditorDestination.Envelope ->
        if (destination.address.index >= 0) InstrumentEditorDestination.VoiceDetail else null
    is InstrumentEditorDestination.Lfo ->
        if (destination.address.index >= 0) InstrumentEditorDestination.VoiceDetail else null
    is InstrumentEditorDestination.Filter ->
        if (destination.address.index >= 0) InstrumentEditorDestination.VoiceDetail else null
}

private val voiceEditorSections =
    listOf("Voice", "Oscillator", "Frequency", "Amplitude", "Filter", "Modulation", "Unison")
private val oscillatorEditorSections =
    listOf("Preview", "Output", "Base", "Shape & filter", "Modulation", "Spectrum", "Harmonics")

@Composable
private fun SynthEditorScaffold(
    title: String,
    contextPath: String,
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
    headerActions: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        Surface(color = Color(0xFF181818), shadowElevation = 5.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(contextPath, color = Color(0xFF7AB8BA), style = MaterialTheme.typography.labelSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onNavigateUp) {
                        Text(navigationLabel, fontFamily = FontFamily(Font(R.font.vt323regular)), fontSize = 15.sp)
                    }
                    Text(
                        title,
                        color = Color(0xFFFFC857),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    headerActions()
                    if (dirty) {
                        Spacer(Modifier.width(5.dp))
                        Text("●", color = Color(0xFFFFC857))
                    }
                    onActions?.let { action ->
                        TextButton(onClick = action) { Text("⋮") }
                    }
                }
                if (tabs.isNotEmpty()) Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    tabs.forEach { tab ->
                        Surface(
                            color = if (selectedTab == tab) Color(0xFF23616A) else Color(0xFF182F35),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clickable { onTabSelected(tab) },
                        ) {
                            Text(tab, fontFamily = FontFamily(Font(R.font.vt323regular)), fontSize = 15.sp,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp))
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
    CompositionLocalProvider(LocalEnumContext provides listOf(partIndex, kitIndex, module, state.partIndex, state.kitIndex, state.selectedVoice)) {
    var savedVoice by rememberSaveable { mutableStateOf(0) }
    var selector by remember { mutableStateOf<String?>(null) }
    var destination by rememberSaveable(
        stateSaver = Saver(
            save = { destination: InstrumentEditorDestination? -> destination?.encode().orEmpty() },
            restore = ::decodeDestination,
        )
    ) { mutableStateOf<InstrumentEditorDestination?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val contentScroll = remember(partIndex, kitIndex, module) { LazyListState() }
    val voiceDetailScroll = rememberScrollState()
    val voiceSectionOffsets = remember(state.selectedVoice) { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val oscillatorScroll = rememberScrollState()
    val oscillatorSectionOffsets = remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val oscillatorPreviewOcclusion = with(density) { 112.dp.roundToPx() }
    val resonanceScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(partIndex, kitIndex, module) {
        model.open(partIndex, kitIndex)
        model.selectEngine(module)
        model.selectVoice(savedVoice)
    }

    LaunchedEffect(state.partIndex, state.kitIndex, module, state.snapshot != null) {
        if (state.snapshot != null) contentScroll.scrollToItem(0)
    }

    LaunchedEffect(state.selectedVoice) { savedVoice = state.selectedVoice }

    // Do not anchor the lazy list on the temporary Resonance-only content before open() loads the kit.
    val sectionNames = state.snapshot?.let { populatedSections(it.values, module) }.orEmpty()
    val openSection: (String) -> Unit = { tab ->
        destination = null
        model.selectTab(tab)
        scope.launch {
            val index = sectionNames.indexOf(tab)
            if (index >= 0) contentScroll.animateScrollToItem(index)
        }
    }
    val currentDestination = destination
    val baseContextPath = "Part ${state.partIndex + 1} › Kit ${state.kitIndex + 1} › $module"
    val contextVoiceIndex = when (currentDestination) {
        is InstrumentEditorDestination.Oscillator -> currentDestination.target.ownerVoice
        is InstrumentEditorDestination.Envelope -> currentDestination.address.index
        is InstrumentEditorDestination.Lfo -> currentDestination.address.index
        is InstrumentEditorDestination.Filter -> currentDestination.address.index
        else -> -1
    }
    val contextPath = if (contextVoiceIndex >= 0) {
        "$baseContextPath › Voice ${contextVoiceIndex + 1}"
    } else baseContextPath
    val navigateBack: () -> Unit = { destination = destination?.let(::parentDestination) }
    BackHandler(enabled = currentDestination != null) { navigateBack() }
    if (currentDestination is InstrumentEditorDestination.Oscillator) {
        val oscillatorTitle = if (currentDestination.target.kind == OscillatorEditorKind.Modulator) {
            "Modulator oscillator"
        } else "Voice oscillator"
        SynthEditorScaffold(
            title = oscillatorTitle,
            contextPath = contextPath,
            navigationLabel = "‹ Voice ${state.selectedVoice + 1}",
            onNavigateUp = navigateBack,
            tabs = oscillatorEditorSections,
            selectedTab = if (oscillatorScroll.value > 0 && oscillatorScroll.value == oscillatorScroll.maxValue) {
                oscillatorEditorSections.last()
            } else activeAnchor(
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
        return@CompositionLocalProvider
    }
    if (currentDestination == InstrumentEditorDestination.Resonance) {
        SynthEditorScaffold(
            title = "ADsynth Resonance",
            contextPath = contextPath,
            navigationLabel = "‹ ADD",
            onNavigateUp = navigateBack,
            tabs = emptyList(),
            selectedTab = "",
            onTabSelected = {},
            dirty = state.dirty,
            onActions = null,
            heldNotes = heldNotes,
            keyboardOctaveShift = keyboardOctaveShift,
            onPressKeyboardNote = onPressKeyboardNote,
            onReleaseKeyboardNote = onReleaseKeyboardNote,
            modifier = modifier,
            headerActions = {
                val address = ModuleAddress.Resonance
                ModuleClipboardActions(
                    title = "Resonance",
                    onCopy = { model.copyModule(address) },
                    onPaste = { model.pasteModule(address) },
                    canPaste = model.canPasteModule(address),
                )
            },
        ) { contentModifier ->
            AddResonanceEditorScreen(
                model = model,
                modifier = contentModifier,
                scrollState = resonanceScroll,
            )
        }
        return@CompositionLocalProvider
    }
    if (currentDestination is InstrumentEditorDestination.Envelope) {
        val envelope = remember(state.snapshot, currentDestination.address) {
            EnvelopeModel.from(state.snapshot?.values.orEmpty(), currentDestination.address)
        }
        val preview = remember(state.revision, currentDestination.address) {
            model.preview(currentDestination.address, 192)
        }
        SynthEditorScaffold(
            title = envelope?.title ?: "Envelope",
            contextPath = contextPath,
            navigationLabel = if (currentDestination.address.index >= 0) {
                "‹ Voice ${currentDestination.address.index + 1}"
            } else "‹ ADD",
            onNavigateUp = navigateBack,
            tabs = emptyList(),
            selectedTab = "",
            onTabSelected = {},
            dirty = state.dirty,
            onActions = null,
            heldNotes = heldNotes,
            keyboardOctaveShift = keyboardOctaveShift,
            onPressKeyboardNote = onPressKeyboardNote,
            onReleaseKeyboardNote = onReleaseKeyboardNote,
            modifier = modifier,
        ) { contentModifier ->
            if (envelope != null) {
                FreeEnvelopeEditor(
                    model = envelope,
                    preview = preview,
                    onWrite = model::write,
                    onCommit = model::finishParameterDrag,
                    onAction = model::performPath,
                    valueOf = model::parameterValue,
                    modifier = contentModifier,
                )
            }
        }
        return@CompositionLocalProvider
    }
    if (currentDestination is InstrumentEditorDestination.Lfo) {
        val lfo = LfoModel.from(state.snapshot?.values.orEmpty(), currentDestination.address)
        val preview = remember(state.revision, currentDestination.address) {
            model.preview(currentDestination.address, 192)
        }
        SynthEditorScaffold(
            title = lfo?.title ?: "LFO",
            contextPath = contextPath,
            navigationLabel = if (currentDestination.address.index >= 0) {
                "‹ Voice ${currentDestination.address.index + 1}"
            } else "‹ ADD",
            onNavigateUp = navigateBack,
            tabs = emptyList(),
            selectedTab = "",
            onTabSelected = {},
            dirty = state.dirty,
            onActions = null,
            heldNotes = heldNotes,
            keyboardOctaveShift = keyboardOctaveShift,
            onPressKeyboardNote = onPressKeyboardNote,
            onReleaseKeyboardNote = onReleaseKeyboardNote,
            modifier = modifier,
        ) { contentModifier ->
            if (lfo != null) {
                LfoEditor(
                    model = lfo,
                    preview = preview,
                    onWrite = model::write,
                    onDrag = model::dragParameter,
                    onCommit = model::finishParameterDrag,
                    modifier = contentModifier,
                )
            }
        }
        return@CompositionLocalProvider
    }
    if (currentDestination is InstrumentEditorDestination.Filter) {
        val filter = FilterModel.from(state.snapshot?.values.orEmpty(), currentDestination.address)
        val preview = remember(state.revision, currentDestination.address, filter?.category) {
            model.preview(currentDestination.address, 192)
        }
        SynthEditorScaffold(
            title = "Filter",
            contextPath = contextPath,
            navigationLabel = if (currentDestination.address.index >= 0) {
                "‹ Voice ${currentDestination.address.index + 1}"
            } else "‹ ADD",
            onNavigateUp = navigateBack,
            tabs = emptyList(),
            selectedTab = "",
            onTabSelected = {},
            dirty = state.dirty,
            onActions = null,
            heldNotes = heldNotes,
            keyboardOctaveShift = keyboardOctaveShift,
            onPressKeyboardNote = onPressKeyboardNote,
            onReleaseKeyboardNote = onReleaseKeyboardNote,
            modifier = modifier,
        ) { contentModifier ->
            if (filter != null) {
                FilterEditor(
                    model = filter,
                    preview = preview,
                    previewRevision = state.revision,
                    onWrite = model::write,
                    onDrag = model::dragParameter,
                    onCommit = model::finishParameterDrag,
                    onPreviewVowel = { vowel -> model.preview(currentDestination.address.copy(vowel = vowel), 192) },
                    onCopyVowel = { vowel -> model.copyModule(ModuleAddress.Vowel(currentDestination.address.index, vowel)) },
                    onPasteVowel = { vowel -> model.pasteModule(ModuleAddress.Vowel(currentDestination.address.index, vowel)) },
                    canPasteVowel = { vowel -> model.canPasteModule(ModuleAddress.Vowel(currentDestination.address.index, vowel)) },
                    modifier = contentModifier,
                )
            }
        }
        return@CompositionLocalProvider
    }
    if (currentDestination == InstrumentEditorDestination.VoiceDetail) {
        SynthEditorScaffold(
            title = "Voice ${state.selectedVoice + 1}",
            contextPath = contextPath,
            navigationLabel = "‹ Voices",
            onNavigateUp = navigateBack,
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
            headerActions = {
                val address = ModuleAddress.Voice(state.selectedVoice)
                ModuleClipboardActions(
                    title = "Voice ${state.selectedVoice + 1}",
                    onCopy = { model.copyModule(address) },
                    onPaste = { model.pasteModule(address) },
                    canPaste = model.canPasteModule(address),
                )
            },
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
                onOpenEnvelope = {
                    destination = InstrumentEditorDestination.Envelope(it)
                },
                onOpenLfo = {
                    destination = InstrumentEditorDestination.Lfo(it)
                },
                onOpenFilter = {
                    destination = InstrumentEditorDestination.Filter(it)
                },
            )
        }
        return@CompositionLocalProvider
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) {
                Surface(
                    modifier = Modifier.width(148.dp).fillMaxSize(),
                    color = Color(0xFF131313),
                    border = BorderStroke(1.dp, Color(0xFF234A53)),
                ) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Part ${state.partIndex + 1}", color = Color(0xFF8EF5EE))
                        listOf("ADD", "SUB", "PAD", "FX").forEach { item ->
                            Surface(
                                color = if (item == module) Color(0xFF23616A) else Color(0xFF202020),
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
                title = "Instrument",
                contextPath = contextPath,
                navigationLabel = "‹ Part",
                onNavigateUp = onBack,
                tabs = sectionNames,
                selectedTab = sectionNames.getOrNull(contentScroll.firstVisibleItemIndex)
                    ?: sectionNames.firstOrNull().orEmpty(),
                onTabSelected = openSection,
                dirty = state.dirty,
                onActions = null,
                heldNotes = heldNotes,
                keyboardOctaveShift = keyboardOctaveShift,
                onPressKeyboardNote = onPressKeyboardNote,
                onReleaseKeyboardNote = onReleaseKeyboardNote,
                modifier = Modifier.weight(1f),
            ) { contentModifier ->
                LazyColumn(
                    modifier = contentModifier.fillMaxSize(),
                    state = contentScroll,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(7.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(sectionNames, key = { it }) { section ->
                        val rawParameters = parametersFor(
                            state.snapshot?.values.orEmpty(),
                            module,
                            section,
                            state.selectedVoice,
                        ).let { if (section == "Voices") emptyList() else it }
                        val parameters = if (module == "ADD") {
                            when (section) {
                                "Amp", "Frequency" -> rawParameters.filterNot {
                                    it.descriptor.family in setOf(
                                        SynthEngine.ParameterFamily.ENVELOPE,
                                        SynthEngine.ParameterFamily.LFO,
                                    )
                                }
                                "Filter" -> emptyList()
                                else -> rawParameters
                            }
                        } else rawParameters
                        ZynEditorSection(
                            title = section,
                            parameters = parameters,
                            modifier = Modifier,
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
                            onEdit = model::reset,
                            onDrag = model::dragParameter,
                            onCommit = model::finishParameterDrag,
                            verticalLabels = module == "ADD",
                            leadingContentAfterParameters = module == "ADD" && section in setOf("Amp", "Frequency"),
                            leadingContent = when (section) {
                                "Voices" -> ({
                                    VoiceMatrix(
                                        model = model,
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
                                        ResonanceCurve(
                                            points = state.snapshot?.values.orEmpty().filter {
                                                it.descriptor.group == "ADD / Resonance points"
                                            },
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
                                "Amp", "Frequency", "Filter" -> if (module == "ADD") ({
                                    CommonSynthModules(
                                        model = model,
                                        section = when (section) {
                                            "Amp" -> SynthEngine.ParameterSection.AMPLITUDE
                                            "Frequency" -> SynthEngine.ParameterSection.FREQUENCY
                                            else -> SynthEngine.ParameterSection.FILTER
                                        },
                                        onOpenEnvelope = {
                                            destination = InstrumentEditorDestination.Envelope(it)
                                        },
                                        onOpenLfo = {
                                            destination = InstrumentEditorDestination.Lfo(it)
                                        },
                                        onOpenFilter = {
                                            destination = InstrumentEditorDestination.Filter(it)
                                        },
                                    )
                                }) else null
                                else -> null
                            },
                        )
                    }
                }
            }
        }
    }

    selector?.let { kind ->
        ModalBottomSheet(onDismissRequest = { selector = null }, sheetState = sheetState) {
            when (kind) {
                "voice" -> SelectorSheet("Voice", 8, state.selectedVoice) {
                    model.selectVoice(it)
                    selector = null
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
}

@Composable
private fun VoiceMatrix(
    model: InstrumentEditorViewModel,
    onOpenDetail: (Int) -> Unit,
) {
    val state = model.state
    val values = state.snapshot?.values.orEmpty()
    val voices = remember(values) {
        val indexed = values.associateBy { it.descriptor.path }
        (0..7).mapNotNull { AddVoiceSummary.from(indexed, it) }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        voices.forEach { voice ->
            key(state.partIndex, state.kitIndex, voice.voice) {
                VoiceMatrixRow(model, voice, state.revision, onOpenDetail)
            }
        }
    }
}

@Composable
private fun VoiceMatrixRow(
    model: InstrumentEditorViewModel,
    voice: AddVoiceSummary,
    revision: Long,
    onOpenDetail: (Int) -> Unit,
) {
    val active = voice.enabled.value >= .5
    val pitched = voice.soundMode == 0
    val preview = remember(revision, voice.oscillatorSource, pitched) {
        if (pitched) model.preview(ModuleAddress.Oscillator(voice.oscillatorSource), 64) else PreviewSeries.Empty
    }
    val phasedPreview = remember(preview, voice.oscillatorPhase) {
        val samples = preview.values
        val offset = (voice.oscillatorPhase / 128f * samples.size).roundToInt()
        PreviewSeries(samples.indices.map { samples[Math.floorMod(it + offset, samples.size)] })
    }
    Surface(
        color = Color(0xFF191919),
        border = BorderStroke(1.dp, Color(0xFF2E6973)),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            // Activation and navigation remain separate, including when the voice is off.
            Box(
                Modifier.width(50.dp).height(58.dp)
                    .background(Color(0xFF09191D), RoundedCornerShape(7.dp))
                    .toggleable(
                        value = active,
                        role = Role.Switch,
                        onValueChange = { model.write(voice.enabled, if (it) 1.0 else 0.0) },
                    )
                    .semantics { contentDescription = "Voice ${voice.voice + 1} enabled" },
            ) {
                ModulePreview(
                    phasedPreview,
                    Modifier.fillMaxSize().padding(top = 14.dp),
                    accent = if (active) Color(0xFF65F55D) else Color(0xFF526651),
                )
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text((voice.voice + 1).toString().padStart(2, '0'),
                        color = Color(0xFF8EF5EE), style = MaterialTheme.typography.labelSmall)
                    Text(if (active) "ON" else "OFF",
                        color = if (active) Color(0xFF99EF91) else Color(0xFF9FAE9E),
                        style = MaterialTheme.typography.labelSmall)
                }
                if (!pitched) Text(
                    if (voice.soundMode == 1) "White\nnoise" else "Pink\nnoise",
                    color = Color(0xFFA9BBAA),
                    fontSize = 10.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Box(
                Modifier.padding(start = 8.dp).width(27.dp).height(43.dp)
                    .alpha(if (active && pitched) 1f else .45f)
                    .align(Alignment.Bottom),
                contentAlignment = Alignment.Center,
            ) {
                voice.resonance?.let { parameter ->
                    LedButton(
                        label = "Voice ${voice.voice + 1} resonance",
                        lit = parameter.value >= .5,
                        onClick = { model.write(parameter, if (parameter.value >= .5) 0.0 else 1.0) },
                        modifier = Modifier.size(27.dp), hue = 125f,
                        displayLabel = "Res",
                        enabled = active && pitched, compact = true,
                    )
                }
            }
            VoiceMatrixKnob("Vol", voice.volume, active, model::dragParameter,
                model::finishParameterDrag, Modifier.weight(1f), description = "Voice ${voice.voice + 1} volume")
            VoiceMatrixKnob("Pan", voice.panning, active, model::dragParameter,
                model::finishParameterDrag, Modifier.weight(1f), description = "Voice ${voice.voice + 1} panning")
            VoiceMatrixKnob(
                "Detune", voice.detune, active && pitched, model::dragParameter,
                model::finishParameterDrag, Modifier.weight(1f), description = "Voice ${voice.voice + 1} fine detune in cents",
                valueText = { "%.2f".format(SynthEngine.fineDetuneCents(voice.detuneType, it.roundToInt())) },
            )
            VoiceMatrixKnob(
                "Vibrato", voice.lfoDepth, active && pitched, model::dragParameter,
                model::finishParameterDrag, Modifier.weight(1f),
                description = "Voice ${voice.voice + 1} frequency LFO depth, LFO ${if (voice.lfoEnabled) "on" else "off"}",
            )
            Box(
                Modifier.width(18.dp).height(58.dp)
                    .clickable(onClickLabel = "Edit Voice ${voice.voice + 1}") { onOpenDetail(voice.voice) },
                contentAlignment = Alignment.Center,
            ) {
                Text("›", color = Color(0xFF66F0E9))
            }
        }
    }
}

@Composable
private fun VoiceMatrixKnob(
    label: String,
    parameter: SynthEngine.ParameterValue?,
    enabled: Boolean,
    onDrag: (SynthEngine.ParameterValue, Double) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier,
    description: String = label,
    valueText: ((Float) -> String)? = null,
) {
    Column(
        modifier.alpha(if (enabled) 1f else .45f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        if (parameter != null) {
            TinyKnob(
                label = "", value = parameter.value.toFloat(),
                accessibilityLabel = description,
                min = parameter.descriptor.minimum.toFloat(), max = parameter.descriptor.maximum.toFloat(),
                enabled = enabled,
                valueText = valueText ?: {
                    if (parameter.descriptor.path.endsWith("/panning") && it.roundToInt() == 0) "Rnd"
                    else it.roundToInt().toString()
                },
                onValueChange = { onDrag(parameter, it.toDouble()) },
                onValueChangeFinished = onCommit,
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
    onOpenEnvelope: (ModuleAddress.Envelope) -> Unit,
    onOpenLfo: (ModuleAddress.Lfo) -> Unit,
    onOpenFilter: (ModuleAddress.Filter) -> Unit,
) {
    val state = model.state
    val snapshotValues = state.snapshot?.values.orEmpty()
    val voiceSummary = remember(snapshotValues, state.selectedVoice) {
        AddVoiceSummary.from(snapshotValues.associateBy { it.descriptor.path }, state.selectedVoice)
    }
    val values = snapshotValues.filter {
        it.descriptor.path.startsWith("add/voice/${state.selectedVoice}/") &&
            !it.descriptor.path.contains("/osc/") &&
            !it.descriptor.path.contains("/modOsc/")
    }
    val carrierShared = voiceSummary?.let { it.oscillatorSource != it.voice } ?: false
    val carrierOwner = voiceSummary?.oscillatorSource ?: state.selectedVoice
    val voiceRoot = "ADD / Voice ${state.selectedVoice + 1}"
    val oscillatorGroup = "ADD / Voice ${carrierOwner + 1} / Oscillator"
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
                        "Voice" -> group.endsWith("/ Voice") ||
                            value.descriptor.path == "add/voice/${state.selectedVoice}/enabled" ||
                            (group.endsWith("/ Voice Oscillator") &&
                                (state.selectedVoice > 0 || !value.descriptor.path.endsWith("/externalOscillator")))
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
                                    "detuneType", "coarse", "bend", "offsetHz"
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
                            "unison", "spread", "phaseRandom", "stereoSpread",
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
                        header = if (section == "Voice") ({}) else if (section == "Amplitude") ({
                            val address = ModuleAddress.Amplitude(state.selectedVoice)
                            CompactModuleActions("Amplitude", { model.copyModule(address) },
                                { model.pasteModule(address) }, model.canPasteModule(address), sectionHeading = true)
                        }) else if (section == "Filter") ({}) else if (section == "Oscillator" || (section == "Modulation" && externalModulator == 0)) ({
                            val modulator = section == "Modulation"
                            val address = ModuleAddress.Oscillator(
                                if (modulator) modulatorOscillatorOwner else state.selectedVoice,
                                modulator,
                            )
                            CompactModuleActions(
                                title = when {
                                    modulator -> "Modulation · Oscillator"
                                    carrierShared -> "Oscillator · using Voice ${carrierOwner + 1}"
                                    else -> section
                                },
                                onCopy = { model.copyModule(address) },
                                onPaste = { model.pasteModule(address) },
                                canPaste = model.canPasteModule(address),
                                clipboardEnabled = modulator || !carrierShared,
                                sectionHeading = true,
                            )
                        }) else null,
                    ) {
                        if (section == "Oscillator") {
                            Surface(
                                modifier = Modifier.fillMaxWidth().height(118.dp)
                                    .alpha(if (carrierShared) .55f else 1f)
                                    .clickable(enabled = !carrierShared) {
                                        onOpenOscillator(
                                            OscillatorEditorTarget(
                                                OscillatorEditorKind.Voice,
                                                state.selectedVoice,
                                            )
                                        )
                                    },
                                color = Color(0xFF191919),
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
                                if (externalModulator > 0) Surface(
                                    modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
                                    color = Color(0xCC18383E),
                                    shape = RoundedCornerShape(5.dp),
                                ) {
                                    Text(
                                        "Oscillator inactive",
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
                        selected.groupBy {
                            if (section == "Voice") "$voiceRoot / Voice" else it.descriptor.group
                        }.entries
                            .sortedBy { voiceGroupOrder(section, it.key) }
                            .filterNot { (group, _) ->
                                section == "Oscillator" || group.endsWith("/ Envelope") || group.endsWith("/ LFO") ||
                                    group.endsWith("/ Amplitude envelope") ||
                                    group.endsWith("/ Frequency envelope") ||
                                    (section == "Filter" && group.startsWith("$voiceRoot / Filter"))
                            }
                            .forEach { (group, parameters) ->
                            val displayedParameters = parameters.filterNot {
                                section == "Modulation" &&
                                    it.descriptor.path.substringAfterLast('/') in setOf(
                                        "modAmpEnvelopeEnabled", "modFreqEnvelopeEnabled"
                                    )
                            }
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
                                parameters = displayedParameters.sortedBy {
                                    voiceParameterOrder(section, group, it.descriptor.path)
                                },
                                onWrite = model::write,
                                onDrag = model::dragParameter,
                                onCommit = model::finishParameterDrag,
                                verticalLabels = true,
                                onLongPress = { model.reset(it) },
                                onReset = model::reset,
                                enabled = { parameter ->
                                    externalModulator == 0 || section != "Modulation" ||
                                        !isInternalModulatorFrequencyParameter(parameter.descriptor.path)
                                },
                                valueText = {
                                    if (it.descriptor.path.endsWith("/detune")) {
                                        "%.2f ct".format(SynthEngine.fineDetuneCents(voiceSummary?.detuneType ?: 1, it.value.roundToInt()))
                                    } else {
                                        it.value.roundToInt().toString()
                                    }
                                },
                            )
                        }
                        if (section in setOf("Amplitude", "Frequency", "Filter", "Modulation")) {
                            CommonSynthModules(
                                model = model,
                                section = when (section) {
                                    "Amplitude" -> SynthEngine.ParameterSection.AMPLITUDE
                                    "Frequency" -> SynthEngine.ParameterSection.FREQUENCY
                                    "Filter" -> SynthEngine.ParameterSection.FILTER
                                    else -> SynthEngine.ParameterSection.MODULATION
                                },
                                voiceIndex = state.selectedVoice,
                                onOpenEnvelope = onOpenEnvelope,
                                onOpenLfo = onOpenLfo,
                                onOpenFilter = onOpenFilter,
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
        section == "Voice" -> listOf(
            "enabled", "delay", "resonance", "oscillatorType", "oscillatorPhase", "externalOscillator"
        )
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
            "detune", "fixedFreq", "fixedFreqEt", "octave", "detuneType", "coarse", "bend", "offsetHz"
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
    header: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (header != null) header() else SectionBanner(title)
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
    val externalCarrier = values.firstOrNull {
        it.descriptor.path == "add/voice/${target.ownerVoice}/externalOscillator"
    }?.value?.toInt() ?: 0
    val oscillatorEditable = modulator || externalCarrier == 0
    val oscillatorAddress = ModuleAddress.Oscillator(target.ownerVoice, modulator)
    val previewOscillator = oscillatorAddress.copy(
        index = if (oscillatorEditable) target.ownerVoice else externalCarrier - 1,
    )
    var previewView by remember(target) { mutableStateOf<OscillatorPreviewView?>(null) }
    val previewAddress = previewView?.let { ModuleAddress.OscillatorPreview(previewOscillator, it) }
        ?: previewOscillator
    val exactPreview = remember(model.state.revision, previewAddress) {
        model.preview(previewAddress, if (previewView != null && previewView != OscillatorPreviewView.BASE_WAVE) 128 else 256)
    }
    val actionPrefix = "add/voice/${target.ownerVoice}/${if (modulator) "modOsc" else "osc"}/action"
    var pendingAction by remember(target) { mutableStateOf<Pair<String, String>?>(null) }
    if (oscillatorEditable) pendingAction?.let { (label, action) ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(label) },
            text = {
                Text(if (action == "clearHarmonics") "Reset every harmonic to the default magnitude and phase."
                else "Convert the current output to sine harmonics and reset the oscillator's other controls.")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingAction = null
                    model.writePath("$actionPrefix/$action", 1.0)
                }) { Text(label) }
            },
            dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("Cancel") } },
        )
    }
    val group = "ADD / Voice ${target.ownerVoice + 1} / " +
        if (modulator) "Modulator oscillator" else "Oscillator"
    val controls = remember(values, group) { values.filter { it.descriptor.group == group } }
    val voiceOscillatorControls = remember(values, target) {
        if (modulator) emptyList() else values.filter {
            it.descriptor.group == "ADD / Voice ${target.ownerVoice + 1} / Voice Oscillator"
        }
    }
    val magnitudes = remember(values, group) { values.filter { it.descriptor.group == "$group harmonics" } }
    val phases = remember(values, group) { values.filter { it.descriptor.group == "$group phases" } }
    var selectedHarmonic by remember(target) { mutableStateOf(0) }
    val magnitude = magnitudes.getOrNull(selectedHarmonic)
    val phase = phases.getOrNull(selectedHarmonic)
    val byField = remember(controls) { controls.associateBy { it.descriptor.path.substringAfterLast('/') } }
    val sections = remember(byField, voiceOscillatorControls) {
        listOf(
            "Output" to listOf("magnitudeType", "randomness", "ampRandomType", "ampRandomPower"),
            "Base" to listOf("baseFunction", "baseShape", "baseModType", "baseMod1", "baseMod2", "baseMod3"),
            "Shape & filter" to listOf("waveshapeType", "waveshape", "filterType", "filter1", "filter2", "filterBeforeShape"),
            "Modulation" to listOf("modulationType", "modulation1", "modulation2", "modulation3"),
            "Spectrum" to listOf("spectrumType", "spectrumAmount", "adaptiveMode", "adaptivePower", "adaptiveBaseFreq", "adaptiveAmount"),
        ).map { (title, fields) ->
            title to ((if (title == "Output") voiceOscillatorControls else emptyList()) + fields.mapNotNull(byField::get))
        }
    }
    fun controlValue(field: String) = byField[field]?.value?.toInt() ?: 0
    val positionSection: (String) -> Modifier = { section ->
        Modifier.onGloballyPositioned {
            onSectionPosition(section, it.positionInParent().y.roundToInt())
        }
    }

    CollapsingPreviewLayout(
        scrollState = scrollState,
        modifier = modifier,
        preview = { previewModifier ->
            OscillatorPreviewPanel(exactPreview, previewView, { previewView = it }, previewModifier)
        },
    ) {
            if (!oscillatorEditable) Text(
                "Internal oscillator inactive · using Voice $externalCarrier",
                color = Color(0xFFFFB74D),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 7.dp),
            )

            if (target.ownerVoice != model.state.selectedVoice) {
                Text(
                    "Shared from Voice ${target.ownerVoice + 1}",
                    color = Color(0xFFFFB74D),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 7.dp),
                )
            }
            ZynVoiceSection(
                "Oscillator preview",
                modifier = positionSection("Preview").padding(horizontal = 7.dp),
            ) {
                Spacer(Modifier.fillMaxWidth().height(210.dp))
            }

            sections.forEach { (title, parameters) ->
                if (parameters.isNotEmpty()) {
                    ZynVoiceSection(
                        title,
                        modifier = positionSection(title).padding(horizontal = 7.dp),
                    ) {
                        DenseParameterGrid(
                            parameters = parameters,
                            onWrite = model::write,
                            onDrag = model::dragParameter,
                            onCommit = model::finishParameterDrag,
                            onLongPress = model::reset,
                            onReset = model::reset,
                            verticalLabels = true,
                            enabled = { parameter ->
                                parameter in voiceOscillatorControls || oscillatorEditable && when (
                                    parameter.descriptor.path.substringAfterLast('/')
                                ) {
                                    "baseShape" -> controlValue("baseFunction") in 1..16
                                    "baseModType" -> controlValue("baseFunction") != 0
                                    "baseMod1", "baseMod2", "baseMod3" ->
                                        controlValue("baseFunction") != 0 && controlValue("baseModType") != 0
                                    "waveshape" -> controlValue("waveshapeType") != 0
                                    "filter1", "filter2" -> controlValue("filterType") != 0
                                    "filterBeforeShape" -> controlValue("filterType") != 0 && controlValue("waveshapeType") != 0
                                    "modulation1", "modulation2", "modulation3" -> controlValue("modulationType") != 0
                                    "spectrumAmount" -> controlValue("spectrumType") != 0
                                    "adaptivePower", "adaptiveBaseFreq" -> controlValue("adaptiveMode") != 0
                                    "adaptiveAmount" -> controlValue("adaptiveMode") > 1
                                    "ampRandomPower" -> controlValue("ampRandomType") != 0
                                    else -> true
                                }
                            },
                        )
                    }
                }
            }

            ZynVoiceSection("Harmonics", modifier = positionSection("Harmonics")) {
                val labelColor = Color.hsv(188f, saturation = .30f, value = .22f)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("harmonicShift", "harmonicShiftFirst").forEach { field ->
                            Box(Modifier.weight(1f)) {
                                byField[field]?.let { parameter ->
                                    DenseParameterControl(
                                        parameter = parameter,
                                        onWrite = model::write,
                                        onDrag = model::dragParameter,
                                        onCommit = model::finishParameterDrag,
                                        onLongPress = model::reset,
                                        onReset = model::reset,
                                        verticalLabel = true,
                                        enabled = oscillatorEditable,
                                    )
                                }
                            }
                        }
                    }
                    DenseControlCard(
                        label = "Oscillator",
                        labelColor = labelColor,
                        modifier = Modifier.weight(1f),
                        enabled = oscillatorEditable,
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 3.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            LedButton("Use output as base", lit = false, action = true, compact = true,
                                displayLabel = "Use as base", enabled = oscillatorEditable,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { model.writePath("$actionPrefix/useAsBase", 1.0) })
                            LedButton("Convert to sine", lit = false, action = true, compact = true,
                                enabled = oscillatorEditable, modifier = Modifier.fillMaxWidth(),
                                onClick = { pendingAction = "Convert to sine" to "convertToSine" })
                        }
                    }
                }

                if (magnitude != null && phase != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        HarmonicKnobCard(
                            label = "Harmonic #",
                            labelColor = labelColor,
                            value = { (selectedHarmonic + 1).toFloat() },
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
                            labelColor = labelColor,
                            identity = magnitude.descriptor.path,
                            enabled = oscillatorEditable,
                            value = { model.parameterValue(magnitude).toFloat() },
                            minimum = magnitude.descriptor.minimum.toFloat(),
                            maximum = magnitude.descriptor.maximum.toFloat(),
                            onValueChange = { model.dragParameter(magnitude, it.roundToInt().toDouble()) },
                            onCommit = model::finishParameterDrag,
                            modifier = Modifier.weight(1f),
                        )
                        HarmonicKnobCard(
                            label = "Phase",
                            labelColor = labelColor,
                            identity = phase.descriptor.path,
                            enabled = oscillatorEditable,
                            value = { model.parameterValue(phase).toFloat() },
                            minimum = phase.descriptor.minimum.toFloat(),
                            maximum = phase.descriptor.maximum.toFloat(),
                            onValueChange = { model.dragParameter(phase, it.roundToInt().toDouble()) },
                            onCommit = model::finishParameterDrag,
                            modifier = Modifier.weight(1f),
                        )
                        val canResetOne = oscillatorEditable &&
                            (model.parameterValue(magnitude) != magnitude.descriptor.defaultValue ||
                                model.parameterValue(phase) != phase.descriptor.defaultValue)
                        val hasEditedHarmonics = remember(magnitudes, phases) {
                            magnitudes.any { it.value != it.descriptor.defaultValue } ||
                                phases.any { it.value != it.descriptor.defaultValue }
                        }
                        val canResetAll = oscillatorEditable && (hasEditedHarmonics || canResetOne)
                        DenseControlCard(
                            label = "Reset",
                            labelColor = Color.hsv(32f, saturation = .30f, value = .22f),
                            modifier = Modifier.weight(1f),
                            enabled = canResetAll,
                        ) {
                            Column(
                                Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                LedButton(
                                    label = "Reset harmonic ${selectedHarmonic + 1}",
                                    lit = canResetOne,
                                    hue = 32f,
                                    displayLabel = "ONE",
                                    enabled = canResetOne,
                                    action = canResetOne,
                                    compact = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        model.dragParameter(magnitude, magnitude.descriptor.defaultValue)
                                        model.dragParameter(phase, phase.descriptor.defaultValue)
                                        model.finishParameterDrag()
                                    },
                                )
                                LedButton(
                                    label = "Reset all harmonics",
                                    lit = canResetAll,
                                    hue = 32f,
                                    displayLabel = "ALL",
                                    enabled = canResetAll,
                                    action = canResetAll,
                                    compact = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { pendingAction = "Clear harmonics" to "clearHarmonics" },
                                )
                            }
                        }
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
                    enabled = oscillatorEditable,
                    valueOf = model::parameterValue,
                    onCommit = model::finishParameterDrag,
                    selected = selectedHarmonic,
                    onSelect = { selectedHarmonic = it },
                    onMagnitudeChange = { index, value ->
                        magnitudes.getOrNull(index)?.let { model.dragParameter(it, value) }
                    },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                )
            }
    }

}

@Composable
private fun HarmonicKnobCard(
    label: String,
    labelColor: Color,
    value: () -> Float,
    minimum: Float,
    maximum: Float,
    onValueChange: (Float) -> Unit,
    onCommit: () -> Unit = {},
    identity: String = label,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    DenseControlCard(label = label, labelColor = labelColor, modifier = modifier, enabled = enabled) {
        key(identity) {
            TinyKnob(
                label = "",
                accessibilityLabel = label,
                value = value(),
                enabled = enabled,
                min = minimum,
                max = maximum,
                sensitivity = KnobSensitivity.Adjust,
                onValueChange = onValueChange,
                onValueChangeFinished = onCommit,
            )
        }
    }
}

@Composable
private fun OscillatorPreviewPanel(
    preview: PreviewSeries,
    view: OscillatorPreviewView?,
    onViewSelected: (OscillatorPreviewView?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Color(0xFFFF4D57)
    Surface(
        modifier = modifier,
        color = Color.Black,
        border = BorderStroke(1.dp, Color(0xFF2E6973)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                (listOf(null) + OscillatorPreviewView.entries).forEach { choice ->
                    Text(
                        choice?.label ?: "Output wave",
                        color = if (choice == view) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.selectable(choice == view, role = Role.Tab) { onViewSelected(choice) }
                            .padding(horizontal = 6.dp, vertical = 9.dp),
                    )
                }
            }
            val graph = Modifier.fillMaxWidth().weight(1f)
            if (view != null && view != OscillatorPreviewView.BASE_WAVE) {
                Canvas(graph.background(Color(0xFF09191D))) {
                    val width = size.width / preview.values.size.coerceAtLeast(1)
                    preview.values.forEachIndexed { index, value ->
                        val x = (index + .5f) * width
                        drawLine(accent, androidx.compose.ui.geometry.Offset(x, size.height),
                            androidx.compose.ui.geometry.Offset(x, size.height * (1f - .95f * value)),
                            strokeWidth = width * .72f)
                    }
                }
            } else {
                ModulePreview(preview, graph, accent = accent)
            }
        }
    }
}

@Composable
private fun HarmonicOverview(
    magnitudes: List<SynthEngine.ParameterValue>,
    enabled: Boolean,
    valueOf: (SynthEngine.ParameterValue) -> Double,
    onCommit: () -> Unit,
    selected: Int,
    onSelect: (Int) -> Unit,
    onMagnitudeChange: (Int, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val currentValueOf by rememberUpdatedState(valueOf)
    val currentOnCommit by rememberUpdatedState(onCommit)
    val currentSelected by rememberUpdatedState(selected)
    val currentMagnitudes by rememberUpdatedState(magnitudes)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnMagnitudeChange by rememberUpdatedState(onMagnitudeChange)
    Canvas(
        modifier.alpha(if (enabled) 1f else .45f).onSizeChanged { size = it }.pointerInput(magnitudes.firstOrNull()?.descriptor?.path, magnitudes.size, size, enabled) {
            if (!enabled) return@pointerInput
            var accumulatedHorizontalDrag = 0f
            var dragSelection = currentSelected
            var dragMagnitude = currentMagnitudes.getOrNull(dragSelection)?.let(currentValueOf) ?: 64.0
            try {
                detectDragGestures(
                    onDragStart = {
                        accumulatedHorizontalDrag = 0f
                        dragSelection = currentSelected
                        dragMagnitude = currentMagnitudes.getOrNull(dragSelection)?.let(currentValueOf) ?: 64.0
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
                                dragMagnitude = currentMagnitudes.getOrNull(dragSelection)?.let(currentValueOf) ?: 64.0
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
                    onDragEnd = {
                        accumulatedHorizontalDrag = 0f
                        currentOnCommit()
                    },
                    onDragCancel = {
                        accumulatedHorizontalDrag = 0f
                        currentOnCommit()
                    },
                )
            } finally {
                currentOnCommit()
            }
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
                val normalized = ((valueOf(harmonic) - 64.0) / 63.0).coerceIn(-1.0, 1.0).toFloat()
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
    var drawingValues by remember { mutableStateOf<List<Int>?>(null) }
    var drawingPosition by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    val currentPoints by rememberUpdatedState(points)

    Column(
        modifier.fillMaxSize().verticalScroll(scrollState).padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        val position = drawingPosition
        val centerHz = center?.value?.let { 10000.0 * 10.0.pow(-(1.0 - it / 127.0) * 2.0) }
        val octaveSpan = octaves?.value?.let { 0.25 + 10.0 * it / 127.0 }
        val frequency = if (position != null && centerHz != null && octaveSpan != null) {
            centerHz * 2.0.pow(octaveSpan * (position.x - 0.5))
        } else null
        val amplitude = if (position != null && maxDb != null) {
            (1.0 - position.y * 2.0) * maxDb.value
        } else null
        Box(Modifier.fillMaxWidth()) {
            ResonanceCurve(
                points = points,
                drawingValues = drawingValues,
                modifier = Modifier.fillMaxWidth().height(260.dp)
                    .onSizeChanged { graphSize = it }.pointerInput(graphSize, points.size) {
                    var previousPosition: androidx.compose.ui.geometry.Offset? = null
                    var gestureValues: MutableList<Int>? = null
                    fun write(positionX: Float, positionY: Float) {
                        if (graphSize.width <= 0 || graphSize.height <= 0 || currentPoints.isEmpty()) return
                        val values = gestureValues ?: return
                        val index = ((positionX / graphSize.width) * (values.size - 1))
                            .roundToInt().coerceIn(values.indices)
                        val previous = previousPosition
                        val previousIndex = previous?.let {
                            ((it.x / graphSize.width) * (values.size - 1))
                                .roundToInt().coerceIn(values.indices)
                        } ?: index
                        for (pointIndex in minOf(previousIndex, index)..maxOf(previousIndex, index)) {
                            val fraction = if (previousIndex == index) 1f else {
                                (pointIndex - previousIndex).toFloat() / (index - previousIndex)
                            }
                            val y = if (previous == null) positionY else {
                                previous.y + (positionY - previous.y) * fraction
                            }
                            val value = (127f * (1f - y / graphSize.height))
                                .roundToInt().coerceIn(0, 127)
                            values[pointIndex] = value
                        }
                        drawingValues = values.toList()
                        drawingPosition = androidx.compose.ui.geometry.Offset(
                            (positionX / graphSize.width).coerceIn(0f, 1f),
                            (positionY / graphSize.height).coerceIn(0f, 1f),
                        )
                        previousPosition = androidx.compose.ui.geometry.Offset(positionX, positionY)
                    }
                    detectDragGestures(
                        onDragStart = {
                            previousPosition = null
                            gestureValues = currentPoints.map { it.value.roundToInt() }.toMutableList()
                            write(it.x, it.y)
                        },
                        onDrag = { change, _ ->
                            write(change.position.x, change.position.y)
                            change.consume()
                        },
                        onDragEnd = {
                            gestureValues?.let { model.commitResonanceCurve(currentPoints, it) }
                            gestureValues = null
                            drawingValues = null
                            drawingPosition = null
                            previousPosition = null
                        },
                        onDragCancel = {
                            gestureValues = null
                            drawingValues = null
                            drawingPosition = null
                            previousPosition = null
                        },
                    )
                },
            )
            if (amplitude != null) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                    color = Color(0xCC50151A),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text("%+.1f dB".format(amplitude), color = Color(0xFFFF8A90),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
            if (frequency != null) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                    color = Color(0xCC50151A),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text("%.2f kHz".format(frequency / 1000.0), color = Color(0xFFFF8A90),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
        }
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
                ) {
                    Text(label, fontSize = 9.sp, maxLines = 1)
                }
            }
        }
        val resonanceControls = listOfNotNull(enabled, maxDb, center, octaves, protect)
        DenseParameterGrid(
            parameters = resonanceControls,
            onWrite = model::write,
            onDrag = model::dragParameter,
            onCommit = model::finishParameterDrag,
            verticalLabels = true,
            onLongPress = { model.reset(it) },
            onReset = model::reset,
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

@Composable
private fun ResonanceCurve(
    points: List<SynthEngine.ParameterValue>,
    modifier: Modifier = Modifier,
    drawingValues: List<Int>? = null,
) {
    Canvas(modifier) {
        drawRect(Color(0xFF050A0C))
        repeat(9) { line ->
            val x = size.width * line / 8f
            drawLine(Color(0xFF31515A), start = androidx.compose.ui.geometry.Offset(x, 0f),
                end = androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = 1f)
        }
        repeat(5) { line ->
            val y = size.height * line / 4f
            drawLine(Color(0xFF31515A), start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1f)
        }
        if (points.isNotEmpty()) {
            val path = Path()
            points.forEachIndexed { index, point ->
                val x = size.width * index / (points.size - 1).coerceAtLeast(1)
                val y = size.height * (1f - (drawingValues?.getOrNull(index)?.toFloat()
                    ?: point.value.toFloat()) / 127f)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Color(0xFFFF4D57), style = androidx.compose.ui.graphics.drawscope.Stroke(2.5f))
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
    leadingContentAfterParameters: Boolean = false,
    onDrag: (SynthEngine.ParameterValue, Double) -> Unit = onWrite,
    onCommit: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!(title == "Filter" && verticalLabels && parameters.isEmpty())) {
            if (verticalLabels) SectionBanner(title) else EditorSectionHeader(title)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
            if (!leadingContentAfterParameters) leadingContent?.invoke()
            if (complex) ComplexEditorLauncher(title, onComplex)
            if (parameters.isNotEmpty()) {
                if (verticalLabels) {
                    parameters.groupBy {
                        if (it.descriptor.path == "add/randomGrouping") "ADD / Amplitude / Global"
                        else it.descriptor.group
                    }.forEach { (group, groupedParameters) ->
                        if (!group.endsWith("/ Global")) {
                            Text(
                                addSubsectionLabel(group),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(start = 4.dp, top = 5.dp, bottom = 2.dp),
                            )
                        }
                        DenseParameterGrid(
                            groupedParameters,
                            onWrite,
                            verticalLabels = true,
                            onLongPress = onEdit,
                            onReset = onEdit,
                            onDrag = onDrag,
                            onCommit = onCommit,
                        )
                    }
                } else {
                    DenseParameterGrid(
                        parameters,
                        onWrite,
                        verticalLabels = false,
                        onLongPress = onEdit,
                        onReset = onEdit,
                        onDrag = onDrag,
                        onCommit = onCommit,
                    )
                }
            }
            if (leadingContentAfterParameters && leadingContent != null) {
                Spacer(Modifier.height(10.dp))
                leadingContent()
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

internal fun parametersFor(
    all: List<SynthEngine.ParameterValue>,
    module: String,
    tab: String,
    voice: Int,
): List<SynthEngine.ParameterValue> = all.filter { parameter ->
    val descriptor = parameter.descriptor
    if (module == "ADD") {
        if (descriptor.module != SynthEngine.ParameterModule.ADD) return@filter false
        return@filter when (tab) {
            "Amp" -> descriptor.scope == SynthEngine.ParameterScope.GLOBAL &&
                (descriptor.section == SynthEngine.ParameterSection.AMPLITUDE ||
                    descriptor.path == "add/randomGrouping")
            "Frequency" -> descriptor.scope == SynthEngine.ParameterScope.GLOBAL &&
                descriptor.section == SynthEngine.ParameterSection.FREQUENCY
            "Filter" -> descriptor.scope == SynthEngine.ParameterScope.GLOBAL &&
                descriptor.section == SynthEngine.ParameterSection.FILTER
            "Voices" -> descriptor.scope == SynthEngine.ParameterScope.VOICE &&
                descriptor.ownerIndex == voice
            // Resonance points belong to the touch editor, never to the overview knob grid.
            "Resonance" -> false
            else -> false
        }
    }
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
internal fun DenseParameterGrid(
    parameters: List<SynthEngine.ParameterValue>,
    onWrite: (SynthEngine.ParameterValue, Double) -> Unit,
    verticalLabels: Boolean = false,
    valueText: (SynthEngine.ParameterValue) -> String = { it.value.roundToInt().toString() },
    enabled: (SynthEngine.ParameterValue) -> Boolean = { true },
    onLongPress: (SynthEngine.ParameterValue) -> Unit,
    onDrag: (SynthEngine.ParameterValue, Double) -> Unit = onWrite,
    onCommit: () -> Unit = {},
    trailingControl: (@Composable () -> Unit)? = null,
    enumContext: Any? = null,
    onReset: ((SynthEngine.ParameterValue) -> Unit)? = null,
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
            (0 until parameters.size + if (trailingControl != null) 1 else 0).toList().chunked(columns).forEach { rowIndices ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    rowIndices.forEach { index ->
                        Box(Modifier.weight(1f)) {
                            if (index < parameters.size) {
                                val parameter = parameters[index]
                                DenseParameterControl(parameter, onWrite, onLongPress, verticalLabels,
                                    valueText, enabled(parameter), onDrag, onCommit, enumContext, onReset)
                            } else {
                                trailingControl?.invoke()
                            }
                        }
                    }
                    repeat(columns - rowIndices.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
internal fun DenseControlCard(
    label: String,
    labelColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    verticalLabel: Boolean = true,
    unit: String = "",
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    longClickLabel: String? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().alpha(if (enabled) 1f else .45f).combinedClickable(
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick,
            onLongClickLabel = longClickLabel,
        ),
        color = Color(0xFF202020),
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.dp, Color(0xFF274B54)),
    ) {
        Box(Modifier.fillMaxWidth().height(if (verticalLabel) 78.dp else 86.dp)
            .background(remember {
                Brush.verticalGradient(listOf(Color(0xFF1D3033), Color(0xFF152124), Color(0xFF10191B)))
            })) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                if (verticalLabel) {
                    Surface(
                        modifier = Modifier.width(28.dp).fillMaxHeight(),
                        color = labelColor,
                        shape = RoundedCornerShape(topStart = 7.dp, bottomStart = 7.dp),
                    ) {
                        Box(
                            modifier = Modifier.background(remember {
                                Brush.verticalGradient(listOf(
                                    Color.White.copy(alpha = .07f), Color.Transparent, Color.Black.copy(alpha = .18f),
                                ))
                            }),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                modifier = Modifier.rotate(-90f).requiredWidth(72.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Column(
                    Modifier.weight(1f).padding(horizontal = 3.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    content()
                    if (!verticalLabel) {
                        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                    }
                }
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

@Composable
internal fun DenseParameterControl(
    parameter: SynthEngine.ParameterValue,
    onWrite: (SynthEngine.ParameterValue, Double) -> Unit,
    onLongPress: (SynthEngine.ParameterValue) -> Unit,
    verticalLabel: Boolean = false,
    valueText: (SynthEngine.ParameterValue) -> String = { it.value.roundToInt().toString() },
    enabled: Boolean = true,
    onDrag: (SynthEngine.ParameterValue, Double) -> Unit = onWrite,
    onCommit: () -> Unit = {},
    enumContext: Any? = null,
    onReset: ((SynthEngine.ParameterValue) -> Unit)? = null,
) {
    val descriptor = parameter.descriptor
    val labelHue = addLabelHue(descriptor)
    var enumOpen by remember(descriptor.path, LocalEnumContext.current, enumContext) { mutableStateOf(false) }
    var numberOpen by remember(descriptor.path, LocalEnumContext.current, enumContext) { mutableStateOf(false) }
    var draft by remember(descriptor.path, LocalEnumContext.current, enumContext) { mutableStateOf(parameter.value.toFloat()) }
    val openEnum = {
        onCommit()
        enumOpen = true
    }
    Box {
    DenseControlCard(
        label = if (descriptor.path.endsWith("/volumeMinus")) "Polarity" else if (verticalLabel) compactAddLabel(descriptor) else descriptor.label,
        labelColor = Color.hsv(labelHue, saturation = .30f, value = .22f),
        verticalLabel = verticalLabel,
        enabled = enabled,
        longClickLabel = "Choose ${descriptor.label}",
        unit = when {
            descriptor.path.endsWith("/maxDb") -> "dB"
            descriptor.path.endsWith("/center") -> "Hz"
            else -> ""
        },
        onClick = {
            if (descriptor.type == SynthEngine.ParameterType.BOOLEAN && !descriptor.path.endsWith("/volumeMinus")) {
                onWrite(parameter, if (parameter.value >= .5) 0.0 else 1.0)
            }
        },
        onLongClick = {
            if (descriptor.type != SynthEngine.ParameterType.INTEGER) openEnum()
            else if (descriptor.type == SynthEngine.ParameterType.INTEGER) {
                onCommit()
                draft = parameter.value.toFloat()
                numberOpen = true
            }
        },
    ) {
        when {
                descriptor.path.endsWith("/volumeMinus") -> TinyKnob(
                    label = "", value = parameter.value.toFloat(), min = 0f, max = 1f,
                    accessibilityLabel = "Polarity",
                    accent = Color.hsv(labelHue, .65f, .90f),
                    valueText = { if (it < .5f) "+" else "−" }, enabled = enabled,
                    onValueChange = { onDrag(parameter, it.roundToInt().toDouble()) },
                    onValueChangeFinished = onCommit,
                )
                descriptor.type == SynthEngine.ParameterType.BOOLEAN -> LedButton(
                    label = descriptor.label,
                    lit = parameter.value >= .5,
                    onClick = { onWrite(parameter, if (parameter.value >= .5) 0.0 else 1.0) },
                    modifier = Modifier.size(43.dp),
                    hue = labelHue,
                    displayLabel = if (parameter.value >= .5) "1" else "0",
                    enabled = enabled,
                    compact = true,
                    onLongClick = openEnum,
                )
                descriptor.type == SynthEngine.ParameterType.ENUM -> if (verticalLabel) {
                    TinyKnob(
                        label = "",
                        accessibilityLabel = descriptor.label,
                        accent = Color.hsv(labelHue, .65f, .90f),
                        value = parameter.value.toFloat(),
                        min = descriptor.minimum.toFloat(),
                        max = descriptor.maximum.toFloat(),
                        valueText = {
                            val index = it.roundToInt()
                            if (index == 127 && descriptor.path.endsWith("/baseFunction")) "Custom"
                            else descriptor.options.getOrNull(index) ?: index.toString()
                        },
                        compactValueLabel = true,
                        sensitivity = KnobSensitivity.Adjust,
                        enabled = enabled,
                        onValueChange = {
                            if (enabled) onDrag(parameter, it.roundToInt().toDouble())
                        },
                        onValueChangeFinished = onCommit,
                    )
                } else {
                    CompactSelector(
                        label = "",
                        value = descriptor.options.getOrNull(parameter.value.roundToInt())
                            ?: parameter.value.roundToInt().toString(),
                        onClick = { if (enabled) openEnum() },
                    )
                }
                else -> TinyKnob(
                    label = "",
                    accessibilityLabel = descriptor.label,
                    accent = Color.hsv(labelHue, .65f, .90f),
                    enabled = enabled,
                    value = parameter.value.toFloat(),
                    min = descriptor.minimum.toFloat(),
                    max = descriptor.maximum.toFloat(),
                    sensitivity = if (descriptor.path.endsWith("/oscillatorPhase")) {
                        KnobSensitivity.Adjust
                    } else {
                        KnobSensitivity.Default
                    },
                    valueText = { valueText(parameter.copy(value = it.roundToInt().toDouble())) },
                    onValueChange = {
                        if (enabled) onDrag(parameter, it.roundToInt().toDouble())
                    },
                    onValueChangeFinished = onCommit,
                )
                }
        }
    EnumChoiceMenu(
        expanded = enumOpen && enabled,
        title = descriptor.label,
        indices = if (descriptor.type == SynthEngine.ParameterType.BOOLEAN) 0..1 else
            remember(descriptor.options, descriptor.minimum, descriptor.maximum) {
                descriptor.options.indices.filter { it.toDouble() in descriptor.minimum..descriptor.maximum }
            },
        selectedIndex = parameter.value.roundToInt(),
        label = { if (descriptor.type == SynthEngine.ParameterType.BOOLEAN) {
            if (descriptor.path.endsWith("/volumeMinus")) { if (it == 0) "+" else "−" }
            else if (it == 0) "Off" else "On"
        } else descriptor.options[it] },
        onDismiss = { enumOpen = false },
        onSelect = { onWrite(parameter, it.toDouble()); enumOpen = false },
    )
    ControlPopup(numberOpen && enabled, descriptor.label, { numberOpen = false }) {
        Text(draft.roundToInt().toString(), color = Color(0xFFFF9A16),
            fontFamily = FontFamily(Font(R.font.vt323regular)), fontSize = 18.sp)
        if (descriptor.minimum < descriptor.maximum) {
            Slider(value = draft.coerceIn(descriptor.minimum.toFloat(), descriptor.maximum.toFloat()),
                onValueChange = { draft = it.roundToInt().toFloat() },
                valueRange = descriptor.minimum.toFloat()..descriptor.maximum.toFloat())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { draft = (draft - 1).coerceAtLeast(descriptor.minimum.toFloat()) }) { Text("−", fontFamily = FontFamily(Font(R.font.vt323regular))) }
            TextButton(onClick = { draft = (draft + 1).coerceAtMost(descriptor.maximum.toFloat()) }) { Text("+", fontFamily = FontFamily(Font(R.font.vt323regular))) }
            TextButton(onClick = { onWrite(parameter, draft.toDouble()); numberOpen = false }) { Text("Apply", fontFamily = FontFamily(Font(R.font.vt323regular))) }
        }
        if (onReset != null) TextButton(onClick = { onReset(parameter); numberOpen = false }) { Text("Reset", fontFamily = FontFamily(Font(R.font.vt323regular))) }
    }
    }
}

@Composable
internal fun EnumChoiceMenu(
    expanded: Boolean,
    title: String,
    indices: Iterable<Int>,
    selectedIndex: Int,
    label: (Int) -> String,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    ControlPopup(expanded, title, onDismiss) {
                indices.forEach { index ->
                        val selected = index == selectedIndex
                        Text(label(index), color = if (selected) Color(0xFFFF9A16) else Color(0xFFE7E2DA),
                            fontFamily = FontFamily(Font(R.font.vt323regular)), fontSize = 18.sp,
                            modifier = Modifier.fillMaxWidth()
                                .selectable(selected = selected, role = Role.RadioButton) {
                                    onSelect(index)
                                }.semantics { stateDescription = if (selected) "Selected" else "Not selected" }
                                .padding(horizontal = 8.dp, vertical = 5.dp))
                }
    }
}

@Composable
private fun ControlPopup(expanded: Boolean, title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 160.dp, max = 300.dp).heightIn(max = 400.dp)
            .background(Color(0xFF242220))
            .border(1.dp, Color(0xFF5A5651), RoundedCornerShape(9.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(9.dp),
        containerColor = Color(0xFF242220),
    ) {
                Text(title, color = Color(0xFFE7E2DA), fontSize = 18.sp,
                    fontFamily = FontFamily(Font(R.font.vt323regular)),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
                androidx.compose.material3.HorizontalDivider(color = Color(0xFF514D48))
                content()
    }
}

internal fun addLabelHue(descriptor: SynthEngine.ParameterDescriptor): Float = when {
    descriptor.path == "add/randomGrouping" -> 285f
    descriptor.path.startsWith("part/portamento") -> 32f
    descriptor.family == SynthEngine.ParameterFamily.ENVELOPE ||
        descriptor.group.contains("envelope", ignoreCase = true) ||
        descriptor.path.contains("Envelope") -> 188f
    descriptor.family == SynthEngine.ParameterFamily.LFO ||
        descriptor.group.contains("LFO", ignoreCase = true) ||
        descriptor.path.contains("Lfo") -> 270f
    descriptor.group.endsWith("/ Modulation") -> 286f
    descriptor.group.endsWith("/ Voice Oscillator") -> 32f
    descriptor.group.endsWith("/ Filter") -> 48f
    descriptor.group.endsWith("/ Frequency") -> 140f
    descriptor.group.endsWith("/ Amplitude") -> 205f
    descriptor.group.endsWith("/ Voice") -> 188f
    descriptor.path.startsWith("add/punch") -> 24f
    descriptor.path.startsWith("add/filter/") ||
        descriptor.path.startsWith("add/filterVelocity") -> 48f
    descriptor.path in setOf("add/detune", "add/coarse", "add/octave", "add/detuneType") -> 140f
    else -> 188f
}

private fun compactAddLabel(descriptor: SynthEngine.ParameterDescriptor): String = when {
    descriptor.path.endsWith("/resonance") -> "Resonance"
    descriptor.path == "part/velocitySense" -> "Vel. sns"
    descriptor.path == "part/velocityOffset" -> "Vel. off"
    descriptor.path == "part/keyShift" -> "Key shift"
    descriptor.path == "part/channel" -> "Channel"
    descriptor.path == "part/keyLimit" -> "Key limit"
    descriptor.path == "part/minKey" -> "Min. key"
    descriptor.path == "part/maxKey" -> "Max. key"
    descriptor.path == "part/portamento" -> "Portamento"
    descriptor.path == "part/portamentoTime" -> "Port. time"
    descriptor.path == "part/portamentoStretch" -> "Port. str."
    descriptor.path == "add/resonance/maxDb" -> "Max dB"
    descriptor.path == "add/resonance/protectFundamental" -> "Prot. 1st"
    descriptor.path == "add/stereo" -> "Stereo"
    descriptor.path == "add/randomGrouping" -> "Random group"
    descriptor.path == "add/volume" -> "Volume"
    descriptor.path == "add/panning" -> "Panning"
    descriptor.path == "add/velocity" -> "Velocity"
    descriptor.path == "add/punchStrength" -> "Punch Str."
    descriptor.path == "add/punchTime" -> "Punch Time"
    descriptor.path == "add/punchStretch" -> "Punch Stret."
    descriptor.path == "add/punchVelocity" -> "Punch Vel."
    descriptor.path == "add/octave" -> "Octave"
    descriptor.path == "add/coarse" -> "Coarse Det."
    descriptor.path.endsWith("filterVelocityAmount") -> "Vel. amount"
    descriptor.path.endsWith("filterVelocitySense") ||
        descriptor.path.endsWith("filterVelocity") -> "Filter vel."
    descriptor.path.endsWith("/formant/count") -> "Formants"
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
        color = Color(0xFF252525),
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

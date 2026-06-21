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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.pow
import kotlinx.coroutines.launch

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
    val model = remember(engine) { InstrumentEditorViewModel(engine) }
    val state = model.state
    var selector by remember { mutableStateOf<String?>(null) }
    var specializedScreen by remember { mutableStateOf<String?>(null) }
    var editedParameter by remember { mutableStateOf<SynthEngine.ParameterValue?>(null) }
    var exportedFile by remember { mutableStateOf<File?>(null) }
    var exportedRevision by remember { mutableStateOf(0L) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val contentScroll = rememberScrollState()
    val sectionOffsets = remember(module, state.kitIndex) { mutableStateOf<Map<String, Int>>(emptyMap()) }
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

    if (specializedScreen == "oscillator") {
        AddOscillatorEditorScreen(model = model, onBack = { specializedScreen = null })
        return
    }
    if (specializedScreen == "resonance") {
        AddResonanceEditorScreen(
            model = model,
            heldNotes = heldNotes,
            keyboardOctaveShift = keyboardOctaveShift,
            onPressKeyboardNote = onPressKeyboardNote,
            onReleaseKeyboardNote = onReleaseKeyboardNote,
            onBack = { specializedScreen = null },
        )
        return
    }
    if (specializedScreen == "voiceDetail") {
        AddVoiceDetailScreen(
            model = model,
            onBack = { specializedScreen = null },
            onOpenOscillator = { specializedScreen = "oscillator" },
        )
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
            Column(Modifier.weight(1f).fillMaxSize()) {
                Surface(color = Color(0xFF102329), shadowElevation = 5.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text("‹ Part") }
                    Text(
                        "$module · P${state.partIndex + 1} K${state.kitIndex + 1}",
                        color = Color(0xFF8EF5EE),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.dirty) Text("●", color = Color(0xFFFFC857))
                    TextButton(onClick = { selector = "actions" }) { Text("⋮") }
                }
                val sectionNames = populatedSections(state.snapshot?.values.orEmpty(), module)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    sectionNames.forEachIndexed { index, tab ->
                        Surface(
                            color = if (activeAnchor(contentScroll.value, sectionNames, sectionOffsets.value) == tab) Color(0xFF23616A) else Color(0xFF182F35),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.combinedClickable(onClick = {
                                model.selectTab(tab)
                                scope.launch {
                                    contentScroll.animateScrollTo(sectionOffsets.value[tab] ?: 0)
                                }
                            }),
                        ) {
                            Text(tab, modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp))
                        }
                    }
                }
            }
                }

                Column(
                    Modifier.weight(1f).verticalScroll(contentScroll).padding(7.dp),
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
                                specializedScreen = if (section == "Resonance") "resonance" else null
                                if (specializedScreen == null) selector = "complex"
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
                                            specializedScreen = "voiceDetail"
                                        },
                                    )
                                })
                                "Resonance" -> ({
                                    Box(
                                        Modifier.fillMaxWidth().height(90.dp).clickable {
                                            model.selectTab(section)
                                            specializedScreen = "resonance"
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
                ZynClassicKeyboardStrip(
                    heldNotes = heldNotes,
                    octaveShift = keyboardOctaveShift,
                    onPress = onPressKeyboardNote,
                    onRelease = onReleaseKeyboardNote,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 4.dp),
                )
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
            val voiceValues = values.filter { it.descriptor.group.endsWith("Voice ${voice + 1}") }
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
    onBack: () -> Unit,
    onOpenOscillator: () -> Unit,
) {
    val state = model.state
    val values = state.snapshot?.values.orEmpty().filter {
        it.descriptor.group.endsWith("Voice ${state.selectedVoice + 1}")
    }
    Column(Modifier.fillMaxSize()) {
        EditorSubScreenHeader("Voice ${state.selectedVoice + 1} details", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Button(onClick = onOpenOscillator, modifier = Modifier.fillMaxWidth()) {
                Text("Edit voice oscillator")
            }
            listOf("Voice", "Frequency", "Amplitude", "Filter", "Modulation").forEach { section ->
                val selected = values.filter { value ->
                    when (section) {
                        "Voice" -> value.descriptor.path.substringAfterLast('/') in setOf(
                            "unison", "spread", "phaseRandom", "stereoSpread", "vibratoSpeed"
                        )
                        "Frequency" -> value.descriptor.path.endsWith("/fixedFreq") ||
                            value.descriptor.path.endsWith("/detune")
                        "Amplitude" -> value.descriptor.path.endsWith("/volume") ||
                            value.descriptor.path.endsWith("/panning")
                        "Filter" -> value.descriptor.path.endsWith("/filter") ||
                            value.descriptor.path.endsWith("/resonance")
                        else -> value.descriptor.path.endsWith("/fmType")
                    }
                }
                if (selected.isNotEmpty()) {
                    Text(section.uppercase(), color = Color(0xFF66F0E9), style = MaterialTheme.typography.labelSmall)
                    DenseParameterGrid(selected, model::write) { model.reset(it) }
                }
            }
        }
    }
}

@Composable
private fun AddOscillatorEditorScreen(model: InstrumentEditorViewModel, onBack: () -> Unit) {
    val values = model.state.snapshot?.values.orEmpty()
    val voice = model.state.selectedVoice
    val group = "ADD / Voice ${voice + 1} / Oscillator"
    val controls = values.filter { it.descriptor.group == group }
    val magnitudes = values.filter { it.descriptor.group == "$group harmonics" }
    val phases = values.filter { it.descriptor.group == "$group phases" }
    Column(Modifier.fillMaxSize()) {
        EditorSubScreenHeader("Voice ${voice + 1} oscillator", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OscillatorPreview(magnitudes, phases, Modifier.weight(1f).height(150.dp))
                BaseFunctionPreview(controls, Modifier.weight(1f).height(150.dp))
            }
            Spacer(Modifier.height(6.dp))
            DenseParameterGrid(controls, model::write) { model.reset(it) }
            Text("HARMONICS", color = Color(0xFF66F0E9), modifier = Modifier.padding(vertical = 5.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                magnitudes.forEachIndexed { index, magnitude ->
                    val phase = phases.getOrNull(index)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.Slider(
                            value = magnitude.value.toFloat(),
                            onValueChange = { model.write(magnitude, it.toDouble()) },
                            valueRange = 0f..127f,
                            modifier = Modifier.width(76.dp),
                        )
                        phase?.let {
                            androidx.compose.material3.Slider(
                                value = it.value.toFloat(),
                                onValueChange = { v -> model.write(it, v.toDouble()) },
                                valueRange = 0f..127f,
                                modifier = Modifier.width(76.dp),
                            )
                        }
                        Text("${index + 1}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddResonanceEditorScreen(
    model: InstrumentEditorViewModel,
    heldNotes: Set<Int>,
    keyboardOctaveShift: Int,
    onPressKeyboardNote: (Int) -> Unit,
    onReleaseKeyboardNote: (Int) -> Unit,
    onBack: () -> Unit,
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

    Column(Modifier.fillMaxSize()) {
        EditorSubScreenHeader("ADsynth Resonance", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            ResonanceCurve(
                points = points,
                enabled = enabled?.value?.let { it >= .5 } == true,
                modifier = Modifier.fillMaxWidth().height(260.dp).onSizeChanged { graphSize = it }.pointerInput(graphSize) {
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
private fun EditorSubScreenHeader(title: String, onBack: () -> Unit) {
    Surface(color = Color(0xFF102329)) {
        Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text(title, color = Color(0xFF8EF5EE), style = MaterialTheme.typography.titleMedium)
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
    modifier: Modifier,
) {
    Canvas(modifier) {
        drawRect(Color.Black)
        val path = Path()
        repeat(128) { x ->
            var sample = 0.0
            magnitudes.take(32).forEachIndexed { h, value ->
                val amplitude = (value.value - 64.0) / 63.0
                val phase = phases.getOrNull(h)?.value?.div(127.0)?.times(Math.PI * 2) ?: 0.0
                sample += amplitude * kotlin.math.sin((h + 1) * x / 128.0 * Math.PI * 2 + phase)
            }
            val px = size.width * x / 127f
            val py = size.height * (.5f - (sample / 8.0).coerceIn(-.45, .45)).toFloat()
            if (x == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, Color.Green, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
    }
}

@Composable
private fun BaseFunctionPreview(controls: List<SynthEngine.ParameterValue>, modifier: Modifier) {
    val shape = controls.firstOrNull { it.descriptor.path.endsWith("/osc/baseShape") }?.value ?: 64.0
    Canvas(modifier) {
        drawRect(Color.Black)
        val path = Path()
        repeat(128) { x ->
            val px = size.width * x / 127f
            val power = .5 + shape / 64.0
            val y = kotlin.math.sin(x / 127.0 * Math.PI * 2)
            val shaped = kotlin.math.sign(y) * kotlin.math.abs(y).pow(power)
            val py = size.height * (.5f - shaped.toFloat() * .42f)
            if (x == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, Color.Green, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
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
) {
    val descriptor = parameter.descriptor
    Surface(
        modifier = Modifier.fillMaxWidth().combinedClickable(
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
                    onCheckedChange = { onWrite(parameter, if (it) 1.0 else 0.0) },
                )
                SynthEngine.ParameterType.ENUM -> if (verticalLabel) {
                    TinyKnob(
                        label = "",
                        value = parameter.value.toFloat(),
                        min = descriptor.minimum.toFloat(),
                        max = descriptor.maximum.toFloat(),
                        dragRangePx = 28f * (descriptor.maximum - descriptor.minimum)
                            .toFloat().coerceAtLeast(1f),
                        onValueChange = { onWrite(parameter, it.roundToInt().toDouble()) },
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
                        onClick = { onLongPress(parameter) },
                    )
                }
                SynthEngine.ParameterType.INTEGER -> TinyKnob(
                    label = "",
                    value = parameter.value.toFloat(),
                    min = descriptor.minimum.toFloat(),
                    max = descriptor.maximum.toFloat(),
                    valueText = valueText,
                    onValueChange = { onWrite(parameter, it.roundToInt().toDouble()) },
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
private fun ParameterEditSheet(
    parameter: SynthEngine.ParameterValue,
    onDismiss: () -> Unit,
    onValue: (Double) -> Unit,
    onReset: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(parameter.descriptor.label, style = MaterialTheme.typography.titleMedium)
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

@Composable
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

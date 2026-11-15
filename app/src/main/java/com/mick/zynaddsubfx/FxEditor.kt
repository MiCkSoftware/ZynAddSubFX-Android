package com.mick.zynaddsubfx

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun FxEditor(
    engine: SynthEngine,
    partIndex: Int,
    heldNotes: Set<Int>,
    keyboardOctaveShift: Int,
    onBack: () -> Unit,
    onPressKeyboardNote: (Int) -> Unit,
    onReleaseKeyboardNote: (Int) -> Unit,
    modifier: Modifier = Modifier,
    initialAddress: FxAddress = FxAddress(part = partIndex),
) {
    val address = initialAddress
    val model = remember(engine, address) { FxEditorModel(engine, address) }
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    var selectedBand by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(address) { filterOpen = false; model.open(address) }
    DisposableEffect(model) { onDispose { model.commit() } }
    BackHandler(filterOpen) { model.commit(); filterOpen = false }
    val snapshot = model.snapshot
    val goBack = { model.commit(); if (filterOpen) filterOpen = false else onBack() }
    val write: (SynthEngine.ParameterValue, Double) -> Unit = { p, v -> model.write(p, v) }
    val drag: (SynthEngine.ParameterValue, Double) -> Unit = { p, v -> model.write(p, v, continuous = true) }
    SynthEditorScaffold(
        title = if (filterOpen) "Filter" else "${when (address.scope) {
            FxScope.PART -> "Part"
            FxScope.INSERT -> "Insert"
            FxScope.SYSTEM -> "Master"
        }} ${address.slot + 1}",
        contextPath = "${address.context}${snapshot?.let { " / ${it.typeName}" }.orEmpty()}",
        navigationLabel = "‹ FX",
        onNavigateUp = goBack,
        tabs = emptyList(),
        selectedTab = "",
        onTabSelected = {},
        dirty = model.dirty, onActions = null,
        heldNotes = heldNotes, keyboardOctaveShift = keyboardOctaveShift,
        onPressKeyboardNote = onPressKeyboardNote, onReleaseKeyboardNote = onReleaseKeyboardNote,
        modifier = modifier,
        headerActions = {
            if (snapshot != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    LedButton("Reset effect to current preset", true, { model.action("reset") },
                        Modifier.width(28.dp), hue = 2f, action = true, displayLabel = "R", compact = true,
                        enabled = snapshot.type != 0)
                    LedButton("Empty effect audio memory", true, { model.action("cleanup") },
                        Modifier.width(28.dp), hue = 28f, action = true, displayLabel = "E", compact = true,
                        enabled = snapshot.type != 0)
                    LedButton("Copy effect", true, { model.action(if (filterOpen) "copyFilter" else "copy") },
                        Modifier.width(28.dp), action = true, displayLabel = "C", compact = true)
                    val canPaste = model.clipboard == if (filterOpen) "filter" else "effect"
                    LedButton("Paste effect", canPaste, { model.action(if (filterOpen) "pasteFilter" else "paste") },
                        Modifier.width(28.dp), action = true, displayLabel = "P", compact = true, enabled = canPaste)
                }
            }
        },
    ) { contentModifier ->
        if (snapshot == null || snapshot.address != address) {
            Text(model.error ?: "Loading FX…", contentModifier.padding(12.dp))
        } else if (filterOpen && snapshot.type == 8) {
            val filter = remember(snapshot) { FilterModel.from(snapshot.parameters, ModuleAddress.Filter(fx = address)) }
            if (filter != null) FilterEditor(filter, model.preview, snapshot.revision,
                onWrite = write, onDrag = drag, onCommit = model::commit,
                onPreviewVowel = { engine.fxPreview(address, it) },
                onCopyVowel = { model.action("copyVowel", it) },
                onPasteVowel = { model.action("pasteVowel", it) },
                canPasteVowel = { model.clipboard == "vowel" },
                modifier = contentModifier.padding(7.dp), parameterValue = model::value)
        } else {
            val groups = remember(snapshot) {
                snapshot.parameters.groupBy {
                    if (address.scope == FxScope.INSERT && it.descriptor.group in setOf("Routing", "Mix"))
                        "Routing / Mix" else it.descriptor.group
                }
            }
            Column(contentModifier.verticalScroll(rememberScrollState()).padding(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
//                Text("Keyboard follows Play channel", style = MaterialTheme.typography.labelSmall)
//                if (address.scope == FxScope.INSERT) Text(when (snapshot.assignedPart) {
//                    -1 -> "No audio source assigned · this Insert receives no sound"
//                    -2 -> "Assigned to Master"
//                    else -> "Assigned to Part ${snapshot.assignedPart + 1}"
//                }, style = MaterialTheme.typography.labelSmall)
//                if (address.scope == FxScope.SYSTEM) Text("Requires a Part send to this System slot", style = MaterialTheme.typography.labelSmall)
                model.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                groups["Slot"]?.let { parameters ->
                    SectionBanner("Slot ${address.slot + 1} · ${snapshot.typeName}")
                    FxParameterGrid(model, parameters)
                }
                if (snapshot.type == 0) Text("Choose an effect")
                groups.forEach { (group, parameters) ->
                    if (group != "Slot" && !group.startsWith("Filter / ") &&
                        (!group.startsWith("Band ") || group == "Band ${selectedBand + 1}")) {
                        SectionBanner(group)
                        if (snapshot.type == 7 && group.startsWith("Band ")) {
                            FxEqGraph(model, selectedBand, { selectedBand = it })
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                repeat(8) { band ->
                                    val active = snapshot.parameter("fx/parameter/${10 + band * 5}")?.value != 0.0
                                    LedButton("Band ${band + 1}${if (!active) " Off" else ""}", selectedBand == band,
                                        { model.commit(); selectedBand = band }, Modifier.width(48.dp),
                                        hue = subHarmonicHue(band), displayLabel = "${band + 1}${if (!active) " Off" else ""}")
                                }
                            }
                        }
                        if (group == "Timing") Text("Reference: ${snapshot.tempo.roundToInt()} BPM · numerator 0 = free",
                            style = MaterialTheme.typography.labelSmall)
                        FxParameterGrid(model, parameters)
                    }
                }
                if (snapshot.type == 8) {
                    SectionBanner("Filter")
                    Text("Static native filter response · modulation excluded", style = MaterialTheme.typography.labelSmall)
                    ModulePreview(model.preview, Modifier.fillMaxWidth().height(92.dp)
                        .clickable(onClickLabel = "Edit filter") { model.commit(); filterOpen = true },
                        accent = Color(0xFFFFC66A))
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun FxParameterGrid(model: FxEditorModel, parameters: List<SynthEngine.ParameterValue>) {
    val snapshot = model.snapshot ?: return
    // Only the small, visible group is projected during a drag, never a complete instrument or filter snapshot.
    val displayed = parameters.map { it.copy(value = model.value(it)) }
    DenseParameterGrid(displayed,
        onWrite = { p, v -> model.write(p, v) },
        onDrag = { p, v -> model.write(p, v, continuous = true) }, onCommit = model::commit,
        verticalLabels = true, onLongPress = {}, enumContext = snapshot.address,
        enabled = { fxControlEnabled(snapshot.type, it, snapshot.parameters) },
        valueText = { fxValueText(snapshot.type, it) },
        onReset = { model.write(it, it.descriptor.defaultValue) })
}

@Composable
private fun FxEqGraph(model: FxEditorModel, selected: Int, onSelect: (Int) -> Unit) {
    val snapshot = model.snapshot ?: return
    val latestModel by rememberUpdatedState(model)
    fun value(index: Int): Double = snapshot.parameter("fx/parameter/$index")?.let(model::value) ?: 0.0
    val labelPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(150, 190, 194) } }
    val colors = remember { (0..7).map { Color.hsv(subHarmonicHue(it), .7f, .95f) } }
    fun xFor(raw: Double) = (ln(600.0 * 30.0.pow((raw - 64.0) / 64.0) / 20.0) / ln(1000.0)).toFloat().coerceIn(0f, 1f)
    fun markerY(band: Int): Float = if (value(10 + 5 * band).toInt() in 7..9)
        (.5 - (value(12 + 5 * band) - 64.0) / 128.0).toFloat() else .5f
    val latestX by rememberUpdatedState((0..7).map { xFor(value(11 + 5 * it)) })
    val latestY by rememberUpdatedState((0..7).map(::markerY))
    val latestActive by rememberUpdatedState((0..7).map { value(10 + 5 * it) != 0.0 })
//    Text("20 Hz — 20 kHz · ±30 dB${if (model.dragging) " · release to update curve" else ""}",style = MaterialTheme.typography.labelSmall)
    if (model.preview.values.any { it < -30f || it > 30f }) Text("Response extends beyond ±30 dB", style = MaterialTheme.typography.labelSmall) // FIXME need red flag or something
    Canvas(Modifier.fillMaxWidth().height(200.dp).background(SynthGraphBackground).semantics { contentDescription = "EQ frequency response; select a band below or drag its marker" }.pointerInput(snapshot.address) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val band = (0..7).filter { latestActive[it] }.minByOrNull {
                (Offset(latestX[it] * size.width, latestY[it] * size.height) - down.position).getDistance()
            } ?: return@awaitEachGesture
            if ((Offset(latestX[band] * size.width, latestY[band] * size.height) - down.position).getDistance() > 28.dp.toPx()) return@awaitEachGesture
            onSelect(band)
            down.consume()
            try {
                drag(down.id) { change ->
                    val current = latestModel.snapshot ?: return@drag
                    val frequency = current.parameter("fx/parameter/${11 + band * 5}") ?: return@drag
                    val hz = 20.0 * 1000.0.pow((change.position.x / size.width).toDouble().coerceIn(0.0, 1.0))
                    latestModel.write(frequency, 64.0 + 64.0 * ln(hz / 600.0) / ln(30.0), continuous = true)
                    val bandType = current.parameter("fx/parameter/${10 + band * 5}")?.value?.toInt()
                    if (bandType in 7..9) current.parameter("fx/parameter/${12 + band * 5}")?.let { gain ->
                        latestModel.write(gain, 64.0 + (.5 - change.position.y / size.height) * 128.0, continuous = true)
                    }
                    change.consume()
                }
            } finally { latestModel.commit() }
        }
    }) {
        labelPaint.textSize = 10.dp.toPx()
        listOf(-30, -15, 0, 15, 30).forEach { db ->
            val y = size.height * (.5f - db / 60f)
            drawLine(Color(0xFF244044), Offset(0f, y), Offset(size.width, y))
            drawContext.canvas.nativeCanvas.drawText("$db dB", 3.dp.toPx(), (y - 2.dp.toPx()).coerceIn(12.dp.toPx(), size.height - 14.dp.toPx()), labelPaint)
        }
        listOf(20.0, 100.0, 1000.0, 10000.0, 20000.0).forEach { hz ->
            val x = (ln(hz / 20.0) / ln(1000.0)).toFloat() * size.width
            drawLine(Color(0xFF244044), Offset(x, 0f), Offset(x, size.height))
            val label = if (hz >= 1000) "${(hz / 1000).toInt()}k" else hz.toInt().toString()
            drawContext.canvas.nativeCanvas.drawText(label, x.coerceIn(0f, size.width - labelPaint.measureText(label)), size.height - 2.dp.toPx(), labelPaint)
        }
        val curve = model.preview.values
        if (curve.size > 1) {
            val path = Path()
            curve.forEachIndexed { n, db ->
                val x = n * size.width / (curve.size - 1)
                val y = (.5f - db.coerceIn(-30f, 30f) / 60f) * size.height
                if (n == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawNeonCurve(path, Color(0xFF8CF5F1))
        }
        repeat(8) { band ->
            if (latestActive[band]) {
                val point = Offset(latestX[band] * size.width, latestY[band] * size.height)
                drawCircle(colors[band].copy(alpha = .25f), if (band == selected) 14.dp.toPx() else 10.dp.toPx(), point)
                drawCircle(colors[band], if (band == selected) 6.dp.toPx() else 4.dp.toPx(), point)
                drawContext.canvas.nativeCanvas.drawText("${band + 1}", (point.x + 8.dp.toPx()).coerceAtMost(size.width - 10.dp.toPx()), (point.y - 8.dp.toPx()).coerceAtLeast(12.dp.toPx()), labelPaint)
            }
        }
    }
}

@Composable
internal fun FxSlotRows(
    engine: SynthEngine,
    scope: FxScope,
    part: Int,
    onOpen: (FxAddress) -> Unit,
    onChanged: () -> Unit = {},
) {
    var revision by remember(engine, scope, part) { mutableIntStateOf(0) }
    val slots = remember(engine, scope, part, revision) {
        (0 until scope.slots).mapNotNull { engine.fxSnapshot(FxAddress(scope, part, it)) }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        slots.forEach { snapshot ->
            val active = snapshot.type != 0 && snapshot.parameter("fx/bypass")?.value == 0.0
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("FX ${snapshot.address.slot + 1} · ${snapshot.typeName}", color = Color(0xFFA7F4F0),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f).semantics { contentDescription = "${snapshot.address.context} · ${snapshot.typeName}" }.clickable { onOpen(snapshot.address) })
                LedButton("${snapshot.address.context} enabled", active, {
                    if (snapshot.type == 0) onOpen(snapshot.address)
                    else snapshot.parameter("fx/bypass")?.let {
                        if (engine.writeFx(snapshot, it, if (active) 1.0 else 0.0)) {
                            revision++; onChanged()
                        }
                    }
                }, Modifier.width(LedSlotWidth.dp), displayLabel = if (active) "ON" else "OFF")
                Text("›", color = Color(0xFFA7F4F0), modifier = Modifier.width(22.dp)
                    .clickable { onOpen(snapshot.address) }.padding(5.dp))
            }
        }
    }
}

@Composable
internal fun MasterFxScreen(
    engine: SynthEngine,
    part: Int,
    heldNotes: Set<Int>,
    octave: Int,
    onBack: () -> Unit,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf<FxAddress?>(null) }
    val address = selected
    if (address != null) {
        BackHandler { selected = null }
        FxEditor(engine, part, heldNotes, octave, { selected = null }, onPress, onRelease,
            modifier = modifier, initialAddress = address)
    } else SynthEditorScaffold("FX", "Master", "‹ Play", onBack,
        emptyList(), "", {}, false, null, heldNotes, octave, onPress, onRelease, modifier = modifier) { content ->
        Column(content.verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionBanner("Master / System")
            Surface(color = Color(0xFF161616), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(8.dp)) { FxSlotRows(engine, FxScope.SYSTEM, part, { selected = it }) }
            }
            SectionBanner("Insert")
            Surface(color = Color(0xFF161616), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(8.dp)) { FxSlotRows(engine, FxScope.INSERT, part, { selected = it }) }
            }
        }
    }
}

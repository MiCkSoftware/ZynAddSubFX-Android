@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mick.zynaddsubfx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.log2
import kotlin.math.pow

@Composable
private fun FormantResponsePreview(
    model: FilterModel,
    preview: PreviewSeries,
    vowel: Int,
    formant: Int,
    modifier: Modifier = Modifier,
) {
    val prefix = "${model.prefix}/formant"
    val values = model.formant?.parameters.orEmpty()
    fun value(path: String, fallback: Double = 64.0) =
        values.firstOrNull { it.descriptor.path == path }?.value ?: fallback
    val centerHz = 10000.0 * 10.0.pow(-(1.0 - value("$prefix/center") / 127.0) * 2.0)
    val octaves = .25 + 10.0 * value("$prefix/octaves") / 127.0
    val lowHz = centerHz / 2.0.pow(octaves / 2.0)
    val markerValue = value("$prefix/vowel/$vowel/$formant/frequency")
    val markerHz = lowHz * 2.0.pow(octaves * markerValue / 127.0)
    val amplitude = value("$prefix/vowel/$vowel/$formant/amplitude", 127.0)
    val gain = model.parameters.firstOrNull { it.descriptor.path.endsWith("/gain") ||
        it.descriptor.path.endsWith("filterGain") }?.value ?: 64.0
    val markerDb = -80.0 * (1.0 - amplitude / 127.0) + (gain / 64.0 - 1.0) * 30.0
    Canvas(modifier.background(Color(0xFF09191D), RoundedCornerShape(7.dp))) {
        val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(145, 179, 183)
            textSize = 10.dp.toPx()
        }
        listOf(-30, -15, 0, 15, 30).forEach { db ->
            val y = size.height * (.5f - db / 30f * .45f)
            drawLine(Color(0xFF244047), Offset(0f, y), Offset(size.width, y), 1f)
            drawContext.canvas.nativeCanvas.drawText("$db", 4.dp.toPx(), y - 2.dp.toPx(), labelPaint)
        }
        listOf(100, 200, 500, 1000, 2000, 5000, 10000, 20000).forEach { hz ->
            val fraction = (log2(hz / lowHz) / octaves).toFloat()
            if (fraction in 0f..1f) {
                val x = size.width * fraction
                drawLine(Color(0xFF244047), Offset(x, 0f), Offset(x, size.height), 1f)
                val label = if (hz >= 1000) "${hz / 1000}k" else "$hz"
                drawContext.canvas.nativeCanvas.drawText(label, x + 2.dp.toPx(),
                    size.height - 4.dp.toPx(), labelPaint)
            }
        }
        val markerX = size.width * (markerValue / 127.0).toFloat()
        drawLine(Color(0xFFE8CA58), Offset(markerX, 0f), Offset(markerX, size.height), 2f)
        if (preview.values.size > 1) {
            val path = Path()
            preview.values.forEachIndexed { index, value ->
                val x = size.width * index / (preview.values.size - 1f)
                val y = size.height * (.5f - value.coerceIn(-1f, 1f) * .45f)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Color(0xFFFF7048), style = Stroke(2.5f, cap = StrokeCap.Round))
        }
        labelPaint.color = android.graphics.Color.rgb(255, 215, 103)
        drawContext.canvas.nativeCanvas.drawText(
            "F${formant + 1} · ${"%.2f".format(markerHz / 1000.0)} kHz · ${markerDb.roundToInt()} dB",
            5.dp.toPx(), 13.dp.toPx(), labelPaint,
        )
    }
}

@Composable
fun CollapsingPreviewLayout(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    expandedTop: Dp = 30.dp,
    previewHorizontalPadding: Dp = 7.dp,
    maskBehindPreview: Boolean = false,
    preview: @Composable (Modifier) -> Unit,
    content: @Composable ColumnScope.(Dp) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val transition = (scrollState.value / 360f).coerceIn(0f, 1f)
        val expandedHeight = 210.dp
        val previewHeight = expandedHeight + (104.dp - expandedHeight) * transition
        val previewTop = expandedTop + (4.dp - expandedTop) * transition
        val bottomPadding = (maxHeight - 180.dp).coerceAtLeast(112.dp)
        Column(
            Modifier.fillMaxSize().verticalScroll(scrollState).padding(vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content(bottomPadding)
        }
        if (maskBehindPreview) {
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .height(previewTop + previewHeight + 8.dp)
                .background(Color(0xFF061419)))
        }
        preview(Modifier.align(Alignment.TopCenter).offset(y = previewTop)
            .fillMaxWidth().height(previewHeight).padding(horizontal = previewHorizontalPadding))
    }
}

@Composable
fun ModulePreview(
    series: PreviewSeries,
    modifier: Modifier = Modifier,
    accent: Color = Color(0xFF66F0E9),
    sustainFraction: Float? = null,
) {
    Canvas(
        modifier.background(Color(0xFF09191D), RoundedCornerShape(7.dp)),
    ) {
        drawLine(Color(0xFF214047), Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f))
        for (division in 1..3) {
            val x = size.width * division / 4f
            drawLine(Color(0xFF173238), Offset(x, 0f), Offset(x, size.height))
        }
        sustainFraction?.let {
            val x = size.width * it.coerceIn(0f, 1f)
            drawLine(Color(0xFFE8CA58), Offset(x, 0f), Offset(x, size.height), 2f)
        }
        if (series.values.size > 1) {
            val path = Path()
            series.values.forEachIndexed { index, value ->
                val point = Offset(
                    size.width * index / (series.values.size - 1f),
                    size.height * (.5f - value.coerceIn(-1f, 1f) * .45f),
                )
                if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(path, accent, style = Stroke(2.5f, cap = StrokeCap.Round))
        }
    }
}

@Composable
fun ModuleClipboardActions(
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    canPaste: Boolean,
    onOpen: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LuminousActionButton("Copy", onCopy, Modifier.weight(1f))
        LuminousActionButton("Paste", onPaste, Modifier.weight(1f), enabled = canPaste)
        onOpen?.let { LuminousActionButton("Edit", it, Modifier.weight(1f)) }
    }
}

@Composable
private fun CommonParameterGrid(
    parameters: List<SynthEngine.ParameterValue>,
    onWrite: (SynthEngine.ParameterValue, Double) -> Unit,
    onDrag: (SynthEngine.ParameterValue, Double) -> Unit = onWrite,
    onCommit: () -> Unit = {},
    enabled: Boolean = true,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 350.dp) 3 else 4
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            parameters.chunked(columns).forEach { rowParameters ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    rowParameters.forEach { parameter ->
                        Box(Modifier.weight(1f)) {
                            DenseParameterControl(
                                parameter = parameter,
                                onWrite = onWrite,
                                onLongPress = {},
                                verticalLabel = true,
                                enabled = enabled,
                                onDrag = onDrag,
                                onCommit = onCommit,
                            )
                        }
                    }
                    repeat(columns - rowParameters.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CompactModuleActions(
    title: String,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    canPaste: Boolean,
    onOpen: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        LuminousActionButton("C", onCopy, Modifier.size(27.dp), compact = true, description = "Copy $title")
        LuminousActionButton("P", onPaste, Modifier.size(27.dp), enabled = canPaste, compact = true, description = "Paste $title")
        LuminousActionButton("E", onOpen, Modifier.size(27.dp), compact = true, description = "Edit $title")
    }
}

@Composable
private fun EnvelopeCurve(
    model: EnvelopeModel,
    preview: PreviewSeries,
    modifier: Modifier = Modifier,
    selectedPoint: Int? = null,
    onSelectPoint: ((Int) -> Unit)? = null,
    onWritePoint: ((SynthEngine.ParameterValue, Double, Boolean) -> Unit)? = null,
    onCommit: () -> Unit = {},
) {
    val points = model.points
    val positions = envelopePositions(points)
    val currentPoints by rememberUpdatedState(points)
    val currentPositions by rememberUpdatedState(positions)
    val currentOnCommit by rememberUpdatedState(onCommit)
    val interactive = model.freeMode && onSelectPoint != null && onWritePoint != null
    val touchModifier = if (interactive) modifier
        .pointerInput(model.prefix, model.freeMode) {
            detectTapGestures { position ->
                val targetX = position.x / size.width
                onSelectPoint?.invoke(currentPoints.minByOrNull { abs((currentPositions.getOrNull(it.index) ?: 0f) - targetX) }?.index ?: 0)
            }
        }
        .pointerInput(model.prefix, model.freeMode) {
            var timeDrag = 0f
            var valueDrag = 0f
            var draggedPoint = 0
            var dragPoints = currentPoints
            detectDragGestures(
                onDragStart = { position ->
                    val targetX = position.x / size.width
                    dragPoints = currentPoints
                    draggedPoint = dragPoints.minByOrNull { abs((currentPositions.getOrNull(it.index) ?: 0f) - targetX) }?.index ?: 0
                    onSelectPoint?.invoke(draggedPoint)
                    timeDrag = 0f
                    valueDrag = 0f
                },
                onDragEnd = { currentOnCommit() },
            ) { change, drag ->
                change.consume()
                dragPoints.getOrNull(draggedPoint)?.let { point ->
                    valueDrag += drag.y
                    onWritePoint?.invoke(point.value, point.value.value - valueDrag / size.height *
                        (point.value.descriptor.maximum - point.value.descriptor.minimum), false)
                    if (draggedPoint > 0) {
                        timeDrag += drag.x
                        onWritePoint?.invoke(point.time, point.time.value + timeDrag / size.width * 127.0, false)
                    }
                }
            }
        } else modifier
    Box(touchModifier) {
        ModulePreview(preview, Modifier.fillMaxSize(), sustainFraction = model.sustainPoint?.let { positions.getOrNull(it) })
        if (model.freeMode) Canvas(Modifier.fillMaxSize()) {
            points.forEach { point ->
                val range = (point.value.descriptor.maximum - point.value.descriptor.minimum).coerceAtLeast(1.0)
                drawCircle(
                    if (point.index == selectedPoint) Color.Cyan else Color.White,
                    radius = if (point.index == selectedPoint) 9f else 5f,
                    center = Offset(
                        size.width * (positions.getOrNull(point.index) ?: 0f),
                        size.height * (1f - ((point.value.value - point.value.descriptor.minimum) / range).toFloat()),
                    ),
                )
            }
        }
    }
}

private fun List<SynthEngine.ParameterValue>.fields(vararg names: String): List<SynthEngine.ParameterValue> =
    names.mapNotNull { name -> firstOrNull { it.descriptor.path.substringAfterLast('/') == name ||
        it.descriptor.path.substringAfterLast('/').equals("filter${name.replaceFirstChar(Char::uppercase)}") } }

@Composable
fun EnvelopeUI(
    model: EnvelopeModel,
    preview: PreviewSeries,
    onWrite: (SynthEngine.ParameterValue, Double) -> Unit,
    onOpenEditor: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    canPaste: Boolean,
    modifier: Modifier = Modifier,
    onDrag: (SynthEngine.ParameterValue, Double) -> Unit = onWrite,
    onCommit: () -> Unit = {},
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CompactModuleActions(model.title, onCopy, onPaste, canPaste, onOpenEditor)
        EnvelopeCurve(model, preview, Modifier.fillMaxWidth().height(92.dp).clickable(onClick = onOpenEditor))
        CommonParameterGrid(model.parameters.fields("attackTime", "decayTime", "sustain", "releaseTime", "stretch"), onWrite, onDrag, onCommit)
    }
}

@Composable
fun LFOUI(
    model: LfoModel,
    preview: PreviewSeries,
    onWrite: (SynthEngine.ParameterValue, Double) -> Unit,
    onOpenEditor: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    canPaste: Boolean,
    modifier: Modifier = Modifier,
    onDrag: (SynthEngine.ParameterValue, Double) -> Unit = onWrite,
    onCommit: () -> Unit = {},
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CompactModuleActions(model.title, onCopy, onPaste, canPaste, onOpenEditor)
        ModulePreview(preview, Modifier.fillMaxWidth().height(72.dp), accent = Color(0xFFC08BFF))
        CommonParameterGrid(model.parameters.fields("ampLfoEnabled", "freqLfoEnabled", "filterLfoEnabled", "frequency", "depth", "waveform"), onWrite, onDrag, onCommit)
    }
}

@Composable
fun FilterUI(
    model: FilterModel,
    preview: PreviewSeries,
    onWrite: (SynthEngine.ParameterValue, Double) -> Unit,
    onOpenEditor: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    canPaste: Boolean,
    modifier: Modifier = Modifier,
    onDrag: (SynthEngine.ParameterValue, Double) -> Unit = onWrite,
    onCommit: () -> Unit = {},
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CompactModuleActions("FILTER", onCopy, onPaste, canPaste, onOpenEditor)
        ModulePreview(
            preview,
            Modifier.fillMaxWidth().height(92.dp).clickable(onClick = onOpenEditor),
            accent = Color(0xFFFFC66A),
        )
        CommonParameterGrid(model.parameters.fields("category", "type", "cutoff", "q", "gain", "stages"), onWrite, onDrag, onCommit)
    }
}

@Composable
fun CommonSynthModules(
    model: InstrumentEditorViewModel,
    section: SynthEngine.ParameterSection,
    voiceIndex: Int = -1,
    onOpenEnvelope: (ModuleAddress.Envelope) -> Unit,
    onOpenLfo: (ModuleAddress.Lfo) -> Unit,
    onOpenFilter: (ModuleAddress.Filter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val values = model.state.snapshot?.values.orEmpty()
    val envelopeRoles = when (section) {
        SynthEngine.ParameterSection.AMPLITUDE -> listOf(EnvelopeRole.AMPLITUDE)
        SynthEngine.ParameterSection.FREQUENCY -> listOf(EnvelopeRole.FREQUENCY)
        SynthEngine.ParameterSection.FILTER -> listOf(EnvelopeRole.FILTER)
        SynthEngine.ParameterSection.MODULATION ->
            listOf(EnvelopeRole.MODULATOR_AMPLITUDE, EnvelopeRole.MODULATOR_FREQUENCY)
        else -> emptyList()
    }
    val lfoRole = when (section) {
        SynthEngine.ParameterSection.AMPLITUDE -> LfoRole.AMPLITUDE
        SynthEngine.ParameterSection.FREQUENCY -> LfoRole.FREQUENCY
        SynthEngine.ParameterSection.FILTER -> LfoRole.FILTER
        else -> null
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (section == SynthEngine.ParameterSection.FILTER) {
            val address = ModuleAddress.Filter(voiceIndex)
            FilterModel.from(values, address)?.let { filter ->
                val preview = remember(model.state.revision, address, filter.category) { model.preview(address) }
                FilterUI(
                    model = filter,
                    preview = preview,
                    onWrite = model::write,
                    onOpenEditor = { onOpenFilter(address) },
                    onCopy = { model.copyModule(address) },
                    onPaste = { model.pasteModule(address) },
                    canPaste = model.canPasteModule(address),
                    onDrag = model::dragParameter,
                    onCommit = model::finishParameterDrag,
                )
            }
        }
        envelopeRoles.forEach { role ->
            val address = ModuleAddress.Envelope(voiceIndex, role)
            EnvelopeModel.from(values, address)?.let { envelope ->
                val preview = remember(model.state.revision, address) { model.preview(address) }
                EnvelopeUI(
                    model = envelope,
                    preview = preview,
                    onWrite = model::write,
                    onOpenEditor = { onOpenEnvelope(address) },
                    onCopy = { model.copyModule(address) },
                    onPaste = { model.pasteModule(address) },
                    canPaste = model.canPasteModule(address),
                    onDrag = model::dragParameter,
                    onCommit = model::finishParameterDrag,
                )
            }
        }
        lfoRole?.let { role ->
            val address = ModuleAddress.Lfo(voiceIndex, role)
            LfoModel.from(values, address)?.let { lfo ->
                val preview = model.preview(address)
                LFOUI(
                    model = lfo,
                    preview = preview,
                    onWrite = model::write,
                    onOpenEditor = { onOpenLfo(address) },
                    onCopy = { model.copyModule(address) },
                    onPaste = { model.pasteModule(address) },
                    canPaste = model.canPasteModule(address),
                    onDrag = model::dragParameter,
                    onCommit = model::finishParameterDrag,
                )
            }
        }
    }
}

@Composable
fun LfoEditor(
    model: LfoModel,
    preview: PreviewSeries,
    onWrite: (SynthEngine.ParameterValue, Double) -> Unit,
    onDrag: (SynthEngine.ParameterValue, Double) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ModulePreview(preview, Modifier.fillMaxWidth().height(170.dp), accent = Color(0xFFC08BFF))
        val primary = model.parameters.fields("ampLfoEnabled", "freqLfoEnabled", "filterLfoEnabled", "frequency", "depth", "start", "delay", "stretch", "waveform")
        CommonParameterGrid(primary + model.parameters.filterNot { it in primary }, onWrite, onDrag, onCommit)
    }
}

@Composable
fun FilterEditor(
    model: FilterModel,
    preview: PreviewSeries,
    previewRevision: Long,
    onWrite: (SynthEngine.ParameterValue, Double) -> Unit,
    onDrag: (SynthEngine.ParameterValue, Double) -> Unit,
    onCommit: () -> Unit,
    onPreviewVowel: (Int) -> PreviewSeries,
    onCopyVowel: (Int) -> Unit,
    onPasteVowel: (Int) -> Unit,
    canPasteVowel: (Int) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    var selectedVowel by remember(model.prefix) { mutableIntStateOf(0) }
    var selectedFormant by remember(model.prefix) { mutableIntStateOf(0) }
    val selectedPreview = remember(previewRevision, selectedVowel, model.address, model.category) {
        if (model.category == 1 && selectedVowel != 0) onPreviewVowel(selectedVowel) else preview
    }
    CollapsingPreviewLayout(
        scrollState = scrollState,
        modifier = modifier,
        expandedTop = 4.dp,
        previewHorizontalPadding = 0.dp,
        maskBehindPreview = true,
        preview = { previewModifier ->
            if (model.category == 1 && model.formant != null) {
                FormantResponsePreview(model, selectedPreview, selectedVowel,
                    selectedFormant.coerceIn(0, model.formant.count - 1), previewModifier)
            } else ModulePreview(selectedPreview, previewModifier, accent = Color(0xFFFFC66A))
        },
    ) { bottomPadding ->
        Spacer(Modifier.fillMaxWidth().height(210.dp))
        val primary = model.parameters.fields("filter", "category", "type", "cutoff", "q", "gain", "stages", "tracking")
        CommonParameterGrid(primary, onWrite, onDrag, onCommit)
        CommonParameterGrid(
            model.parameters.filterNot { parameter ->
                parameter.descriptor.path.contains("/formant/") || parameter in primary
            },
            onWrite, onDrag, onCommit,
        )
        if (model.category == 1 && model.formant != null) FormantFilterEditor(
            model = model,
            selectedVowel = selectedVowel,
            onSelectVowel = { selectedVowel = it },
            selectedFormant = selectedFormant.coerceIn(0, model.formant.count - 1),
            onSelectFormant = { selectedFormant = it },
            onWrite = onWrite,
            onDrag = onDrag,
            onCommit = onCommit,
            onCopyVowel = onCopyVowel,
            onPasteVowel = onPasteVowel,
            canPasteVowel = canPasteVowel,
        )
        Spacer(Modifier.height(bottomPadding))
    }
}

@Composable
fun FreeEnvelopeEditor(
    model: EnvelopeModel,
    preview: PreviewSeries,
    onWrite: (SynthEngine.ParameterValue, Double, Boolean) -> Unit,
    onCommit: () -> Unit,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedPoint by remember(model.prefix) { mutableIntStateOf(1) }
    val points = model.points
    val activePoint = selectedPoint.coerceIn(0, points.lastIndex.coerceAtLeast(0))
    val primary = model.parameters.fields("attackValue", "attackTime", "decayValue", "decayTime", "sustain", "releaseValue", "releaseTime", "stretch")
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EnvelopeCurve(
            model, preview, Modifier.fillMaxWidth().height(210.dp), activePoint,
            onSelectPoint = { selectedPoint = it }, onWritePoint = onWrite, onCommit = onCommit,
        )
        CommonParameterGrid(model.parameters.filterNot { parameter ->
            parameter in primary || parameter.descriptor.path.contains("/point/") ||
                parameter.descriptor.path.endsWith("/pointCount") || parameter.descriptor.path.endsWith("/sustainPoint")
        }, onWrite = { parameter, value -> onWrite(parameter, value, true) },
            onDrag = { parameter, value -> onWrite(parameter, value, false) }, onCommit = onCommit)
        CommonParameterGrid(primary, onWrite = { parameter, value -> onWrite(parameter, value, true) },
            onDrag = { parameter, value -> onWrite(parameter, value, false) }, onCommit = onCommit)
        if (model.freeMode && points.isNotEmpty()) {
            Text("Point ${activePoint + 1} of ${points.size}")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LuminousActionButton("Previous", { selectedPoint = (selectedPoint - 1).coerceAtLeast(0) }, Modifier.weight(1f))
                LuminousActionButton("Next", { selectedPoint = (selectedPoint + 1).coerceAtMost(points.lastIndex) }, Modifier.weight(1f))
                LuminousActionButton("Add", {
                    onAction("${model.prefix}/insert/${activePoint.coerceAtMost(points.lastIndex - 1)}")
                }, Modifier.weight(1f), enabled = points.size < 40)
                LuminousActionButton("Delete", {
                    onAction("${model.prefix}/delete/$activePoint")
                    selectedPoint = (selectedPoint - 1).coerceAtLeast(0)
                }, Modifier.weight(1f), enabled = activePoint in 1 until points.lastIndex && points.size > 3)
            }
            CommonParameterGrid(
                parameters = listOfNotNull(
                    points.getOrNull(activePoint)?.time?.takeIf { activePoint > 0 },
                    points.getOrNull(activePoint)?.value,
                    model.parameter("sustainPoint"),
                ),
                onWrite = { parameter, value -> onWrite(parameter, value, true) },
                onDrag = { parameter, value -> onWrite(parameter, value, false) },
                onCommit = onCommit,
            )
            LuminousActionButton("Sustain at selected point", {
                model.parameter("sustainPoint")?.let { onWrite(it, activePoint.toDouble(), true) }
            }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun FormantFilterEditor(
    model: FilterModel,
    selectedVowel: Int,
    onSelectVowel: (Int) -> Unit,
    selectedFormant: Int,
    onSelectFormant: (Int) -> Unit,
    onWrite: (SynthEngine.ParameterValue, Double) -> Unit,
    onDrag: (SynthEngine.ParameterValue, Double) -> Unit,
    onCommit: () -> Unit,
    onCopyVowel: (Int) -> Unit,
    onPasteVowel: (Int) -> Unit,
    canPasteVowel: (Int) -> Boolean,
) {
    val formant = model.formant ?: return
    val prefix = "${model.prefix}/formant"
    fun find(path: String) = formant.parameters.firstOrNull { it.descriptor.path == path }
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Formant parameters", color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium)
        CommonParameterGrid(
            listOfNotNull(
                find("$prefix/count"), find("$prefix/slowness"), find("$prefix/clearness"),
                find("$prefix/center"), find("$prefix/octaves"),
            ), onWrite, onDrag, onCommit,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Vowel and formant", modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1, softWrap = false)
            LuminousActionButton("C", { onCopyVowel(selectedVowel) }, Modifier.size(27.dp),
                compact = true, description = "Copy vowel")
            LuminousActionButton("P", { onPasteVowel(selectedVowel) }, Modifier.size(27.dp),
                compact = true, enabled = canPasteVowel(selectedVowel),
                description = "Paste vowel")
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        val showFourthColumn = maxWidth >= 350.dp
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                DenseControlCard("Vowel", Color(0xFF4A3D1E)) {
                    TinyKnob(
                        label = "", value = (selectedVowel + 1).toFloat(),
                        min = 1f, max = FORMANT_VOWEL_SLOTS.toFloat(),
                        valueText = "${selectedVowel + 1}/$FORMANT_VOWEL_SLOTS",
                        dragRangePx = 220f,
                        onValueChange = {
                            onSelectVowel(it.roundToInt().minus(1).coerceIn(0, FORMANT_VOWEL_SLOTS - 1))
                        },
                    )
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                DenseControlCard("Formant", Color(0xFF4A3D1E)) {
                    TinyKnob(
                        label = "", value = (selectedFormant + 1).toFloat(),
                        min = 1f, max = formant.count.toFloat(),
                        valueText = "${selectedFormant + 1}/${formant.count}",
                        dragRangePx = 220f,
                        onValueChange = {
                            onSelectFormant(it.roundToInt().minus(1).coerceIn(0, formant.count - 1))
                        },
                    )
                }
            }
            repeat(if (showFourthColumn) 2 else 1) { Spacer(Modifier.weight(1f)) }
        }
        }
        CommonParameterGrid(
            listOfNotNull(
                find("$prefix/vowel/$selectedVowel/$selectedFormant/frequency"),
                find("$prefix/vowel/$selectedVowel/$selectedFormant/amplitude"),
                find("$prefix/vowel/$selectedVowel/$selectedFormant/q"),
            ),
            onWrite, onDrag, onCommit,
        )
        Text("Vowel sequence", color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium)
        CommonParameterGrid(
            listOfNotNull(find("$prefix/sequenceSize"), find("$prefix/sequenceStretch"),
                find("$prefix/sequenceReversed")), onWrite, onDrag, onCommit,
        )
        CommonParameterGrid(
            (0 until formant.sequenceSize).mapNotNull { find("$prefix/sequence/$it") },
            onWrite, onDrag, onCommit,
        )
        Spacer(Modifier.height(40.dp))
    }
}

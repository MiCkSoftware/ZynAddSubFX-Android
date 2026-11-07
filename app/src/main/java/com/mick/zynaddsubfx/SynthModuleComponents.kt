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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Piano
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
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

// Reuse the preview's existing samples for both the luminous stroke and its wash.
// Layered strokes keep the glow compatible with API 29 without an offscreen blur.
internal fun DrawScope.drawNeonCurve(path: Path, color: Color) {
    clipRect {
        val wash = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(wash, Brush.verticalGradient(listOf(
            color.copy(alpha = color.alpha * .18f),
            color.copy(alpha = color.alpha * .07f),
            Color.Transparent,
        )))
        // Fine opacity steps approximate a soft falloff without visible contour bands.
        for (layer in 12 downTo 1) {
            val opacity = .065f * kotlin.math.exp(-layer * layer / 32f)
            drawPath(path, color.copy(alpha = color.alpha * opacity),
                style = Stroke((1.5f + layer * 2f).dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        drawPath(path, color,
            style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, androidx.compose.ui.graphics.lerp(color, Color.White.copy(alpha = color.alpha), .55f),
            style = Stroke(.65.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

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
            drawNeonCurve(path, Color(0xFFFF7048))
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
            drawNeonCurve(path, accent)
        }
    }
}

@Composable
fun ModuleClipboardActions(
    title: String,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    canPaste: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        LedButton("Copy $title", enabled, onCopy, Modifier.size(27.dp),
            action = true, displayLabel = "C", enabled = enabled, compact = true)
        LedButton("Paste $title", canPaste && enabled, onPaste, Modifier.size(27.dp),
            action = true, displayLabel = "P", enabled = canPaste && enabled, compact = true)
    }
}

@Composable
private fun CommonParameterGrid(
    parameters: List<SynthEngine.ParameterValue>,
    onWrite: (SynthEngine.ParameterValue, Double) -> Unit,
    onDrag: (SynthEngine.ParameterValue, Double) -> Unit = onWrite,
    onCommit: () -> Unit = {},
    enabled: Boolean = true,
    columnsOverride: Int? = null,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = columnsOverride ?: if (maxWidth < 350.dp) 3 else 4
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
internal fun NeonHeaderIcon(title: String, color: Color, modifier: Modifier = Modifier.size(22.dp)) {
    val icon = remember(title) {
        val name = title.lowercase()
        when {
            "keyboard" in name || "key range" in name -> Icons.Outlined.Piano
            "kit" in name || "voice" in name -> Icons.Outlined.Layers
            "envelope" in name -> Icons.AutoMirrored.Outlined.ShowChart
            "lfo" in name || "oscillator" in name || "resonance" in name -> Icons.Outlined.Waves
            "filter" in name || "formant" in name -> Icons.Outlined.FilterAlt
            "frequency" in name || "harmonic" in name -> Icons.Outlined.GraphicEq
            "amp" in name -> Icons.AutoMirrored.Outlined.VolumeUp
            "fx" in name || "effect" in name -> Icons.Outlined.AutoAwesome
            "performance" in name -> Icons.Outlined.Speed
            else -> Icons.Outlined.Tune
        }
    }
    Box(modifier.background(remember(color) {
        Brush.radialGradient(listOf(color.copy(alpha = .28f), color.copy(alpha = .08f), Color.Transparent))
    }), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.fillMaxSize().padding(2.dp))
    }
}

@Composable
internal fun SectionBanner(title: String, gradient: Boolean = true, textColor: Color = Color(0xFF8CF5F1), actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().then(if (gradient) Modifier.background(
            remember(textColor) { Brush.horizontalGradient(listOf(textColor.copy(alpha = .13f), Color.Transparent)) },
            RoundedCornerShape(7.dp),
        ) else Modifier).padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NeonHeaderIcon(title, textColor)
            Text(title.uppercase(), color = textColor,
                style = MaterialTheme.typography.labelSmall.copy(
                    shadow = Shadow(textColor.copy(alpha = .65f), blurRadius = 12f),
                ),
                modifier = Modifier.semantics { heading() },
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.weight(1f).height(9.dp).background(remember(textColor) {
                Brush.verticalGradient(listOf(Color.Transparent, textColor.copy(alpha = .12f), Color.Transparent))
            }), contentAlignment = Alignment.Center) {
                Spacer(Modifier.fillMaxWidth().height(1.dp).background(remember(textColor) {
                    Brush.horizontalGradient(listOf(textColor.copy(alpha = .65f), textColor.copy(alpha = .10f)))
                }))
            }
        }
        actions()
    }
}

@Composable
internal fun CompactModuleActions(
    title: String,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    canPaste: Boolean,
    enabledParameter: SynthEngine.ParameterValue? = null,
    onWrite: ((SynthEngine.ParameterValue, Double) -> Unit)? = null,
    clipboardEnabled: Boolean = true,
    sectionHeading: Boolean = false,
    textColor: Color = Color(0xFF8CF5F1),
) {
    SectionBanner(title, gradient = sectionHeading, textColor = textColor) {
        if (enabledParameter != null && onWrite != null) {
            LedButton(
                label = "$title enabled",
                lit = enabledParameter.value >= .5,
                onClick = { onWrite(enabledParameter, if (enabledParameter.value >= .5) 0.0 else 1.0) },
                modifier = Modifier.size(width = 34.dp, height = 27.dp),
                hue = 125f,
                displayLabel = "ON",
                compact = true,
            )
        }
        ModuleClipboardActions(title, onCopy, onPaste, canPaste, enabled = clipboardEnabled)
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
    valueOf: (SynthEngine.ParameterValue) -> Double = { it.value },
) {
    val points = model.points
    val standardFrequency = !model.freeMode && points.size == 3 &&
        (model.address.envelopeRole == EnvelopeRole.FREQUENCY ||
            model.address.envelopeRole == EnvelopeRole.MODULATOR_FREQUENCY)
    val attackValue = if (standardFrequency) model.parameter("attackValue") else null
    val attackTime = if (standardFrequency) model.parameter("attackTime") else null
    val releaseValue = if (standardFrequency) model.parameter("releaseValue") else null
    val releaseTime = if (standardFrequency) model.parameter("releaseTime") else null
    val currentPoints by rememberUpdatedState(points)
    val currentValueOf by rememberUpdatedState(valueOf)
    val currentSelect by rememberUpdatedState(onSelectPoint)
    val currentWrite by rememberUpdatedState(onWritePoint)
    val currentOnCommit by rememberUpdatedState(onCommit)
    val interactive = model.freeMode && onSelectPoint != null && onWritePoint != null
    val touchModifier = if (interactive) modifier
        .pointerInput(model.prefix) {
            detectTapGestures { position ->
                val positions = envelopePositions(currentPoints) { currentValueOf(it.time) }
                val index = positions.indices.minByOrNull { abs(positions[it] - position.x / size.width) }
                index?.let { currentSelect?.invoke(it) }
            }
        }
        .pointerInput(model.prefix) {
            var timeDrag = 0f
            var valueDrag = 0f
            var point: EnvelopePoint? = null
            var startValue = 0.0
            var startTime = 0.0
            val finish = {
                if (point != null) {
                    point = null
                    currentOnCommit()
                }
            }
            try {
                detectDragGestures(
                    onDragStart = { position ->
                        val dragPoints = currentPoints
                        val positions = envelopePositions(dragPoints) { currentValueOf(it.time) }
                        val index = positions.indices.minByOrNull { abs(positions[it] - position.x / size.width) }
                        point = index?.let(dragPoints::get)
                        point?.let {
                            currentSelect?.invoke(it.index)
                            startValue = currentValueOf(it.value)
                            startTime = currentValueOf(it.time)
                        }
                        timeDrag = 0f
                        valueDrag = 0f
                    },
                    onDragEnd = finish,
                    onDragCancel = finish,
                ) { change, drag ->
                    change.consume()
                    point?.let {
                        valueDrag += drag.y
                        val range = it.value.descriptor.maximum - it.value.descriptor.minimum
                        currentWrite?.invoke(it.value, startValue - valueDrag / size.height * range, false)
                        if (it.index > 0) {
                            timeDrag += drag.x
                            currentWrite?.invoke(it.time, startTime + timeDrag / size.width * 127.0, false)
                        }
                    }
                }
            } finally {
                finish()
            }
        } else modifier
    val drawLiveCurve = (model.freeMode || standardFrequency) && points.size > 1
    Box(touchModifier) {
        ModulePreview(
            if (drawLiveCurve) PreviewSeries.Empty else preview,
            Modifier.fillMaxSize(),
            sustainFraction = if (drawLiveCurve) null else
                model.sustainPoint?.let { envelopePositions(points).getOrNull(it) },
        )
        if (drawLiveCurve) Canvas(Modifier.fillMaxSize()) {
            // Read only this envelope's transient values in the draw phase.
            val positions = envelopePositions(points) { point ->
                val parameter = when (point.index) {
                    1 -> attackTime
                    2 -> releaseTime
                    else -> null
                } ?: point.time
                valueOf(parameter)
            }
            model.sustainPoint?.let { positions.getOrNull(it) }?.let { fraction ->
                val x = size.width * fraction
                drawLine(Color(0xFFE8CA58), Offset(x, 0f), Offset(x, size.height), 2f)
            }
            val path = Path()
            points.forEachIndexed { index, point ->
                val parameter = when (point.index) {
                    0 -> attackValue
                    2 -> releaseValue
                    else -> null
                } ?: point.value
                val range = (parameter.descriptor.maximum - parameter.descriptor.minimum).coerceAtLeast(1.0)
                val fraction = ((valueOf(parameter) - parameter.descriptor.minimum) / range).toFloat()
                val position = Offset(size.width * positions[index], size.height * (.95f - .9f * fraction))
                if (index == 0) path.moveTo(position.x, position.y) else path.lineTo(position.x, position.y)
                if (model.freeMode) {
                    drawCircle(
                        if (point.index == selectedPoint) Color.Cyan else Color.White,
                        radius = if (point.index == selectedPoint) 9f else 5f,
                        center = position,
                    )
                }
            }
            drawNeonCurve(path, Color(0xFF66F0E9))
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
    valueOf: (SynthEngine.ParameterValue) -> Double = { it.value },
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val enable = model.parameters.firstOrNull { it.descriptor.path == "add/voice/${model.address.index}/" + when (model.address.envelopeRole) {
                EnvelopeRole.AMPLITUDE -> "ampEnvelopeEnabled"
                EnvelopeRole.FREQUENCY -> "freqEnvelopeEnabled"
                EnvelopeRole.FILTER -> "filterEnvelopeEnabled"
                EnvelopeRole.MODULATOR_AMPLITUDE -> "modAmpEnvelopeEnabled"
                EnvelopeRole.MODULATOR_FREQUENCY -> "modFreqEnvelopeEnabled"
            } }
        CompactModuleActions(model.title, onCopy, onPaste, canPaste, enable, onWrite,
            textColor = model.parameters.firstOrNull { it.descriptor.path.startsWith("${model.prefix}/") }
                ?.let { Color.hsv(addLabelHue(it.descriptor), .45f, .95f) } ?: Color(0xFFFFC857))
        if (enable == null || enable.value >= .5) {
        EnvelopeCurve(model, preview, Modifier.fillMaxWidth().height(92.dp).clickable(onClickLabel = "Edit ${model.title}", onClick = onOpenEditor), valueOf = valueOf)
        val previewFields = when (model.address.envelopeRole) {
            EnvelopeRole.AMPLITUDE, EnvelopeRole.MODULATOR_AMPLITUDE ->
                arrayOf("attackTime", "decayTime", "sustain", "releaseTime")
            EnvelopeRole.FREQUENCY, EnvelopeRole.MODULATOR_FREQUENCY ->
                arrayOf("attackValue", "attackTime", "releaseTime", "releaseValue")
            EnvelopeRole.FILTER ->
                arrayOf("attackValue", "attackTime", "decayTime", "releaseTime")
        }
        CommonParameterGrid(model.parameters.fields(*previewFields), onWrite, onDrag, onCommit, columnsOverride = 4)
        }
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
        val enable = model.parameters.firstOrNull { it.descriptor.path == "add/voice/${model.address.index}/" + when (model.address.lfoRole) {
                LfoRole.AMPLITUDE -> "ampLfoEnabled"
                LfoRole.FREQUENCY -> "freqLfoEnabled"
                LfoRole.FILTER -> "filterLfoEnabled"
            } }
        CompactModuleActions(model.title, onCopy, onPaste, canPaste, enable, onWrite,
            textColor = model.parameters.firstOrNull { it.descriptor.path.startsWith("${model.prefix}/") }
                ?.let { Color.hsv(addLabelHue(it.descriptor), .45f, .95f) } ?: Color(0xFFFFC857))
        if (enable == null || enable.value >= .5) {
        ModulePreview(preview, Modifier.fillMaxWidth().height(72.dp)
            .clickable(onClickLabel = "Edit ${model.title}", onClick = onOpenEditor), accent = Color(0xFFC08BFF))
        CommonParameterGrid(model.parameters.fields("frequency", "depth", "start", "delay"), onWrite, onDrag, onCommit, columnsOverride = 4)
        }
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
        val enable = model.parameters.firstOrNull {
                it.descriptor.path == "add/voice/${model.address.index}/filter"
            }
        CompactModuleActions("FILTER", onCopy, onPaste, canPaste, enable, onWrite, sectionHeading = true)
        if (enable == null || enable.value >= .5) {
        ModulePreview(
            preview,
            Modifier.fillMaxWidth().height(92.dp).clickable(onClickLabel = "Edit filter", onClick = onOpenEditor),
            accent = Color(0xFFFFC66A),
        )
        CommonParameterGrid(model.parameters.fields("cutoff", "q", "gain", "stages"), onWrite, onDrag, onCommit, columnsOverride = 4)
        }
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
        val filterEnabled = section != SynthEngine.ParameterSection.FILTER || voiceIndex < 0 ||
            values.firstOrNull { it.descriptor.path == "add/voice/$voiceIndex/filter" }?.value != 0.0
        if (filterEnabled) {
            envelopeRoles.forEach { role ->
                val address = ModuleAddress.Envelope(voiceIndex, role)
                remember(values, address) { EnvelopeModel.from(values, address) }?.let { envelope ->
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
                        valueOf = model::parameterValue,
                    )
                }
            }
            lfoRole?.let { role ->
                val address = ModuleAddress.Lfo(voiceIndex, role)
                LfoModel.from(values, address)?.let { lfo ->
                    val preview = remember(model.state.revision, address) { model.preview(address) }
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
    valueOf: (SynthEngine.ParameterValue) -> Double = { it.value },
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
            valueOf = valueOf,
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
            LedButton("Copy vowel", true, { onCopyVowel(selectedVowel) }, Modifier.size(27.dp),
                action = true, displayLabel = "C", compact = true)
            LedButton("Paste vowel", canPasteVowel(selectedVowel), { onPasteVowel(selectedVowel) }, Modifier.size(27.dp),
                action = true, displayLabel = "P", compact = true, enabled = canPasteVowel(selectedVowel))
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        val showFourthColumn = maxWidth >= 350.dp
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                DenseControlCard("Vowel", Color(0xFF4A3D1E)) {
                    TinyKnob(
                        label = "", value = (selectedVowel + 1).toFloat(),
                        accessibilityLabel = "Selected vowel",
                        min = 1f, max = FORMANT_VOWEL_SLOTS.toFloat(),
                        valueText = { "${it.roundToInt()}/$FORMANT_VOWEL_SLOTS" },
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
                        accessibilityLabel = "Selected formant",
                        min = 1f, max = formant.count.toFloat(),
                        valueText = { "${it.roundToInt()}/${formant.count}" },
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

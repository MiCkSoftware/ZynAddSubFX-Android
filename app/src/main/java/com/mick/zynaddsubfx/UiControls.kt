package com.mick.zynaddsubfx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.mick.zynaddsubfx.ui.theme.LedDefaultHue
import com.mick.zynaddsubfx.ui.theme.LedFxHue
import com.mick.zynaddsubfx.ui.theme.LedStereoHue
import com.mick.zynaddsubfx.ui.theme.ledColors

enum class KnobSensitivity {
    Default,
    Adjust,
}

@Composable
fun TinyKnob(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    dragRangePx: Float = 600f,
    sensitivity: KnobSensitivity = KnobSensitivity.Default,
    valueText: String = value.toInt().toString(),
    onValueChangeFinished: () -> Unit = {},
    onValueChange: (Float) -> Unit,
) {
    val screenHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        LocalConfiguration.current.screenHeightDp.dp.toPx()
    }
    val effectiveDragRangePx = when (sensitivity) {
        KnobSensitivity.Default -> dragRangePx
        KnobSensitivity.Adjust -> screenHeightPx.coerceAtLeast(1f)
    }
    val safeRange = (max - min).coerceAtLeast(1f)
    val normalized = ((value - min) / safeRange).coerceIn(0f, 1f)
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    var dragNormalized by remember { mutableFloatStateOf(normalized) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(normalized) {
        if (!isDragging) {
            dragNormalized = normalized
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(43.dp)
                .pointerInput(min, max, effectiveDragRangePx, sensitivity) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            dragNormalized = ((currentValue - min) / safeRange).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            isDragging = false
                            currentOnValueChangeFinished()
                        },
                        onDragCancel = {
                            isDragging = false
                            currentOnValueChangeFinished()
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        val deltaRaw = ((-dragAmount.y) + (dragAmount.x * 0.2f)) / effectiveDragRangePx
                        val delta = if (sensitivity == KnobSensitivity.Adjust) {
                            deltaRaw
                        } else {
                            deltaRaw.coerceIn(-0.03f, 0.03f)
                        }
                        dragNormalized = (dragNormalized + delta).coerceIn(0f, 1f)
                        onValueChange(min + dragNormalized * safeRange)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 4.dp.toPx()
                drawCircle(
                    color = Color(0x5533C8C8),
                    radius = size.minDimension / 2 - stroke,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)
                )
                drawArc(
                    color = Color(0xFF33C8C8),
                    startAngle = 140f,
                    sweepAngle = 260f * normalized,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)
                )
            }
            Text(
                text = valueText,
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                fontSize = when {
                    kotlin.math.abs(value) >= 10000f -> 9.sp
                    kotlin.math.abs(value) >= 1000f -> 11.sp
                    kotlin.math.abs(value) >= 100f -> 13.sp
                    else -> 14.sp
                },
                maxLines = 1,
            )
        }
        if (label.isNotBlank()) Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ZynValueChip(
    label: String,
    value: String,
    editable: Boolean = false,
    onClick: () -> Unit = {},
) {
    Surface(
        modifier = Modifier.clickable(enabled = editable, onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFF1A353D),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E5F68)),
    ) {
        Text(
            "$label $value",
            color = Color(0xFFA7F4F0),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun ZynModuleRow(
    title: String,
    count: Int,
    active: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    val stateColors = ledColors(active, LedFxHue)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF122229),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF234A53)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, color = Color(0xFF66F0E9), modifier = Modifier.weight(1f).clickable(onClick = onOpen))
            ZynValueChip("", count.toString())
            Text(
                if (active) "ON" else "+",
                color = stateColors.content,
                modifier = Modifier
                    .background(stateColors.surface, RoundedCornerShape(6.dp))
                    .border(1.dp, stateColors.border, RoundedCornerShape(6.dp))
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 9.dp, vertical = 6.dp),
            )
            Text("›", modifier = Modifier.clickable(onClick = onOpen), color = Color(0xFFA7F4F0))
        }
    }
}

@Composable
fun ZynKitItemRow(
    label: String,
    muted: Boolean,
    onOpen: () -> Unit,
    onMute: () -> Unit,
    middleContent: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, top = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f).clickable(onClick = onOpen), color = Color(0xFFA7F4F0))
        middleContent?.invoke()
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (muted) Color(0xFF64343A) else Color(0xFF18383E),
            modifier = Modifier.clickable(onClick = onMute),
        ) {
            Text(if (muted) "MUTED" else "MUTE", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        }
        Text("›", modifier = Modifier.clickable(onClick = onOpen).padding(5.dp))
    }
}

@Composable
fun ZynClassicKeyboardStrip(
    heldNotes: Set<Int>,
    octaveShift: Int,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val whiteNotes = listOf(60, 62, 64, 65, 67, 69, 71)
    val blackNotes = listOf(61 to 0.70f, 63 to 1.70f, 66 to 3.70f, 68 to 4.70f, 70 to 5.70f)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF071114),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF286269)),
        shape = RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(23.dp)
                    .background(Color(0xFF10272C)),
            ) {
                Text(
                    "KEYBOARD",
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
                    color = Color(0xFF71EEE5),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 1.4.sp,
                )
                Text(
                    "OCT ${if (octaveShift >= 0) "+" else ""}$octaveShift",
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                    color = Color(0xFFFFC45B),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp,
                    letterSpacing = 1.sp,
                )
                Canvas(Modifier.fillMaxSize()) {
                    var y = 1f
                    while (y < size.height) {
                        drawLine(
                            Color(0x24000000),
                            androidx.compose.ui.geometry.Offset(0f, y),
                            androidx.compose.ui.geometry.Offset(size.width, y),
                        )
                        y += 4f
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 3.dp)) {
                val whiteKeyWidth = maxWidth / whiteNotes.size
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    whiteNotes.forEach { note ->
                        val effective = (note + octaveShift * 12).coerceIn(0, 127)
                        val active = effective in heldNotes
                        Surface(
                            modifier = Modifier.weight(1f).fillMaxSize().pointerInput(note) {
                                detectTapGestures(onPress = {
                                    onPress(note)
                                    tryAwaitRelease()
                                    onRelease(note)
                                })
                            },
                            color = if (active) Color(0xFF65D5D0) else Color(0xFFDCE7E8),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (active) Color(0xFFA8FFF8) else Color(0xFF71868A),
                            ),
                            shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp),
                        ) {
                            Box(contentAlignment = Alignment.BottomCenter) {
                                Text(
                                    noteName(effective),
                                    color = Color(0xFF243439),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(bottom = 5.dp),
                                )
                            }
                        }
                    }
                }
                blackNotes.forEach { (note, position) ->
                    val effective = (note + octaveShift * 12).coerceIn(0, 127)
                    val active = effective in heldNotes
                    Surface(
                        modifier = Modifier
                            .offset(x = whiteKeyWidth * position)
                            .width(whiteKeyWidth * 0.61f)
                            .height(47.dp)
                            .pointerInput(note) {
                                detectTapGestures(onPress = {
                                    onPress(note)
                                    tryAwaitRelease()
                                    onRelease(note)
                                })
                            },
                        color = if (active) Color(0xFFFFB84D) else Color(0xFF101A1D),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (active) Color(0xFFFFE0A3) else Color(0xFF486066),
                        ),
                        shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp),
                        shadowElevation = 4.dp,
                    ) {
                        Box(contentAlignment = Alignment.BottomCenter) {
                            Text(
                                noteName(effective),
                                color = if (active) Color(0xFF2A1A08) else Color(0xFF8EA8AA),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 7.sp,
                                modifier = Modifier.padding(bottom = 3.dp),
                            )
                        }
                    }
                }
                Canvas(Modifier.fillMaxSize()) {
                    var y = 2f
                    while (y < size.height) {
                        drawLine(
                            Color(0x14071114),
                            androidx.compose.ui.geometry.Offset(0f, y),
                            androidx.compose.ui.geometry.Offset(size.width, y),
                        )
                        y += 5f
                    }
                }
            }
        }
    }
}

@Composable
fun RetroTestKeyboard(
    heldNote: Int?,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pixelFont = FontFamily.Monospace
    val whiteNotes = listOf(60, 62, 64, 65, 67, 69, 71)
    val blackNotes = listOf(
        61 to 0.70f,
        63 to 1.70f,
        66 to 3.70f,
        68 to 4.70f,
        70 to 5.70f,
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF071013),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C6C70)),
        shadowElevation = 3.dp,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(Color(0xFF10282D))
                    .border(width = 1.dp, color = Color(0xFF23545A)),
            ) {
                Text(
                    text = "TEST KEYBOARD",
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp),
                    color = Color(0xFF7FF7E9),
                    fontFamily = pixelFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.5.sp,
                )
                Text(
                    text = heldNote?.let(::noteName)?.uppercase() ?: "READY",
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 10.dp)
                        .background(Color(0xFF081719), RoundedCornerShape(3.dp))
                        .border(1.dp, Color(0xFF2D7777), RoundedCornerShape(3.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    color = if (heldNote == null) Color(0xFF58B9B2) else Color(0xFFFFC857),
                    fontFamily = pixelFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    var y = 1f
                    while (y < size.height) {
                        drawLine(Color(0x22000000), start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y))
                        y += 4f
                    }
                }
            }
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .padding(start = 5.dp, end = 5.dp, bottom = 5.dp),
            ) {
                val whiteKeyWidth = maxWidth / whiteNotes.size
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    whiteNotes.forEach { note ->
                        val active = heldNote == note
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .pointerInput(note) {
                                    detectTapGestures(onPress = {
                                        onPress(note)
                                        tryAwaitRelease()
                                        onRelease(note)
                                    })
                                },
                            color = if (active) Color(0xFF6CD6D1) else Color(0xFFDDE7E5),
                            shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (active) Color(0xFFB7FFF7) else Color(0xFF829493),
                            ),
                        ) {
                            Box(contentAlignment = Alignment.BottomCenter) {
                                Text(
                                    text = noteName(note),
                                    modifier = Modifier.padding(bottom = 7.dp),
                                    color = Color(0xFF172629),
                                    fontFamily = pixelFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                }
                blackNotes.forEach { (note, keyPosition) ->
                    val active = heldNote == note
                    Surface(
                        modifier = Modifier
                            .offset(x = whiteKeyWidth * keyPosition)
                            .width(whiteKeyWidth * 0.62f)
                            .height(63.dp)
                            .pointerInput(note) {
                                detectTapGestures(onPress = {
                                    onPress(note)
                                    tryAwaitRelease()
                                    onRelease(note)
                                })
                            },
                        color = if (active) Color(0xFFFFB84D) else Color(0xFF10191C),
                        shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (active) Color(0xFFFFE0A3) else Color(0xFF395257),
                        ),
                        shadowElevation = 4.dp,
                    ) {
                        Box(contentAlignment = Alignment.BottomCenter) {
                            Text(
                                text = noteName(note).replace("#", "♯"),
                                modifier = Modifier.padding(bottom = 5.dp),
                                color = if (active) Color(0xFF2A1A08) else Color(0xFF8FB0B0),
                                fontFamily = pixelFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp,
                            )
                        }
                    }
                }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    var y = 2f
                    while (y < size.height) {
                        drawLine(Color(0x14071719), start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y))
                        y += 5f
                    }
                }
            }
        }
    }
}

@Composable
fun TinyKnobDisplay(
    label: String,
    value: Float,
    min: Float,
    max: Float,
) {
    val safeRange = (max - min).coerceAtLeast(1f)
    val normalized = ((value - min) / safeRange).coerceIn(0f, 1f)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(50.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 4.dp.toPx()
                drawCircle(
                    color = Color(0x5533C8C8),
                    radius = size.minDimension / 2 - stroke,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)
                )
                drawArc(
                    color = Color(0xFF33C8C8),
                    startAngle = 140f,
                    sweepAngle = 260f * normalized,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)
                )
            }
            Text(
                text = value.toInt().toString(),
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LuminousToggleButton(
    label: String,
    enabled: Boolean,
    onToggle: () -> Unit,
    hue: Float = LedDefaultHue,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val colors = ledColors(enabled, hue)

    Surface(
        modifier = modifier
            .clickable { onToggle() }
            .border(
                width = 1.5.dp,
                color = colors.border,
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp),
        color = colors.surface,
        shadowElevation = if (enabled) 8.dp else 1.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.glow.copy(alpha = if (enabled) 0.16f else 0.05f))
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = colors.content)
        }
    }
}

@Composable
fun StudioStereoSelector(
    stereo: Boolean,
    onStereoChange: (Boolean) -> Unit,
) {
    val colors = ledColors(stereo, LedStereoHue)
    Surface(
        modifier = Modifier.width(66.dp).height(34.dp).clickable {
            onStereoChange(!stereo)
        },
        shape = RoundedCornerShape(4.dp),
        color = colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
        shadowElevation = if (stereo) 6.dp else 0.dp,
    ) {
        Box(
            modifier = Modifier.background(colors.glow.copy(alpha = if (stereo) .20f else .03f)),
            contentAlignment = Alignment.Center,
        ) {
            if (stereo) {
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    HifiSpeakerIcon(mirrored = true, tint = colors.content)
                    HifiSpeakerIcon(mirrored = false, tint = colors.content)
                }
            } else {

                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    HifiSpeakerIcon(mirrored = true, tint = Color(0x77555555))
                    HifiSpeakerIcon(mirrored = false, tint = colors.content)
                }
            }
        }
    }
}

@Composable
private fun HifiSpeakerIcon(mirrored: Boolean, tint: Color) {
    androidx.compose.foundation.Image(
        painter = painterResource(R.drawable.ic_hifi_speaker),
        contentDescription = null,
        modifier = Modifier.size(29.dp).scale(scaleX = if (mirrored) -1f else 1f, scaleY = 1f),
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(tint),
    )
}

@Composable
fun LuminousActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Color(0xFF33C8C8),
    enabled: Boolean = true,
) {
    Surface(
        modifier = modifier
            .clickable(enabled = enabled) { onClick() }
            .alpha(if (enabled) 1f else .42f)
            .border(
                width = 1.5.dp,
                color = accent.copy(alpha = 0.85f),
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF14262C),
        shadowElevation = 6.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(accent.copy(alpha = 0.14f))
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = Color(0xFFE6FFFF),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

package com.mick.zynaddsubfx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.mick.zynaddsubfx.ui.theme.LedDefaultHue
import com.mick.zynaddsubfx.ui.theme.LedFxHue
import com.mick.zynaddsubfx.ui.theme.LedStereoHue
import com.mick.zynaddsubfx.ui.theme.ledColors

@Composable
fun TinyKnob(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    dragRangePx: Float = 600f,
    onValueChange: (Float) -> Unit,
) {
    val safeRange = (max - min).coerceAtLeast(1f)
    val normalized = ((value - min) / safeRange).coerceIn(0f, 1f)
    val currentValue by rememberUpdatedState(value)
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
                .pointerInput(min, max, dragRangePx) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            dragNormalized = ((currentValue - min) / safeRange).coerceIn(0f, 1f)
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    ) { change, dragAmount ->
                        change.consume()
                        val deltaRaw = ((-dragAmount.y) + (dragAmount.x * 0.2f)) / dragRangePx
                        val delta = deltaRaw.coerceIn(-0.03f, 0.03f)
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
                text = value.toInt().toString(),
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
    Row(modifier.height(68.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(60, 62, 64, 65, 67, 69, 71).forEach { note ->
            val effective = (note + octaveShift * 12).coerceIn(0, 127)
            Surface(
                modifier = Modifier.weight(1f).fillMaxSize().pointerInput(note) {
                    detectTapGestures(onPress = {
                        onPress(note)
                        tryAwaitRelease()
                        onRelease(note)
                    })
                },
                color = if (effective in heldNotes) Color(0xFF1E6C73) else Color(0xFFE3ECEE),
                shape = RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp),
            ) {
                Box(contentAlignment = Alignment.BottomCenter) {
                    Text(
                        noteName(effective),
                        color = if (effective in heldNotes) Color.White else Color(0xFF26373C),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(bottom = 5.dp),
                    )
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
) {
    Surface(
        modifier = modifier
            .clickable { onClick() }
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

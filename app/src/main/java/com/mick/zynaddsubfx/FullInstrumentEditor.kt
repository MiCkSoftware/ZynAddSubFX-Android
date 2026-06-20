@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.mick.zynaddsubfx

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.roundToInt
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
    var editedParameter by remember { mutableStateOf<SynthEngine.ParameterValue?>(null) }
    var exportedFile by remember { mutableStateOf<File?>(null) }
    var exportedRevision by remember { mutableStateOf(0L) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val contentScroll = rememberScrollState()
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
                            color = if (activeAnchor(contentScroll.value, sectionNames) == tab) Color(0xFF23616A) else Color(0xFF182F35),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.combinedClickable(onClick = {
                                model.selectTab(tab)
                                scope.launch { contentScroll.animateScrollTo(index * 430) }
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
                        )
                        ZynEditorSection(
                            title = section,
                            parameters = parameters,
                            complex = section in setOf("Oscillator", "Resonance", "Spectrum") ||
                                (module == "FX" && section != "Routing"),
                            onComplex = {
                                model.selectTab(section)
                                selector = "complex"
                            },
                            onWrite = model::write,
                            onEdit = { editedParameter = it },
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

private fun populatedSections(
    all: List<SynthEngine.ParameterValue>,
    module: String,
): List<String> {
    val declared = InstrumentEditorViewModel.tabsFor(module)
    return declared.filter { section ->
        parametersFor(all, module, section, 0).isNotEmpty() ||
            section in setOf("Oscillator", "Resonance", "Spectrum") ||
            (module == "FX" && section != "Routing")
    }
}

private fun activeAnchor(scroll: Int, sections: List<String>): String =
    sections.getOrElse((scroll / 430).coerceIn(0, (sections.size - 1).coerceAtLeast(0))) {
        sections.firstOrNull().orEmpty()
    }

@Composable
private fun ZynEditorSection(
    title: String,
    parameters: List<SynthEngine.ParameterValue>,
    complex: Boolean,
    onComplex: () -> Unit,
    onWrite: (SynthEngine.ParameterValue, Double) -> Unit,
    onEdit: (SynthEngine.ParameterValue) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Surface(
            modifier = Modifier.width(34.dp),
            color = Color(0xFF102A31),
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, Color(0xFF2E6973)),
        ) {
            Text(
                title.uppercase(),
                color = Color(0xFF66F0E9),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 12.dp),
            )
        }
        Surface(
            modifier = Modifier.weight(1f),
            color = Color(0xFF0E2228),
            shape = RoundedCornerShape(7.dp),
            border = BorderStroke(1.dp, Color(0xFF234A53)),
        ) {
            Column(Modifier.padding(5.dp)) {
                if (complex) ComplexEditorLauncher(title, onComplex)
                if (parameters.isNotEmpty()) DenseParameterGrid(parameters, onWrite, onEdit)
            }
        }
    }
}

private fun parametersFor(
    all: List<SynthEngine.ParameterValue>,
    module: String,
    tab: String,
    voice: Int,
): List<SynthEngine.ParameterValue> = all.filter { parameter ->
    val group = parameter.descriptor.group
    if (!group.startsWith(module, ignoreCase = true)) return@filter false
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
    tab in setOf("Oscillator", "Resonance", "Spectrum") -> "Open the terminal editor above."
    else -> "No $tab parameters are available for this kit item."
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DenseParameterGrid(
    parameters: List<SynthEngine.ParameterValue>,
    onWrite: (SynthEngine.ParameterValue, Double) -> Unit,
    onLongPress: (SynthEngine.ParameterValue) -> Unit,
) {
    BoxWithConstraints {
        val columns = when {
            maxWidth >= 900.dp -> 6
            maxWidth >= 650.dp -> 5
            maxWidth >= 420.dp -> 4
            else -> 3
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = columns,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            parameters.forEach { parameter ->
                Box(Modifier.fillMaxWidth(1f / columns)) {
                    DenseParameterControl(parameter, onWrite, onLongPress)
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
) {
    val descriptor = parameter.descriptor
    Surface(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = {
                if (descriptor.type == SynthEngine.ParameterType.BOOLEAN) {
                    onWrite(parameter, if (parameter.value >= .5) 0.0 else 1.0)
                }
            },
            onLongClick = { onLongPress(parameter) },
        ),
        color = Color(0xFF162D33),
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.dp, Color(0xFF274B54)),
    ) {
        Column(
            Modifier.padding(horizontal = 4.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (descriptor.type) {
                SynthEngine.ParameterType.BOOLEAN -> Switch(
                    checked = parameter.value >= .5,
                    onCheckedChange = { onWrite(parameter, if (it) 1.0 else 0.0) },
                )
                SynthEngine.ParameterType.ENUM -> CompactSelector(
                    label = "",
                    value = descriptor.options.getOrNull(parameter.value.roundToInt())
                        ?: parameter.value.roundToInt().toString(),
                    onClick = { onLongPress(parameter) },
                )
                SynthEngine.ParameterType.INTEGER -> TinyKnob(
                    label = "",
                    value = parameter.value.toFloat(),
                    min = descriptor.minimum.toFloat(),
                    max = descriptor.maximum.toFloat(),
                    onValueChange = { onWrite(parameter, it.roundToInt().toDouble()) },
                )
            }
            Text(
                descriptor.label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
            )
        }
    }
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

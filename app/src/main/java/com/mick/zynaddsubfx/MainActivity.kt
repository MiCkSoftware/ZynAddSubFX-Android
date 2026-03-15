package com.mick.zynaddsubfx

import android.os.Bundle
import android.util.Log
import android.media.AudioManager
import android.content.Context
import java.io.File
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import com.mick.zynaddsubfx.ui.theme.ZynAddSubFXTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.roundToInt

private const val APP_LOG_TAG = "ZynAddSubFX"
private const val SCAN_LOG_TAG = "ZynScan"
private const val INSPECT_LOG_TAG = "ZynInspect"
private const val EXPORT_LOG_TAG = "ZynExport"
private const val SCAN_LOG_PREFIX = "SCAN"
private const val FIRST_NOTE_PEAK_THRESHOLD = 0.0015f
private const val FIRST_NOTE_POLL_MS = 20L
private const val FIRST_NOTE_AUTO_TIMEOUT_MS = 2500L
private const val HEAVY_FIRST_NOTE_MS_THRESHOLD = 250L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val nativeStatus = smokeTestNativeBridge()
        enableEdgeToEdge()
        setContent {
            ZynAddSubFXTheme {
                ZynAddSubFXApp(nativeStatus = nativeStatus)
            }
        }
    }

    private fun smokeTestNativeBridge(): NativeSmokeStatus {
        return try {
            val (deviceSampleRate, deviceFramesPerBurst) = readDeviceAudioDefaults()
            val version = NativeSynthBridge.nativeGetVersion()
            val zynProbe = NativeSynthBridge.nativeGetZynProbeSummary()
            val initialized = NativeSynthBridge.nativeInit(
                sampleRate = deviceSampleRate,
                framesPerBurst = deviceFramesPerBurst
            )
            val lastRate = NativeSynthBridge.nativeGetLastSampleRate()
            val lastBurst = NativeSynthBridge.nativeGetLastFramesPerBurst()
            val renderBackend = NativeSynthBridge.nativeGetRenderBackendName()

            NativeSmokeStatus(
                loaded = true,
                version = version,
                zynProbe = zynProbe,
                renderBackend = renderBackend,
                initOk = initialized,
                sampleRate = lastRate,
                framesPerBurst = lastBurst,
                error = null
            )
        } catch (t: Throwable) {
            Log.e(APP_LOG_TAG, "Native bridge smoke test failed", t)
            NativeSmokeStatus(
                loaded = false,
                version = null,
                zynProbe = null,
                renderBackend = null,
                initOk = false,
                sampleRate = null,
                framesPerBurst = null,
                error = "${t::class.simpleName}: ${t.message}"
            )
        }
    }

    private fun readDeviceAudioDefaults(): Pair<Int, Int> {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val sampleRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            ?.toIntOrNull()
            ?: 48_000
        val framesPerBurst = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
            ?.toIntOrNull()
            ?: 192
        return sampleRate to framesPerBurst
    }

    override fun onStop() {
        super.onStop()
        runCatching { NativeSynthBridge.nativeSetTestToneEnabled(false) }
        runCatching { NativeSynthBridge.nativeStopAudio() }
    }
}

@PreviewScreenSizes
@Composable
fun ZynAddSubFXApp(nativeStatus: NativeSmokeStatus = NativeSmokeStatus.preview()) {
    val context = LocalContext.current
    val engine = remember(context) { SynthEngine(context) }
    val scope = rememberCoroutineScope()
    var packagedPresets by remember { mutableStateOf<List<SynthEngine.PresetAsset>>(emptyList()) }
    var presetCatalogLoading by rememberSaveable { mutableStateOf(true) }
    val scanResults = remember(context) {
        mutableStateMapOf<String, PresetScanRecord>().apply {
            putAll(loadScanResults(context))
        }
    }
    val presetRatings = remember(context) {
        mutableStateMapOf<String, Int>().apply {
            putAll(loadPresetRatings(context))
        }
    }
    val bankExpandedSections = remember(context) {
        mutableStateMapOf<String, Boolean>().apply {
            putAll(loadBankExpandedSections(context))
        }
    }
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var audioRunning by rememberSaveable { mutableStateOf(false) }
    var toneEnabled by rememberSaveable { mutableStateOf(false) }
    var actionStatus by rememberSaveable { mutableStateOf("Audio idle") }
    var heldNote by rememberSaveable { mutableStateOf<Int?>(null) }
    val heldNotes = remember { mutableStateListOf<Int>() }
    var masterVolume by rememberSaveable { mutableStateOf(0.6f) }
    var keyboardVelocity by rememberSaveable { mutableStateOf(110) }
    var keyboardOctaveShift by rememberSaveable { mutableStateOf(0) }
    var presetLoading by rememberSaveable { mutableStateOf(false) }
    var stressRunning by rememberSaveable { mutableStateOf(false) }
    var currentPresetPath by rememberSaveable { mutableStateOf(loadLastSelectedPresetPath(context)) }
    var firstNoteTimingPresetPath by rememberSaveable { mutableStateOf<String?>(null) }
    var firstNoteTimingStartMs by rememberSaveable { mutableStateOf<Long?>(null) }
    var firstNoteMeasureToken by rememberSaveable { mutableStateOf(0) }
    var currentPresetLoadToken by rememberSaveable { mutableStateOf(0) }
    var firstNoteCapturedLoadToken by rememberSaveable { mutableStateOf(-1) }
    var activeFxSlots by remember { mutableStateOf<List<SynthEngine.ActiveFxSlot>>(emptyList()) }
    var presetInspector by remember { mutableStateOf<SynthEngine.PresetInspector?>(null) }
    var partInspectors by remember { mutableStateOf<List<SynthEngine.PartInspector>>(emptyList()) }
    var mixerInspector by remember { mutableStateOf(SynthEngine.MixerInspector(emptyList(), emptyList())) }
    var inspectorRefreshToken by rememberSaveable { mutableStateOf(0) }
    var debugSection by rememberSaveable { mutableStateOf(DebugSection.INSPECTOR) }
    var selectedPlayPartIndex by rememberSaveable { mutableStateOf(0) }
    val statusText = remember(nativeStatus) { nativeStatus.toDisplayString() }
    val zynProbeText = remember(nativeStatus) { nativeStatus.zynProbeDisplayString() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var autoStartAttempted by rememberSaveable { mutableStateOf(false) }
    var startupPresetRestoreAttempted by rememberSaveable { mutableStateOf(false) }
    var shouldResumeAudioAfterPause by rememberSaveable { mutableStateOf(false) }
    var shouldResumeToneAfterPause by rememberSaveable { mutableStateOf(false) }

    fun pressNote(effectiveNote: Int) {
        if (!heldNotes.contains(effectiveNote)) {
            heldNotes.add(effectiveNote)
            runCatching { engine.noteOn(0, effectiveNote, keyboardVelocity) }
        }
        heldNote = effectiveNote
    }

    fun releaseNote(effectiveNote: Int) {
        runCatching { engine.noteOff(0, effectiveNote) }
        heldNotes.removeAll { it == effectiveNote }
        if (heldNote == effectiveNote) {
            heldNote = heldNotes.lastOrNull()
        }
    }

    fun releaseAllHeldNotes() {
        heldNotes.distinct().forEach { note ->
            runCatching { engine.noteOff(0, note) }
        }
        heldNotes.clear()
        heldNote = null
    }
    remember(nativeStatus) {
        Log.d(APP_LOG_TAG, "startup: $statusText")
        Log.d(APP_LOG_TAG, "probe: $zynProbeText")
    }
    LaunchedEffect(engine) {
        presetCatalogLoading = true
        packagedPresets = withContext(Dispatchers.IO) { engine.listPackagedPresets() }
        presetCatalogLoading = false
        Log.d(APP_LOG_TAG, "catalog loaded: ${packagedPresets.size} presets")
    }
    LaunchedEffect(engine, nativeStatus.loaded, nativeStatus.initOk, autoStartAttempted) {
        if (!nativeStatus.loaded || !nativeStatus.initOk || autoStartAttempted) return@LaunchedEffect
        autoStartAttempted = true
        val ok = runCatching { engine.startAudio() }.getOrDefault(false)
        audioRunning = runCatching { engine.isAudioRunning() }.getOrDefault(ok)
        masterVolume = runCatching { engine.getMasterVolumeNormalized() }.getOrDefault(masterVolume)
        Log.d(APP_LOG_TAG, "diag: ${engine.runtimeDiagnostics()}")
        actionStatus = if (audioRunning) "Audio auto-started" else "Audio auto-start failed"
    }
    LaunchedEffect(engine, currentPresetPath, inspectorRefreshToken) {
        val (inspector, fx, parts, mixer) = withContext(Dispatchers.IO) {
            listOf(
                engine.inspectPreset(currentPresetPath),
                engine.describeActiveEffects(currentPresetPath),
                engine.inspectParts(),
                engine.inspectMixer()
            )
        }
        @Suppress("UNCHECKED_CAST")
        presetInspector = inspector as SynthEngine.PresetInspector?
        @Suppress("UNCHECKED_CAST")
        activeFxSlots = fx as List<SynthEngine.ActiveFxSlot>
        @Suppress("UNCHECKED_CAST")
        partInspectors = parts as List<SynthEngine.PartInspector>
        @Suppress("UNCHECKED_CAST")
        mixerInspector = mixer as SynthEngine.MixerInspector
        currentPresetPath?.let { path ->
            val presetName = File(path).nameWithoutExtension
            Log.i(
                INSPECT_LOG_TAG,
                "preset=$presetName inspector=${inspector?.toDebugString() ?: "null"}"
            )
            Log.i(
                INSPECT_LOG_TAG,
                "preset=$presetName fx=${if (fx.isEmpty()) "none" else fx.joinToString { "${it.scope}#${it.slotId}:${it.typeName}" }}"
            )
            val partsCompact = parts.joinToString(separator = " ; ") {
                "p${it.partIndex}[en=${if (it.enabled) 1 else 0},nOn=${if (it.noteOn) 1 else 0},ch=${it.receiveChannel},keys=${it.minKey}..${it.maxKey},vol=${it.volume},pan=${it.panning},peak=${"%.4f".format(it.outputPeak)},kit=${it.kitMode},km=${it.mutedKitItems},a=${it.addEnabledCount},s=${it.subEnabledCount},p=${it.padEnabledCount},fx=${it.partFxActiveCount}]"
            }
            Log.i(INSPECT_LOG_TAG, "preset=$presetName parts=$partsCompact")
            val mixerCompact = buildString {
                append("insert=")
                if (mixer.insertRoutings.isEmpty()) {
                    append("none")
                } else {
                    append(
                        mixer.insertRoutings.joinToString { "i${it.slotId}:${it.typeName}->p${it.assignedPart}" }
                    )
                }
                append(" sysSends=")
                if (mixer.systemSends.isEmpty()) {
                    append("none")
                } else {
                    append(
                        mixer.systemSends.take(16)
                            .joinToString { "p${it.partIndex}->s${it.systemFxSlot}=${it.sendValue}" }
                    )
                    if (mixer.systemSends.size > 16) {
                        append(" (+${mixer.systemSends.size - 16})")
                    }
                }
            }
            Log.i(INSPECT_LOG_TAG, "preset=$presetName mixer=$mixerCompact")
        }
    }

    val startAutoFirstNoteDetection: (String?) -> Unit = { presetPath ->
        if (presetPath == null) {
            firstNoteTimingPresetPath = null
            firstNoteTimingStartMs = null
        } else {
            runCatching { engine.clearRecentOutputPeak() }
            val started = SystemClock.elapsedRealtime()
            firstNoteTimingPresetPath = presetPath
            firstNoteTimingStartMs = started
            val token = firstNoteMeasureToken + 1
            firstNoteMeasureToken = token
            scope.launch {
                while (true) {
                    delay(FIRST_NOTE_POLL_MS)
                    if (firstNoteMeasureToken != token) break
                    val currentPath = firstNoteTimingPresetPath
                    val startMs = firstNoteTimingStartMs
                    if (currentPath == null || startMs == null) break
                    val elapsed = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(0L)
                    if (elapsed > FIRST_NOTE_AUTO_TIMEOUT_MS) {
                        break
                    }
                    val peak = runCatching { engine.recentOutputPeak() }.getOrDefault(0f)
                    if (peak >= FIRST_NOTE_PEAK_THRESHOLD) {
                        val prev = scanResults[currentPath] ?: PresetScanRecord()
                        scanResults[currentPath] = prev.copy(firstNoteMs = elapsed, firstNotePeak = peak)
                        firstNoteCapturedLoadToken = currentPresetLoadToken
                        Log.i(
                            SCAN_LOG_TAG,
                            "$SCAN_LOG_PREFIX first_note_auto preset=${File(currentPath).nameWithoutExtension} ms=$elapsed peak=$peak status=${scanResults[currentPath]?.status?.short ?: "-"}"
                        )
                        firstNoteTimingPresetPath = null
                        firstNoteTimingStartMs = null
                        break
                    }
                }
            }
        }
    }

    val persistScanState = {
        persistScanResults(context, scanResults.toMap())
    }
    val persistPresetRatingsState = {
        persistPresetRatings(context, presetRatings.toMap())
    }
    val persistBankExpandedSectionsState = {
        persistBankExpandedSections(context, bankExpandedSections.toMap())
    }

    val handleLoadPreset: (SynthEngine.PresetAsset) -> Unit = { preset ->
        if (presetLoading) {
            actionStatus = "Preset load already in progress…"
        } else {
            presetLoading = true
            actionStatus = "Loading preset: ${preset.displayName}…"
            heldNotes.clear()
            heldNote = null
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { engine.loadPackagedPresetMeasured(preset.assetPath) }
                        .getOrElse { SynthEngine.PresetLoadResult(false, 0L, false) }
                }
                currentPresetPath = preset.assetPath
                persistLastSelectedPresetPath(context, preset.assetPath)
                currentPresetLoadToken += 1
                firstNoteCapturedLoadToken = -1
                firstNoteTimingPresetPath = null
                firstNoteTimingStartMs = null
                firstNoteMeasureToken += 1
                val prev = scanResults[preset.assetPath] ?: PresetScanRecord()
                val status = when {
                    result.timedOut -> PresetScanStatus.TIMEOUT
                    !result.ok -> PresetScanStatus.FAIL
                    else -> prev.status
                }
                scanResults[preset.assetPath] = prev.copy(
                    attempts = prev.attempts + 1,
                    status = status,
                    loadMs = result.loadMs,
                    firstNoteMs = null
                )
                persistScanState()
                Log.i(
                    SCAN_LOG_TAG,
                    "$SCAN_LOG_PREFIX load preset=${preset.displayName} section=${preset.section} ok=${result.ok} timeout=${result.timedOut} load_ms=${result.loadMs} status=${status.short}"
                )
                masterVolume = runCatching {
                    engine.getMasterVolumeNormalized()
                }.getOrDefault(masterVolume)
                Log.d(APP_LOG_TAG, "diag: ${engine.runtimeDiagnostics()}")
                logScanSummary(packagedPresets, scanResults.toMap())
                actionStatus = if (result.ok) {
                    "Preset loaded: ${preset.displayName} (${result.loadMs}ms)"
                } else {
                    "Preset load failed: ${preset.displayName} (${result.loadMs}ms)"
                }
                presetLoading = false
            }
        }
    }

    LaunchedEffect(
        presetCatalogLoading,
        packagedPresets,
        currentPresetPath,
        startupPresetRestoreAttempted,
        presetLoading
    ) {
        if (startupPresetRestoreAttempted || presetCatalogLoading || presetLoading) return@LaunchedEffect
        startupPresetRestoreAttempted = true
        val savedPath = currentPresetPath ?: return@LaunchedEffect
        val preset = packagedPresets.firstOrNull { it.assetPath == savedPath } ?: run {
            actionStatus = "Saved preset not found in catalog"
            return@LaunchedEffect
        }
        handleLoadPreset(preset)
    }

    DisposableEffect(lifecycleOwner, audioRunning, toneEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    shouldResumeAudioAfterPause = audioRunning
                    shouldResumeToneAfterPause = audioRunning && toneEnabled
                    if (toneEnabled) {
                        runCatching { engine.setTestToneEnabled(false) }
                        toneEnabled = false
                    }
                    if (audioRunning) {
                        runCatching { engine.stopAudio() }
                        audioRunning = false
                        Log.d(APP_LOG_TAG, "diag: ${engine.runtimeDiagnostics()}")
                        actionStatus = "Audio paused (lifecycle)"
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (shouldResumeAudioAfterPause) {
                        val ok = runCatching { engine.startAudio() }.getOrDefault(false)
                        audioRunning = runCatching { engine.isAudioRunning() }.getOrDefault(ok)
                        if (audioRunning && shouldResumeToneAfterPause) {
                            runCatching { engine.setTestToneEnabled(true) }
                            toneEnabled = true
                        }
                        shouldResumeAudioAfterPause = false
                        shouldResumeToneAfterPause = false
                        actionStatus = if (audioRunning) "Audio resumed" else "Audio resume failed"
                        Log.d(APP_LOG_TAG, "diag: ${engine.runtimeDiagnostics()}")
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = currentDestination == AppDestinations.EDITOR) {
        currentDestination = AppDestinations.HOME
    }
    BackHandler(enabled = currentDestination == AppDestinations.BANKS) {
        currentDestination = AppDestinations.HOME
    }

    LaunchedEffect(currentDestination) {
        if (currentDestination == AppDestinations.EDITOR) {
            inspectorRefreshToken += 1
        }
    }

    if (currentDestination == AppDestinations.EDITOR) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            PresetEditorScreen(
                uiState = EditorUiState(
                    parts = partInspectors,
                    selectedPartIndex = selectedPlayPartIndex
                ),
                heldNote = heldNote,
                heldNotes = heldNotes.toSet(),
                keyboardOctaveShift = keyboardOctaveShift,
                onPressKeyboardNote = { note ->
                    val effectiveNote = (note + keyboardOctaveShift * 12).coerceIn(0, 127)
                    pressNote(effectiveNote)
                },
                onReleaseKeyboardNote = { note ->
                    val effectiveNote = (note + keyboardOctaveShift * 12).coerceIn(0, 127)
                    releaseNote(effectiveNote)
                },
                onSetPartEnabled = { partIndex, enabled ->
                    val ok = runCatching { engine.setPartEnabled(partIndex, enabled) }.getOrDefault(false)
                    actionStatus = if (ok) {
                        "Part $partIndex ${if (enabled) "enabled" else "disabled"}"
                    } else {
                        "Failed to set Part $partIndex ${if (enabled) "enabled" else "disabled"}"
                    }
                    if (ok) inspectorRefreshToken += 1
                },
                onSetPartAddEnabled = { partIndex, enabled ->
                    val ok = runCatching { engine.setPartAddEnabled(partIndex, enabled) }.getOrDefault(false)
                    actionStatus = if (ok) "Part $partIndex ADD ${if (enabled) "on" else "off"}" else "Failed to set Part $partIndex ADD"
                    if (ok) inspectorRefreshToken += 1
                },
                onSetPartSubEnabled = { partIndex, enabled ->
                    val ok = runCatching { engine.setPartSubEnabled(partIndex, enabled) }.getOrDefault(false)
                    actionStatus = if (ok) "Part $partIndex SUB ${if (enabled) "on" else "off"}" else "Failed to set Part $partIndex SUB"
                    if (ok) inspectorRefreshToken += 1
                },
                onSetPartPadEnabled = { partIndex, enabled ->
                    val ok = runCatching { engine.setPartPadEnabled(partIndex, enabled) }.getOrDefault(false)
                    actionStatus = if (ok) "Part $partIndex PAD ${if (enabled) "on" else "off"}" else "Failed to set Part $partIndex PAD"
                    if (ok) inspectorRefreshToken += 1
                },
                onSoloPart = { partIndex ->
                    val ok = runCatching { engine.soloPart(partIndex) }.getOrDefault(false)
                    actionStatus = if (ok) "Solo Part $partIndex" else "Failed to solo Part $partIndex"
                    if (ok) inspectorRefreshToken += 1
                },
                modifier = Modifier.padding(innerPadding)
            )
        }
    } else {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            if (currentDestination == AppDestinations.BANKS) {
                BankBrowserScreen(
                    packagedPresets = packagedPresets,
                    scanResults = scanResults.toMap(),
                    presetRatings = presetRatings.toMap(),
                    currentPresetPath = currentPresetPath,
                    presetLoading = presetLoading,
                    catalogLoading = presetCatalogLoading,
                    expandedSections = bankExpandedSections.toMap(),
                    onLoadPreset = handleLoadPreset,
                    onToggleSectionExpanded = { section ->
                        bankExpandedSections[section] = !(bankExpandedSections[section] ?: false)
                        persistBankExpandedSectionsState()
                    },
                    onSetPresetRating = { assetPath, rating ->
                        val clamped = rating.coerceIn(0, 5)
                        presetRatings[assetPath] = clamped
                        persistPresetRatingsState()
                        actionStatus = "Rated ${File(assetPath).nameWithoutExtension}: ${if (clamped == 0) "0★" else "$clamped★"}"
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            } else {
                PlayScreen(
                    uiState = PlayUiState(
                        audioRunning = audioRunning,
                        actionStatus = actionStatus,
                        currentPresetName = currentPresetPath?.let { File(it).nameWithoutExtension },
                        currentPresetRating = currentPresetPath?.let { presetRatings[it] } ?: 0,
                        masterVolume = masterVolume,
                        keyboardVelocity = keyboardVelocity,
                        keyboardOctaveShift = keyboardOctaveShift,
                        heldNote = heldNote,
                        heldNotes = heldNotes.toSet(),
                        presetLoading = presetLoading,
                        parts = partInspectors,
                        selectedPartIndex = selectedPlayPartIndex,
                    ),
                    onOpenBanks = { currentDestination = AppDestinations.BANKS },
                    onPressKeyboardNote = { note ->
                        val effectiveNote = (note + keyboardOctaveShift * 12).coerceIn(0, 127)
                        if (currentPresetPath != null && firstNoteCapturedLoadToken != currentPresetLoadToken) {
                            startAutoFirstNoteDetection(currentPresetPath)
                        }
                        pressNote(effectiveNote)
                    },
                    onReleaseKeyboardNote = { note ->
                        val effectiveNote = (note + keyboardOctaveShift * 12).coerceIn(0, 127)
                        releaseNote(effectiveNote)
                    },
                    onAllNotesOff = {
                        releaseAllHeldNotes()
                    },
                    onPanic = {
                        runCatching { engine.panic() }
                        heldNotes.clear()
                        heldNote = null
                        currentPresetPath?.let { path ->
                            val prev = scanResults[path] ?: PresetScanRecord()
                            val updated = prev.copy(panicCount = prev.panicCount + 1)
                            scanResults[path] = updated
                            persistScanState()
                            Log.i(
                                SCAN_LOG_TAG,
                                "$SCAN_LOG_PREFIX panic preset=${File(path).nameWithoutExtension} panic_count=${updated.panicCount} status=${updated.status.short}"
                            )
                        }
                        actionStatus = "Panic (voices reset)"
                    },
                    onSetCurrentPresetRating = { rating ->
                        currentPresetPath?.let { path ->
                            val clamped = rating.coerceIn(0, 5)
                            presetRatings[path] = clamped
                            persistPresetRatingsState()
                            actionStatus = "Rated ${File(path).nameWithoutExtension}: ${if (clamped == 0) "0★" else "$clamped★"}"
                        } ?: run {
                            actionStatus = "No preset selected"
                        }
                    },
                    onMasterVolumeChange = { value ->
                        masterVolume = value
                        runCatching { engine.setMasterVolumeNormalized(value) }
                    },
                    onKeyboardVelocityChange = { keyboardVelocity = it.coerceIn(1, 127) },
                    onKeyboardOctaveShiftChange = { keyboardOctaveShift = it.coerceIn(-2, 2) },
                    onPartTapped = { partIndex, wasSelected ->
                        if (wasSelected) {
                            inspectorRefreshToken += 1
                            currentDestination = AppDestinations.EDITOR
                        } else {
                            selectedPlayPartIndex = partIndex.coerceIn(0, 15)
                        }
                    },
                    onSetSelectedPartChannel = { channel ->
                        val partIndex = selectedPlayPartIndex.coerceIn(0, 15)
                        val channel0 = (channel.coerceIn(1, 16) - 1)
                        val ok = runCatching { engine.setPartReceiveChannel(partIndex, channel0) }.getOrDefault(false)
                        actionStatus = if (ok) "Part $partIndex channel set to ${channel0 + 1}" else "Failed to set Part $partIndex channel"
                        if (ok) inspectorRefreshToken += 1
                    },
                    onSetSelectedPartVolume = { volume ->
                        val partIndex = selectedPlayPartIndex.coerceIn(0, 15)
                        val clamped = volume.coerceIn(0, 127)
                        val ok = runCatching { engine.setPartVolume127(partIndex, clamped) }.getOrDefault(false)
                        actionStatus = if (ok) "Part $partIndex volume set to $clamped" else "Failed to set Part $partIndex volume"
                        if (ok) inspectorRefreshToken += 1
                    },
                    onSetSelectedPartPan = { pan ->
                        val partIndex = selectedPlayPartIndex.coerceIn(0, 15)
                        val clamped = pan.coerceIn(0, 127)
                        val ok = runCatching { engine.setPartPanning(partIndex, clamped) }.getOrDefault(false)
                        actionStatus = if (ok) "Part $partIndex pan set to $clamped" else "Failed to set Part $partIndex pan"
                        if (ok) inspectorRefreshToken += 1
                    },
                    onSetSelectedPartSense = { sense ->
                        val partIndex = selectedPlayPartIndex.coerceIn(0, 15)
                        val clamped = sense.coerceIn(0, 127)
                        val ok = runCatching { engine.setPartVelocitySense127(partIndex, clamped) }.getOrDefault(false)
                        actionStatus = if (ok) "Part $partIndex sens set to $clamped" else "Failed to set Part $partIndex sens"
                        if (ok) inspectorRefreshToken += 1
                    },
                    onSetSelectedPartStrength = { strength ->
                        val partIndex = selectedPlayPartIndex.coerceIn(0, 15)
                        val clamped = strength.coerceIn(0, 127)
                        val ok = runCatching { engine.setPartVelocityOffset127(partIndex, clamped) }.getOrDefault(false)
                        actionStatus = if (ok) "Part $partIndex str set to $clamped" else "Failed to set Part $partIndex str"
                        if (ok) inspectorRefreshToken += 1
                    },
                    onSetSelectedPartTime = { time ->
                        val partIndex = selectedPlayPartIndex.coerceIn(0, 15)
                        val clamped = time.coerceIn(0, 127)
                        val ok = runCatching { engine.setPartPortamentoTime127(partIndex, clamped) }.getOrDefault(false)
                        actionStatus = if (ok) "Part $partIndex tim set to $clamped" else "Failed to set Part $partIndex tim"
                        if (ok) inspectorRefreshToken += 1
                    },
                    onSetSelectedPartStretch = { stretch ->
                        val partIndex = selectedPlayPartIndex.coerceIn(0, 15)
                        val clamped = stretch.coerceIn(0, 127)
                        val ok = runCatching { engine.setPartPortamentoStretch127(partIndex, clamped) }.getOrDefault(false)
                        actionStatus = if (ok) "Part $partIndex stretch set to $clamped" else "Failed to set Part $partIndex stretch"
                        if (ok) inspectorRefreshToken += 1
                    },
                    onSetSelectedPartStereo = { enabled ->
                        val partIndex = selectedPlayPartIndex.coerceIn(0, 15)
                        val ok = runCatching { engine.setPartStereoEnabled(partIndex, enabled) }.getOrDefault(false)
                        actionStatus = if (ok) "Part $partIndex stereo ${if (enabled) "on" else "off"}" else "Failed to set Part $partIndex stereo"
                        if (ok) inspectorRefreshToken += 1
                    },
                    onSetSelectedPartRndGrp = { enabled ->
                        val partIndex = selectedPlayPartIndex.coerceIn(0, 15)
                        val ok = runCatching { engine.setPartRndGroupingEnabled(partIndex, enabled) }.getOrDefault(false)
                        actionStatus = if (ok) "Part $partIndex rnd grp ${if (enabled) "on" else "off"}" else "Failed to set Part $partIndex rnd grp"
                        if (ok) inspectorRefreshToken += 1
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Play", Icons.Default.Home),
    BANKS("Banks", Icons.Default.Favorite),
    DEBUG("Debug", Icons.Default.AccountBox),
    EDITOR("Editor", Icons.Default.AccountBox),
}

enum class DebugSection { INSPECTOR, TOOLS }

@Composable
private fun DebugScreen(
    section: DebugSection,
    onSectionChange: (DebugSection) -> Unit,
    currentPresetPath: String?,
    inspector: SynthEngine.PresetInspector?,
    partInspectors: List<SynthEngine.PartInspector>,
    mixerInspector: SynthEngine.MixerInspector,
    activeFxSlots: List<SynthEngine.ActiveFxSlot>,
    onSetPart0Enabled: (Boolean) -> Unit,
    onSetPartEnabled: (Int, Boolean) -> Unit,
    onSetPartChannel: (Int, Int) -> Unit,
    onSoloPart: (Int) -> Unit,
    legacyPanel: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        Text("Debug", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onSectionChange(DebugSection.INSPECTOR) }) { Text("Inspector") }
            OutlinedButton(onClick = { onSectionChange(DebugSection.TOOLS) }) { Text("Tools") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        when (section) {
            DebugSection.INSPECTOR -> FxActiveScreen(
                currentPresetPath = currentPresetPath,
                inspector = inspector,
                partInspectors = partInspectors,
                mixerInspector = mixerInspector,
                activeFxSlots = activeFxSlots,
                onSetPart0Enabled = onSetPart0Enabled,
                onSetPartEnabled = onSetPartEnabled,
                onSetPartChannel = onSetPartChannel,
                onSoloPart = onSoloPart,
                modifier = Modifier.weight(1f)
            )
            DebugSection.TOOLS -> legacyPanel()
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun Greeting(
    title: String,
    subtitle: String,
    zynProbeText: String,
    actionStatus: String,
    audioRunning: Boolean,
    toneEnabled: Boolean,
    onStartAudio: () -> Unit,
    onStopAudio: () -> Unit,
    onToggleTone: () -> Unit,
    onNoteOn: () -> Unit,
    onNoteOff: () -> Unit,
    onPressKeyboardNote: (Int) -> Unit,
    onReleaseKeyboardNote: (Int) -> Unit,
    onReleaseKeyboard: () -> Unit,
    onPanic: () -> Unit,
    onMarkHeard: () -> Unit,
    onNextUnreviewed: () -> Unit,
    onResetScan: () -> Unit,
    onMarkPresetStatus: (PresetScanStatus) -> Unit,
    onLoadPreset: (SynthEngine.PresetAsset) -> Unit,
    onStressNotes: () -> Unit,
    onStressReloadCurrentPreset: () -> Unit,
    masterVolume: Float,
    onMasterVolumeChange: (Float) -> Unit,
    currentPresetRating: Int,
    onSetCurrentPresetRating: (Int) -> Unit,
    onExportStars: () -> Unit,
    onAnalyzeRatedGaps: () -> Unit,
    keyboardVelocity: Int,
    onKeyboardVelocityChange: (Int) -> Unit,
    keyboardOctaveShift: Int,
    onKeyboardOctaveShiftChange: (Int) -> Unit,
    packagedPresets: List<SynthEngine.PresetAsset>,
    presetRatings: Map<String, Int>,
    scanResults: Map<String, PresetScanRecord>,
    currentPresetPath: String?,
    presetLoading: Boolean,
    stressRunning: Boolean,
    firstNoteTimerActive: Boolean,
    heldNote: Int?,
    modifier: Modifier = Modifier
) {
    val totalPresets = packagedPresets.size
    val ratingSummary = remember(packagedPresets, presetRatings) { buildRatingSummary(packagedPresets, presetRatings) }
    val currentPresetRecord = currentPresetPath?.let(scanResults::get)
    val currentPresetLabel = currentPresetPath?.let { File(it).nameWithoutExtension }
    val currentPresetRating = currentPresetPath?.let { presetRatings[it] } ?: 0
    Column(
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        Text(text = title)
        Text(text = subtitle)
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "State: ${if (audioRunning) "Running" else "Stopped"}")
        Text(text = actionStatus)
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onStartAudio) { Text("Start Audio") }
            Button(onClick = onStopAudio) { Text("Stop Audio") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Master Volume: ${(masterVolume * 100).toInt()}%")
        Slider(
            value = masterVolume,
            onValueChange = onMasterVolumeChange,
            valueRange = 0f..1f
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Keyboard Velocity: $keyboardVelocity")
        Slider(
            value = keyboardVelocity.toFloat(),
            onValueChange = { onKeyboardVelocityChange(it.toInt()) },
            valueRange = 1f..127f
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Octave Shift: ${if (keyboardOctaveShift >= 0) "+" else ""}$keyboardOctaveShift")
            Button(onClick = { onKeyboardOctaveShiftChange(keyboardOctaveShift - 1) }) { Text("-") }
            Button(onClick = { onKeyboardOctaveShiftChange(0) }) { Text("0") }
            Button(onClick = { onKeyboardOctaveShiftChange(keyboardOctaveShift + 1) }) { Text("+") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onNoteOn) { Text("Note On") }
            Button(onClick = onNoteOff) { Text("Note Off") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onStressNotes, enabled = !stressRunning && !presetLoading) { Text("Stress Notes") }
            Button(
                onClick = onStressReloadCurrentPreset,
                enabled = !stressRunning && !presetLoading && currentPresetPath != null
            ) { Text("Stress Reload") }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Touch keyboard (press/release)")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(60, 62, 64, 65, 67, 69, 71).forEach { note ->
                TactileKey(
                    note = note,
                    active = heldNote == note,
                    onPress = { onPressKeyboardNote(note) },
                    onRelease = { onReleaseKeyboardNote(note) }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onReleaseKeyboard) { Text("All Notes Off") }
            Button(onClick = onPanic) { Text("Panic") }
            Text(text = "Held: ${heldNote?.let(::noteName) ?: "none"}")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Ratings: ${ratingSummary.rated}/${ratingSummary.total} rated | " +
                "1★=${ratingSummary.s1} 2★=${ratingSummary.s2} 3★=${ratingSummary.s3} 4★=${ratingSummary.s4} 5★=${ratingSummary.s5}"
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onNextUnreviewed, enabled = !presetLoading) { Text("Random Instrument") }
            Button(onClick = onExportStars, enabled = !presetLoading) { Text("Export Stars") }
            Button(onClick = onAnalyzeRatedGaps, enabled = !presetLoading) { Text("Analyze Gaps") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = buildString {
                append("Current preset: ")
                append(currentPresetLabel ?: "none")
                append(" | rating=")
                append(if (currentPresetRating > 0) "${currentPresetRating}★" else "0★")
                currentPresetRecord?.let { rec ->
                    rec.loadMs?.let { append(" | load=${it}ms") }
                    rec.firstNoteMs?.let { append(" | first-note=${it}ms") }
                    rec.firstNotePeak?.let { append(" | peak=${String.format(Locale.US, "%.4f", it)}") }
                    if (rec.panicCount > 0) append(" | panic=${rec.panicCount}")
                }
            }
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Rating:")
            PresetStarRating(
                rating = currentPresetRating.coerceIn(0, 5),
                onSetRating = onSetCurrentPresetRating
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Preset browser moved to Banks screen (${totalPresets} packaged)" +
                (if (presetLoading) " • loading…" else "") +
                (if (stressRunning) " • stress…" else "")
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FxActiveScreen(
    currentPresetPath: String?,
    inspector: SynthEngine.PresetInspector?,
    partInspectors: List<SynthEngine.PartInspector>,
    mixerInspector: SynthEngine.MixerInspector,
    activeFxSlots: List<SynthEngine.ActiveFxSlot>,
    onSetPart0Enabled: (Boolean) -> Unit,
    onSetPartEnabled: (Int, Boolean) -> Unit,
    onSetPartChannel: (Int, Int) -> Unit,
    onSoloPart: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        FxScreenHeader(currentPresetPath = currentPresetPath)
        Spacer(modifier = Modifier.height(10.dp))
        inspector?.let { info ->
            InspectorSummaryCard(
                info = info,
                onSetPart0Enabled = onSetPart0Enabled
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        PartsInspectorSection(
            partInspectors = partInspectors,
            onSetPartEnabled = onSetPartEnabled,
            onSetPartChannel = onSetPartChannel,
            onSoloPart = onSoloPart
        )
        Spacer(modifier = Modifier.height(12.dp))
        MixerInspectorSection(mixerInspector = mixerInspector)
        Spacer(modifier = Modifier.height(12.dp))
        if (currentPresetPath == null) {
            Text("Load an instrument/preset to inspect effects.")
        } else if (activeFxSlots.isEmpty()) {
            Text("No active FX detected in current preset.")
        } else {
            ActiveFxList(activeFxSlots = activeFxSlots)
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun FxScreenHeader(currentPresetPath: String?) {
    Text("FX / Inspector")
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Preset: ${currentPresetPath?.let { File(it).nameWithoutExtension } ?: "none"}",
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun InspectorSummaryCard(
    info: SynthEngine.PresetInspector,
    onSetPart0Enabled: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Text("Inspector (Engine Snapshot)", color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "format=${info.format} | part0.enabled=${info.part0Enabled ?: "?"} | part0.note_on=${info.part0NoteOn ?: "?"} | poly=${info.part0PolyMode ?: "?"}",
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "part0.volume=${info.part0Volume ?: "?"} | kit_mode=${info.part0KitMode ?: "?"}",
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "kit items: active=${info.activeKitItems} add=${info.addEnabledCount} sub=${info.subEnabledCount} pad=${info.padEnabledCount}",
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "fx slots: sys=${info.systemFxActiveCount} ins=${info.insertionFxActiveCount} part=${info.instrumentFxActiveCount}",
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onSetPart0Enabled(true) },
                enabled = info.part0Enabled != true
            ) { Text("Enable Part 0") }
            OutlinedButton(
                onClick = { onSetPart0Enabled(false) },
                enabled = info.part0Enabled != false
            ) { Text("Disable Part 0") }
        }
    }
}

@Composable
private fun PartsInspectorSection(
    partInspectors: List<SynthEngine.PartInspector>,
    onSetPartEnabled: (Int, Boolean) -> Unit,
    onSetPartChannel: (Int, Int) -> Unit,
    onSoloPart: (Int) -> Unit,
) {
    if (partInspectors.isEmpty()) return
    Text("Parts")
    Spacer(modifier = Modifier.height(8.dp))
    partInspectors
        .sortedWith(
            compareByDescending<SynthEngine.PartInspector> { it.isLikelyRelevant() }
                .thenBy { it.partIndex }
        )
        .forEach { part ->
            PartInspectorCard(
                part = part,
                onSetPartEnabled = onSetPartEnabled,
                onSetPartChannel = onSetPartChannel,
                onSoloPart = onSoloPart
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
}

@Composable
private fun PartInspectorCard(
    part: SynthEngine.PartInspector,
    onSetPartEnabled: (Int, Boolean) -> Unit,
    onSetPartChannel: (Int, Int) -> Unit,
    onSoloPart: (Int) -> Unit,
) {
    val titleColor = if (part.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        val partLabel = if (part.name.isBlank()) "Part ${part.partIndex}" else "Part ${part.partIndex} - ${part.name}"
        Text(partLabel, color = titleColor)
        Spacer(modifier = Modifier.height(4.dp))
        Text("enabled=${part.enabled} note_on=${part.noteOn} poly=${part.poly}", color = MaterialTheme.colorScheme.onSurface)
        Text(
            "ch=${part.receiveChannel} keys=${part.minKey}..${part.maxKey} vol=${part.volume} pan=${part.panning} kit_mode=${part.kitMode}",
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "volRaw=${"%.2f".format(part.volumeRaw)} gain=${"%.3f".format(part.gainRaw)} peak=${"%.4f".format(part.outputPeak)} | kit: active=${part.activeKitItems} muted=${part.mutedKitItems} add=${part.addEnabledCount} sub=${part.subEnabledCount} pad=${part.padEnabledCount} | partFX=${part.partFxActiveCount}",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onSetPartEnabled(part.partIndex, true) },
                enabled = !part.enabled
            ) { Text("Enable") }
            OutlinedButton(
                onClick = { onSetPartEnabled(part.partIndex, false) },
                enabled = part.enabled
            ) { Text("Disable") }
            OutlinedButton(onClick = { onSoloPart(part.partIndex) }) { Text("Solo") }
        }
        if (part.receiveChannel != 0) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = { onSetPartChannel(part.partIndex, 0) }) {
                Text("Force channel 0")
            }
        }
    }
}

@Composable
private fun MixerInspectorSection(mixerInspector: SynthEngine.MixerInspector) {
    Text("Mixer / Routing")
    Spacer(modifier = Modifier.height(8.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Text("Insert FX -> Part", color = MaterialTheme.colorScheme.primary)
        if (mixerInspector.insertRoutings.isEmpty()) {
            Text("none", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            mixerInspector.insertRoutings.forEach { ins ->
                Text(
                    "Insert #${ins.slotId} ${ins.typeName} (type=${ins.typeId}) -> Part ${ins.assignedPart}",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("System FX Sends (non-zero)", color = MaterialTheme.colorScheme.primary)
        if (mixerInspector.systemSends.isEmpty()) {
            Text("none", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            mixerInspector.systemSends.take(24).forEach { send ->
                Text(
                    "Part ${send.partIndex} -> System #${send.systemFxSlot}: ${send.sendValue}",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (mixerInspector.systemSends.size > 24) {
                Text(
                    "... +${mixerInspector.systemSends.size - 24} more",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ActiveFxList(activeFxSlots: List<SynthEngine.ActiveFxSlot>) {
    activeFxSlots
        .sortedWith(compareBy<SynthEngine.ActiveFxSlot> { it.scope }.thenBy { it.slotId })
        .forEach { fx ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = fx.scope, color = MaterialTheme.colorScheme.primary)
                Text(text = "#${fx.slotId}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = fx.typeName, color = MaterialTheme.colorScheme.onSurface)
                Text(text = "(type=${fx.typeId})", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ZynAddSubFXTheme {
        Greeting(
            title = "Android + JNI",
            subtitle = NativeSmokeStatus.preview().toDisplayString(),
            zynProbeText = NativeSmokeStatus.preview().zynProbeDisplayString(),
            actionStatus = "Preview mode",
            audioRunning = false,
            toneEnabled = false,
            onStartAudio = {},
            onStopAudio = {},
            onToggleTone = {},
            onNoteOn = {},
            onNoteOff = {}
            ,
            onPressKeyboardNote = {},
            onReleaseKeyboardNote = {},
            onReleaseKeyboard = {},
            onPanic = {},
            onMarkHeard = {},
            onNextUnreviewed = {},
            onResetScan = {},
            onMarkPresetStatus = {},
            onLoadPreset = {},
            onStressNotes = {},
            onStressReloadCurrentPreset = {},
            masterVolume = 0.6f,
            onMasterVolumeChange = {},
            currentPresetRating = 3,
            onSetCurrentPresetRating = {},
            onExportStars = {},
            onAnalyzeRatedGaps = {},
            keyboardVelocity = 110,
            onKeyboardVelocityChange = {},
            keyboardOctaveShift = 0,
            onKeyboardOctaveShiftChange = {},
            packagedPresets = listOf(
                SynthEngine.PresetAsset("presets/examples/Saw.xmz", "Examples", "Saw"),
                SynthEngine.PresetAsset("presets/examples/Supersaw.xmz", "Examples", "Supersaw"),
                SynthEngine.PresetAsset("presets/banks/Misc/0001-Arpegio_Bell.xiz", "Bank: Misc", "Arpegio_Bell"),
            ),
            presetRatings = mapOf("presets/examples/Saw.xmz" to 4),
            scanResults = emptyMap(),
            currentPresetPath = null,
            presetLoading = false,
            stressRunning = false,
            firstNoteTimerActive = false,
            heldNote = null
        )
    }
}

enum class PresetScanStatus(val short: String, val label: String) {
    UNKNOWN("-", "Unknown"),
    OK("OK", "OK"),
    SILENT("S", "Silent"),
    HEAVY("H", "Heavy"),
    FAIL("F", "Fail"),
    TIMEOUT("T", "Timeout"),
    ;

    companion object {
        fun fromCode(code: String): PresetScanStatus =
            entries.firstOrNull { it.short == code } ?: UNKNOWN
    }
}

data class PresetScanRecord(
    val status: PresetScanStatus = PresetScanStatus.UNKNOWN,
    val attempts: Int = 0,
    val loadMs: Long? = null,
    val firstNoteMs: Long? = null,
    val firstNotePeak: Float? = null,
    val panicCount: Int = 0,
)

private data class ScanSummary(
    val total: Int,
    val reviewed: Int,
    val ok: Int,
    val silent: Int,
    val heavy: Int,
    val fail: Int,
    val timeout: Int,
)

private data class RatingSummary(
    val total: Int,
    val rated: Int,
    val s1: Int,
    val s2: Int,
    val s3: Int,
    val s4: Int,
    val s5: Int,
)

private data class SectionStatusCounts(
    val ok: Int = 0,
    val silent: Int = 0,
    val heavy: Int = 0,
)

private data class PresetStarsExport(
    val csvFile: File,
    val analysisFile: File,
)

private data class RatedPresetGapAnalysis(
    val summaryLine: String,
    val logLines: List<String>,
)

private data class GroupStats(
    val count: Int,
    val withScan: Int,
    val avgLoadMs: Double,
    val avgFirstNoteMs: Double,
    val avgFirstNotePeak: Double,
    val lowPeakRatio: Double,
    val missingFirstNoteRatio: Double,
    val avgPanicCount: Double,
    val statusCounts: Map<PresetScanStatus, Int>,
    val formatCounts: Map<String, Int>,
    val topSections: List<Pair<String, Int>>,
)

private fun exportPresetRatingsSnapshot(
    context: Context,
    presets: List<SynthEngine.PresetAsset>,
    ratings: Map<String, Int>,
    scanResults: Map<String, PresetScanRecord>,
): PresetStarsExport {
    val timestamp = System.currentTimeMillis()
    val exportDir = context.getExternalFilesDir(null) ?: context.filesDir
    val csvFile = File(exportDir, "preset_stars_export_$timestamp.csv")
    val analysisFile = File(exportDir, "preset_stars_analysis_$timestamp.txt")

    val csvText = buildString {
        append("asset_path,section,display_name,format,rating,scan_status,load_ms,first_note_ms,first_note_peak,panic_count\n")
        presets.sortedBy { it.assetPath }.forEach { preset ->
            val rec = scanResults[preset.assetPath]
            append(csvEscape(preset.assetPath)).append(',')
            append(csvEscape(preset.section)).append(',')
            append(csvEscape(preset.displayName)).append(',')
            append(csvEscape(presetFormatFromAssetPath(preset.assetPath))).append(',')
            append((ratings[preset.assetPath] ?: 0).coerceIn(0, 5)).append(',')
            append(csvEscape(rec?.status?.short ?: "-")).append(',')
            append(rec?.loadMs ?: -1).append(',')
            append(rec?.firstNoteMs ?: -1).append(',')
            append(if (rec?.firstNotePeak != null) String.format(Locale.US, "%.6f", rec.firstNotePeak) else "-1").append(',')
            append(rec?.panicCount ?: 0).append('\n')
        }
    }
    csvFile.writeText(csvText)

    val analysis = buildRatedPresetGapAnalysis(presets, ratings, scanResults)
    analysisFile.writeText(analysis.logLines.joinToString(separator = "\n", postfix = "\n"))
    return PresetStarsExport(csvFile = csvFile, analysisFile = analysisFile)
}

private fun buildRatedPresetGapAnalysis(
    presets: List<SynthEngine.PresetAsset>,
    ratings: Map<String, Int>,
    scanResults: Map<String, PresetScanRecord>,
): RatedPresetGapAnalysis {
    val silentPresets = presets.filter { (ratings[it.assetPath] ?: 0) == 1 }
    val workingPresets = presets.filter { (ratings[it.assetPath] ?: 0) >= 4 }
    if (silentPresets.isEmpty() || workingPresets.isEmpty()) {
        val msg = "Need both 1★ and 4-5★ presets to compare (1★=${silentPresets.size}, 4-5★=${workingPresets.size})"
        return RatedPresetGapAnalysis(summaryLine = msg, logLines = listOf(msg))
    }

    val silentStats = buildGroupStats(silentPresets, scanResults)
    val workingStats = buildGroupStats(workingPresets, scanResults)

    val lines = mutableListOf<String>()
    lines += "Rated gap analysis"
    lines += "silent_group=1★ count=${silentStats.count} with_scan=${silentStats.withScan}"
    lines += "working_group=4-5★ count=${workingStats.count} with_scan=${workingStats.withScan}"
    lines += "load_ms avg: 1★=${fmt1(silentStats.avgLoadMs)} vs 4-5★=${fmt1(workingStats.avgLoadMs)}"
    lines += "first_note_ms avg: 1★=${fmt1(silentStats.avgFirstNoteMs)} vs 4-5★=${fmt1(workingStats.avgFirstNoteMs)}"
    lines += "first_note_peak avg: 1★=${fmt4(silentStats.avgFirstNotePeak)} vs 4-5★=${fmt4(workingStats.avgFirstNotePeak)}"
    lines += "low_peak(<${String.format(Locale.US, "%.4f", FIRST_NOTE_PEAK_THRESHOLD)}): 1★=${fmtPct(silentStats.lowPeakRatio)} vs 4-5★=${fmtPct(workingStats.lowPeakRatio)}"
    lines += "missing_first_note: 1★=${fmtPct(silentStats.missingFirstNoteRatio)} vs 4-5★=${fmtPct(workingStats.missingFirstNoteRatio)}"
    lines += "panic avg: 1★=${fmt2(silentStats.avgPanicCount)} vs 4-5★=${fmt2(workingStats.avgPanicCount)}"
    lines += "status(1★): ${formatStatusCounts(silentStats.statusCounts)}"
    lines += "status(4-5★): ${formatStatusCounts(workingStats.statusCounts)}"
    lines += "format(1★): ${formatMapCounts(silentStats.formatCounts)}"
    lines += "format(4-5★): ${formatMapCounts(workingStats.formatCounts)}"
    lines += "top_sections(1★): ${formatTopSections(silentStats.topSections)}"
    lines += "top_sections(4-5★): ${formatTopSections(workingStats.topSections)}"

    val summary = "Gap 1★ vs 4-5★: missing-first=${fmtPct(silentStats.missingFirstNoteRatio)} vs ${fmtPct(workingStats.missingFirstNoteRatio)}"
    return RatedPresetGapAnalysis(summaryLine = summary, logLines = lines)
}

private fun buildGroupStats(
    presets: List<SynthEngine.PresetAsset>,
    scanResults: Map<String, PresetScanRecord>,
): GroupStats {
    var withScan = 0
    var loadSum = 0.0
    var loadCount = 0
    var firstNoteSum = 0.0
    var firstNoteCount = 0
    var peakSum = 0.0
    var peakCount = 0
    var lowPeakCount = 0
    var missingFirstNote = 0
    var panicSum = 0.0
    val statusCounts = mutableMapOf<PresetScanStatus, Int>()
    val formatCounts = mutableMapOf<String, Int>()
    val sectionCounts = mutableMapOf<String, Int>()

    presets.forEach { preset ->
        val rec = scanResults[preset.assetPath]
        if (rec != null) {
            withScan++
            statusCounts[rec.status] = (statusCounts[rec.status] ?: 0) + 1
            rec.loadMs?.let {
                loadSum += it
                loadCount++
            }
            rec.firstNoteMs?.let {
                firstNoteSum += it
                firstNoteCount++
            } ?: run {
                missingFirstNote++
            }
            rec.firstNotePeak?.let {
                peakSum += it
                peakCount++
                if (it < FIRST_NOTE_PEAK_THRESHOLD) lowPeakCount++
            }
            panicSum += rec.panicCount.toDouble()
        }
        val format = presetFormatFromAssetPath(preset.assetPath)
        formatCounts[format] = (formatCounts[format] ?: 0) + 1
        sectionCounts[preset.section] = (sectionCounts[preset.section] ?: 0) + 1
    }

    val topSections = sectionCounts.entries
        .sortedByDescending { it.value }
        .take(5)
        .map { it.key to it.value }

    return GroupStats(
        count = presets.size,
        withScan = withScan,
        avgLoadMs = if (loadCount > 0) loadSum / loadCount else 0.0,
        avgFirstNoteMs = if (firstNoteCount > 0) firstNoteSum / firstNoteCount else 0.0,
        avgFirstNotePeak = if (peakCount > 0) peakSum / peakCount else 0.0,
        lowPeakRatio = if (peakCount > 0) lowPeakCount.toDouble() / peakCount else 0.0,
        missingFirstNoteRatio = if (withScan > 0) missingFirstNote.toDouble() / withScan else 0.0,
        avgPanicCount = if (withScan > 0) panicSum / withScan else 0.0,
        statusCounts = statusCounts.toSortedMap(compareBy { it.ordinal }),
        formatCounts = formatCounts.toSortedMap(),
        topSections = topSections,
    )
}

private fun presetFormatFromAssetPath(assetPath: String): String = when {
    assetPath.lowercase(Locale.US).endsWith(".xmz") -> "XMZ"
    assetPath.lowercase(Locale.US).endsWith(".xiz") -> "XIZ"
    else -> "?"
}

private fun csvEscape(value: String): String {
    val escaped = value.replace("\"", "\"\"")
    return "\"$escaped\""
}

private fun fmt1(v: Double): String = String.format(Locale.US, "%.1f", v)
private fun fmt2(v: Double): String = String.format(Locale.US, "%.2f", v)
private fun fmt4(v: Double): String = String.format(Locale.US, "%.4f", v)
private fun fmtPct(v: Double): String = String.format(Locale.US, "%.1f%%", v * 100.0)

private fun formatStatusCounts(counts: Map<PresetScanStatus, Int>): String =
    PresetScanStatus.entries.joinToString(", ") { status ->
        "${status.short}=${counts[status] ?: 0}"
    }

private fun formatMapCounts(counts: Map<String, Int>): String =
    if (counts.isEmpty()) "-" else counts.entries.joinToString(", ") { "${it.key}=${it.value}" }

private fun formatTopSections(topSections: List<Pair<String, Int>>): String =
    if (topSections.isEmpty()) "-" else topSections.joinToString(" | ") { (section, count) -> "$section:$count" }

private fun scanResultsFile(context: Context): File =
    File(context.filesDir, "preset_scan_results_v1.json")

private fun presetRatingsFile(context: Context): File =
    File(context.filesDir, "preset_ratings_v1.json")
private fun bankExpandedSectionsFile(context: Context): File =
    File(context.filesDir, "bank_expanded_sections_v1.json")
private fun bankUiStateFile(context: Context): File =
    File(context.filesDir, "bank_ui_state_v1.json")

private fun loadScanResults(context: Context): Map<String, PresetScanRecord> {
    val file = scanResultsFile(context)
    if (!file.exists()) return emptyMap()
    return runCatching {
        val root = JSONObject(file.readText())
        val items = root.optJSONArray("items") ?: JSONArray()
        buildMap {
            for (i in 0 until items.length()) {
                val obj = items.optJSONObject(i) ?: continue
                val path = obj.optString("path", "")
                if (path.isBlank()) continue
                put(
                    path,
                    PresetScanRecord(
                        status = PresetScanStatus.fromCode(obj.optString("status", "-")),
                        attempts = obj.optInt("attempts", 0),
                        loadMs = obj.optLong("loadMs").takeIf { obj.has("loadMs") && it >= 0L },
                        firstNoteMs = obj.optLong("firstNoteMs").takeIf { obj.has("firstNoteMs") && it >= 0L },
                        firstNotePeak = obj.optDouble("firstNotePeak").takeIf { obj.has("firstNotePeak") }
                            ?.toFloat()
                            ?.takeIf { it >= 0f },
                        panicCount = obj.optInt("panicCount", 0),
                    )
                )
            }
        }
    }.getOrElse {
        Log.w(APP_LOG_TAG, "Failed to restore scan results", it)
        emptyMap()
    }
}

private fun persistScanResults(context: Context, results: Map<String, PresetScanRecord>) {
    runCatching {
        val items = JSONArray()
        results.toSortedMap().forEach { (path, rec) ->
            val obj = JSONObject()
                .put("path", path)
                .put("status", rec.status.short)
                .put("attempts", rec.attempts)
                .put("panicCount", rec.panicCount)
            rec.loadMs?.let { obj.put("loadMs", it) }
            rec.firstNoteMs?.let { obj.put("firstNoteMs", it) }
            rec.firstNotePeak?.let { obj.put("firstNotePeak", it) }
            items.put(obj)
        }
        val root = JSONObject()
            .put("version", 1)
            .put("items", items)
        scanResultsFile(context).writeText(root.toString())
    }.onFailure {
        Log.w(APP_LOG_TAG, "Failed to persist scan results", it)
    }
}

private fun clearScanResults(context: Context) {
    runCatching { scanResultsFile(context).delete() }
}

private fun loadPresetRatings(context: Context): Map<String, Int> {
    val file = presetRatingsFile(context)
    if (!file.exists()) return emptyMap()
    return runCatching {
        val root = JSONObject(file.readText())
        val items = root.optJSONArray("items") ?: JSONArray()
        buildMap {
            for (i in 0 until items.length()) {
                val obj = items.optJSONObject(i) ?: continue
                val assetPath = obj.optString("assetPath", "")
                if (assetPath.isBlank()) continue
                put(assetPath, obj.optInt("rating", 0).coerceIn(0, 5))
            }
        }
    }.getOrElse {
        Log.w(APP_LOG_TAG, "Failed to restore preset ratings", it)
        emptyMap()
    }
}

private fun persistPresetRatings(context: Context, ratings: Map<String, Int>) {
    runCatching {
        val items = JSONArray()
        ratings.toSortedMap().forEach { (assetPath, rating) ->
            items.put(
                JSONObject()
                    .put("assetPath", assetPath)
                    .put("rating", rating.coerceIn(0, 5))
            )
        }
        presetRatingsFile(context).writeText(
            JSONObject()
                .put("version", 1)
                .put("items", items)
                .toString()
        )
    }.onFailure {
        Log.w(APP_LOG_TAG, "Failed to persist preset ratings", it)
    }
}

private fun loadBankExpandedSections(context: Context): Map<String, Boolean> {
    val file = bankExpandedSectionsFile(context)
    if (!file.exists()) return emptyMap()
    return runCatching {
        val root = JSONObject(file.readText())
        val items = root.optJSONArray("items") ?: JSONArray()
        buildMap {
            for (i in 0 until items.length()) {
                val obj = items.optJSONObject(i) ?: continue
                val section = obj.optString("section", "")
                if (section.isBlank()) continue
                put(section, obj.optBoolean("expanded", false))
            }
        }
    }.getOrElse {
        Log.w(APP_LOG_TAG, "Failed to restore bank expanded sections", it)
        emptyMap()
    }
}

private fun persistBankExpandedSections(context: Context, states: Map<String, Boolean>) {
    runCatching {
        val items = JSONArray()
        states.toSortedMap().forEach { (section, expanded) ->
            items.put(JSONObject().put("section", section).put("expanded", expanded))
        }
        bankExpandedSectionsFile(context).writeText(
            JSONObject()
                .put("version", 1)
                .put("items", items)
                .toString()
        )
    }.onFailure {
        Log.w(APP_LOG_TAG, "Failed to persist bank expanded sections", it)
    }
}

private fun loadLastSelectedPresetPath(context: Context): String? {
    val file = bankUiStateFile(context)
    if (!file.exists()) return null
    return runCatching {
        JSONObject(file.readText()).optString("lastPresetPath", "").ifBlank { null }
    }.getOrElse {
        Log.w(APP_LOG_TAG, "Failed to restore bank UI state", it)
        null
    }
}

private fun persistLastSelectedPresetPath(context: Context, path: String?) {
    runCatching {
        val root = JSONObject()
        if (!path.isNullOrBlank()) root.put("lastPresetPath", path)
        bankUiStateFile(context).writeText(root.toString())
    }.onFailure {
        Log.w(APP_LOG_TAG, "Failed to persist bank UI state", it)
    }
}

private fun isHeavyHint(record: PresetScanRecord?): Boolean {
    if (record == null) return false
    if (record.panicCount > 0) return true
    val firstNoteMs = record.firstNoteMs ?: return false
    return firstNoteMs >= HEAVY_FIRST_NOTE_MS_THRESHOLD
}

private fun countStatusesForSection(
    presets: List<SynthEngine.PresetAsset>,
    results: Map<String, PresetScanRecord>
): SectionStatusCounts {
    var ok = 0
    var silent = 0
    var heavy = 0
    presets.forEach { preset ->
        when (results[preset.assetPath]?.status ?: PresetScanStatus.UNKNOWN) {
            PresetScanStatus.OK -> ok++
            PresetScanStatus.SILENT -> silent++
            PresetScanStatus.HEAVY -> heavy++
            else -> Unit
        }
    }
    return SectionStatusCounts(ok = ok, silent = silent, heavy = heavy)
}

private fun buildRatingSummary(
    presets: List<SynthEngine.PresetAsset>,
    ratings: Map<String, Int>
): RatingSummary {
    var rated = 0
    var s1 = 0
    var s2 = 0
    var s3 = 0
    var s4 = 0
    var s5 = 0
    presets.forEach { preset ->
        when ((ratings[preset.assetPath] ?: 0).coerceIn(0, 5)) {
            0 -> Unit
            1 -> { rated++; s1++ }
            2 -> { rated++; s2++ }
            3 -> { rated++; s3++ }
            4 -> { rated++; s4++ }
            5 -> { rated++; s5++ }
            else -> Unit
        }
    }
    return RatingSummary(
        total = presets.size,
        rated = rated,
        s1 = s1,
        s2 = s2,
        s3 = s3,
        s4 = s4,
        s5 = s5,
    )
}

private fun statusColor(status: PresetScanStatus): Color = when (status) {
    PresetScanStatus.OK -> Color(0xFF2E7D32)
    PresetScanStatus.SILENT -> Color(0xFF9C6B00)
    PresetScanStatus.HEAVY -> Color(0xFF8E24AA)
    PresetScanStatus.FAIL, PresetScanStatus.TIMEOUT -> Color(0xFFC62828)
    PresetScanStatus.UNKNOWN -> Color(0xFF666666)
}

private fun presetReviewPriority(status: PresetScanStatus): Int = when (status) {
    PresetScanStatus.HEAVY -> 0
    PresetScanStatus.SILENT -> 1
    PresetScanStatus.FAIL -> 2
    PresetScanStatus.TIMEOUT -> 3
    PresetScanStatus.UNKNOWN -> 4
    PresetScanStatus.OK -> 5
}

private fun buildScanSummary(
    presets: List<SynthEngine.PresetAsset>,
    results: Map<String, PresetScanRecord>
): ScanSummary {
    var reviewed = 0
    var ok = 0
    var silent = 0
    var heavy = 0
    var fail = 0
    var timeout = 0
    presets.forEach { preset ->
        when (results[preset.assetPath]?.status ?: PresetScanStatus.UNKNOWN) {
            PresetScanStatus.UNKNOWN -> Unit
            PresetScanStatus.OK -> { reviewed++; ok++ }
            PresetScanStatus.SILENT -> { reviewed++; silent++ }
            PresetScanStatus.HEAVY -> { reviewed++; heavy++ }
            PresetScanStatus.FAIL -> { reviewed++; fail++ }
            PresetScanStatus.TIMEOUT -> { reviewed++; timeout++ }
        }
    }
    return ScanSummary(
        total = presets.size,
        reviewed = reviewed,
        ok = ok,
        silent = silent,
        heavy = heavy,
        fail = fail,
        timeout = timeout,
    )
}

private fun logScanSummary(
    presets: List<SynthEngine.PresetAsset>,
    results: Map<String, PresetScanRecord>
) {
    val s = buildScanSummary(presets, results)
    Log.i(
        SCAN_LOG_TAG,
        "$SCAN_LOG_PREFIX summary reviewed=${s.reviewed}/${s.total} ok=${s.ok} silent=${s.silent} heavy=${s.heavy} fail=${s.fail} timeout=${s.timeout}"
    )
}

data class NativeSmokeStatus(
    val loaded: Boolean,
    val version: String?,
    val zynProbe: String?,
    val renderBackend: String?,
    val initOk: Boolean,
    val sampleRate: Int?,
    val framesPerBurst: Int?,
    val error: String?
) {
    fun toDisplayString(): String {
        if (!loaded) return "JNI load failed: ${error ?: "unknown error"}"
        return "JNI OK | version=$version | init=$initOk | sr=${sampleRate ?: 0} | burst=${framesPerBurst ?: 0} | render=${renderBackend ?: "unknown"}"
    }

    fun zynProbeDisplayString(): String = zynProbe ?: "Zyn upstream probe unavailable"

    companion object {
        fun preview() = NativeSmokeStatus(
            loaded = true,
            version = "preview",
            zynProbe = "zyn-upstream-probe 3.0.7 | parts=16 | fusion_dir=",
            renderBackend = "zyn-master",
            initOk = true,
            sampleRate = 48_000,
            framesPerBurst = 192,
            error = null
        )
    }
}

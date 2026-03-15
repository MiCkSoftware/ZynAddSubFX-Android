package com.mick.zynaddsubfx

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlin.system.measureNanoTime

class SynthEngine(private val context: Context) {
    companion object {
        @Volatile
        private var packagedPresetCache: List<PresetAsset>? = null
    }
    data class PresetAsset(
        val assetPath: String,
        val section: String,
        val displayName: String,
    )

    data class RuntimeCounters(
        val presetFails: Int,
        val presetTimeouts: Int,
    )

    data class ActiveFxSlot(
        val scope: String,
        val slotId: Int,
        val typeId: Int,
        val typeName: String,
    )

    data class PresetInspector(
        val format: String,
        val part0Enabled: Boolean?,
        val part0Volume: Int?,
        val part0NoteOn: Boolean?,
        val part0PolyMode: Boolean?,
        val part0KitMode: Int?,
        val activeKitItems: Int,
        val addEnabledCount: Int,
        val subEnabledCount: Int,
        val padEnabledCount: Int,
        val systemFxActiveCount: Int,
        val insertionFxActiveCount: Int,
        val instrumentFxActiveCount: Int,
    ) {
        fun toDebugString(): String = buildString {
            append("format=").append(format)
            append(" part0.enabled=").append(part0Enabled?.toString() ?: "?")
            append(" part0.note_on=").append(part0NoteOn?.toString() ?: "?")
            append(" part0.poly=").append(part0PolyMode?.toString() ?: "?")
            append(" part0.volume=").append(part0Volume?.toString() ?: "?")
            append(" kit_mode=").append(part0KitMode?.toString() ?: "?")
            append(" kit.active=").append(activeKitItems)
            append(" add=").append(addEnabledCount)
            append(" sub=").append(subEnabledCount)
            append(" pad=").append(padEnabledCount)
            append(" fx(sys=").append(systemFxActiveCount)
            append(",ins=").append(insertionFxActiveCount)
            append(",part=").append(instrumentFxActiveCount).append(")")
        }
    }

    data class PartInspector(
        val partIndex: Int,
        val enabled: Boolean,
        val noteOn: Boolean,
        val poly: Boolean,
        val receiveChannel: Int,
        val minKey: Int,
        val maxKey: Int,
        val volume: Int,
        val panning: Int,
        val volumeRaw: Float,
        val gainRaw: Float,
        val outputPeak: Float,
        val kitMode: Int,
        val activeKitItems: Int,
        val mutedKitItems: Int,
        val addEnabledCount: Int,
        val subEnabledCount: Int,
        val padEnabledCount: Int,
        val partFxActiveCount: Int,
        val velocitySense: Int,
        val velocityOffset: Int,
        val portamentoTime: Int,
        val portamentoStretch: Int,
        val stereoEnabled: Boolean,
        val rndGroupingEnabled: Boolean,
        val name: String,
    ) {
        fun isLikelyRelevant(): Boolean =
            enabled || activeKitItems > 0 || addEnabledCount > 0 || subEnabledCount > 0 || padEnabledCount > 0
    }

    data class InsertRoutingInfo(
        val slotId: Int,
        val typeId: Int,
        val typeName: String,
        val assignedPart: Int,
    )

    data class SystemSendInfo(
        val systemFxSlot: Int,
        val partIndex: Int,
        val sendValue: Int,
    )

    data class MixerInspector(
        val insertRoutings: List<InsertRoutingInfo>,
        val systemSends: List<SystemSendInfo>,
    )

    data class PresetLoadResult(
        val ok: Boolean,
        val loadMs: Long,
        val timedOut: Boolean,
    )

    fun startAudio(): Boolean = NativeSynthBridge.nativeStartAudio()

    fun stopAudio() = NativeSynthBridge.nativeStopAudio()

    fun isAudioRunning(): Boolean = NativeSynthBridge.nativeIsAudioRunning()

    fun noteOn(channel: Int, note: Int, velocity: Int) =
        NativeSynthBridge.nativeNoteOn(channel, note, velocity)

    fun noteOff(channel: Int, note: Int) =
        NativeSynthBridge.nativeNoteOff(channel, note)

    fun panic() = NativeSynthBridge.nativePanic()

    fun setTestToneEnabled(enabled: Boolean) =
        NativeSynthBridge.nativeSetTestToneEnabled(enabled)

    fun setTestToneFrequencyHz(frequencyHz: Float) =
        NativeSynthBridge.nativeSetTestToneFrequencyHz(frequencyHz)

    fun loadDemoPreset(assetName: String): Boolean {
        return loadPackagedPreset("presets/examples/$assetName")
    }

    fun loadPackagedPreset(assetPath: String): Boolean {
        val path = ensurePresetAssetOnDisk(assetPath)
        return NativeSynthBridge.nativeLoadPresetFile(path.absolutePath)
    }

    fun loadPackagedPresetMeasured(assetPath: String): PresetLoadResult {
        val before = runtimeCounters()
        var ok = false
        val elapsedNanos = measureNanoTime {
            ok = loadPackagedPreset(assetPath)
        }
        val after = runtimeCounters()
        return PresetLoadResult(
            ok = ok,
            loadMs = elapsedNanos / 1_000_000L,
            timedOut = after.presetTimeouts > before.presetTimeouts
        )
    }

    fun listPackagedPresets(): List<PresetAsset> {
        packagedPresetCache?.let { return it }
        val roots = listOf("presets/examples", "presets/banks")
        val assetPaths = mutableListOf<String>()
        roots.forEach { root -> collectPresetAssets(root, assetPaths) }
        val built = assetPaths
            .sorted()
            .map { assetPath ->
                PresetAsset(
                    assetPath = assetPath,
                    section = sectionForAsset(assetPath),
                    displayName = File(assetPath).nameWithoutExtension
                )
            }
        packagedPresetCache = built
        return built
    }

    fun getMasterVolumeNormalized(): Float =
        NativeSynthBridge.nativeGetMasterVolumeNormalized()

    fun setMasterVolumeNormalized(value: Float) =
        NativeSynthBridge.nativeSetMasterVolumeNormalized(value)

    fun runtimeDiagnostics(): String =
        NativeSynthBridge.nativeGetRuntimeDiagnostics()

    fun recentOutputPeak(): Float =
        NativeSynthBridge.nativeGetRecentOutputPeak()

    fun clearRecentOutputPeak() =
        NativeSynthBridge.nativeClearRecentOutputPeak()

    fun runtimeCounters(): RuntimeCounters {
        val diag = runtimeDiagnostics()
        return RuntimeCounters(
            presetFails = parseCounter(diag, "presetFails"),
            presetTimeouts = parseCounter(diag, "presetTimeouts"),
        )
    }

    fun setPart0Enabled(enabled: Boolean): Boolean =
        runCatching { NativeSynthBridge.nativeSetPart0Enabled(enabled) }.getOrDefault(false)

    fun setPartEnabled(partIndex: Int, enabled: Boolean): Boolean =
        runCatching { NativeSynthBridge.nativeSetPartEnabled(partIndex, enabled) }.getOrDefault(false)

    fun setPartReceiveChannel(partIndex: Int, channel: Int): Boolean =
        runCatching { NativeSynthBridge.nativeSetPartReceiveChannel(partIndex, channel) }.getOrDefault(false)

    fun setPartVolume127(partIndex: Int, volume127: Int): Boolean =
        runCatching { NativeSynthBridge.nativeSetPartVolume127(partIndex, volume127) }.getOrDefault(false)

    fun setPartPanning(partIndex: Int, panning127: Int): Boolean =
        runCatching { NativeSynthBridge.nativeSetPartPanning(partIndex, panning127) }.getOrDefault(false)

    fun setPartVelocitySense127(partIndex: Int, sense127: Int): Boolean =
        runCatching { NativeSynthBridge.nativeSetPartVelocitySense127(partIndex, sense127) }.getOrDefault(false)

    fun setPartVelocityOffset127(partIndex: Int, offset127: Int): Boolean =
        runCatching { NativeSynthBridge.nativeSetPartVelocityOffset127(partIndex, offset127) }.getOrDefault(false)

    fun setPartPortamentoTime127(partIndex: Int, time127: Int): Boolean =
        runCatching { NativeSynthBridge.nativeSetPartPortamentoTime127(partIndex, time127) }.getOrDefault(false)

    fun setPartPortamentoStretch127(partIndex: Int, stretch127: Int): Boolean =
        runCatching { NativeSynthBridge.nativeSetPartPortamentoStretch127(partIndex, stretch127) }.getOrDefault(false)

    fun setPartAddEnabled(partIndex: Int, enabled: Boolean): Boolean =
        runCatching { NativeSynthBridge.nativeSetPartAddEnabled(partIndex, enabled) }.getOrDefault(false)

    fun setPartSubEnabled(partIndex: Int, enabled: Boolean): Boolean =
        runCatching { NativeSynthBridge.nativeSetPartSubEnabled(partIndex, enabled) }.getOrDefault(false)

    fun setPartPadEnabled(partIndex: Int, enabled: Boolean): Boolean =
        runCatching { NativeSynthBridge.nativeSetPartPadEnabled(partIndex, enabled) }.getOrDefault(false)

    fun setPartStereoEnabled(partIndex: Int, enabled: Boolean): Boolean =
        runCatching { NativeSynthBridge.nativeSetPartStereoEnabled(partIndex, enabled) }.getOrDefault(false)

    fun setPartRndGroupingEnabled(partIndex: Int, enabled: Boolean): Boolean =
        runCatching { NativeSynthBridge.nativeSetPartRndGroupingEnabled(partIndex, enabled) }.getOrDefault(false)

    fun soloPart(partIndex: Int): Boolean =
        runCatching { NativeSynthBridge.nativeSoloPart(partIndex) }.getOrDefault(false)

    fun inspectParts(): List<PartInspector> =
        parseNativePartsSummary(
            runCatching { NativeSynthBridge.nativeGetCurrentPartsSummary() }.getOrDefault("")
        )

    fun inspectMixer(): MixerInspector =
        parseNativeMixerSummary(
            runCatching { NativeSynthBridge.nativeGetCurrentMixerSummary() }.getOrDefault("")
        )

    fun describeActiveEffects(assetPath: String?): List<ActiveFxSlot> {
        val nativeSummary = runCatching { NativeSynthBridge.nativeGetCurrentActiveFxSummary() }.getOrDefault("")
        val nativeSlots = parseNativeFxSummary(nativeSummary)
        if (nativeSlots.isNotEmpty()) return nativeSlots

        if (assetPath.isNullOrBlank()) return emptyList()
        val text = readPresetAssetText(assetPath) ?: return emptyList()
        val slots = mutableListOf<ActiveFxSlot>()
        collectFxFromScope(text, "SYSTEM_EFFECT", "System", slots)
        collectFxFromScope(text, "INSERTION_EFFECT", "Insert", slots)
        collectFxFromScope(text, "INSTRUMENT_EFFECT", "Instrument", slots)
        return slots
    }

    fun inspectPreset(assetPath: String?): PresetInspector? {
        parseNativeInspectorSummary(
            runCatching { NativeSynthBridge.nativeGetCurrentPresetInspectorSummary() }.getOrDefault("")
        )?.let { return it }

        if (assetPath.isNullOrBlank()) return null
        val xml = readPresetAssetText(assetPath) ?: return null
        val format = when {
            assetPath.lowercase().endsWith(".xmz") -> "XMZ"
            assetPath.lowercase().endsWith(".xiz") -> "XIZ"
            else -> "?"
        }
        val part0Block = Regex("""<PART\s+id="0">(.*?)</PART>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(xml)?.groupValues?.getOrNull(1)

        fun firstInt(block: String?, name: String): Int? =
            block?.let {
                Regex("""<par\s+name="$name"\s+value="(-?\d+)"""", RegexOption.IGNORE_CASE)
                    .find(it)?.groupValues?.getOrNull(1)?.toIntOrNull()
            }
        fun firstBool(block: String?, name: String): Boolean? =
            block?.let {
                Regex("""<par_bool\s+name="$name"\s+value="(yes|no)"""", RegexOption.IGNORE_CASE)
                    .find(it)?.groupValues?.getOrNull(1)?.lowercase(Locale.US)
                    ?.let { v -> v == "yes" }
            }

        val kitItemRegex = Regex("""<INSTRUMENT_KIT_ITEM\s+id="\d+">(.*?)</INSTRUMENT_KIT_ITEM>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        var activeKitItems = 0
        var addEnabledCount = 0
        var subEnabledCount = 0
        var padEnabledCount = 0
        kitItemRegex.findAll(part0Block ?: "").forEach { m ->
            val block = m.groupValues[1]
            val enabled = firstBool(block, "enabled") == true
            val add = firstBool(block, "add_enabled") == true
            val sub = firstBool(block, "sub_enabled") == true
            val pad = firstBool(block, "pad_enabled") == true
            if (enabled) activeKitItems++
            if (add) addEnabledCount++
            if (sub) subEnabledCount++
            if (pad) padEnabledCount++
        }

        val fxSlots = describeActiveEffects(assetPath)
        return PresetInspector(
            format = format,
            part0Enabled = firstBool(part0Block, "enabled"),
            part0Volume = firstInt(part0Block, "volume"),
            part0NoteOn = firstBool(part0Block, "note_on"),
            part0PolyMode = firstBool(part0Block, "poly_mode"),
            part0KitMode = firstInt(part0Block, "kit_mode"),
            activeKitItems = activeKitItems,
            addEnabledCount = addEnabledCount,
            subEnabledCount = subEnabledCount,
            padEnabledCount = padEnabledCount,
            systemFxActiveCount = fxSlots.count { it.scope == "System" },
            insertionFxActiveCount = fxSlots.count { it.scope == "Insert" },
            instrumentFxActiveCount = fxSlots.count { it.scope == "Instrument" },
        )
    }

    private fun ensurePresetAssetOnDisk(assetPath: String): File {
        val outRoot = File(context.cacheDir, "zyn_packaged_presets").apply { mkdirs() }
        val outFile = File(outRoot, assetPath)
        outFile.parentFile?.mkdirs()
        if (outFile.exists() && outFile.length() > 0L) return outFile
        context.assets.open(assetPath).use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        }
        return outFile
    }

    private fun readPresetAssetText(assetPath: String): String? {
        val file = runCatching { ensurePresetAssetOnDisk(assetPath) }.getOrNull() ?: return null
        return runCatching {
            if (isGzipFile(file)) {
                GZIPInputStream(FileInputStream(file)).use { gz ->
                    InputStreamReader(gz, Charsets.UTF_8).use { it.readText() }
                }
            } else {
                file.readText()
            }
        }.getOrNull()
    }

    private fun isGzipFile(file: File): Boolean {
        if (!file.exists() || file.length() < 2L) return false
        return runCatching {
            FileInputStream(file).use { input ->
                val b0 = input.read()
                val b1 = input.read()
                b0 == 0x1f && b1 == 0x8b
            }
        }.getOrDefault(false)
    }

    private fun parseNativeFxSummary(summary: String): List<ActiveFxSlot> {
        if (summary.isBlank()) return emptyList()
        return summary.lineSequence().mapNotNull { line ->
            val parts = line.split('|')
            if (parts.size < 4) return@mapNotNull null
            val scope = parts[0]
            val slotId = parts[1].toIntOrNull() ?: return@mapNotNull null
            val typeId = parts[2].toIntOrNull() ?: return@mapNotNull null
            val typeName = parts.subList(3, parts.size).joinToString("|")
            if (typeId <= 0) null else ActiveFxSlot(scope, slotId, typeId, typeName)
        }.toList()
    }

    private fun parseNativeInspectorSummary(summary: String): PresetInspector? {
        if (summary.isBlank()) return null
        if (!summary.contains("part0.")) return null
        fun findToken(name: String): String? =
            Regex("""\b${Regex.escape(name)}=([^\s]+)""").find(summary)?.groupValues?.getOrNull(1)
        fun parseBool(name: String): Boolean? = when (findToken(name)?.lowercase(Locale.US)) {
            "true", "1", "yes" -> true
            "false", "0", "no" -> false
            "?" -> null
            else -> null
        }
        fun parseInt(name: String): Int? = findToken(name)?.takeUnless { it == "?" }?.toIntOrNull()
        fun parseFxCount(label: String): Int {
            val fxToken = Regex("""fx\(sys=(\d+),ins=(\d+),part=(\d+)\)""").find(summary)
            return when (label) {
                "sys" -> fxToken?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                "ins" -> fxToken?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
                "part" -> fxToken?.groupValues?.getOrNull(3)?.toIntOrNull() ?: 0
                else -> 0
            }
        }
        return PresetInspector(
            format = findToken("format") ?: "?",
            part0Enabled = parseBool("part0.enabled"),
            part0Volume = parseInt("part0.volume"),
            part0NoteOn = parseBool("part0.note_on"),
            part0PolyMode = parseBool("part0.poly"),
            part0KitMode = parseInt("kit_mode"),
            activeKitItems = parseInt("kit.active") ?: 0,
            addEnabledCount = parseInt("add") ?: 0,
            subEnabledCount = parseInt("sub") ?: 0,
            padEnabledCount = parseInt("pad") ?: 0,
            systemFxActiveCount = parseFxCount("sys"),
            insertionFxActiveCount = parseFxCount("ins"),
            instrumentFxActiveCount = parseFxCount("part"),
        )
    }

    private fun parseNativePartsSummary(summary: String): List<PartInspector> {
        if (summary.isBlank()) return emptyList()
        return summary.lineSequence().mapNotNull { line ->
            val p = line.split('|')
            if (p.size < 27) return@mapNotNull null
            val idx = p[0].toIntOrNull() ?: return@mapNotNull null
            fun b(i: Int) = p.getOrNull(i) == "1"
            fun n(i: Int) = p.getOrNull(i)?.toIntOrNull() ?: 0
            fun f(i: Int) = p.getOrNull(i)?.toFloatOrNull() ?: 0f
            PartInspector(
                partIndex = idx,
                enabled = b(1),
                noteOn = b(3),
                poly = b(4),
                receiveChannel = n(5),
                minKey = n(6),
                maxKey = n(7),
                volume = n(8),
                panning = n(9),
                volumeRaw = f(10),
                gainRaw = f(11),
                outputPeak = f(12),
                kitMode = n(13),
                activeKitItems = n(14),
                mutedKitItems = n(15),
                addEnabledCount = n(16),
                subEnabledCount = n(17),
                padEnabledCount = n(18),
                partFxActiveCount = n(19),
                velocitySense = n(20),
                velocityOffset = n(21),
                portamentoTime = n(22),
                portamentoStretch = n(23),
                stereoEnabled = b(24),
                rndGroupingEnabled = b(25),
                name = p.subList(26, p.size).joinToString("|")
            )
        }.toList()
    }

    private fun parseNativeMixerSummary(summary: String): MixerInspector {
        if (summary.isBlank()) return MixerInspector(emptyList(), emptyList())
        val inserts = mutableListOf<InsertRoutingInfo>()
        val sends = mutableListOf<SystemSendInfo>()
        summary.lineSequence().forEach { line ->
            val p = line.split('|')
            when (p.firstOrNull()) {
                "INS" -> {
                    if (p.size >= 5) {
                        val slotId = p[1].toIntOrNull() ?: return@forEach
                        val typeId = p[2].toIntOrNull() ?: return@forEach
                        val typeName = p[3]
                        val assignedPart = p[4].toIntOrNull() ?: -1
                        inserts += InsertRoutingInfo(slotId, typeId, typeName, assignedPart)
                    }
                }
                "SYS_SEND" -> {
                    if (p.size >= 4) {
                        val fx = p[1].toIntOrNull() ?: return@forEach
                        val part = p[2].toIntOrNull() ?: return@forEach
                        val send = p[3].toIntOrNull() ?: return@forEach
                        sends += SystemSendInfo(fx, part, send)
                    }
                }
            }
        }
        return MixerInspector(
            insertRoutings = inserts.sortedWith(compareBy<InsertRoutingInfo> { it.slotId }.thenBy { it.assignedPart }),
            systemSends = sends.sortedWith(compareBy<SystemSendInfo> { it.systemFxSlot }.thenBy { it.partIndex })
        )
    }

    private fun collectFxFromScope(
        xml: String,
        tagName: String,
        scopeLabel: String,
        out: MutableList<ActiveFxSlot>
    ) {
        val blockRegex = Regex("""<$tagName\s+id="(\d+)">(.*?)</$tagName>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val typeRegex = Regex("""<par\s+name="type"\s+value="(\d+)"""", RegexOption.IGNORE_CASE)
        blockRegex.findAll(xml).forEach { match ->
            val slotId = match.groupValues[1].toIntOrNull() ?: return@forEach
            val block = match.groupValues[2]
            val typeId = typeRegex.find(block)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            if (typeId <= 0) return@forEach
            out += ActiveFxSlot(
                scope = scopeLabel,
                slotId = slotId,
                typeId = typeId,
                typeName = effectTypeName(typeId)
            )
        }
    }

    private fun effectTypeName(typeId: Int): String = when (typeId) {
        1 -> "Reverb"
        2 -> "Echo"
        3 -> "Chorus"
        4 -> "Phaser"
        5 -> "Alienwah"
        6 -> "Distortion"
        7 -> "EQ"
        8 -> "DynFilter"
        9 -> "Sympathetic"
        10 -> "Reverse"
        else -> "FX-$typeId"
    }

    private fun collectPresetAssets(dir: String, out: MutableList<String>) {
        val children = context.assets.list(dir).orEmpty().sorted()
        for (child in children) {
            val path = "$dir/$child"
            if (isPresetFile(path)) {
                out += path
                continue
            }
            val nested = context.assets.list(path).orEmpty()
            if (nested.isNotEmpty()) {
                collectPresetAssets(path, out)
            }
        }
    }

    private fun isPresetFile(path: String): Boolean {
        val lower = path.lowercase()
        return lower.endsWith(".xmz") || lower.endsWith(".xiz")
    }

    private fun sectionForAsset(assetPath: String): String {
        return when {
            assetPath.startsWith("presets/examples/") -> "Examples"
            assetPath.startsWith("presets/banks/") -> {
                val parts = assetPath.split("/")
                val bankName = parts.getOrNull(2) ?: "Unknown"
                "Bank: $bankName"
            }
            else -> "Other"
        }
    }

    private fun parseCounter(diag: String, key: String): Int {
        val marker = "$key="
        val start = diag.indexOf(marker)
        if (start < 0) return 0
        val valueStart = start + marker.length
        var end = valueStart
        while (end < diag.length && diag[end].isDigit()) {
            end++
        }
        return diag.substring(valueStart, end).toIntOrNull() ?: 0
    }
}

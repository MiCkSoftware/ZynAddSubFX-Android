package com.mick.zynaddsubfx

import kotlin.math.pow

const val FORMANT_VOWEL_SLOTS = 6

internal fun subHarmonicHue(index: Int): Float = (index * 137.508f) % 360f

enum class ModuleEngine(val code: Int, val path: String) {
    ADD(0, "add"), SUB(1, "sub"), PAD(2, "pad"),
}

sealed interface ModuleAddress {
    val engine: ModuleEngine get() = ModuleEngine.ADD
    val referenceNote: Int get() = 69
    val kind: Int
    val index: Int
    val role: Int

    data class Voice(override val index: Int) : ModuleAddress {
        override val kind = 0
        override val role = 0
    }

    data class Oscillator(override val index: Int, val modulator: Boolean = false, override val engine: ModuleEngine = ModuleEngine.ADD) : ModuleAddress {
        override val kind = 1
        override val role = if (modulator) 1 else 0
    }

    data class OscillatorPreview(val oscillator: Oscillator, val view: OscillatorPreviewView) : ModuleAddress {
        override val engine = oscillator.engine
        override val kind = 8
        override val index = oscillator.index
        override val role = view.code * 2 + oscillator.role
    }

    data class Envelope(override val index: Int = -1, val envelopeRole: EnvelopeRole, override val engine: ModuleEngine = ModuleEngine.ADD) : ModuleAddress {
        override val kind = 2
        override val role = envelopeRole.code
    }

    data class Lfo(override val index: Int = -1, val lfoRole: LfoRole, override val engine: ModuleEngine = ModuleEngine.ADD) : ModuleAddress {
        override val kind = 3
        override val role = lfoRole.code
    }

    data class Filter(override val index: Int = -1, val vowel: Int = 0, override val engine: ModuleEngine = ModuleEngine.ADD) : ModuleAddress {
        override val kind = 4
        override val role = vowel.coerceIn(0, FORMANT_VOWEL_SLOTS - 1)
    }

    data class SubHarmonicsPreview(override val referenceNote: Int) : ModuleAddress {
        override val engine = ModuleEngine.SUB
        override val kind = 9
        override val index = -1
        override val role = 0
    }

    data object SubHarmonics : ModuleAddress {
        override val engine = ModuleEngine.SUB
        override val kind = 9
        override val index = -1
        override val role = 0
    }

    data object PadProfile : ModuleAddress {
        override val engine = ModuleEngine.PAD
        override val kind = 10
        override val index = -1
        override val role = 0
    }

    data object PadOvertones : ModuleAddress {
        override val engine = ModuleEngine.PAD
        override val kind = 11
        override val index = -1
        override val role = 0
    }

    data class Amplitude(override val index: Int) : ModuleAddress {
        override val kind = 7
        override val role = 0
    }

    data class EngineResonance(override val engine: ModuleEngine) : ModuleAddress {
        override val kind = 5
        override val index = -1
        override val role = 0
    }

    data object Resonance : ModuleAddress {
        override val kind = 5
        override val index = -1
        override val role = 0
    }

    data class Vowel(override val index: Int = -1, val vowel: Int, override val engine: ModuleEngine = ModuleEngine.ADD) : ModuleAddress {
        override val kind = 6
        override val role = vowel.coerceIn(0, FORMANT_VOWEL_SLOTS - 1)
    }
}

enum class OscillatorPreviewView(val code: Int, val label: String) {
    BASE_WAVE(0, "Base wave"),
    OUTPUT_SPECTRUM(1, "Output spectrum"),
    BASE_SPECTRUM(2, "Base spectrum"),
}

enum class EnvelopeRole(val code: Int, val title: String) {
    AMPLITUDE(0, "Amplitude envelope"),
    FREQUENCY(1, "Frequency envelope"),
    FILTER(2, "Filter envelope"),
    MODULATOR_AMPLITUDE(3, "Modulator amplitude envelope"),
    MODULATOR_FREQUENCY(4, "Modulator frequency envelope"),
    BANDWIDTH(5, "Bandwidth envelope"),
}

enum class LfoRole(val code: Int, val title: String) {
    AMPLITUDE(0, "Amplitude LFO"),
    FREQUENCY(1, "Frequency LFO"),
    FILTER(2, "Filter LFO"),
}

data class PreviewSeries(val values: List<Float>) {
    companion object {
        val Empty = PreviewSeries(emptyList())
    }
}

data class EnvelopePoint(
    val index: Int,
    val time: SynthEngine.ParameterValue,
    val value: SynthEngine.ParameterValue,
)

internal fun envelopePositions(
    points: List<EnvelopePoint>,
    timeOf: (EnvelopePoint) -> Double = { it.time.value },
): List<Float> {
    if (points.isEmpty()) return emptyList()
    // Match the minimum segment duration used by the native envelope preview.
    val durations = points.drop(1).map {
        ((2.0.pow(timeOf(it) / 127.0 * 12.0) - 1.0) / 100.0).toFloat().coerceAtLeast(.0001f)
    }
    val total = durations.sum().coerceAtLeast(.0001f)
    var elapsed = 0f
    return listOf(0f) + durations.map { duration ->
        elapsed += duration
        elapsed / total
    }
}

data class EnvelopeModel(
    val address: ModuleAddress.Envelope,
    val prefix: String,
    val title: String,
    val parameters: List<SynthEngine.ParameterValue>,
    val freeMode: Boolean,
    val linear: Boolean,
    val sustainPoint: Int?,
    val points: List<EnvelopePoint>,
) {
    fun parameter(field: String): SynthEngine.ParameterValue? =
        parameters.firstOrNull { it.descriptor.path == "$prefix/$field" }

    companion object {
        fun from(
            values: List<SynthEngine.ParameterValue>,
            address: ModuleAddress.Envelope,
        ): EnvelopeModel? {
            if (address.index >= 0 && address.engine != ModuleEngine.ADD) return null
            val field = when (address.envelopeRole) {
                EnvelopeRole.AMPLITUDE -> "ampEnvelope"
                EnvelopeRole.FREQUENCY -> "freqEnvelope"
                EnvelopeRole.FILTER -> "filterEnvelope"
                EnvelopeRole.BANDWIDTH -> if (address.engine == ModuleEngine.SUB && address.index < 0) "bandwidthEnvelope" else return null
                EnvelopeRole.MODULATOR_AMPLITUDE -> if (address.index >= 0) "modAmpEnvelope" else return null
                EnvelopeRole.MODULATOR_FREQUENCY -> if (address.index >= 0) "modFreqEnvelope" else return null
            }
            val owner = if (address.index < 0) address.engine.path else "add/voice/${address.index}"
            val prefix = "$owner/$field"
            val enablePath = "${prefix}Enabled"
            val parameters = values.filter {
                it.descriptor.path.startsWith("$prefix/") || it.descriptor.path == enablePath
            }
            if (parameters.isEmpty()) return null
            fun raw(field: String) = parameters.firstOrNull { it.descriptor.path == "$prefix/$field" }?.value
            val count = raw("pointCount")?.toInt()?.coerceIn(0, 40) ?: 0
            val points = (0 until count).mapNotNull { point ->
                val time = parameters.firstOrNull { it.descriptor.path == "$prefix/point/$point/time" }
                val value = parameters.firstOrNull { it.descriptor.path == "$prefix/point/$point/value" }
                if (time == null || value == null) null else EnvelopePoint(point, time, value)
            }
            return EnvelopeModel(
                address = address,
                prefix = prefix,
                title = address.envelopeRole.title,
                parameters = parameters,
                freeMode = raw("freeMode")?.let { it >= .5 } == true,
                linear = raw("linear")?.let { it >= .5 } == true,
                sustainPoint = raw("sustainPoint")?.toInt()?.takeIf { it in points.indices },
                points = points,
            )
        }
    }
}

data class LfoModel(
    val address: ModuleAddress.Lfo,
    val prefix: String,
    val title: String,
    val parameters: List<SynthEngine.ParameterValue>,
) {
    fun parameter(field: String): SynthEngine.ParameterValue? =
        parameters.firstOrNull { it.descriptor.path == "$prefix/$field" }

    companion object {
        fun from(values: List<SynthEngine.ParameterValue>, address: ModuleAddress.Lfo): LfoModel? {
            if (address.engine == ModuleEngine.SUB || (address.index >= 0 && address.engine != ModuleEngine.ADD)) return null
            val owner = if (address.index < 0) address.engine.path else "add/voice/${address.index}"
            val prefix = "$owner/" + when (address.lfoRole) {
                LfoRole.AMPLITUDE -> "ampLfo"
                LfoRole.FREQUENCY -> "freqLfo"
                LfoRole.FILTER -> "filterLfo"
            }
            val enablePath = "${prefix}Enabled"
            val parameters = values.filter {
                it.descriptor.path.startsWith("$prefix/") || it.descriptor.path == enablePath
            }
            return parameters.takeIf { it.isNotEmpty() }?.let {
                LfoModel(address, prefix, address.lfoRole.title, it)
            }
        }
    }
}

data class FormantModel(
    val count: Int,
    val sequenceSize: Int,
    val parameters: List<SynthEngine.ParameterValue>,
)

data class FilterModel(
    val address: ModuleAddress.Filter,
    val prefix: String,
    val parameters: List<SynthEngine.ParameterValue>,
    val category: Int,
    val formant: FormantModel?,
) {
    fun parameter(field: String): SynthEngine.ParameterValue? = parameters.firstOrNull {
        it.descriptor.path == if (address.index < 0) "$prefix/$field" else "$prefix${field.replaceFirstChar(Char::uppercase)}"
    }

    companion object {
        fun from(values: List<SynthEngine.ParameterValue>, address: ModuleAddress.Filter): FilterModel? {
            if (address.index >= 0 && address.engine != ModuleEngine.ADD) return null
            val prefix = if (address.index < 0) "${address.engine.path}/filter" else "add/voice/${address.index}/filter"
            val voiceFilterFields = setOf(
                "filter", "bypassGlobalFilter", "filterCategory", "filterType", "filterCutoff",
                "filterQ", "filterStages", "filterTracking", "filterGain", "filterCombHpf",
                "filterCombLpf", "filterVelocityAmount", "filterVelocity",
            )
            val parameters = values.filter { value ->
                val path = value.descriptor.path
                if (address.index < 0) {
                    path.startsWith("$prefix/") || path in setOf("${address.engine.path}/filterVelocity", "${address.engine.path}/filterVelocitySense", "${address.engine.path}/filterEnabled")
                } else {
                    path.startsWith("$prefix/formant/") ||
                        path.substringAfter("add/voice/${address.index}/") in voiceFilterFields
                }
            }
            if (parameters.isEmpty()) return null
            val categoryPath = if (address.index < 0) "$prefix/category" else "${prefix}Category"
            val category = parameters.firstOrNull { it.descriptor.path == categoryPath }?.value?.toInt() ?: 0
            val formantPrefix = "$prefix/formant/"
            val formantParameters = parameters.filter { it.descriptor.path.startsWith(formantPrefix) }
            return FilterModel(
                address,
                prefix,
                parameters,
                category,
                formantParameters.takeIf { it.isNotEmpty() }?.let {
                    FormantModel(
                        count = it.firstOrNull { value -> value.descriptor.path == "${prefix}/formant/count" }
                            ?.value?.toInt()?.coerceIn(1, 12) ?: 3,
                        sequenceSize = it.firstOrNull { value -> value.descriptor.path == "${prefix}/formant/sequenceSize" }
                            ?.value?.toInt()?.coerceIn(1, 8) ?: 3,
                        parameters = it,
                    )
                },
            )
        }
    }
}

data class PadGenerationStatus(
    val phase: String = "unavailable",
    val completed: Int = 0,
    val total: Int = 0,
    val frames: Int = 0,
    val bytes: Long = 0,
    val peak: Float = 0f,
    val elapsedMs: Long = 0,
    val error: String = "",
) {
    val busy get() = phase in setOf("generating", "ready", "cancelling")

    companion object {
        fun parse(raw: String): PadGenerationStatus {
            val fields = raw.split('|', limit = 8)
            return PadGenerationStatus(fields.getOrElse(0) { "unavailable" },
                fields.getOrNull(1)?.toIntOrNull() ?: 0, fields.getOrNull(2)?.toIntOrNull() ?: 0,
                fields.getOrNull(3)?.toIntOrNull() ?: 0, fields.getOrNull(4)?.toLongOrNull() ?: 0,
                fields.getOrNull(5)?.toFloatOrNull() ?: 0f, fields.getOrNull(6)?.toLongOrNull() ?: 0,
                fields.getOrElse(7) { "" })
        }
    }
}

package com.mick.zynaddsubfx

/** Parameters behind the original voice-list shortcuts; paths are exact to avoid nested-field collisions. */
internal data class AddVoiceSummary(
    val voice: Int,
    val enabled: SynthEngine.ParameterValue,
    val volume: SynthEngine.ParameterValue?,
    val panning: SynthEngine.ParameterValue?,
    val resonance: SynthEngine.ParameterValue?,
    val detune: SynthEngine.ParameterValue?,
    val lfoDepth: SynthEngine.ParameterValue?,
    val lfoEnabled: Boolean,
    val soundMode: Int,
    val oscillatorSource: Int,
    val oscillatorPhase: Int,
    val detuneType: Int,
) {
    companion object {
        fun from(values: Map<String, SynthEngine.ParameterValue>, voice: Int): AddVoiceSummary? {
            val prefix = "add/voice/$voice/"
            val enabled = values[prefix + "enabled"] ?: return null
            val external = values[prefix + "externalOscillator"]?.value?.toInt() ?: 0
            val detuneType = values[prefix + "detuneType"]?.value?.toInt() ?: 0
            return AddVoiceSummary(
                voice, enabled, values[prefix + "volume"], values[prefix + "panning"],
                values[prefix + "resonance"], values[prefix + "detune"], values[prefix + "freqLfo/depth"],
                (values[prefix + "freqLfoEnabled"]?.value ?: 0.0) >= .5,
                values[prefix + "oscillatorType"]?.value?.toInt() ?: 0,
                if (external in 1..voice) external - 1 else voice,
                values[prefix + "oscillatorPhase"]?.value?.toInt() ?: 0,
                if (detuneType != 0) detuneType else values["add/detuneType"]?.value?.toInt() ?: 1,
            )
        }
    }
}

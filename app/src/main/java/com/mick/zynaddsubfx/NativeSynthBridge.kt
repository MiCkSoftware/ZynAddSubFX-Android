package com.mick.zynaddsubfx

object NativeSynthBridge {
    init {
        System.loadLibrary("zynbridge")
    }

    external fun nativeGetVersion(): String
    external fun nativeGetZynProbeSummary(): String
    external fun nativeInit(sampleRate: Int, framesPerBurst: Int): Boolean
    external fun nativeStartAudio(): Boolean
    external fun nativeStopAudio()
    external fun nativeSetTestToneEnabled(enabled: Boolean)
    external fun nativeSetTestToneFrequencyHz(frequencyHz: Float)
    external fun nativeIsAudioRunning(): Boolean
    external fun nativeNoteOn(channel: Int, note: Int, velocity: Int)
    external fun nativeNoteOff(channel: Int, note: Int)
    external fun nativePanic()
    external fun nativeLoadMasterXml(path: String): Boolean
    external fun nativeLoadPresetFile(path: String): Boolean
    external fun nativeSetMasterVolumeNormalized(normalized: Float)
    external fun nativeGetMasterVolumeNormalized(): Float
    external fun nativeGetRuntimeDiagnostics(): String
    external fun nativeGetRecentOutputPeak(): Float
    external fun nativeClearRecentOutputPeak()
    external fun nativeGetCurrentPresetInspectorSummary(): String
    external fun nativeGetCurrentActiveFxSummary(): String
    external fun nativeGetCurrentPartsSummary(): String
    external fun nativeGetCurrentMixerSummary(): String
    external fun nativeSetPart0Enabled(enabled: Boolean): Boolean
    external fun nativeSetPartEnabled(partIndex: Int, enabled: Boolean): Boolean
    external fun nativeSetPartReceiveChannel(partIndex: Int, channel: Int): Boolean
    external fun nativeSetPartVolume127(partIndex: Int, volume127: Int): Boolean
    external fun nativeSetPartPanning(partIndex: Int, panning127: Int): Boolean
    external fun nativeSetPartVelocitySense127(partIndex: Int, sense127: Int): Boolean
    external fun nativeSetPartVelocityOffset127(partIndex: Int, offset127: Int): Boolean
    external fun nativeSetPartPortamentoTime127(partIndex: Int, time127: Int): Boolean
    external fun nativeSetPartPortamentoStretch127(partIndex: Int, stretch127: Int): Boolean
    external fun nativeSetPartAddEnabled(partIndex: Int, enabled: Boolean): Boolean
    external fun nativeSetPartSubEnabled(partIndex: Int, enabled: Boolean): Boolean
    external fun nativeSetPartPadEnabled(partIndex: Int, enabled: Boolean): Boolean
    external fun nativeSetPartStereoEnabled(partIndex: Int, enabled: Boolean): Boolean
    external fun nativeSetPartRndGroupingEnabled(partIndex: Int, enabled: Boolean): Boolean
    external fun nativeSoloPart(partIndex: Int): Boolean

    // Debug helpers for M1 smoke validation in UI
    external fun nativeGetLastSampleRate(): Int
    external fun nativeGetLastFramesPerBurst(): Int
    external fun nativeGetRenderBackendName(): String
}

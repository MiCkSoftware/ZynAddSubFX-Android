#include <aaudio/AAudio.h>
#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <mutex>
#include <string>
#include "ZynAndroidEngine.h"
#include "ZynUpstreamProbe.h"

namespace {
constexpr const char *kLogTag = "zynbridge";
constexpr int32_t kStartWaitTimeoutNanos = 200 * 1000 * 1000; // 200ms
int gSampleRate = 0;
int gFramesPerBurst = 0;
bool gInitialized = false;

std::mutex gAudioMutex;
AAudioStream *gStream = nullptr;
bool gAudioRunning = false;
std::atomic<bool> gAudioNeedsRecreate{false};
std::atomic<uint32_t> gAudioStartCount{0};
std::atomic<uint32_t> gAudioStartFailCount{0};
std::atomic<uint32_t> gAudioErrorCallbackCount{0};
std::atomic<uint32_t> gAudioRecoveredRestartCount{0};
std::atomic<int32_t> gAudioLastOpenedSampleRate{0};
std::atomic<int32_t> gAudioLastOpenedBurst{0};
std::atomic<int32_t> gAudioLastOpenedBuffer{0};
std::atomic<int32_t> gAudioLastXRunCount{-1};
ZynAndroidEngine gEngine;

void logInfo(const char *msg) {
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "%s", msg);
}

aaudio_data_callback_result_t audioDataCallback(
        AAudioStream *stream,
        void * /* userData */,
        void *audioData,
        int32_t numFrames) {
    auto *out = static_cast<float *>(audioData);
    const int32_t channels = AAudioStream_getChannelCount(stream);
    const int32_t sampleRate = AAudioStream_getSampleRate(stream);
    gEngine.renderInterleavedFloat(out, numFrames, channels, sampleRate);
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

void errorCallback(
        AAudioStream * /* stream */,
        void * /* userData */,
        aaudio_result_t error) {
    gAudioErrorCallbackCount.fetch_add(1, std::memory_order_relaxed);
    __android_log_print(ANDROID_LOG_ERROR, kLogTag, "AAudio error callback: %d", error);
    gAudioRunning = false;
    gAudioNeedsRecreate.store(true, std::memory_order_relaxed);
}

void closeStreamLocked() {
    if (gStream == nullptr) {
        gAudioRunning = false;
        return;
    }
    AAudioStream_close(gStream);
    gStream = nullptr;
    gAudioRunning = false;
}

void refreshXRunCounter() {
    if (gStream == nullptr) return;
    const int32_t xruns = AAudioStream_getXRunCount(gStream);
    if (xruns >= 0) {
        gAudioLastXRunCount.store(xruns, std::memory_order_relaxed);
    }
}

bool startAudioLocked() {
    if (gStream != nullptr) {
        if (gAudioNeedsRecreate.load(std::memory_order_relaxed) || !gAudioRunning) {
            closeStreamLocked();
            gAudioNeedsRecreate.store(false, std::memory_order_relaxed);
            gAudioRecoveredRestartCount.fetch_add(1, std::memory_order_relaxed);
        } else {
            refreshXRunCounter();
            return true;
        }
    }

    gAudioStartCount.fetch_add(1, std::memory_order_relaxed);
    AAudioStreamBuilder *builder = nullptr;
    aaudio_result_t result = AAudio_createStreamBuilder(&builder);
    if (result != AAUDIO_OK || builder == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "AAudio_createStreamBuilder failed: %d", result);
        gAudioStartFailCount.fetch_add(1, std::memory_order_relaxed);
        return false;
    }

    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setChannelCount(builder, 2);
    // Debug bring-up mode: prefer stability over ultra-low latency while the Zyn port
    // still has heavy code paths (preset load, first-note warmup, shim FFT, etc.).
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_NONE);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
    if (gSampleRate > 0) {
        AAudioStreamBuilder_setSampleRate(builder, gSampleRate);
    }
    if (gFramesPerBurst > 0) {
        AAudioStreamBuilder_setFramesPerDataCallback(builder, gFramesPerBurst);
    }
    AAudioStreamBuilder_setDataCallback(builder, audioDataCallback, nullptr);
    AAudioStreamBuilder_setErrorCallback(builder, errorCallback, nullptr);

    result = AAudioStreamBuilder_openStream(builder, &gStream);
    AAudioStreamBuilder_delete(builder);
    if (result != AAUDIO_OK || gStream == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "openStream failed: %d", result);
        gStream = nullptr;
        gAudioStartFailCount.fetch_add(1, std::memory_order_relaxed);
        return false;
    }

    const int32_t actualSampleRate = AAudioStream_getSampleRate(gStream);
    const int32_t actualBurst = AAudioStream_getFramesPerBurst(gStream);
    gAudioLastOpenedSampleRate.store(actualSampleRate, std::memory_order_relaxed);
    gAudioLastOpenedBurst.store(actualBurst, std::memory_order_relaxed);
    const int32_t targetBufferFrames = std::max(actualBurst * 4, gFramesPerBurst * 2);
    if (targetBufferFrames > 0) {
        const int32_t applied = AAudioStream_setBufferSizeInFrames(gStream, targetBufferFrames);
        gAudioLastOpenedBuffer.store(applied, std::memory_order_relaxed);
        __android_log_print(ANDROID_LOG_INFO, kLogTag,
                            "AAudio opened: sr=%d burst=%d targetBuf=%d appliedBuf=%d",
                            actualSampleRate, actualBurst, targetBufferFrames, applied);
    } else {
        gAudioLastOpenedBuffer.store(AAudioStream_getBufferSizeInFrames(gStream), std::memory_order_relaxed);
    }

    result = AAudioStream_requestStart(gStream);
    if (result != AAUDIO_OK) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "requestStart failed: %d", result);
        gAudioStartFailCount.fetch_add(1, std::memory_order_relaxed);
        closeStreamLocked();
        return false;
    }

    aaudio_stream_state_t nextState = AAUDIO_STREAM_STATE_UNINITIALIZED;
    const aaudio_stream_state_t inputState = AAudioStream_getState(gStream);
    (void) AAudioStream_waitForStateChange(gStream, inputState, &nextState, kStartWaitTimeoutNanos);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "AAudio start state: %d -> %d",
                        static_cast<int>(inputState), static_cast<int>(nextState));

    gAudioRunning = true;
    gAudioNeedsRecreate.store(false, std::memory_order_relaxed);
    refreshXRunCounter();
    logInfo("AAudio stream started");
    return true;
}

void stopAudioLocked() {
    if (gStream == nullptr) {
        gAudioRunning = false;
        return;
    }

    AAudioStream_requestStop(gStream);
    refreshXRunCounter();
    closeStreamLocked();
    logInfo("AAudio stream stopped");
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeGetVersion(
        JNIEnv *env,
        jobject /* thiz */) {
    std::string version = "zynbridge-m1-stub/0.1";
    return env->NewStringUTF(version.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeGetZynProbeSummary(
        JNIEnv *env,
        jobject /* thiz */) {
    const std::string summary = zyn_android::zynUpstreamProbeSummary();
    return env->NewStringUTF(summary.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeInit(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jint sampleRate,
        jint framesPerBurst) {
    if (sampleRate <= 0 || framesPerBurst <= 0) {
        gInitialized = false;
        return JNI_FALSE;
    }

    gSampleRate = sampleRate;
    gFramesPerBurst = framesPerBurst;
    gInitialized = gEngine.initialize(sampleRate, framesPerBurst);
    return gInitialized ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeGetLastSampleRate(
        JNIEnv * /* env */,
        jobject /* thiz */) {
    return gInitialized ? gSampleRate : 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeGetLastFramesPerBurst(
        JNIEnv * /* env */,
        jobject /* thiz */) {
    return gInitialized ? gFramesPerBurst : 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeGetRenderBackendName(
        JNIEnv *env,
        jobject /* thiz */) {
    const std::string name = gEngine.renderBackendName();
    return env->NewStringUTF(name.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeLoadMasterXml(
        JNIEnv *env,
        jobject /* thiz */,
        jstring path) {
    if (path == nullptr) {
        return JNI_FALSE;
    }
    const char *rawPath = env->GetStringUTFChars(path, nullptr);
    if (rawPath == nullptr) {
        return JNI_FALSE;
    }
    const std::string pathString(rawPath);
    env->ReleaseStringUTFChars(path, rawPath);

    std::lock_guard<std::mutex> lock(gAudioMutex);
    const bool wasRunning = gAudioRunning;
    if (wasRunning) {
        stopAudioLocked();
    }
    const bool ok = gEngine.loadMasterXml(pathString);
    if (wasRunning) {
        (void) startAudioLocked();
    }
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeLoadPresetFile(
        JNIEnv *env,
        jobject /* thiz */,
        jstring path) {
    if (path == nullptr) {
        return JNI_FALSE;
    }
    const char *rawPath = env->GetStringUTFChars(path, nullptr);
    if (rawPath == nullptr) {
        return JNI_FALSE;
    }
    const std::string pathString(rawPath);
    env->ReleaseStringUTFChars(path, rawPath);

    std::lock_guard<std::mutex> lock(gAudioMutex);
    const bool wasRunning = gAudioRunning;
    if (wasRunning) {
        stopAudioLocked();
    }
    const bool ok = gEngine.loadPresetFile(pathString);
    if (wasRunning) {
        (void) startAudioLocked();
    }
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeSetMasterVolumeNormalized(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jfloat normalized) {
    gEngine.setMasterVolumeNormalized(normalized);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeGetMasterVolumeNormalized(
        JNIEnv * /* env */,
        jobject /* thiz */) {
    return gEngine.masterVolumeNormalized();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeGetRuntimeDiagnostics(
        JNIEnv *env,
        jobject /* thiz */) {
    std::string diag = gEngine.diagnosticsSummary();
    diag += " aaudioRunning=";
    diag += (gAudioRunning ? "1" : "0");
    diag += " aaudioStarts=" + std::to_string(gAudioStartCount.load(std::memory_order_relaxed));
    diag += " aaudioStartFails=" + std::to_string(gAudioStartFailCount.load(std::memory_order_relaxed));
    diag += " aaudioErrCb=" + std::to_string(gAudioErrorCallbackCount.load(std::memory_order_relaxed));
    diag += " aaudioRecovered=" + std::to_string(gAudioRecoveredRestartCount.load(std::memory_order_relaxed));
    diag += " aaudioOpenSr=" + std::to_string(gAudioLastOpenedSampleRate.load(std::memory_order_relaxed));
    diag += " aaudioOpenBurst=" + std::to_string(gAudioLastOpenedBurst.load(std::memory_order_relaxed));
    diag += " aaudioBuf=" + std::to_string(gAudioLastOpenedBuffer.load(std::memory_order_relaxed));
    diag += " aaudioXruns=" + std::to_string(gAudioLastXRunCount.load(std::memory_order_relaxed));
    return env->NewStringUTF(diag.c_str());
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeGetRecentOutputPeak(
        JNIEnv * /* env */,
        jobject /* thiz */) {
    return gEngine.recentOutputPeak();
}

extern "C" JNIEXPORT void JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeClearRecentOutputPeak(
        JNIEnv * /* env */,
        jobject /* thiz */) {
    gEngine.clearRecentOutputPeak();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeGetCurrentPresetInspectorSummary(
        JNIEnv *env,
        jobject /* thiz */) {
    const std::string s = gEngine.inspectorSummary();
    return env->NewStringUTF(s.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeGetCurrentActiveFxSummary(
        JNIEnv *env,
        jobject /* thiz */) {
    const std::string s = gEngine.activeFxSummary();
    return env->NewStringUTF(s.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeGetCurrentPartsSummary(
        JNIEnv *env,
        jobject /* thiz */) {
    const std::string s = gEngine.partsSummary();
    return env->NewStringUTF(s.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeGetCurrentMixerSummary(
        JNIEnv *env,
        jobject /* thiz */) {
    const std::string s = gEngine.mixerSummary();
    return env->NewStringUTF(s.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeSetPart0Enabled(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jboolean enabled) {
    const bool ok = gEngine.setPart0Enabled(enabled == JNI_TRUE);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeSetPartEnabled(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jint partIndex,
        jboolean enabled) {
    const bool ok = gEngine.setPartEnabled(static_cast<int>(partIndex), enabled == JNI_TRUE);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeSetPartReceiveChannel(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jint partIndex,
        jint channel) {
    const bool ok = gEngine.setPartReceiveChannel(static_cast<int>(partIndex), static_cast<int>(channel));
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeSetPartVolume127(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jint partIndex,
        jint volume127) {
    const bool ok = gEngine.setPartVolume127(static_cast<int>(partIndex), static_cast<int>(volume127));
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeSetPartPanning(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jint partIndex,
        jint panning127) {
    const bool ok = gEngine.setPartPanning(static_cast<int>(partIndex), static_cast<int>(panning127));
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeSetPartVelocitySense127(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jint partIndex,
        jint sense127) {
    const bool ok = gEngine.setPartVelocitySense127(static_cast<int>(partIndex), static_cast<int>(sense127));
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeSetPartVelocityOffset127(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jint partIndex,
        jint offset127) {
    const bool ok = gEngine.setPartVelocityOffset127(static_cast<int>(partIndex), static_cast<int>(offset127));
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeSetPartPortamentoTime127(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jint partIndex,
        jint time127) {
    const bool ok = gEngine.setPartPortamentoTime127(static_cast<int>(partIndex), static_cast<int>(time127));
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeSetPartPortamentoStretch127(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jint partIndex,
        jint stretch127) {
    const bool ok = gEngine.setPartPortamentoStretch127(static_cast<int>(partIndex), static_cast<int>(stretch127));
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeSetPartAddEnabled(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jint partIndex,
        jboolean enabled) {
    const bool ok = gEngine.setPartAddEnabled(static_cast<int>(partIndex), enabled == JNI_TRUE);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeSetPartSubEnabled(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jint partIndex,
        jboolean enabled) {
    const bool ok = gEngine.setPartSubEnabled(static_cast<int>(partIndex), enabled == JNI_TRUE);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeSetPartPadEnabled(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jint partIndex,
        jboolean enabled) {
    const bool ok = gEngine.setPartPadEnabled(static_cast<int>(partIndex), enabled == JNI_TRUE);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeSetPartStereoEnabled(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jint partIndex,
        jboolean enabled) {
    const bool ok = gEngine.setPartStereoEnabled(static_cast<int>(partIndex), enabled == JNI_TRUE);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeSetPartRndGroupingEnabled(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jint partIndex,
        jboolean enabled) {
    const bool ok = gEngine.setPartRndGroupingEnabled(static_cast<int>(partIndex), enabled == JNI_TRUE);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeSoloPart(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jint partIndex) {
    const bool ok = gEngine.soloPart(static_cast<int>(partIndex));
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeStartAudio(
        JNIEnv * /* env */,
        jobject /* thiz */) {
    std::lock_guard<std::mutex> lock(gAudioMutex);
    if (!gInitialized) {
        return JNI_FALSE;
    }
    return startAudioLocked() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeStopAudio(
        JNIEnv * /* env */,
        jobject /* thiz */) {
    std::lock_guard<std::mutex> lock(gAudioMutex);
    stopAudioLocked();
}

extern "C" JNIEXPORT void JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeSetTestToneEnabled(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jboolean enabled) {
    gEngine.setTestToneEnabled(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeSetTestToneFrequencyHz(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jfloat frequencyHz) {
    gEngine.setTestToneFrequencyHz(frequencyHz);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeIsAudioRunning(
        JNIEnv * /* env */,
        jobject /* thiz */) {
    return gAudioRunning ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeNoteOn(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jint channel,
        jint note,
        jint velocity) {
    gEngine.noteOn(channel, note, velocity);
}

extern "C" JNIEXPORT void JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativeNoteOff(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jint channel,
        jint note) {
    gEngine.noteOff(channel, note);
}

extern "C" JNIEXPORT void JNICALL
Java_com_mick_zynaddsubfx_NativeSynthBridge_nativePanic(
        JNIEnv * /* env */,
        jobject /* thiz */) {
    gEngine.panic();
}

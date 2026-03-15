#pragma once

#include <cstdint>
#include <atomic>
#include <array>
#include <memory>
#include <string>
#include <vector>

namespace zyn {
struct SYNTH_T;
class Config;
class Master;
}

class ZynAndroidEngine {
public:
    ZynAndroidEngine();
    ~ZynAndroidEngine();

    bool initialize(int sampleRate, int framesPerBurst);
    void shutdown();

    bool isInitialized() const;
    int sampleRate() const;
    int framesPerBurst() const;

    void setTestToneEnabled(bool enabled);
    bool isTestToneEnabled() const;
    bool isZynReady() const;
    std::string renderBackendName() const;
    bool loadMasterXml(const std::string &path);
    bool loadPresetFile(const std::string &path);
    void setMasterVolumeNormalized(float normalized);
    float masterVolumeNormalized() const;
    float recentOutputPeak() const;
    void clearRecentOutputPeak();
    std::string diagnosticsSummary() const;
    std::string inspectorSummary() const;
    std::string activeFxSummary() const;
    std::string partsSummary() const;
    std::string mixerSummary() const;
    bool setPart0Enabled(bool enabled);
    bool setPartEnabled(int partIndex, bool enabled);
    bool setPartReceiveChannel(int partIndex, int channel);
    bool setPartVolume127(int partIndex, int volume127);
    bool setPartPanning(int partIndex, int panning127);
    bool setPartVelocitySense127(int partIndex, int sense127);
    bool setPartVelocityOffset127(int partIndex, int offset127);
    bool setPartPortamentoTime127(int partIndex, int time127);
    bool setPartPortamentoStretch127(int partIndex, int stretch127);
    bool setPartAddEnabled(int partIndex, bool enabled);
    bool setPartSubEnabled(int partIndex, bool enabled);
    bool setPartPadEnabled(int partIndex, bool enabled);
    bool setPartStereoEnabled(int partIndex, bool enabled);
    bool setPartRndGroupingEnabled(int partIndex, bool enabled);
    bool soloPart(int partIndex);

    void setTestToneFrequencyHz(float frequencyHz);
    float testToneFrequencyHz() const;

    // Input seam for future Zyn integration (currently mapped to test tone behavior).
    void panic();
    void noteOn(int channel, int note, int velocity);
    void noteOff(int channel, int note);

    // Render interleaved float PCM (stereo/mono depending on channelCount).
    // This is the seam that will later call into ZynAddSubFX core render code.
    void renderInterleavedFloat(float *out, int32_t numFrames, int32_t channelCount, int32_t streamSampleRate);

private:
    enum class CommandType : uint8_t {
        NoteOn,
        NoteOff,
        Panic,
    };

    struct Command {
        CommandType type;
        int channel;
        int note;
        int velocity;
    };

    static constexpr uint32_t kCommandQueueSize = 128;

    bool pushCommand(const Command &cmd);
    bool popCommand(Command &cmd);
    void applyQueuedCommands();
    void renderFallbackTone(float *out, int32_t numFrames, int32_t channelCount, int32_t streamSampleRate);
    void ensureTempBuffers(int32_t numFrames);

    std::atomic<bool> initialized_{false};
    std::atomic<int> sampleRate_{0};
    std::atomic<int> framesPerBurst_{0};
    std::atomic<bool> testToneEnabled_{false};
    std::atomic<float> testToneFrequencyHz_{440.0f};
    std::atomic<bool> noteHeld_{false};
    std::atomic<int> activeNote_{69};
    std::atomic<int> activeVelocity_{100};

    std::array<Command, kCommandQueueSize> commandQueue_{};
    std::atomic<uint32_t> commandWriteIndex_{0};
    std::atomic<uint32_t> commandReadIndex_{0};
    std::atomic<bool> zynReady_{false};
    std::atomic<float> masterVolumeNorm_{0.6f};
    std::atomic<float> recentOutputPeak_{0.0f};
    std::atomic<uint32_t> queueDrops_{0};
    std::atomic<uint32_t> fallbackRenderCalls_{0};
    std::atomic<uint32_t> presetLoadCount_{0};
    std::atomic<uint32_t> presetLoadFailCount_{0};
    std::atomic<uint32_t> presetLoadTimeoutCount_{0};

    // Callback thread-owned state
    double phase_ = 0.0;

    std::unique_ptr<zyn::SYNTH_T> synth_;
    std::unique_ptr<zyn::Config> config_;
    std::unique_ptr<zyn::Master> master_;
    std::vector<float> zynLeft_;
    std::vector<float> zynRight_;
    std::string lastLoadedPresetPath_;
};

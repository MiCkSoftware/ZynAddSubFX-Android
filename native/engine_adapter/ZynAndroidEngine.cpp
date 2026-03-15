#include "ZynAndroidEngine.h"

#include <cmath>
#include <algorithm>
#include <sstream>
#include <cctype>
#include <chrono>
#include <string_view>
#ifdef __ANDROID__
#include <android/log.h>
#endif

#include "globals.h"
#include "Misc/Config.h"
#include "Misc/Master.h"
#include "Misc/Part.h"
#include "Effects/EffectMgr.h"
#include "Params/ADnoteParameters.h"
#include "Params/SUBnoteParameters.h"
#include "Params/PADnoteParameters.h"

namespace {
constexpr double kTwoPi = 6.28318530717958647692;
constexpr auto kInstrumentApplyTimeout = std::chrono::milliseconds(1200);
#ifndef ZYN_FFT_BACKEND_NAME
#define ZYN_FFT_BACKEND_NAME "unknown"
#endif
#ifdef __ANDROID__
constexpr const char *kEngineLogTag = "zynbridge";
#endif

const char *effectTypeNameNative(int typeId) {
    switch (typeId) {
        case 1: return "Reverb";
        case 2: return "Echo";
        case 3: return "Chorus";
        case 4: return "Phaser";
        case 5: return "Alienwah";
        case 6: return "Distortion";
        case 7: return "EQ";
        case 8: return "DynFilter";
        case 9: return "Sympathetic";
        case 10: return "Reverse";
        default: return "Unknown";
    }
}

bool applyAndInitPartWithTimeout(
        zyn::Part *part,
        const std::chrono::steady_clock::time_point deadline) {
    if (!part) return false;
    bool aborted = false;
    part->applyparameters([&]() {
        if (std::chrono::steady_clock::now() >= deadline) {
            aborted = true;
            return true;
        }
        return false;
    });
    if (aborted) {
        return false;
    }
    part->initialize_rt();
    return true;
}
}

ZynAndroidEngine::ZynAndroidEngine() = default;
ZynAndroidEngine::~ZynAndroidEngine() = default;

bool ZynAndroidEngine::initialize(int sampleRate, int framesPerBurst) {
    if (sampleRate <= 0 || framesPerBurst <= 0) {
        initialized_.store(false);
        zynReady_.store(false);
        sampleRate_.store(0);
        framesPerBurst_.store(0);
        return false;
    }

    sampleRate_.store(sampleRate);
    framesPerBurst_.store(framesPerBurst);
    phase_ = 0.0;
    commandReadIndex_.store(0);
    commandWriteIndex_.store(0);
    noteHeld_.store(false);
    zynReady_.store(false);

    master_.reset();
    config_.reset();
    synth_.reset();
    zynLeft_.clear();
    zynRight_.clear();

    try {
        auto synth = std::make_unique<zyn::SYNTH_T>();
        synth->samplerate = static_cast<unsigned int>(sampleRate);
        synth->buffersize = framesPerBurst > 0 ? framesPerBurst : 256;
        // Keep upstream default oscilsize unless we need to tune it later.
        synth->alias(false);

        auto config = std::make_unique<zyn::Config>();
        config->cfg.SampleRate = sampleRate;
        config->cfg.SoundBufferSize = synth->buffersize;
        config->cfg.OscilSize = synth->oscilsize;

        auto master = std::make_unique<zyn::Master>(*synth, config.get());
        master->initialize_rt();

        synth_ = std::move(synth);
        config_ = std::move(config);
        master_ = std::move(master);
        setMasterVolumeNormalized(masterVolumeNorm_.load());
        ensureTempBuffers(std::max(framesPerBurst, synth_->buffersize));
        zynReady_.store(true);
    } catch (...) {
        // Keep the app usable with the fallback tone while upstream init is being integrated.
        master_.reset();
        config_.reset();
        synth_.reset();
        zynLeft_.clear();
        zynRight_.clear();
        zynReady_.store(false);
    }

    initialized_.store(true);
    return true;
}

void ZynAndroidEngine::shutdown() {
    testToneEnabled_.store(false);
    noteHeld_.store(false);
    zynReady_.store(false);
    master_.reset();
    config_.reset();
    synth_.reset();
    zynLeft_.clear();
    zynRight_.clear();
    initialized_.store(false);
}

bool ZynAndroidEngine::isInitialized() const {
    return initialized_.load();
}

int ZynAndroidEngine::sampleRate() const {
    return sampleRate_.load();
}

int ZynAndroidEngine::framesPerBurst() const {
    return framesPerBurst_.load();
}

void ZynAndroidEngine::setTestToneEnabled(bool enabled) {
    testToneEnabled_.store(enabled);
}

bool ZynAndroidEngine::isTestToneEnabled() const {
    return testToneEnabled_.load();
}

bool ZynAndroidEngine::isZynReady() const {
    return zynReady_.load();
}

std::string ZynAndroidEngine::renderBackendName() const {
    return zynReady_.load() ? "zyn-master" : "fallback-sine";
}

bool ZynAndroidEngine::loadMasterXml(const std::string &path) {
    presetLoadCount_.fetch_add(1);
    if (!zynReady_.load() || !master_ || path.empty()) {
        presetLoadFailCount_.fetch_add(1);
        return false;
    }
    master_->ShutUp();
    noteHeld_.store(false);
    const int rc = master_->loadXML(path.c_str());
    if (rc == 0) {
        master_->ShutUp();
        lastLoadedPresetPath_ = path;
    }
    setMasterVolumeNormalized(masterVolumeNorm_.load());
    if (rc != 0) {
        presetLoadFailCount_.fetch_add(1);
    }
    return rc == 0;
}

bool ZynAndroidEngine::loadPresetFile(const std::string &path) {
    presetLoadCount_.fetch_add(1);
    if (!zynReady_.load() || !master_ || path.empty()) {
        presetLoadFailCount_.fetch_add(1);
        return false;
    }
    master_->ShutUp();
    noteHeld_.store(false);

    std::string lowerPath = path;
    std::transform(lowerPath.begin(), lowerPath.end(), lowerPath.begin(),
                   [](unsigned char c) { return static_cast<char>(std::tolower(c)); });

    int rc = -1;
    if (lowerPath.size() >= 4 && lowerPath.rfind(".xmz") == lowerPath.size() - 4) {
        rc = master_->loadXML(path.c_str());
        if (rc == 0) {
            const auto deadline = std::chrono::steady_clock::now() + kInstrumentApplyTimeout;
            int initializedParts = 0;
            for (int i = 0; i < NUM_MIDI_PARTS; ++i) {
                auto *part = master_->part[i];
                if (!part || !part->Penabled) continue;
                if (!applyAndInitPartWithTimeout(part, deadline)) {
                    rc = -2;
                    presetLoadTimeoutCount_.fetch_add(1);
#ifdef __ANDROID__
                    __android_log_print(ANDROID_LOG_ERROR, kEngineLogTag,
                                        "XMZ post-load init timeout on part=%d path=%s",
                                        i, path.c_str());
#endif
                    break;
                }
                initializedParts++;
            }
#ifdef __ANDROID__
            if (rc == 0) {
                __android_log_print(ANDROID_LOG_INFO, kEngineLogTag,
                                    "XMZ post-load init parts=%d path=%s",
                                    initializedParts, path.c_str());
            }
#endif
        }
    } else if (lowerPath.size() >= 4 && lowerPath.rfind(".xiz") == lowerPath.size() - 4) {
        auto *part0 = master_->part[0];
        if (part0 != nullptr) {
            rc = part0->loadXMLinstrument(path.c_str());
            if (rc == 0) {
                const auto deadline = std::chrono::steady_clock::now() + kInstrumentApplyTimeout;
                if (!applyAndInitPartWithTimeout(part0, deadline)) {
                    rc = -2;
                    presetLoadTimeoutCount_.fetch_add(1);
                }
            }
        }
    }

    if (rc == 0) {
        master_->ShutUp();
        lastLoadedPresetPath_ = path;
    }
    setMasterVolumeNormalized(masterVolumeNorm_.load());
    if (rc != 0) {
        presetLoadFailCount_.fetch_add(1);
        return false;
    }
    return true;
}

void ZynAndroidEngine::setMasterVolumeNormalized(float normalized) {
    float v = normalized;
    if (v < 0.0f) v = 0.0f;
    if (v > 1.0f) v = 1.0f;
    masterVolumeNorm_.store(v);
    if (master_) {
        master_->Volume = -40.0f + v * 53.3333f;
    }
}

float ZynAndroidEngine::masterVolumeNormalized() const {
    return masterVolumeNorm_.load();
}

float ZynAndroidEngine::recentOutputPeak() const {
    return recentOutputPeak_.load();
}

void ZynAndroidEngine::clearRecentOutputPeak() {
    recentOutputPeak_.store(0.0f);
}

std::string ZynAndroidEngine::diagnosticsSummary() const {
    std::ostringstream oss;
    oss << "backend=" << renderBackendName()
        << " zynReady=" << (zynReady_.load() ? "1" : "0")
        << " sr=" << sampleRate_.load()
        << " burst=" << framesPerBurst_.load()
        << " qDrops=" << queueDrops_.load()
        << " fbCalls=" << fallbackRenderCalls_.load()
        << " presetLoads=" << presetLoadCount_.load()
        << " presetFails=" << presetLoadFailCount_.load()
        << " presetTimeouts=" << presetLoadTimeoutCount_.load()
        << " vol=" << masterVolumeNorm_.load()
        << " fft=" << ZYN_FFT_BACKEND_NAME;
    return oss.str();
}

std::string ZynAndroidEngine::inspectorSummary() const {
    std::ostringstream oss;
    std::string format = "?";
    if (!lastLoadedPresetPath_.empty()) {
        const std::string &p = lastLoadedPresetPath_;
        if (p.size() >= 4) {
            std::string ext = p.substr(p.size() - 4);
            std::transform(ext.begin(), ext.end(), ext.begin(), [](unsigned char c) {
                return static_cast<char>(std::tolower(c));
            });
            if (ext == ".xmz") format = "XMZ";
            else if (ext == ".xiz") format = "XIZ";
        }
    }
    oss << "format=" << format;
    if (!zynReady_.load() || !master_ || !master_->part[0]) {
        oss << " part0.enabled=? part0.note_on=? part0.poly=? part0.volume=?"
            << " kit_mode=? kit.active=0 add=0 sub=0 pad=0 fx(sys=0,ins=0,part=0)";
        return oss.str();
    }

    auto *part0 = master_->part[0];
    int activeKitItems = 0;
    int addEnabledCount = 0;
    int subEnabledCount = 0;
    int padEnabledCount = 0;
    for (int i = 0; i < NUM_KIT_ITEMS; ++i) {
        const auto &k = part0->kit[i];
        if (k.Penabled) activeKitItems++;
        if (k.Padenabled) addEnabledCount++;
        if (k.Psubenabled) subEnabledCount++;
        if (k.Ppadenabled) padEnabledCount++;
    }

    int sysFx = 0;
    for (int i = 0; i < NUM_SYS_EFX; ++i) {
        if (master_->sysefx[i] && master_->sysefx[i]->geteffect() > 0) sysFx++;
    }
    int insFx = 0;
    for (int i = 0; i < NUM_INS_EFX; ++i) {
        if (master_->insefx[i] && master_->insefx[i]->geteffect() > 0) insFx++;
    }
    int partFx = 0;
    for (int i = 0; i < NUM_PART_EFX; ++i) {
        if (part0->partefx[i] && part0->partefx[i]->geteffect() > 0) partFx++;
    }

    int partVol127 = std::clamp(
            static_cast<int>(std::lround((part0->Volume / 40.0f) * 96.0f + 96.0f)),
            0,
            127);
    oss << " part0.enabled=" << (part0->Penabled ? "true" : "false")
        << " part0.note_on=" << (part0->Pnoteon ? "true" : "false")
        << " part0.poly=" << (part0->Ppolymode ? "true" : "false")
        << " part0.volume=" << partVol127
        << " kit_mode=" << part0->Pkitmode
        << " kit.active=" << activeKitItems
        << " add=" << addEnabledCount
        << " sub=" << subEnabledCount
        << " pad=" << padEnabledCount
        << " fx(sys=" << sysFx << ",ins=" << insFx << ",part=" << partFx << ")";
    return oss.str();
}

std::string ZynAndroidEngine::activeFxSummary() const {
    if (!zynReady_.load() || !master_) return {};
    std::ostringstream oss;
    bool first = true;
    auto appendFx = [&](const char *scope, int slotId, int typeId) {
        if (typeId <= 0) return;
        if (!first) oss << '\n';
        first = false;
        oss << scope << '|' << slotId << '|' << typeId << '|' << effectTypeNameNative(typeId);
    };
    for (int i = 0; i < NUM_SYS_EFX; ++i) {
        if (master_->sysefx[i]) appendFx("System", i, master_->sysefx[i]->geteffect());
    }
    for (int i = 0; i < NUM_INS_EFX; ++i) {
        if (master_->insefx[i]) appendFx("Insert", i, master_->insefx[i]->geteffect());
    }
    if (master_->part[0]) {
        auto *part0 = master_->part[0];
        for (int i = 0; i < NUM_PART_EFX; ++i) {
            if (part0->partefx[i]) appendFx("Instrument", i, part0->partefx[i]->geteffect());
        }
    }
    return oss.str();
}

std::string ZynAndroidEngine::partsSummary() const {
    if (!zynReady_.load() || !master_) return {};
    std::ostringstream oss;
    bool firstLine = true;
    for (int p = 0; p < NUM_MIDI_PARTS; ++p) {
        auto *part = master_->part[p];
        if (!part) continue;

        int activeKitItems = 0;
        int addEnabledCount = 0;
        int subEnabledCount = 0;
        int padEnabledCount = 0;
        int mutedKitItems = 0;
        for (int i = 0; i < NUM_KIT_ITEMS; ++i) {
            const auto &k = part->kit[i];
            if (k.Penabled) activeKitItems++;
            if (k.Pmuted) mutedKitItems++;
            if (k.Padenabled) addEnabledCount++;
            if (k.Psubenabled) subEnabledCount++;
            if (k.Ppadenabled) padEnabledCount++;
        }

        int partFx = 0;
        for (int i = 0; i < NUM_PART_EFX; ++i) {
            if (part->partefx[i] && part->partefx[i]->geteffect() > 0) partFx++;
        }
        int stereoEnabled = 0;
        int rndGroupingEnabled = 0;
        for (int i = 0; i < NUM_KIT_ITEMS; ++i) {
            const auto &k = part->kit[i];
            if (!(k.Penabled || i == 0)) continue;
            if (k.adpars) {
                stereoEnabled = k.adpars->GlobalPar.PStereo ? 1 : 0;
                rndGroupingEnabled = k.adpars->GlobalPar.Hrandgrouping ? 1 : 0;
                break;
            }
            if (k.subpars) {
                stereoEnabled = k.subpars->Pstereo ? 1 : 0;
            } else if (k.padpars) {
                stereoEnabled = k.padpars->PStereo ? 1 : 0;
            }
        }

        const int partVol127 = std::clamp(
                static_cast<int>(std::lround((part->Volume / 40.0f) * 96.0f + 96.0f)),
                0,
                127);
        const char *name = part->Pname ? part->Pname : "";
        const float peakL = master_->vuoutpeakpartl[p];
        const float peakR = master_->vuoutpeakpartr[p];
        const float peak = std::max(std::fabs(peakL), std::fabs(peakR));
        if (!firstLine) oss << '\n';
        firstLine = false;
        oss << p
            << "|" << (part->Penabled ? 1 : 0)
            << "|" << 0
            << "|" << (part->Pnoteon ? 1 : 0)
            << "|" << (part->Ppolymode ? 1 : 0)
            << "|" << static_cast<int>(part->Prcvchn)
            << "|" << static_cast<int>(part->Pminkey)
            << "|" << static_cast<int>(part->Pmaxkey)
            << "|" << partVol127
            << "|" << static_cast<int>(part->Ppanning)
            << "|" << part->Volume
            << "|" << part->gain
            << "|" << peak
            << "|" << part->Pkitmode
            << "|" << activeKitItems
            << "|" << mutedKitItems
            << "|" << addEnabledCount
            << "|" << subEnabledCount
            << "|" << padEnabledCount
            << "|" << partFx
            << "|" << static_cast<int>(part->Pvelsns)
            << "|" << static_cast<int>(part->Pveloffs)
            << "|" << static_cast<int>(part->ctl.portamento.time)
            << "|" << static_cast<int>(part->ctl.portamento.updowntimestretch)
            << "|" << stereoEnabled
            << "|" << rndGroupingEnabled
            << "|" << name;
    }
    return oss.str();
}

std::string ZynAndroidEngine::mixerSummary() const {
    if (!zynReady_.load() || !master_) return {};
    std::ostringstream oss;
    bool firstLine = true;

    // Insertion FX slot -> assigned part mapping
    for (int i = 0; i < NUM_INS_EFX; ++i) {
        if (!master_->insefx[i]) continue;
        const int fxType = master_->insefx[i]->geteffect();
        if (fxType <= 0) continue;
        const int assignedPart = static_cast<int>(master_->Pinsparts[i]);
        if (!firstLine) oss << '\n';
        firstLine = false;
        oss << "INS|" << i << "|" << fxType << "|" << effectTypeNameNative(fxType) << "|" << assignedPart;
    }

    // System FX sends (only non-zero)
    for (int fx = 0; fx < NUM_SYS_EFX; ++fx) {
        for (int part = 0; part < NUM_MIDI_PARTS; ++part) {
            const int send = static_cast<int>(master_->Psysefxvol[fx][part]);
            if (send <= 0) continue;
            if (!firstLine) oss << '\n';
            firstLine = false;
            oss << "SYS_SEND|" << fx << "|" << part << "|" << send;
        }
    }
    return oss.str();
}

bool ZynAndroidEngine::setPart0Enabled(bool enabled) {
    if (!zynReady_.load() || !master_ || !master_->part[0]) return false;
    auto *part0 = master_->part[0];
    part0->Penabled = enabled;
    if (!enabled) {
        part0->AllNotesOff();
    }
    return true;
}

bool ZynAndroidEngine::setPartEnabled(int partIndex, bool enabled) {
    if (!zynReady_.load() || !master_) return false;
    if (partIndex < 0 || partIndex >= NUM_MIDI_PARTS) return false;
    auto *part = master_->part[partIndex];
    if (!part) return false;
    part->Penabled = enabled;
    if (!enabled) {
        part->AllNotesOff();
    }
    return true;
}

bool ZynAndroidEngine::setPartReceiveChannel(int partIndex, int channel) {
    if (!zynReady_.load() || !master_) return false;
    if (partIndex < 0 || partIndex >= NUM_MIDI_PARTS) return false;
    auto *part = master_->part[partIndex];
    if (!part) return false;
    const int clamped = std::clamp(channel, 0, 15);
    part->Prcvchn = static_cast<unsigned char>(clamped);
    return true;
}

bool ZynAndroidEngine::setPartVolume127(int partIndex, int volume127) {
    if (!zynReady_.load() || !master_) return false;
    if (partIndex < 0 || partIndex >= NUM_MIDI_PARTS) return false;
    auto *part = master_->part[partIndex];
    if (!part) return false;
    const int clamped = std::clamp(volume127, 0, 127);
    // Keep Zyn internal gain state coherent (Volume dB + derived gain smoothing).
    part->setVolumedB(zyn::Part::volume127TodB(static_cast<unsigned char>(clamped)));
    return true;
}

bool ZynAndroidEngine::setPartPanning(int partIndex, int panning127) {
    if (!zynReady_.load() || !master_) return false;
    if (partIndex < 0 || partIndex >= NUM_MIDI_PARTS) return false;
    auto *part = master_->part[partIndex];
    if (!part) return false;
    const int clamped = std::clamp(panning127, 0, 127);
    part->setPpanning(clamped);
    return true;
}

bool ZynAndroidEngine::setPartVelocitySense127(int partIndex, int sense127) {
    if (!zynReady_.load() || !master_) return false;
    if (partIndex < 0 || partIndex >= NUM_MIDI_PARTS) return false;
    auto *part = master_->part[partIndex];
    if (!part) return false;
    part->Pvelsns = static_cast<unsigned char>(std::clamp(sense127, 0, 127));
    return true;
}

bool ZynAndroidEngine::setPartVelocityOffset127(int partIndex, int offset127) {
    if (!zynReady_.load() || !master_) return false;
    if (partIndex < 0 || partIndex >= NUM_MIDI_PARTS) return false;
    auto *part = master_->part[partIndex];
    if (!part) return false;
    part->Pveloffs = static_cast<unsigned char>(std::clamp(offset127, 0, 127));
    return true;
}

bool ZynAndroidEngine::setPartPortamentoTime127(int partIndex, int time127) {
    if (!zynReady_.load() || !master_) return false;
    if (partIndex < 0 || partIndex >= NUM_MIDI_PARTS) return false;
    auto *part = master_->part[partIndex];
    if (!part) return false;
    part->ctl.portamento.time = static_cast<unsigned char>(std::clamp(time127, 0, 127));
    return true;
}

bool ZynAndroidEngine::setPartPortamentoStretch127(int partIndex, int stretch127) {
    if (!zynReady_.load() || !master_) return false;
    if (partIndex < 0 || partIndex >= NUM_MIDI_PARTS) return false;
    auto *part = master_->part[partIndex];
    if (!part) return false;
    part->ctl.portamento.updowntimestretch = static_cast<unsigned char>(std::clamp(stretch127, 0, 127));
    return true;
}

bool ZynAndroidEngine::setPartAddEnabled(int partIndex, bool enabled) {
    if (!zynReady_.load() || !master_) return false;
    if (partIndex < 0 || partIndex >= NUM_MIDI_PARTS) return false;
    auto *part = master_->part[partIndex];
    if (!part) return false;
    bool touched = false;
    for (int i = 0; i < NUM_KIT_ITEMS; ++i) {
        auto &k = part->kit[i];
        if (!(k.Penabled || i == 0)) continue;
        k.Padenabled = enabled;
        touched = true;
    }
    return touched;
}

bool ZynAndroidEngine::setPartSubEnabled(int partIndex, bool enabled) {
    if (!zynReady_.load() || !master_) return false;
    if (partIndex < 0 || partIndex >= NUM_MIDI_PARTS) return false;
    auto *part = master_->part[partIndex];
    if (!part) return false;
    bool touched = false;
    for (int i = 0; i < NUM_KIT_ITEMS; ++i) {
        auto &k = part->kit[i];
        if (!(k.Penabled || i == 0)) continue;
        k.Psubenabled = enabled;
        touched = true;
    }
    return touched;
}

bool ZynAndroidEngine::setPartPadEnabled(int partIndex, bool enabled) {
    if (!zynReady_.load() || !master_) return false;
    if (partIndex < 0 || partIndex >= NUM_MIDI_PARTS) return false;
    auto *part = master_->part[partIndex];
    if (!part) return false;
    bool touched = false;
    for (int i = 0; i < NUM_KIT_ITEMS; ++i) {
        auto &k = part->kit[i];
        if (!(k.Penabled || i == 0)) continue;
        k.Ppadenabled = enabled;
        touched = true;
    }
    return touched;
}

bool ZynAndroidEngine::setPartStereoEnabled(int partIndex, bool enabled) {
    if (!zynReady_.load() || !master_) return false;
    if (partIndex < 0 || partIndex >= NUM_MIDI_PARTS) return false;
    auto *part = master_->part[partIndex];
    if (!part) return false;
    bool touched = false;
    for (int i = 0; i < NUM_KIT_ITEMS; ++i) {
        auto &k = part->kit[i];
        if (!(k.Penabled || i == 0)) continue;
        if (k.adpars) {
            k.adpars->GlobalPar.PStereo = enabled;
            touched = true;
        }
        if (k.subpars) {
            k.subpars->Pstereo = enabled;
            touched = true;
        }
        if (k.padpars) {
            k.padpars->PStereo = enabled;
            touched = true;
        }
    }
    return touched;
}

bool ZynAndroidEngine::setPartRndGroupingEnabled(int partIndex, bool enabled) {
    if (!zynReady_.load() || !master_) return false;
    if (partIndex < 0 || partIndex >= NUM_MIDI_PARTS) return false;
    auto *part = master_->part[partIndex];
    if (!part) return false;
    bool touched = false;
    for (int i = 0; i < NUM_KIT_ITEMS; ++i) {
        auto &k = part->kit[i];
        if (!(k.Penabled || i == 0)) continue;
        if (k.adpars) {
            k.adpars->GlobalPar.Hrandgrouping = enabled;
            touched = true;
        }
    }
    return touched;
}

bool ZynAndroidEngine::soloPart(int partIndex) {
    if (!zynReady_.load() || !master_) return false;
    if (partIndex < 0 || partIndex >= NUM_MIDI_PARTS) return false;
    if (!master_->part[partIndex]) return false;
    for (int i = 0; i < NUM_MIDI_PARTS; ++i) {
        auto *part = master_->part[i];
        if (!part) continue;
        const bool shouldEnable = (i == partIndex);
        if (!shouldEnable) {
            part->AllNotesOff();
        }
        part->Penabled = shouldEnable;
    }
    return true;
}

void ZynAndroidEngine::setTestToneFrequencyHz(float frequencyHz) {
    if (frequencyHz > 20.0f && frequencyHz < 20000.0f) {
        testToneFrequencyHz_.store(frequencyHz);
    }
}

float ZynAndroidEngine::testToneFrequencyHz() const {
    return testToneFrequencyHz_.load();
}

void ZynAndroidEngine::noteOn(int /* channel */, int note, int velocity) {
    if (note < 0 || note > 127) return;
    Command cmd{
        CommandType::NoteOn,
        0,
        note,
        velocity < 0 ? 0 : (velocity > 127 ? 127 : velocity),
    };
    (void) pushCommand(cmd);
}

void ZynAndroidEngine::panic() {
    Command cmd{
        CommandType::Panic,
        0,
        0,
        0,
    };
    (void) pushCommand(cmd);
}

void ZynAndroidEngine::noteOff(int /* channel */, int note) {
    if (note < 0 || note > 127) return;
    Command cmd{
        CommandType::NoteOff,
        0,
        note,
        0,
    };
    (void) pushCommand(cmd);
}

bool ZynAndroidEngine::pushCommand(const Command &cmd) {
    const uint32_t write = commandWriteIndex_.load(std::memory_order_relaxed);
    const uint32_t read = commandReadIndex_.load(std::memory_order_acquire);
    const uint32_t next = (write + 1) % kCommandQueueSize;
    if (next == read) {
        // Queue full; drop newest command for now (safe fallback during M2/M3 prototyping).
        queueDrops_.fetch_add(1);
        return false;
    }
    commandQueue_[write] = cmd;
    commandWriteIndex_.store(next, std::memory_order_release);
    return true;
}

bool ZynAndroidEngine::popCommand(Command &cmd) {
    const uint32_t read = commandReadIndex_.load(std::memory_order_relaxed);
    const uint32_t write = commandWriteIndex_.load(std::memory_order_acquire);
    if (read == write) {
        return false;
    }
    cmd = commandQueue_[read];
    commandReadIndex_.store((read + 1) % kCommandQueueSize, std::memory_order_release);
    return true;
}

void ZynAndroidEngine::applyQueuedCommands() {
    Command cmd{};
    while (popCommand(cmd)) {
        switch (cmd.type) {
            case CommandType::NoteOn:
                activeNote_.store(cmd.note);
                activeVelocity_.store(cmd.velocity);
                noteHeld_.store(true);
                if (zynReady_.load() && master_) {
                    master_->noteOn(static_cast<char>(cmd.channel & 0x0F),
                                    static_cast<zyn::note_t>(cmd.note),
                                    static_cast<char>(cmd.velocity));
                }
                break;
            case CommandType::NoteOff:
                if (cmd.note == activeNote_.load()) {
                    noteHeld_.store(false);
                }
                if (zynReady_.load() && master_) {
                    master_->noteOff(static_cast<char>(cmd.channel & 0x0F),
                                     static_cast<zyn::note_t>(cmd.note));
                }
                break;
            case CommandType::Panic:
                noteHeld_.store(false);
                activeVelocity_.store(0);
                if (zynReady_.load() && master_) {
                    master_->ShutUp();
                }
                break;
        }
    }
}

void ZynAndroidEngine::ensureTempBuffers(int32_t numFrames) {
    if (numFrames <= 0) return;
    if (static_cast<int32_t>(zynLeft_.size()) < numFrames) {
        zynLeft_.resize(static_cast<size_t>(numFrames));
    }
    if (static_cast<int32_t>(zynRight_.size()) < numFrames) {
        zynRight_.resize(static_cast<size_t>(numFrames));
    }
}

void ZynAndroidEngine::renderFallbackTone(
        float *out,
        int32_t numFrames,
        int32_t channelCount,
        int32_t streamSampleRate) {
    const bool toneEnabled = testToneEnabled_.load();
    const bool noteHeld = noteHeld_.load();
    const int note = activeNote_.load();
    const int velocity = activeVelocity_.load();
    const float manualToneFreq = testToneFrequencyHz_.load();
    const float midiToneFreq = 440.0f * std::pow(2.0f, (static_cast<float>(note) - 69.0f) / 12.0f);
    const float toneFreq = noteHeld ? midiToneFreq : manualToneFreq;
    const int32_t sr = streamSampleRate > 0 ? streamSampleRate : sampleRate_.load();
    const double phaseInc = kTwoPi * static_cast<double>(toneFreq) /
                            static_cast<double>(sr > 0 ? sr : 48000);
    double phase = phase_;
    const float velocityGain = noteHeld ? (static_cast<float>(velocity) / 127.0f) : 1.0f;

    for (int32_t frame = 0; frame < numFrames; ++frame) {
        const bool shouldRender = toneEnabled || noteHeld;
        const float sample = shouldRender
                ? static_cast<float>(std::sin(phase) * 0.15 * velocityGain)
                : 0.0f;
        phase += phaseInc;
        if (phase >= kTwoPi) phase -= kTwoPi;

        for (int32_t ch = 0; ch < channelCount; ++ch) {
            out[frame * channelCount + ch] = sample;
        }
    }

    phase_ = phase;
}

void ZynAndroidEngine::renderInterleavedFloat(
        float *out,
        int32_t numFrames,
        int32_t channelCount,
        int32_t streamSampleRate) {
    if (!out || numFrames <= 0 || channelCount <= 0) {
        return;
    }

    applyQueuedCommands();
    float blockPeak = 0.0f;
    if (zynReady_.load() && master_) {
        const int32_t sr = streamSampleRate > 0 ? streamSampleRate : sampleRate_.load();
        ensureTempBuffers(numFrames);
        master_->GetAudioOutSamples(static_cast<size_t>(numFrames),
                                    static_cast<unsigned>(sr > 0 ? sr : 48000),
                                    zynLeft_.data(),
                                    zynRight_.data());

        if (channelCount == 1) {
            for (int32_t i = 0; i < numFrames; ++i) {
                const float s = 0.5f * (zynLeft_[static_cast<size_t>(i)] + zynRight_[static_cast<size_t>(i)]);
                out[i] = s;
                const float a = std::fabs(s);
                if (a > blockPeak) blockPeak = a;
            }
        } else {
            for (int32_t i = 0; i < numFrames; ++i) {
                const float l = zynLeft_[static_cast<size_t>(i)];
                const float r = zynRight_[static_cast<size_t>(i)];
                out[i * channelCount] = l;
                out[i * channelCount + 1] = r;
                const float al = std::fabs(l);
                const float ar = std::fabs(r);
                if (al > blockPeak) blockPeak = al;
                if (ar > blockPeak) blockPeak = ar;
                for (int32_t ch = 2; ch < channelCount; ++ch) {
                    out[i * channelCount + ch] = (ch & 1) ? r : l;
                }
            }
        }
        recentOutputPeak_.store(blockPeak, std::memory_order_relaxed);
        return;
    }

    renderFallbackTone(out, numFrames, channelCount, streamSampleRate);
    const int32_t sampleCount = numFrames * channelCount;
    for (int32_t i = 0; i < sampleCount; ++i) {
        const float a = std::fabs(out[i]);
        if (a > blockPeak) blockPeak = a;
    }
    recentOutputPeak_.store(blockPeak, std::memory_order_relaxed);
    fallbackRenderCalls_.fetch_add(1);
}

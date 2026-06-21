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
#include "Params/EnvelopeParams.h"
#include "Params/LFOParams.h"
#include "Params/SUBnoteParameters.h"
#include "Params/PADnoteParameters.h"
#include "Synth/OscilGen.h"

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

std::string ZynAndroidEngine::parameterSnapshot(int partIndex, int kitIndex) const {
    if (!zynReady_.load() || !master_ || partIndex < 0 || partIndex >= NUM_MIDI_PARTS ||
        kitIndex < 0 || kitIndex >= NUM_KIT_ITEMS || !master_->part[partIndex]) return {};
    const auto *part = master_->part[partIndex];
    const auto &kit = part->kit[kitIndex];
    std::ostringstream out;
    auto add = [&](const char *path, const char *label, const char *group, const char *type,
                   double value, double min, double max, double def, const char *options = "") {
        out << path << '|' << label << '|' << group << '|' << type << '|'
            << value << '|' << min << '|' << max << '|' << def << '|' << options << '\n';
    };
    auto envelopeValue = [&](const char *prefix, const char *group, const zyn::EnvelopeParams &env,
                             bool decay, bool sustain, bool loop) {
        auto time127 = [](float seconds) {
            return std::clamp(static_cast<int>(std::lround(
                std::log2(1.0f + 100.0f * seconds) * 127.0f / 12.0f)), 0, 127);
        };
        add((std::string(prefix) + "/attackValue").c_str(), "Attack value", group, "int", env.PA_val, 0, 127, 64);
        add((std::string(prefix) + "/attackTime").c_str(), "Attack duration", group, "int", time127(env.A_dt), 0, 127, 0);
        if (decay) {
            add((std::string(prefix) + "/decayValue").c_str(), "Decay value", group, "int", env.PD_val, 0, 127, 64);
            add((std::string(prefix) + "/decayTime").c_str(), "Decay duration", group, "int", time127(env.D_dt), 0, 127, 0);
        }
        if (sustain)
            add((std::string(prefix) + "/sustain").c_str(), "Sustain value", group, "int", env.PS_val, 0, 127, 127);
        add((std::string(prefix) + "/releaseTime").c_str(), "Release duration", group, "int", time127(env.R_dt), 0, 127, 0);
        add((std::string(prefix) + "/releaseValue").c_str(), "Release value", group, "int", env.PR_val, 0, 127, 64);
        add((std::string(prefix) + "/stretch").c_str(), "Envelope stretch", group, "int", env.Penvstretch, 0, 127, 64);
        if (loop)
            add((std::string(prefix) + "/loop").c_str(), "Envelope loop", group, "bool", env.Prepeating, 0, 1, 0);
        add((std::string(prefix) + "/forceRelease").c_str(), "Force release", group, "bool", env.Pforcedrelease, 0, 1, 0);
    };
    auto lfoValue = [&](const char *prefix, const char *group, const zyn::LFOParams &lfo) {
        const int frequency = std::clamp(static_cast<int>(std::lround(
            127.0f * std::log2(12.0f * lfo.freq + 1.0f) / 10.0f)), 0, 127);
        const int delay = std::clamp(static_cast<int>(std::lround(127.0f * lfo.delay / 4.0f)), 0, 127);
        add((std::string(prefix) + "/frequency").c_str(), "LFO frequency", group, "int", frequency, 0, 127, 64);
        add((std::string(prefix) + "/depth").c_str(), "LFO depth", group, "int", lfo.Pintensity, 0, 127, 0);
        add((std::string(prefix) + "/start").c_str(), "LFO start phase", group, "int", lfo.Pstartphase, 0, 127, 64);
        add((std::string(prefix) + "/delay").c_str(), "LFO delay", group, "int", delay, 0, 127, 0);
        add((std::string(prefix) + "/random").c_str(), "LFO random amount", group, "int", lfo.Prandomness, 0, 127, 0);
        add((std::string(prefix) + "/continuous").c_str(), "Continuous LFO", group, "bool", lfo.Pcontinous, 0, 1, 0);
        add((std::string(prefix) + "/waveform").c_str(), "LFO waveform", group, "enum", lfo.PLFOtype, 0, 7, 0,
            "Sine,Triangle,Square,Ramp up,Ramp down,Exp down 1,Exp down 2,Random");
    };
    add("part/enabled", "Enabled", "Part", "bool", part->Penabled, 0, 1, 1);
    add("part/volume", "Volume", "Part", "int", std::clamp(
        static_cast<int>(std::lround(96.0f * part->Volume / 40.0f + 96.0f)), 0, 127), 0, 127, 96);
    add("part/panning", "Panning", "Part", "int", part->Ppanning, 0, 127, 64);
    add("part/minKey", "Minimum key", "Part", "int", part->Pminkey, 0, 127, 0);
    add("part/maxKey", "Maximum key", "Part", "int", part->Pmaxkey, 0, 127, 127);
    add("part/keyShift", "Key shift", "Part", "int", static_cast<int>(part->Pkeyshift) - 64, -64, 63, 0);
    add("part/channel", "Receive channel", "Part", "enum", part->Prcvchn, 0, 15, partIndex % 16,
        "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16");
    add("part/velocitySense", "Velocity sense", "Part", "int", part->Pvelsns, 0, 127, 64);
    add("part/velocityOffset", "Velocity offset", "Part", "int", part->Pveloffs, 0, 127, 64);
    add("part/kitMode", "Kit mode", "Part", "enum", part->Pkitmode, 0, 2, 0, "Off,Multi,Single");
    add("part/polyMode", "Polyphonic", "Part", "bool", part->Ppolymode, 0, 1, 1);
    add("part/legatoMode", "Legato", "Part", "bool", part->Plegatomode, 0, 1, 0);
    add("part/drumMode", "Drum mode", "Part", "bool", part->Pdrummode, 0, 1, 0);
    add("kit/enabled", "Enabled", "Kit", "bool", kit.Penabled, 0, 1, kitIndex == 0);
    add("kit/muted", "Muted", "Kit", "bool", kit.Pmuted, 0, 1, 0);
    add("kit/minKey", "Minimum key", "Kit", "int", kit.Pminkey, 0, 127, 0);
    add("kit/maxKey", "Maximum key", "Kit", "int", kit.Pmaxkey, 0, 127, 127);
    add("kit/sendFx", "Send to effect", "Kit", "enum", kit.Psendtoparteffect, 0, 3, 0, "FX 1,FX 2,FX 3,Off");
    add("kit/addEnabled", "ADsynth enabled", "Kit", "bool", kit.Padenabled, 0, 1, kitIndex == 0);
    add("kit/subEnabled", "SUBsynth enabled", "Kit", "bool", kit.Psubenabled, 0, 1, 0);
    add("kit/padEnabled", "PADsynth enabled", "Kit", "bool", kit.Ppadenabled, 0, 1, 0);
    if (kit.adpars) {
        const auto &g = kit.adpars->GlobalPar;
        add("add/stereo", "Stereo", "ADD / Global", "bool", g.PStereo, 0, 1, 1);
        add("add/volume", "Volume", "ADD / Amplitude / Global", "int", std::clamp(
            static_cast<int>(std::lround(96.0f * (1.0f + (g.Volume - 12.0412f) / 60.0f))), 0, 127),
            0, 127, 96);
        add("add/panning", "Panning", "ADD / Amplitude / Global", "int", g.PPanning, 0, 127, 64);
        add("add/detune", "Fine detune", "ADD / Frequency / Global", "int", g.PDetune, 0, 16383, 8192);
        add("add/detuneType", "Detune type", "ADD / Frequency / Global", "enum", g.PDetuneType, 0, 3, 1,
            "L35,L10E,L100E,E1200");
        add("add/velocity", "Velocity sensitivity", "ADD / Amplitude / Global", "int",
            g.PAmpVelocityScaleFunction, 0, 127, 64);
        add("add/punchStrength", "Punch strength", "ADD / Amplitude / Punch", "int", g.PPunchStrength, 0, 127, 0);
        add("add/punchTime", "Punch time", "ADD / Amplitude / Punch", "int", g.PPunchTime, 0, 127, 60);
        add("add/punchStretch", "Punch stretch", "ADD / Amplitude / Punch", "int", g.PPunchStretch, 0, 127, 64);
        add("add/punchVelocity", "Punch velocity", "ADD / Amplitude / Punch", "int",
            g.PPunchVelocitySensing, 0, 127, 72);
        add("add/filterVelocity", "Velocity amount", "ADD / Filter / Parameters", "int",
            g.PFilterVelocityScale, 0, 127, 0);
        add("add/filterVelocitySense", "Filter velocity sensitivity", "ADD / Filter / Parameters", "int",
            g.PFilterVelocityScaleFunction, 0, 127, 64);
        if (g.AmpEnvelope)
            envelopeValue("add/ampEnvelope", "ADD / Amplitude / Envelope", *g.AmpEnvelope, true, true, true);
        if (g.AmpLfo)
            lfoValue("add/ampLfo", "ADD / Amplitude / LFO", *g.AmpLfo);
        if (g.FreqEnvelope)
            envelopeValue("add/freqEnvelope", "ADD / Frequency / Envelope", *g.FreqEnvelope, false, false, false);
        if (g.FreqLfo)
            lfoValue("add/freqLfo", "ADD / Frequency / LFO", *g.FreqLfo);
        const int octave = g.PCoarseDetune / 1024 >= 8 ? g.PCoarseDetune / 1024 - 16 : g.PCoarseDetune / 1024;
        const int coarse = g.PCoarseDetune % 1024 >= 512 ? g.PCoarseDetune % 1024 - 1024 : g.PCoarseDetune % 1024;
        add("add/octave", "Octave", "ADD / Frequency / Global", "int", octave, -8, 7, 0);
        add("add/coarse", "Coarse detune", "ADD / Frequency / Global", "int", coarse, -64, 63, 0);
        if (g.GlobalFilter) {
            const auto &filter = *g.GlobalFilter;
            const int cutoff = std::clamp(static_cast<int>(std::lround(
                ((std::log2(filter.basefreq) - 9.96578428f) / 5.0f + 1.0f) * 64.0f)), 0, 127);
            const int q = std::clamp(static_cast<int>(std::lround(
                127.0f * std::sqrt(std::log(0.9f + filter.baseq) / std::log(1000.0f)))), 0, 127);
            const int tracking = std::clamp(static_cast<int>(std::lround(filter.freqtracking / 100.0f * 64.0f + 64.0f)), 0, 127);
            const int gain = std::clamp(static_cast<int>(std::lround((filter.gain / 30.0f + 1.0f) * 64.0f)), 0, 127);
            add("add/filter/category", "Filter category", "ADD / Filter / Parameters", "enum",
                filter.Pcategory, 0, 4, 0, "Analog,Formant,State variable,Moog,Comb");
            add("add/filter/type", "Filter type", "ADD / Filter / Parameters", "enum",
                filter.Ptype, 0, 8, 0, "LPF1,HPF1,LPF2,HPF2,BPF1,Notch1,Peak1,Low shelf,High shelf");
            add("add/filter/cutoff", "Cutoff frequency", "ADD / Filter / Parameters", "int", cutoff, 0, 127, 94);
            add("add/filter/q", "Q", "ADD / Filter / Parameters", "int", q, 0, 127, 40);
            add("add/filter/tracking", "Frequency tracking", "ADD / Filter / Parameters", "int", tracking, 0, 127, 64);
            add("add/filter/gain", "Filter gain", "ADD / Filter / Parameters", "int", gain, 0, 127, 64);
            add("add/filter/stages", "Filter stages", "ADD / Filter / Parameters", "int", filter.Pstages + 1, 1, 6, 1);
        }
        if (g.FilterEnvelope)
            envelopeValue("add/filterEnvelope", "ADD / Filter / Envelope", *g.FilterEnvelope, true, false, false);
        if (g.FilterLfo)
            lfoValue("add/filterLfo", "ADD / Filter / LFO", *g.FilterLfo);
        add("add/randomGrouping", "Random grouping", "ADD / Oscillator", "bool", g.Hrandgrouping, 0, 1, 0);
        for (int voice = 0; voice < NUM_VOICES; ++voice) {
            const auto &v = kit.adpars->VoicePar[voice];
            const std::string prefix = "add/voice/" + std::to_string(voice) + "/";
            const std::string group = "ADD / Voice " + std::to_string(voice + 1);
            add((prefix + "enabled").c_str(), "Enabled", group.c_str(), "bool", v.Enabled, 0, 1, voice == 0);
            add((prefix + "unison").c_str(), "Unison voices", group.c_str(), "int", v.Unison_size, 1, 50, 1);
            add((prefix + "spread").c_str(), "Unison spread", group.c_str(), "int", v.Unison_frequency_spread, 0, 127, 60);
            add((prefix + "phaseRandom").c_str(), "Phase randomness", group.c_str(), "int", v.Unison_phase_randomness, 0, 127, 127);
            add((prefix + "stereoSpread").c_str(), "Stereo spread", group.c_str(), "int", v.Unison_stereo_spread, 0, 127, 64);
            add((prefix + "vibrato").c_str(), "Vibrato depth", group.c_str(), "int", v.Unison_vibratto, 0, 127, 64);
            add((prefix + "vibratoSpeed").c_str(), "Vibrato speed", group.c_str(), "int", v.Unison_vibratto_speed, 0, 127, 64);
            add((prefix + "panning").c_str(), "Panning", group.c_str(), "int", v.PPanning, 0, 127, 64);
            add((prefix + "volume").c_str(), "Volume", group.c_str(), "int",
                std::clamp(static_cast<int>(std::lround(127.0f * (1.0f + v.volume / 60.0f))), 0, 127),
                0, 127, 100);
            add((prefix + "detune").c_str(), "Fine detune", group.c_str(), "int", v.PDetune, 0, 16383, 8192);
            add((prefix + "fixedFreq").c_str(), "Fixed frequency", group.c_str(), "bool", v.Pfixedfreq, 0, 1, 0);
            add((prefix + "resonance").c_str(), "Resonance", group.c_str(), "bool", v.Presonance, 0, 1, 1);
            add((prefix + "filter").c_str(), "Voice filter", group.c_str(), "bool", v.PFilterEnabled, 0, 1, 0);
            add((prefix + "fmType").c_str(), "Modulation", group.c_str(), "enum",
                static_cast<int>(v.PFMEnabled), 0, 5, 0, "Off,Mix,Ring,Phase,Frequency,PWM");
            if (v.OscilGn) {
                const auto &o = *v.OscilGn;
                const std::string oscGroup = "ADD / Oscillator";
                add("add/osc/magnitudeType", "Magnitude scale", oscGroup.c_str(), "enum", o.Phmagtype, 0, 4, 0,
                    "Linear,-40 dB,-60 dB,-80 dB,-100 dB");
                add("add/osc/baseFunction", "Base function", oscGroup.c_str(), "enum", o.Pcurrentbasefunc, 0, 15, 0,
                    "Sine,Triangle,Pulse,Saw,Power,Gauss,Diode,AbsSine,PulseSine,StretchSine,Chirp,AbsStretchSine,Chebyshev,Sqr,Spike,Circle");
                add("add/osc/baseShape", "Base shape", oscGroup.c_str(), "int", o.Pbasefuncpar, 0, 127, 64);
                add("add/osc/waveshape", "Waveshaping", oscGroup.c_str(), "int", o.Pwaveshaping, 0, 127, 64);
                add("add/osc/filterType", "Filter", oscGroup.c_str(), "enum", o.Pfiltertype, 0, 13, 0,
                    "Off,LP1,LP2,HP1,HP2,BP1,BP2,Cos,Sin,LowShelf,S,Comb,Spike,LP3");
                add("add/osc/filter1", "Filter parameter 1", oscGroup.c_str(), "int", o.Pfilterpar1, 0, 127, 64);
                add("add/osc/filter2", "Filter parameter 2", oscGroup.c_str(), "int", o.Pfilterpar2, 0, 127, 64);
                add("add/osc/randomness", "Phase randomness", oscGroup.c_str(), "int", o.Prand, 0, 127, 64);
                for (int h = 0; h < MAX_AD_HARMONICS; ++h) {
                    add(("add/osc/harmonic/" + std::to_string(h) + "/magnitude").c_str(),
                        ("Harmonic " + std::to_string(h + 1)).c_str(), "ADD / Oscillator harmonics",
                        "int", o.Phmag[h], 0, 127, h == 0 ? 127 : 64);
                    add(("add/osc/harmonic/" + std::to_string(h) + "/phase").c_str(),
                        ("Phase " + std::to_string(h + 1)).c_str(), "ADD / Oscillator phases",
                        "int", o.Phphase[h], 0, 127, 64);
                }
            }
        }
    }
    if (kit.subpars) {
        const auto &s = *kit.subpars;
        add("sub/stereo", "Stereo", "SUB / Global", "bool", s.Pstereo, 0, 1, 1);
        add("sub/panning", "Panning", "SUB / Amplitude", "int", s.PPanning, 0, 127, 64);
        add("sub/bandwidth", "Bandwidth", "SUB / Harmonics", "int", s.Pbandwidth, 0, 127, 64);
        add("sub/bandwidthScale", "Bandwidth scale", "SUB / Harmonics", "int", s.Pbwscale, 0, 127, 64);
        add("sub/stages", "Filter stages", "SUB / Harmonics", "int", s.Pnumstages, 1, 5, 2);
        add("sub/start", "Harmonic start", "SUB / Harmonics", "enum", s.Pstart, 0, 2, 1, "Zero,Random,Positive");
        add("sub/fixedFreq", "Fixed frequency", "SUB / Frequency", "bool", s.Pfixedfreq, 0, 1, 0);
        add("sub/filterEnabled", "Global filter", "SUB / Filter", "bool", s.PGlobalFilterEnabled, 0, 1, 0);
        for (int h = 0; h < MAX_SUB_HARMONICS; ++h) {
            const std::string group = "SUB / Harmonic " + std::to_string(h + 1);
            add(("sub/harmonic/" + std::to_string(h) + "/magnitude").c_str(), "Magnitude", group.c_str(), "int", s.Phmag[h], 0, 127, h == 0 ? 127 : 0);
            add(("sub/harmonic/" + std::to_string(h) + "/bandwidth").c_str(), "Relative bandwidth", group.c_str(), "int", s.Phrelbw[h], 0, 127, 64);
        }
    }
    if (kit.padpars) {
        const auto &p = *kit.padpars;
        add("pad/stereo", "Stereo", "PAD / Global", "bool", p.PStereo, 0, 1, 1);
        add("pad/panning", "Panning", "PAD / Amplitude", "int", p.PPanning, 0, 127, 64);
        add("pad/volume", "Volume", "PAD / Amplitude", "int", p.PVolume, 0, 127, 90);
        add("pad/bandwidth", "Bandwidth", "PAD / Harmonics", "int", p.Pbandwidth, 0, 1000, 500);
        add("pad/bandwidthScale", "Bandwidth scale", "PAD / Harmonics", "int", p.Pbwscale, 0, 127, 64);
        add("pad/mode", "Synthesis mode", "PAD / Harmonics", "enum", static_cast<int>(p.Pmode), 0, 2, 0, "Bandwidth,Discrete,Continuous");
        add("pad/profileWidth", "Profile width", "PAD / Profile", "int", p.Php.width, 0, 127, 127);
        add("pad/profileAutoScale", "Profile auto-scale", "PAD / Profile", "bool", p.Php.autoscale, 0, 1, 1);
        add("pad/qualitySize", "Sample size", "PAD / Quality", "int", p.Pquality.samplesize, 0, 6, 3);
        add("pad/qualityOctaves", "Octaves", "PAD / Quality", "int", p.Pquality.oct, 0, 7, 3);
        add("pad/qualitySamplesPerOctave", "Samples per octave", "PAD / Quality", "int", p.Pquality.smpoct, 0, 6, 2);
        add("pad/fixedFreq", "Fixed frequency", "PAD / Frequency", "bool", p.Pfixedfreq, 0, 1, 0);
    }
    return out.str();
}

bool ZynAndroidEngine::setParameter(int partIndex, int kitIndex, const std::string &path, double value) {
    if (!zynReady_.load() || !master_ || partIndex < 0 || partIndex >= NUM_MIDI_PARTS ||
        kitIndex < 0 || kitIndex >= NUM_KIT_ITEMS || !master_->part[partIndex]) return false;
    auto *part = master_->part[partIndex];
    auto &kit = part->kit[kitIndex];
    auto i = [&](int min, int max) { return std::clamp(static_cast<int>(std::lround(value)), min, max); };
    auto b = [&]() { return value >= 0.5; };
    auto writeEnvelope = [&](zyn::EnvelopeParams &env, const std::string &field) {
        auto seconds = [&](int raw) { return (std::pow(2.0f, raw / 127.0f * 12.0f) - 1.0f) / 100.0f; };
        if (field == "attackValue") env.PA_val = i(0, 127);
        else if (field == "attackTime") env.A_dt = seconds(i(0, 127));
        else if (field == "decayValue") env.PD_val = i(0, 127);
        else if (field == "decayTime") env.D_dt = seconds(i(0, 127));
        else if (field == "sustain") env.PS_val = i(0, 127);
        else if (field == "releaseTime") env.R_dt = seconds(i(0, 127));
        else if (field == "releaseValue") env.PR_val = i(0, 127);
        else if (field == "stretch") env.Penvstretch = i(0, 127);
        else if (field == "loop") env.Prepeating = b();
        else if (field == "forceRelease") env.Pforcedrelease = b();
        else return false;
        env.converttofree();
        return true;
    };
    auto writeLfo = [&](zyn::LFOParams &lfo, const std::string &field) {
        if (field == "frequency") lfo.freq = (std::pow(2.0f, 10.0f * i(0, 127) / 127.0f) - 1.0f) / 12.0f;
        else if (field == "depth") lfo.Pintensity = i(0, 127);
        else if (field == "start") lfo.Pstartphase = i(0, 127);
        else if (field == "delay") lfo.delay = 4.0f * i(0, 127) / 127.0f;
        else if (field == "random") lfo.Prandomness = i(0, 127);
        else if (field == "continuous") lfo.Pcontinous = b();
        else if (field == "waveform") lfo.PLFOtype = i(0, 7);
        else if (field == "type") lfo.fel = static_cast<zyn::consumer_location_type_t>(i(0, 2));
        else return false;
        return true;
    };
    if (path == "part/enabled") return setPartEnabled(partIndex, b());
    if (path == "part/volume") return setPartVolume127(partIndex, i(0, 127));
    if (path == "part/panning") return setPartPanning(partIndex, i(0, 127));
    if (path == "part/minKey") part->Pminkey = i(0, 127);
    else if (path == "part/maxKey") part->Pmaxkey = i(0, 127);
    else if (path == "part/keyShift") part->Pkeyshift = i(-64, 63) + 64;
    else if (path == "part/channel") part->Prcvchn = i(0, 15);
    else if (path == "part/velocitySense") part->Pvelsns = i(0, 127);
    else if (path == "part/velocityOffset") part->Pveloffs = i(0, 127);
    else if (path == "part/kitMode") part->Pkitmode = i(0, 2);
    else if (path == "part/polyMode") part->Ppolymode = b();
    else if (path == "part/legatoMode") part->Plegatomode = b();
    else if (path == "part/drumMode") part->Pdrummode = b();
    else if (path == "kit/enabled") part->setkititemstatus(kitIndex, b());
    else if (path == "kit/muted") kit.Pmuted = b();
    else if (path == "kit/minKey") kit.Pminkey = i(0, 127);
    else if (path == "kit/maxKey") kit.Pmaxkey = i(0, 127);
    else if (path == "kit/sendFx") kit.Psendtoparteffect = i(0, 3);
    else if (path == "kit/addEnabled") kit.Padenabled = b();
    else if (path == "kit/subEnabled") kit.Psubenabled = b();
    else if (path == "kit/padEnabled") kit.Ppadenabled = b();
    else if (path.rfind("add/", 0) == 0 && kit.adpars) {
        auto &g = kit.adpars->GlobalPar;
        if (path == "add/stereo") g.PStereo = b();
        else if (path == "add/volume") g.Volume = 12.0412f - 60.0f * (1.0f - i(0, 127) / 96.0f);
        else if (path == "add/panning") g.PPanning = i(0, 127);
        else if (path == "add/detune") g.PDetune = i(0, 16383);
        else if (path == "add/detuneType") g.PDetuneType = i(0, 3);
        else if (path == "add/velocity") g.PAmpVelocityScaleFunction = i(0, 127);
        else if (path == "add/punchStrength") g.PPunchStrength = i(0, 127);
        else if (path == "add/punchTime") g.PPunchTime = i(0, 127);
        else if (path == "add/punchStretch") g.PPunchStretch = i(0, 127);
        else if (path == "add/punchVelocity") g.PPunchVelocitySensing = i(0, 127);
        else if (path == "add/filterVelocity") g.PFilterVelocityScale = i(0, 127);
        else if (path == "add/filterVelocitySense") g.PFilterVelocityScaleFunction = i(0, 127);
        else if (path == "add/octave") {
            int octave = i(-8, 7);
            if (octave < 0) octave += 16;
            g.PCoarseDetune = octave * 1024 + g.PCoarseDetune % 1024;
        } else if (path == "add/coarse") {
            int coarse = i(-64, 63);
            if (coarse < 0) coarse += 1024;
            g.PCoarseDetune = coarse + (g.PCoarseDetune / 1024) * 1024;
        } else if (path.rfind("add/ampEnvelope/", 0) == 0 && g.AmpEnvelope) {
            if (!writeEnvelope(*g.AmpEnvelope, path.substr(16))) return false;
        } else if (path.rfind("add/freqEnvelope/", 0) == 0 && g.FreqEnvelope) {
            if (!writeEnvelope(*g.FreqEnvelope, path.substr(17))) return false;
        } else if (path.rfind("add/filterEnvelope/", 0) == 0 && g.FilterEnvelope) {
            if (!writeEnvelope(*g.FilterEnvelope, path.substr(19))) return false;
        } else if (path.rfind("add/ampLfo/", 0) == 0 && g.AmpLfo) {
            if (!writeLfo(*g.AmpLfo, path.substr(11))) return false;
        } else if (path.rfind("add/freqLfo/", 0) == 0 && g.FreqLfo) {
            if (!writeLfo(*g.FreqLfo, path.substr(12))) return false;
        } else if (path.rfind("add/filterLfo/", 0) == 0 && g.FilterLfo) {
            if (!writeLfo(*g.FilterLfo, path.substr(14))) return false;
        } else if (path.rfind("add/filter/", 0) == 0 && g.GlobalFilter) {
            auto &filter = *g.GlobalFilter;
            const auto field = path.substr(11);
            if (field == "category") filter.Pcategory = i(0, 4);
            else if (field == "type") filter.Ptype = i(0, 8);
            else if (field == "cutoff") filter.basefreq = zyn::FilterParams::basefreqFromOldPreq(i(0, 127));
            else if (field == "q") filter.baseq = zyn::FilterParams::baseqFromOldPq(i(0, 127));
            else if (field == "tracking") filter.freqtracking = 100.0f * (i(0, 127) - 64.0f) / 64.0f;
            else if (field == "gain") filter.gain = zyn::FilterParams::gainFromOldPgain(i(0, 127));
            else if (field == "stages") filter.Pstages = i(1, 6) - 1;
            else return false;
            filter.changed = true;
        }
        else if (path == "add/randomGrouping") g.Hrandgrouping = b();
        else if (path.rfind("add/voice/", 0) == 0) {
            const auto rest = path.substr(10);
            const auto slash = rest.find('/');
            if (slash == std::string::npos) return false;
            const int voice = std::atoi(rest.substr(0, slash).c_str());
            if (voice < 0 || voice >= NUM_VOICES) return false;
            auto &v = kit.adpars->VoicePar[voice];
            const auto field = rest.substr(slash + 1);
            if (field == "enabled") v.Enabled = b();
            else if (field == "unison") v.Unison_size = i(1, 50);
            else if (field == "spread") v.Unison_frequency_spread = i(0, 127);
            else if (field == "phaseRandom") v.Unison_phase_randomness = i(0, 127);
            else if (field == "stereoSpread") v.Unison_stereo_spread = i(0, 127);
            else if (field == "vibrato") v.Unison_vibratto = i(0, 127);
            else if (field == "vibratoSpeed") v.Unison_vibratto_speed = i(0, 127);
            else if (field == "panning") v.PPanning = i(0, 127);
            else if (field == "volume") v.volume = -60.0f * (1.0f - i(0, 127) / 127.0f);
            else if (field == "detune") v.PDetune = i(0, 16383);
            else if (field == "fixedFreq") v.Pfixedfreq = b();
            else if (field == "resonance") v.Presonance = b();
            else if (field == "filter") v.PFilterEnabled = b();
            else if (field == "fmType") v.PFMEnabled = static_cast<zyn::FMTYPE>(i(0, 5));
            else return false;
        } else if (path.rfind("add/osc/", 0) == 0) {
            auto &o = *kit.adpars->VoicePar[0].OscilGn;
            if (path == "add/osc/magnitudeType") o.Phmagtype = i(0, 4);
            else if (path == "add/osc/baseFunction") o.Pcurrentbasefunc = i(0, 15);
            else if (path == "add/osc/baseShape") o.Pbasefuncpar = i(0, 127);
            else if (path == "add/osc/waveshape") o.Pwaveshaping = i(0, 127);
            else if (path == "add/osc/filterType") o.Pfiltertype = i(0, 13);
            else if (path == "add/osc/filter1") o.Pfilterpar1 = i(0, 127);
            else if (path == "add/osc/filter2") o.Pfilterpar2 = i(0, 127);
            else if (path == "add/osc/randomness") o.Prand = i(0, 127);
            else if (path.rfind("add/osc/harmonic/", 0) == 0) {
                const auto rest = path.substr(17);
                const auto slash = rest.find('/');
                if (slash == std::string::npos) return false;
                const int h = std::atoi(rest.substr(0, slash).c_str());
                if (h < 0 || h >= MAX_AD_HARMONICS) return false;
                const auto field = rest.substr(slash + 1);
                if (field == "magnitude") o.Phmag[h] = i(0, 127);
                else if (field == "phase") o.Phphase[h] = i(0, 127);
                else return false;
            } else return false;
            o.prepare();
        } else return false;
    } else if (path.rfind("sub/", 0) == 0 && kit.subpars) {
        auto &s = *kit.subpars;
        if (path == "sub/stereo") s.Pstereo = b();
        else if (path == "sub/panning") s.PPanning = i(0, 127);
        else if (path == "sub/bandwidth") s.Pbandwidth = i(0, 127);
        else if (path == "sub/bandwidthScale") s.Pbwscale = i(0, 127);
        else if (path == "sub/stages") s.Pnumstages = i(1, 5);
        else if (path == "sub/start") s.Pstart = i(0, 2);
        else if (path == "sub/fixedFreq") s.Pfixedfreq = b();
        else if (path == "sub/filterEnabled") s.PGlobalFilterEnabled = b();
        else if (path.rfind("sub/harmonic/", 0) == 0) {
            const auto rest = path.substr(13);
            const auto slash = rest.find('/');
            if (slash == std::string::npos) return false;
            const int h = std::atoi(rest.substr(0, slash).c_str());
            if (h < 0 || h >= MAX_SUB_HARMONICS) return false;
            const auto field = rest.substr(slash + 1);
            if (field == "magnitude") s.Phmag[h] = i(0, 127);
            else if (field == "bandwidth") s.Phrelbw[h] = i(0, 127);
            else return false;
        } else return false;
        s.updateFrequencyMultipliers();
    } else if (path.rfind("pad/", 0) == 0 && kit.padpars) {
        auto &p = *kit.padpars;
        if (path == "pad/stereo") p.PStereo = b();
        else if (path == "pad/panning") p.PPanning = i(0, 127);
        else if (path == "pad/volume") p.PVolume = i(0, 127);
        else if (path == "pad/bandwidth") p.Pbandwidth = i(0, 1000);
        else if (path == "pad/bandwidthScale") p.Pbwscale = i(0, 127);
        else if (path == "pad/mode") p.Pmode = static_cast<zyn::PADnoteParameters::pad_mode>(i(0, 2));
        else if (path == "pad/profileWidth") p.Php.width = i(0, 127);
        else if (path == "pad/profileAutoScale") p.Php.autoscale = b();
        else if (path == "pad/qualitySize") p.Pquality.samplesize = i(0, 6);
        else if (path == "pad/qualityOctaves") p.Pquality.oct = i(0, 7);
        else if (path == "pad/qualitySamplesPerOctave") p.Pquality.smpoct = i(0, 6);
        else if (path == "pad/fixedFreq") p.Pfixedfreq = b();
        else return false;
    } else return false;
    return true;
}

bool ZynAndroidEngine::exportInstrument(int partIndex, const std::string &path) {
    if (!zynReady_.load() || !master_ || partIndex < 0 || partIndex >= NUM_MIDI_PARTS ||
        !master_->part[partIndex] || path.empty()) return false;
    return master_->part[partIndex]->saveXML(path.c_str()) == 0;
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

void ZynAndroidEngine::noteOn(int channel, int note, int velocity) {
    if (note < 0 || note > 127) return;
    Command cmd{
        CommandType::NoteOn,
        std::clamp(channel, 0, 15),
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

void ZynAndroidEngine::noteOff(int channel, int note) {
    if (note < 0 || note > 127) return;
    Command cmd{
        CommandType::NoteOff,
        std::clamp(channel, 0, 15),
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

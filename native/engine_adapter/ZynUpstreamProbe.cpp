#include "ZynUpstreamProbe.h"

#include <sstream>

#include "zyn-version.h"
#include "zyn-config.h"
#include "globals.h"
#include "Containers/MultiPseudoStack.h"
#include "Containers/ScratchString.h"
#include "Misc/Allocator.h"
#include "Misc/Config.h"
#include "Misc/MsgParsing.h"
#include "Misc/XMLwrapper.h"
#include "Misc/Time.h"
#include "Params/Controller.h"
#include "DSP/FFTwrapper.h"

namespace zyn_android {

std::string zynUpstreamProbeSummary() {
    zyn::SYNTH_T synth;
    synth.alias(false);
    zyn::MultiQueue mq;
    auto *q = mq.alloc();
    bool mqAllocOk = (q != nullptr);
    if (q) {
        mq.free(q);
    }
    zyn::ScratchString labelA("Zyn");
    zyn::ScratchString labelB("Android");
    zyn::ScratchString joined = labelA + zyn::ScratchString("-") + labelB;
    zyn::Alloc allocator;
    int *probeInt = allocator.alloc<int>(42);
    const bool allocOk = (probeInt != nullptr && *probeInt == 42);
    allocator.dealloc(probeInt);
    zyn::Config cfg;
    int part = 1, kit = 2, voice = 3;
    bool isFm = false;
    std::string msg = zyn::buildVoiceParMsg(&part, &kit, &voice, &isFm);
    zyn::XMLwrapper xml;
    const std::string xmlText = xml.getXMLdata();
    zyn::AbsTime absTime(synth);
    zyn::Controller controller(synth, &absTime);
    controller.setpitchwheel(4096);
    zyn::FFTwrapper fft(64);
    auto smp = fft.allocSampleBuf();
    auto frq = fft.allocFreqBuf();
    auto scratchS = fft.allocSampleBuf();
    for (int i = 0; i < smp.fftsize; ++i) {
        smp[i] = (i == 0) ? 1.0f : 0.0f;
    }
    fft.smps2freqs(smp, frq, scratchS);

    std::ostringstream oss;
    oss << "zyn-upstream-probe "
        << zyn::version.get_major() << "."
        << zyn::version.get_minor() << "."
        << zyn::version.get_revision()
        << " | parts=" << NUM_MIDI_PARTS
        << " | synth.sr=" << synth.samplerate
        << " | buf=" << synth.buffersize
        << " | bytes=" << synth.bufferbytes
        << " | mq_alloc=" << (mqAllocOk ? "ok" : "fail")
        << " | alloc=" << (allocOk ? "ok" : "fail")
        << " | cfg.sr=" << cfg.cfg.SampleRate
        << " | msg=" << msg
        << " | xml=" << (xmlText.empty() ? "empty" : "ok")
        << " | pwhl=" << controller.pitchwheel.relfreq
        << " | fftN=" << fft.fftsize()
        << " | label=" << joined.c_str
        << " | fusion_dir=" << zyn::fusion_dir;
    return oss.str();
}

} // namespace zyn_android

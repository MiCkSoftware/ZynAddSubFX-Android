#include "ZynAndroidEngine.h"
#include <cassert>
#include <cmath>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>
#include <thread>
#include <chrono>

static double read(const ZynAndroidEngine &engine, int part, int kit, const std::string &path) {
    std::istringstream snapshot(engine.parameterSnapshot(part, kit));
    std::string line;
    while (std::getline(snapshot, line)) {
        if (line.rfind(path + '|', 0) != 0) continue;
        std::istringstream fields(line);
        for (int n = 0; n < 5; ++n) std::getline(fields, line, '|');
        return std::stod(line);
    }
    std::cerr << "Missing path: " << path << '\n';
    std::abort();
}

static void assertPreviewNear(const std::string &expected, const std::string &actual) {
    std::istringstream left(expected), right(actual);
    std::string a, b;
    while (std::getline(left, a, ',')) {
        assert(static_cast<bool>(std::getline(right, b, ',')));
        if (std::fabs(std::stof(a) - std::stof(b)) > .0001f) {
            std::cerr << "Preview difference: " << a << " vs " << b << std::endl;
            std::abort();
        }
    }
    assert(!std::getline(right, b, ','));
}

int main() {
    ZynAndroidEngine engine;
    assert(engine.initialize(48000, 256));
    assert(engine.isZynReady());
    auto write = [&](int part, int kit, const std::string &path, double value) {
        if (!engine.setParameter(part, kit, path, value)) {
            std::cerr << "Failed write: " << part << ":" << kit << ":" << path << std::endl;
            std::abort();
        }
    };
    assert(engine.modulePreview(0, 0, 2, -1, 0, 64, 1).empty());
    assert(engine.modulePreview(0, 0, 2, -1, 0, 64, 2).empty());
    write(0, 0, "kit/subEnabled", 1);
    assert(engine.setPartPadEnabled(0, true));
    assert(engine.setPartPadEnabled(1, true));
    write(1, 2, "kit/enabled", 1);
    // Multipoint envelopes copy across engines, parts and kits without touching peers or flags.
    write(0, 0, "sub/bandwidthEnvelopeEnabled", 0);
    write(0, 0, "sub/bandwidthEnvelope/freeMode", 1);
    write(0, 0, "sub/bandwidthEnvelope/insert/0", 1);
    write(0, 0, "sub/bandwidthEnvelope/point/1/value", 23);
    write(0, 0, "sub/bandwidthEnvelope/point/1/time", 73);
    write(0, 0, "sub/bandwidthEnvelope/sustainPoint", 4);
    const auto preview = engine.modulePreview(0, 0, 2, -1, 5, 64, 1);
    assert(!preview.empty());
    const double addBefore = read(engine, 1, 2, "add/freqEnvelope/attackTime");
    const double otherKit = read(engine, 1, 0, "pad/freqEnvelope/point/1/value");
    assert(engine.copyModule(0, 0, 2, -1, 5, 1));
    assert(engine.canPasteModule(2, 1, 2));
    assert(!engine.canPasteModule(3, 0, 2));
    assert(!engine.canPasteModule(2, 5, 2));
    assert(engine.pasteModule(1, 2, 2, -1, 1, 2));
    assert(read(engine, 1, 2, "pad/freqEnvelope/point/1/value") == 23);
    assert(read(engine, 1, 2, "pad/freqEnvelope/pointCount") == 4);
    assert(read(engine, 1, 2, "pad/freqEnvelope/sustainPoint") == 4);
    assert(engine.modulePreview(1, 2, 2, -1, 1, 64, 2) == preview);
    assert(read(engine, 1, 2, "add/freqEnvelope/attackTime") == addBefore);
    assert(read(engine, 1, 0, "pad/freqEnvelope/point/1/value") == otherKit);
    assert(read(engine, 0, 0, "sub/bandwidthEnvelopeEnabled") == 0);
    // OFF/ON preserves the entire envelope.
    write(0, 0, "sub/bandwidthEnvelopeEnabled", 1);
    assert(engine.modulePreview(0, 0, 2, -1, 5, 64, 1) == preview);
    write(0, 0, "sub/bandwidthEnvelopeEnabled", 0);
    assert(engine.modulePreview(0, 0, 2, -1, 5, 64, 1) == preview);
    // Absent roles never fall back to an unrelated ADD module.
    assert(!engine.copyModule(0, 0, 3, -1, 0, 1));
    assert(!engine.copyModule(0, 0, 2, -1, 3, 1));
    assert(!engine.copyModule(0, 0, 2, 0, 0, 2));
    assert(!engine.copyModule(0, 0, 2, -1, 3, 0));
    assert(!engine.copyModule(0, 0, 2, -2, 0, 0));
    assert(engine.modulePreview(0, 0, 3, -1, 0, 64, 1).empty());
    assert(!engine.setParameter(0, 0, "sub/ampLfo/depth", 12));
    assert(!engine.setParameter(0, 0, "pad/bandwidthEnvelope/attackTime", 12));
    assert(!engine.pasteModule(0, 0, 2, 0, 0, 1));
    assert(!engine.copyModule(16, 0, 2, -1, 0, 1));
    assert(!engine.copyModule(0, 16, 2, -1, 0, 1));
    // PAD LFO -> ADD voice, and shared typed clipboard incompatibilities.
    write(0, 0, "pad/freqLfo/depth", 51);
    write(0, 0, "pad/freqLfo/waveform", 3);
    assert(engine.copyModule(0, 0, 3, -1, 1, 2));
    assert(!engine.canPasteModule(3, 0, 1));
    assert(!engine.canPasteModule(4, 0, 0));
    assert(engine.pasteModule(1, 2, 3, 4, 2, 0));
    assert(read(engine, 1, 2, "add/voice/4/filterLfo/depth") == 51);
    assert(read(engine, 1, 2, "add/voice/4/filterLfo/waveform") == 3);
    // Entire formant matrix/sequence and isolated vowel copy, including hidden entries.
    write(0, 0, "sub/filter/category", 1);
    write(0, 0, "sub/filter/formant/count", 7);
    write(0, 0, "sub/filter/formant/vowel/5/6/frequency", 91);
    write(0, 0, "sub/filter/formant/sequenceSize", 6);
    write(0, 0, "sub/filter/formant/sequence/5", 5);
    assert(engine.copyModule(0, 0, 4, -1, 0, 1));
    assert(engine.pasteModule(1, 2, 4, -1, 0, 2));
    assert(read(engine, 1, 2, "pad/filter/formant/count") == 7);
    assert(read(engine, 1, 2, "pad/filter/formant/vowel/5/6/frequency") == 91);
    assert(read(engine, 1, 2, "pad/filter/formant/sequence/5") == 5);
    assert(!engine.modulePreview(1, 2, 4, -1, 5, 64, 2).empty());
    assert(engine.copyModule(1, 2, 6, -1, 5, 2));
    assert(!engine.canPasteModule(4, 0, 0));
    write(1, 2, "add/filter/category", 1);
    const double untouchedVowel = read(engine, 1, 2, "add/filter/formant/vowel/1/6/frequency");
    assert(engine.pasteModule(1, 2, 6, -1, 0, 0));
    assert(read(engine, 1, 2, "add/filter/formant/vowel/0/6/frequency") == 91);
    assert(read(engine, 1, 2, "add/filter/formant/vowel/1/6/frequency") == untouchedVowel);
    // The ADD comb contract remains shared, category limits and hidden hpf/lpf included.
    for (const auto &prefix : {"add", "sub", "pad"}) {
        write(0, 0, std::string(prefix) + "/filter/category", 4);
        write(0, 0, std::string(prefix) + "/filter/type", 99);
        assert(read(engine, 0, 0, std::string(prefix) + "/filter/type") == 5);
        write(0, 0, std::string(prefix) + "/filter/combHpf", 21);
        write(0, 0, std::string(prefix) + "/filter/combLpf", 106);
    }
    assert(engine.copyModule(0, 0, 4, -1, 0, 2));
    assert(engine.pasteModule(1, 2, 4, -1, 0, 1));
    assert(read(engine, 1, 2, "sub/filter/combHpf") == 21);
    assert(read(engine, 1, 2, "sub/filter/combLpf") == 106);
    // PAD oscilgen and resonance use their native owners.
    assert(engine.copyModule(0, 0, 1, -1, 0, 2));
    assert(engine.pasteModule(1, 2, 1, 0, 0, 0));
    assert(!engine.modulePreview(0, 0, 8, -1, 2, 64, 2).empty());
    assert(engine.copyModule(0, 0, 5, -1, 0, 2));
    assert(engine.pasteModule(1, 2, 5, -1, 0, 0));
    assert(!engine.copyModule(0, 0, 5, -1, 0, 1));
    assert(!engine.canPasteModule(5, 0, 1));
    assert(!engine.modulePreview(0, 0, 5, -1, 0, 64, 2).empty());
    // ADD retains its existing cross-role envelope clipboard contract.
    write(0, 0, "add/voice/0/freqEnvelope/attackTime", 51);
    assert(engine.copyModule(0, 0, 2, 0, 1));
    assert(engine.pasteModule(1, 2, 2, -1, 0));
    assert(std::fabs(read(engine, 1, 2, "add/ampEnvelope/attackTime") - 51) < .01);
    // Returning from free mode reconciles points in every engine.
    for (int moduleEngine = 0; moduleEngine <= 2; ++moduleEngine) {
        const std::string prefix = std::vector<std::string>{"add", "sub", "pad"}[moduleEngine] + "/freqEnvelope";
        write(0, 0, prefix + "/freeMode", 0);
        const auto simple = engine.modulePreview(0, 0, 2, -1, 1, 64, moduleEngine);
        write(0, 0, prefix + "/freeMode", 1);
        write(0, 0, prefix + "/insert/0", 1);
        write(0, 0, prefix + "/point/1/value", 3);
        write(0, 0, prefix + "/freeMode", 0);
        assert(engine.modulePreview(0, 0, 2, -1, 1, 64, moduleEngine) == simple);
    }
    const auto snapshot = engine.parameterSnapshot(0, 0);
    assert(snapshot.find("sub/ampEnvelopeEnabled|") == std::string::npos);
    assert(snapshot.find("pad/ampEnvelopeEnabled|") == std::string::npos);
    assert(snapshot.find("pad/filterEnabled|") == std::string::npos);
    assert(snapshot.find("sub/ampLfo/") == std::string::npos);
    assert(snapshot.find("|sub|global|-1|bandwidth|envelope") != std::string::npos);
    assert(snapshot.find("|pad|global|-1|filter|lfo") != std::string::npos);
    // Engine OFF/ON keeps data, even when the engine was allocated lazily.
    write(0, 0, "sub/filterEnabled", 0);
    const double hpf = read(engine, 0, 0, "sub/filter/combHpf");
    write(0, 0, "kit/subEnabled", 0);
    write(0, 0, "kit/subEnabled", 1);
    write(0, 0, "sub/filterEnabled", 1);
    assert(read(engine, 0, 0, "sub/filter/combHpf") == hpf);
    assert(!engine.setParameter(1, 3, "kit/subEnabled", 1));
    // XIZ round trip for the edited SUB filter in an enabled engine.
    write(1, 2, "kit/subEnabled", 1);
    assert(engine.exportInstrument(1, "common-modules.xiz"));
    assert(engine.loadPresetFile("common-modules.xiz", 2));
    assert(read(engine, 2, 2, "sub/filter/combHpf") == 21);
    assert(read(engine, 2, 2, "sub/filter/combLpf") == 106);
    // Complete SUB mapping and native reset/clipboard/preview contracts.
    // PAD playing parameters are independent from ADD and sample generation.
    write(5, 0, "kit/padEnabled", 1);
    write(5, 0, "kit/addEnabled", 0);
    const std::vector<std::pair<std::string, double>> padDefaults = {
        {"volume", 90}, {"velocity", 64}, {"fadeIn", 20},
        {"punchStrength", 0}, {"punchTime", 60}, {"punchStretch", 64}, {"punchVelocity", 72},
        {"detune", 8192}, {"detuneType", 1}, {"octave", 0}, {"coarse", 0},
        {"bend", 24}, {"offsetHz", 0}, {"fixedFreq", 0}, {"fixedFreqEt", 0},
        {"filterVelocity", 0}, {"filterVelocitySense", 64},
    };
    for (const auto &[field, value] : padDefaults)
        assert(read(engine, 5, 0, "pad/" + field) == value);
    const auto padInitial = engine.parameterSnapshot(5, 0);
    for (const auto &flag : {"ampEnvelopeEnabled", "freqEnvelopeEnabled", "filterEnvelopeEnabled",
                             "ampLfoEnabled", "freqLfoEnabled", "filterLfoEnabled", "filterEnabled"}) {
        assert(padInitial.find("pad/" + std::string(flag) + "|") == std::string::npos);
        assert(!engine.setParameter(5, 0, "pad/" + std::string(flag), 1));
    }
    // Both signed components retain their neighbour's packed bits, including extremes.
    for (int octave : {-8, -1, 0, 7}) for (int coarse : {-64, -1, 0, 63}) {
        write(5, 0, "pad/octave", octave);
        write(5, 0, "pad/coarse", coarse);
        assert(read(engine, 5, 0, "pad/octave") == octave);
        assert(read(engine, 5, 0, "pad/coarse") == coarse);
        write(5, 0, "pad/octave", -1);
        assert(read(engine, 5, 0, "pad/coarse") == coarse);
    }
    const std::vector<std::pair<std::string, double>> padEdits = {
        {"volume", 113}, {"velocity", 91}, {"panning", 0}, {"fadeIn", 37},
        {"punchStrength", 82}, {"punchTime", 31}, {"punchStretch", 109}, {"punchVelocity", 19},
        {"detune", 12345}, {"detuneType", 4}, {"octave", -3}, {"coarse", -57},
        {"bend", -11}, {"offsetHz", 47}, {"fixedFreq", 1}, {"fixedFreqEt", 93},
        {"filterVelocity", 31}, {"filterVelocitySense", 112},
        {"ampEnvelope/attackTime", 53}, {"freqEnvelope/attackTime", 72}, {"filterEnvelope/attackTime", 96},
        {"ampLfo/depth", 41}, {"freqLfo/depth", 58}, {"filterLfo/depth", 69},
        {"filter/category", 4}, {"filter/type", 3}, {"filter/combHpf", 26}, {"filter/combLpf", 102},
        // Smallest quality bounds this persistence test; full production generation is lot 015.
        {"qualitySize", 0}, {"qualityOctaves", 0}, {"qualitySamplesPerOctave", 0},
    };
    const auto untouchedAdd = read(engine, 5, 0, "add/volume");
    const auto untouchedPad = read(engine, 1, 0, "pad/volume");
    for (const auto &[field, value] : padEdits) write(5, 0, "pad/" + field, value);
    for (const auto &[field, value] : padEdits)
        assert(read(engine, 5, 0, "pad/" + field) == value);
    assert(read(engine, 5, 0, "add/volume") == untouchedAdd);
    assert(read(engine, 1, 0, "pad/volume") == untouchedPad);
    // PAD envelope/LFO/filter module addressing, previews and cross-engine clipboard.
    for (int role = 0; role < 3; ++role) for (int kind : {2, 3, 4}) {
        const auto previewBefore = engine.modulePreview(5, 0, kind, -1, role, 64, 2);
        assert(!previewBefore.empty());
        assert(engine.copyModule(5, 0, kind, -1, role, 2));
        assert(engine.pasteModule(1, 2, kind, -1, role, 2));
        assert(engine.modulePreview(1, 2, kind, -1, role, 64, 2) == previewBefore);
    }
    assert(engine.exportInstrument(5, "pad-only.xiz"));
    assert(engine.loadPresetFile("pad-only.xiz", 6));
    for (const auto &[field, value] : padEdits)
        assert(read(engine, 6, 0, "pad/" + field) == value);
    assert(read(engine, 6, 0, "kit/addEnabled") == 0);
    assert(read(engine, 6, 0, "kit/padEnabled") == 1);
    write(7, 0, "kit/padEnabled", 1);
    write(7, 0, "kit/subEnabled", 1);
    write(7, 0, "part/kitMode", 1);
    write(7, 2, "kit/enabled", 1);
    write(7, 2, "kit/padEnabled", 1);
    write(7, 2, "kit/subEnabled", 1);
    write(7, 2, "kit/addEnabled", 1);
    for (int kitIndex : {0, 2}) for (const auto &[field, value] : padEdits)
        write(7, kitIndex, "pad/" + field, value);
    write(7, 2, "add/volume", 71);
    write(7, 2, "sub/volume", 81);
    assert(engine.exportInstrument(7, "pad-mixed.xiz"));
    assert(engine.loadPresetFile("pad-mixed.xiz", 8));
    for (int kitIndex : {0, 2}) for (const auto &[field, value] : padEdits)
        assert(read(engine, 8, kitIndex, "pad/" + field) == value);
    assert(read(engine, 8, 2, "add/volume") == 71);
    assert(read(engine, 8, 2, "sub/volume") == 81);
    const std::vector<std::pair<std::string, std::pair<int, int>>> padBounds = {
        {"volume", {0, 127}}, {"velocity", {0, 127}}, {"fadeIn", {0, 127}},
        {"punchStrength", {0, 127}}, {"punchTime", {0, 127}}, {"punchStretch", {0, 127}}, {"punchVelocity", {0, 127}},
        {"detune", {0, 16383}}, {"detuneType", {1, 4}}, {"octave", {-8, 7}}, {"coarse", {-64, 63}},
        {"bend", {-64, 63}}, {"offsetHz", {-64, 63}}, {"fixedFreqEt", {0, 127}},
    };
    for (const auto &[field, bounds] : padBounds) {
        write(5, 0, "pad/" + field, -99999);
        assert(read(engine, 5, 0, "pad/" + field) == bounds.first);
        write(5, 0, "pad/" + field, 99999);
        assert(read(engine, 5, 0, "pad/" + field) == bounds.second);
    }
    engine.shutdown();
    assert(engine.initialize(48000, 256));
    assert(engine.isZynReady());
    write(0, 0, "kit/subEnabled", 1);
    write(0, 0, "kit/addEnabled", 0);
    write(1, 2, "kit/enabled", 1);
    write(1, 2, "kit/subEnabled", 1);
    const std::vector<std::pair<std::string, double>> edits = {
        {"volume", 123}, {"velocity", 57}, {"panning", 0}, {"detune", 12345},
        {"detuneType", 4}, {"octave", -5}, {"coarse", -39}, {"bend", -13},
        {"offsetHz", 27}, {"fixedFreq", 1}, {"fixedFreqEt", 83}, {"bandwidth", 97},
        {"bandwidthScale", 23}, {"stages", 5}, {"magnitudeType", 3}, {"start", 2},
        {"overtone/type", 5}, {"overtone/par1", 129}, {"overtone/par2", 217}, {"overtone/par3", 145},
        {"freqEnvelopeEnabled", 0}, {"freqEnvelope/attackTime", 37},
        {"bandwidthEnvelopeEnabled", 0}, {"bandwidthEnvelope/releaseTime", 43},
        {"filterEnabled", 0}, {"filter/category", 4}, {"filter/combHpf", 33}, {"filter/combLpf", 102},
    };
    for (const auto &[field, value] : edits) {
        write(0, 0, "sub/" + field, value);
        assert(std::fabs(read(engine, 0, 0, "sub/" + field) - value) < .01);
    }
    // Packed octave/coarse bits remain independent, including both extreme signs.
    for (int octave : {-8, 7}) for (int coarse : {-64, 63}) {
        write(0, 0, "sub/octave", octave);
        write(0, 0, "sub/coarse", coarse);
        assert(read(engine, 0, 0, "sub/octave") == octave);
        assert(read(engine, 0, 0, "sub/coarse") == coarse);
    }
    write(0, 0, "sub/octave", -5);
    write(0, 0, "sub/coarse", -39);
    // Every rank, including silent harmonics' hidden widths, survives the clipboard.
    for (int h = 0; h < 64; ++h) {
        write(0, 0, "sub/harmonic/" + std::to_string(h) + "/magnitude", h % 3 == 0 ? 0 : 127 - h);
        write(0, 0, "sub/harmonic/" + std::to_string(h) + "/bandwidth", (h * 7) % 128);
    }
    const double peerVolume = read(engine, 1, 2, "sub/volume");
    const double peerBandwidth = read(engine, 1, 2, "sub/bandwidth");
    const double peerAdd = read(engine, 1, 2, "add/volume");
    assert(engine.copyModule(0, 0, 9, -1, 0, 1));
    assert(engine.canPasteModule(9, 0, 1));
    assert(!engine.canPasteModule(9, 0, 0));
    assert(!engine.canPasteModule(9, 0, 2));
    assert(!engine.canPasteModule(1, 0, 0));
    assert(!engine.copyModule(0, 0, 9, 0, 0, 1));
    assert(engine.pasteModule(1, 2, 9, -1, 0, 1));
    for (const auto &field : {"magnitudeType", "start", "overtone/type", "overtone/par1", "overtone/par2", "overtone/par3"})
        assert(read(engine, 1, 2, "sub/" + std::string(field)) == read(engine, 0, 0, "sub/" + std::string(field)));
    for (int h = 0; h < 64; ++h) for (const auto &field : {"magnitude", "bandwidth"}) {
        const std::string path = "sub/harmonic/" + std::to_string(h) + "/" + field;
        assert(read(engine, 1, 2, path) == read(engine, 0, 0, path));
    }
    assert(read(engine, 1, 2, "sub/volume") == peerVolume);
    assert(read(engine, 1, 2, "sub/bandwidth") == peerBandwidth);
    assert(read(engine, 1, 2, "add/volume") == peerAdd);
    assert(read(engine, 1, 0, "kit/subEnabled") == 0);
    // SUB-only and mixed-kit XIZ preserve all mapped fields, even envelopes/filters OFF.
    assert(engine.exportInstrument(0, "sub-only.xiz"));
    assert(engine.loadPresetFile("sub-only.xiz", 2));
    for (const auto &[field, value] : edits)
        assert(std::fabs(read(engine, 2, 0, "sub/" + field) - value) < .01);
    for (int h = 0; h < 64; ++h) for (const auto &field : {"magnitude", "bandwidth"}) {
        const std::string path = "sub/harmonic/" + std::to_string(h) + "/" + field;
        assert(read(engine, 2, 0, path) == read(engine, 0, 0, path));
    }
    assert(engine.exportInstrument(1, "sub-mixed.xiz"));
    assert(engine.loadPresetFile("sub-mixed.xiz", 3));
    assert(read(engine, 3, 2, "sub/overtone/par2") == 217);
    assert(read(engine, 3, 2, "sub/harmonic/63/bandwidth") == (63 * 7) % 128);
    // Reset follows upstream Clear; scale, start, Overtones and all other modules survive.
    write(1, 2, "sub/action/clearHarmonics", 1);
    for (int h = 0; h < 64; ++h) {
        assert(read(engine, 1, 2, "sub/harmonic/" + std::to_string(h) + "/magnitude") == (h == 0 ? 127 : 0));
        assert(read(engine, 1, 2, "sub/harmonic/" + std::to_string(h) + "/bandwidth") == 64);
    }
    assert(read(engine, 1, 2, "sub/magnitudeType") == 3);
    assert(read(engine, 1, 2, "sub/overtone/par2") == 217);
    assert(read(engine, 1, 2, "sub/volume") == peerVolume);
    // Preview triples are generated by the native converters, not Compose approximations.
    auto bands = [&]() {
        std::vector<float> result;
        std::istringstream stream(engine.modulePreview(1, 2, 9, -1, 0, 64, 1));
        std::string field;
        while (std::getline(stream, field, ',')) result.push_back(std::stof(field));
        assert(result.size() == 64 * 3);
        for (float value : result) assert(std::isfinite(value));
        return result;
    };
    write(1, 2, "sub/overtone/type", 0);
    auto initialBands = bands();
    for (int h = 0; h < 64; ++h) assert(std::fabs(initialBands[h * 3] - (h + 1)) < .001);
    assert(initialBands[1] == 1);
    assert(initialBands[4] == 0); // zero stays silent in logarithmic modes
    for (int mode = 1; mode <= 7; ++mode) {
        write(1, 2, "sub/overtone/type", mode);
        write(1, 2, "sub/overtone/par3", 255);
        const auto forced = bands();
        for (int h = 0; h < 64; ++h) assert(std::fabs(forced[h * 3] - std::round(forced[h * 3])) < .001);
    }
    write(1, 2, "sub/overtone/type", 0);
    write(1, 2, "sub/harmonic/1/magnitude", 64);
    for (int scale = 0; scale <= 4; ++scale) {
        write(1, 2, "sub/magnitudeType", scale);
        const float expected = scale == 0 ? 64.0f / 127 : std::exp((1 - 64.0f / 127) * std::log(std::pow(10.0f, -scale - 1)));
        assert(std::fabs(bands()[4] - expected) < .00001);
    }
    write(1, 2, "sub/harmonic/1/bandwidth", 0);
    const float narrow = bands()[5];
    write(1, 2, "sub/harmonic/1/bandwidth", 127);
    assert(bands()[5] > narrow);
    // Clamping and metadata/default values follow the native contract.
    write(1, 2, "sub/volume", 999);
    write(1, 2, "sub/detuneType", 0);
    write(1, 2, "sub/overtone/par1", 999);
    assert(read(engine, 1, 2, "sub/volume") == 127);
    assert(read(engine, 1, 2, "sub/detuneType") == 1);
    assert(read(engine, 1, 2, "sub/overtone/par1") == 255);
    const auto subSnapshot = engine.parameterSnapshot(1, 2);
    assert(subSnapshot.find("|sub|global|-1|bandwidth|basic") != std::string::npos);
    assert(subSnapshot.find("|sub|global|-1|harmonics|harmonic") != std::string::npos);
    // Loading a sparse ADD instrument must not inherit the prior SUB parameters.
    assert(engine.exportInstrument(4, "add-sparse.xiz"));
    assert(engine.loadPresetFile("add-sparse.xiz", 2));
    assert(read(engine, 2, 0, "sub/volume") == 96);
    assert(read(engine, 2, 0, "sub/overtone/type") == 0);
    assert(read(engine, 2, 0, "sub/harmonic/63/bandwidth") == 64);
    // Preview reference follows a nominal MIDI note without mutating the instrument.
    write(1, 2, "sub/bandwidthScale", 64);
    const auto unchanged = engine.parameterSnapshot(1, 2);
    auto noteBands = [&](int note) {
        std::vector<float> result;
        std::istringstream stream(engine.modulePreview(1, 2, 9, -1, 0, 64, 1, note));
        std::string field;
        while (std::getline(stream, field, ',')) result.push_back(std::stof(field));
        assert(result.size() == 192);
        return result;
    };
    assert(noteBands(69) == noteBands(81)); // neutral frequency scaling
    assert(engine.modulePreview(1, 2, 9, -1, 0, 64, 1) ==
        engine.modulePreview(1, 2, 9, -1, 0, 64, 1, 69));
    assert(engine.parameterSnapshot(1, 2) == unchanged);
    write(1, 2, "sub/bandwidthScale", 0);
    const auto referenceA4 = noteBands(69);
    const auto referenceA5 = noteBands(81);
    assert(std::fabs(referenceA5[2] / referenceA4[2] - 8.0f) < .001f);
    assert(referenceA5[0] == referenceA4[0]); // frequency ratios independent of reference
    assert(referenceA5[1] == referenceA4[1]); // magnitudes independent of reference
    for (int note : {0, 127}) for (float value : noteBands(note)) assert(std::isfinite(value));
    assert(engine.modulePreview(1, 2, 9, -1, 0, 64, 1, -1).empty());
    assert(engine.modulePreview(1, 2, 9, -1, 0, 64, 1, 128).empty());
    // Profile and positions preserve hidden values and typed ownership.
    write(9, 0, "kit/padEnabled", 1);
    const std::vector<std::pair<std::string, double>> structureEdits = {
        {"profile/baseType", 2}, {"profile/baseParameter", 91}, {"profileWidth", 69},
        {"profile/side", 1}, {"profileAutoScale", 0}, {"profile/frequencyMultiplier", 17},
        {"profile/modulatorAmount", 35}, {"profile/modulatorFrequency", 44},
        {"profile/amplitudeType", 0}, {"profile/amplitudeMode", 3},
        {"profile/amplitudeParameter1", 83}, {"profile/amplitudeParameter2", 115},
        {"overtone/type", 5}, {"overtone/par1", 131}, {"overtone/par2", 213}, {"overtone/par3", 93},
        {"qualityBaseNote", 9}, {"bandwidthScale", 7},
        {"osc/baseFunction", 3}, {"osc/harmonic/2/magnitude", 91}, {"osc/harmonic/2/phase", 13},
        {"resonance/enabled", 1}, {"resonance/point/17", 94},
        {"qualitySize", 0}, {"qualityOctaves", 0}, {"qualitySamplesPerOctave", 0},
    };
    for (const auto &[field, value] : structureEdits) write(9, 0, "pad/" + field, value);
    const auto profileCurve = engine.modulePreview(9, 0, 10, -1, 0, 512, 2);
    const auto overtoneCurve = engine.modulePreview(9, 0, 11, -1, 0, 64, 2);
    assert(!profileCurve.empty() && !overtoneCurve.empty());
    assert(engine.modulePreview(9, 0, 10, 0, 0, 512, 2).empty());
    assert(engine.modulePreview(9, 0, 10, -1, 0, 512, 0).empty());
    assert(engine.copyModule(9, 0, 10, -1, 0, 2));
    assert(!engine.canPasteModule(11, 0, 2));
    assert(engine.pasteModule(1, 2, 10, -1, 0, 2));
    assert(engine.modulePreview(1, 2, 10, -1, 0, 512, 2) == profileCurve);
    assert(read(engine, 1, 2, "pad/profile/amplitudeParameter2") == 115);
    assert(read(engine, 1, 2, "pad/volume") != 113);
    assert(engine.copyModule(9, 0, 11, -1, 0, 2));
    assert(engine.pasteModule(1, 2, 11, -1, 0, 2));
    assert(engine.modulePreview(1, 2, 11, -1, 0, 64, 2) == overtoneCurve);
    write(9, 0, "pad/osc/action/useAsBase", 1);
    assert(engine.copyModule(9, 0, 1, -1, 0, 2));
    assert(engine.pasteModule(1, 2, 1, -1, 0, 2));
    const auto customBase = engine.modulePreview(9, 0, 8, -1, 0, 256, 2);
    assertPreviewNear(customBase, engine.modulePreview(1, 2, 8, -1, 0, 256, 2));
    write(9, 0, "kit/addEnabled", 0);
    write(9, 0, "pad/mode", 2);
    assert(engine.exportInstrument(9, "pad-structure.xiz"));
    assert(engine.loadPresetFile("pad-structure.xiz", 10));
    for (const auto &[field, value] : structureEdits) {
        if (field.rfind("osc/", 0) != 0) assert(read(engine, 10, 0, "pad/" + field) == value);
    }
    assertPreviewNear(customBase, engine.modulePreview(10, 0, 8, -1, 0, 256, 2));
    for (int type = 0; type <= 7; ++type) {
        write(10, 0, "pad/overtone/type", type);
        write(10, 0, "pad/overtone/par3", 255);
        std::istringstream stream(engine.modulePreview(10, 0, 11, -1, 0, 64, 2));
        std::string field; int n = 1;
        while (std::getline(stream, field, ',')) {
            const auto value = std::stof(field);
            assert(std::isfinite(value) && std::fabs(value - std::round(value)) < .001);
            if (!type) assert(std::fabs(value - n) < .001);
            n++;
        }
        assert(n == 65);
    }
    // A completed isolated bank is committed only at an audio boundary.
    write(11, 0, "kit/padEnabled", 1);
    write(11, 0, "kit/addEnabled", 0);
    write(11, 0, "pad/qualitySize", 0);
    write(11, 0, "pad/qualityOctaves", 0);
    write(11, 0, "pad/qualitySamplesPerOctave", 0);
    write(11, 0, "pad/ampEnvelope/releaseTime", 0);
    assert(engine.startPadGeneration(11, 0));
    for (int n = 0; n < 10000 && engine.padGenerationStatus(11, 0).rfind("generating|", 0) == 0; ++n)
        std::this_thread::sleep_for(std::chrono::milliseconds(1));
    assert(engine.padGenerationStatus(11, 0).rfind("ready|", 0) == 0);
    std::vector<float> pcm(512);
    engine.renderInterleavedFloat(pcm.data(), 256, 2, 48000);
    assert(engine.padGenerationStatus(11, 0).rfind("done|", 0) == 0);
    write(11, 0, "part/enabled", 1);
    write(11, 0, "part/channel", 11);
    engine.noteOn(11, 69, 100);
    engine.renderInterleavedFloat(pcm.data(), 256, 2, 48000);
    write(11, 0, "pad/bandwidth", 601);
    assert(engine.startPadGeneration(11, 0));
    for (int n = 0; n < 10000 && engine.padGenerationStatus(11, 0).rfind("generating|", 0) == 0; ++n)
        std::this_thread::sleep_for(std::chrono::milliseconds(1));
    engine.renderInterleavedFloat(pcm.data(), 256, 2, 48000);
    assert(engine.padGenerationStatus(11, 0).rfind("ready|", 0) == 0); // held note owns old PCM
    engine.noteOff(11, 69);
    for (int n = 0; n < 1000 && engine.padGenerationStatus(11, 0).rfind("done|", 0) != 0; ++n)
        engine.renderInterleavedFloat(pcm.data(), 256, 2, 48000);
    assert(engine.padGenerationStatus(11, 0).rfind("done|", 0) == 0);
    write(11, 0, "pad/qualitySize", 6);
    write(11, 0, "pad/qualityOctaves", 7);
    write(11, 0, "pad/qualitySamplesPerOctave", 6);
    assert(!engine.startPadGeneration(11, 0));
    assert(engine.padGenerationStatus(11, 0).rfind("failed|", 0) == 0);
    write(11, 0, "pad/qualitySize", 0);
    write(11, 0, "pad/qualityOctaves", 0);
    write(11, 0, "pad/qualitySamplesPerOctave", 0);
    assert(engine.startPadGeneration(11, 0));
    write(11, 0, "pad/bandwidth", 777);
    engine.renderInterleavedFloat(pcm.data(), 256, 2, 48000);
    assert(engine.padGenerationStatus(11, 0).rfind("done|", 0) != 0);
    engine.cancelPadGeneration();
    assert(engine.loadPresetFile("pad-structure.xiz", 11)); // joins interrupted worker before deleting owners
    assert(engine.padGenerationStatus(11, 0).rfind("done|", 0) == 0);
    // Removing/recreating a kit invalidates the job even if a new owner's revision matches.
    write(11, 2, "kit/enabled", 1);
    write(11, 2, "kit/padEnabled", 1);
    write(11, 2, "pad/qualitySize", 0);
    write(11, 2, "pad/qualityOctaves", 0);
    write(11, 2, "pad/qualitySamplesPerOctave", 0);
    assert(engine.startPadGeneration(11, 2));
    write(11, 2, "kit/enabled", 0);
    write(11, 2, "kit/enabled", 1);
    engine.servicePadGeneration();
    assert(engine.padGenerationStatus(11, 2).rfind("done|", 0) != 0);
    assert(read(engine, 11, 2, "pad/qualitySize") == 3);
    engine.shutdown();
    std::cout << "Common ADD/SUB/PAD native modules: PASS\n";
}

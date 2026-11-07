#include "ZynAndroidEngine.h"
#include <cassert>
#include <cmath>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>

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
    engine.shutdown();
    std::cout << "Common ADD/SUB/PAD native modules: PASS\n";
}

#pragma once
#include "Misc/XMLwrapper.h"
#include <limits>

static double readFx(ZynAndroidEngine &engine, int scope, int part, int slot, const std::string &path, int field = 4) {
    std::istringstream lines(engine.fxSnapshot(scope, part, slot));
    std::string line;
    while (std::getline(lines, line)) {
        std::istringstream columns(line);
        std::string column;
        std::getline(columns, column, '|');
        if (column != path) continue;
        for (int n = 1; n <= field; ++n) assert(bool(std::getline(columns, column, '|')));
        return std::stod(column);
    }
    std::cerr << "Missing FX parameter " << scope << ':' << part << ':' << slot << ':' << path << '\n';
    std::abort();
}

static void testFxContract(ZynAndroidEngine &engine) {
    assert(engine.fxSnapshot(-1, 0, 0).empty());
    assert(engine.fxSnapshot(0, -1, 0).empty());
    assert(engine.fxSnapshot(0, 16, 0).empty());
    assert(engine.fxSnapshot(0, 0, 3).empty());
    assert(engine.fxSnapshot(1, 0, 0).empty()); // Master has no Part owner.
    assert(engine.fxSnapshot(1, -1, 8).empty());
    assert(engine.fxSnapshot(2, -1, 4).empty());
    auto write = [&](int scope, int part, int slot, const std::string &path, double value) {
        const int type = readFx(engine, scope, part, slot, "fx/type");
        if (!engine.writeFx(scope, part, slot, type, path, value)) {
            std::cerr << "FX write rejected " << scope << ':' << part << ':' << slot << ':' << path << '=' << value << '\n';
            std::abort();
        }
    };
    // Source is available on empty Insert slots and is owned by Master, never a kit.
    write(1, -1, 6, "fx/source", 17);
    assert(readFx(engine, 1, -1, 6, "fx/source") == 17);
    assert(readFx(engine, 1, -1, 6, "@", 4) == 15);
    write(1, -1, 6, "fx/source", 1);
    assert(readFx(engine, 1, -1, 6, "@", 4) == -2);
    write(1, -1, 6, "fx/source", 0);
    assert(readFx(engine, 1, -1, 6, "@", 4) == -1);
    assert(readFx(engine, 1, -1, 7, "fx/source") == 0);
    assert(!engine.writeFx(1, -1, 6, 0, "fx/source", 18));
    assert(!engine.writeFx(1, -1, 6, 0, "fx/source", -1));
    assert(!engine.writeFx(0, 0, 0, 0, "fx/source", 1));
    assert(!engine.writeFx(2, -1, 0, 0, "fx/source", 1));
    // System sends are forward-only, available on Off slots, and isolated from Part/Insert.
    for (int source = 0; source < 4; ++source) {
        for (int destination = source + 1; destination < 4; ++destination) {
            const auto path = "fx/send/" + std::to_string(destination);
            write(2, -1, source, path, 127);
            assert(readFx(engine, 2, -1, source, path) == 127);
            write(2, -1, source, path, 0);
            assert(readFx(engine, 2, -1, source, path) == 0);
        }
        for (const auto &path : {"fx/send/" + std::to_string(source), std::string("fx/send/4"), std::string("fx/send/bad")})
            assert(!engine.writeFx(2, -1, source, 0, path, 1));
    }
    assert(!engine.writeFx(2, -1, 2, 0, "fx/send/0", 1));
    assert(!engine.writeFx(0, 0, 0, 0, "fx/send/1", 1));
    assert(!engine.writeFx(1, -1, 0, 0, "fx/send/1", 1));
    assert(!engine.writeFx(2, -1, 0, 0, "fx/send/1", 128));
    assert(!engine.writeFx(2, -1, 0, 0, "fx/send/1", -1));
    write(2, -1, 0, "fx/send/2", 83);
    assert(readFx(engine, 2, -1, 0, "fx/send/1") == 0);
    assert(readFx(engine, 2, -1, 1, "fx/send/2") == 0);
    for (int route = 0; route <= 2; ++route) {
        write(0, 1, 0, "fx/destination", route);
        assert(readFx(engine, 0, 1, 0, "fx/destination") == route);
    }
    assert(readFx(engine, 0, 1, 1, "fx/destination") == 0);
    assert(readFx(engine, 0, 0, 0, "fx/destination") == 0);
    assert(!engine.writeFx(0, 1, 0, 0, "fx/destination", 3));
    assert(!engine.writeFx(0, 1, 2, 0, "fx/destination", 1));
    assert(!engine.writeFx(1, -1, 0, 0, "fx/destination", 1));
    assert(!engine.writeFx(2, -1, 0, 0, "fx/destination", 1));
    assert(engine.setParameter(1, 0, "part/kitMode", 1));
    assert(engine.setParameter(1, 0, "kit/sendFx", 3));
    assert(read(engine, 1, 0, "kit/sendFx") == 3);
    assert(read(engine, 0, 0, "kit/sendFx") == 0);
    write(0, 0, 1, "fx/type", 2);
    write(0, 0, 1, "fx/parameter/5", 33);
    // Exercise each real effect and every advertised field at both bounds in all owners.
    for (int scope = 0; scope < 3; ++scope) {
        const int part = scope ? -1 : 0;
        for (int type = 1; type <= 10; ++type) {
            std::cerr << "FX contract scope=" << scope << " type=" << type << std::endl;
            write(scope, part, 0, "fx/type", type);
            assert(readFx(engine, scope, part, 0, "fx/type") == type);
            const int lastPreset = readFx(engine, scope, part, 0, "fx/preset", 6);
            for (int preset = 0; preset <= lastPreset; ++preset) {
                write(scope, part, 0, "fx/preset", preset);
                assert(readFx(engine, scope, part, 0, "fx/preset") == preset);
                write(scope, part, 0, "fx/parameter/0", 17);
                assert(readFx(engine, scope, part, 0, "fx/parameter/0") == 17);
            }
            write(scope, part, 0, "fx/preset", 0);
            std::vector<std::pair<std::string, std::pair<double, double>>> fields;
            std::istringstream lines(engine.fxSnapshot(scope, part, 0));
            std::string line;
            while (std::getline(lines, line)) {
                std::vector<std::string> columns;
                std::istringstream input(line); std::string column;
                while (std::getline(input, column, '|')) columns.push_back(column);
                if (columns.size() >= 9 && columns[0].rfind("fx/parameter/", 0) == 0)
                    fields.push_back({columns[0], {std::stod(columns[5]), std::stod(columns[6])}});
            }
            for (const auto &[path, bounds] : fields) {
                for (double value : {bounds.first, bounds.second}) {
                    write(scope, part, 0, path, value);
                    if (readFx(engine, scope, part, 0, path) != value) {
                        std::cerr << "FX roundtrip mismatch type=" << type << " scope=" << scope << " path=" << path << " wrote=" << value << " read=" << readFx(engine, scope, part, 0, path) << '\n';
                        std::abort();
                    }
                }
            }
            assert(!engine.writeFx(scope, part, 0, type, "fx/parameter/bad", 1));
            assert(!engine.writeFx(scope, part, 0, type, "fx/parameter/99999999999999", 1));
            assert(!engine.writeFx(scope, part, 0, type, "fx/parameter/0", std::numeric_limits<double>::quiet_NaN()));
            assert(!engine.writeFx(scope, part, 0, type == 1 ? 2 : 1, "fx/parameter/0", 17));
            write(scope, part, 0, "fx/preset", 0);
            const auto defaultVolume = readFx(engine, scope, part, 0, "fx/parameter/0");
            write(scope, part, 0, "fx/parameter/0", 13);
            assert(engine.fxAction(scope, part, 0, type, "reset"));
            assert(readFx(engine, scope, part, 0, "fx/parameter/0") == defaultVolume);
            assert(engine.fxAction(scope, part, 0, type, "cleanup"));
            if (type == 7) {
                const auto flat = engine.fxPreview(scope, part, 0, 0, 128);
                write(scope, part, 0, "fx/preset", 1);
                assert(engine.fxPreview(scope, part, 0, 0, 128) == flat);
                std::vector<std::string> curves{flat};
                for (int preset = 2; preset <= lastPreset; ++preset) {
                    write(scope, part, 0, "fx/preset", preset);
                    const auto curve = engine.fxPreview(scope, part, 0, 0, 128);
                    assert(!curve.empty());
                    for (const auto &previous : curves) assert(curve != previous);
                    curves.push_back(curve);
                    write(scope, part, 0, "fx/parameter/10", 0);
                    assert(engine.fxAction(scope, part, 0, type, "reset"));
                    assert(engine.fxPreview(scope, part, 0, 0, 128) == curve);
                }
                write(scope, part, 0, "fx/preset", 0);
                write(scope, part, 0, "fx/parameter/10", 7);
                write(scope, part, 0, "fx/parameter/12", 96);
                const auto boosted = engine.fxPreview(scope, part, 0, 0, 128);
                assert(!boosted.empty() && flat != boosted);
                // Frequency changes must update the response without running the audio renderer.
                write(scope, part, 0, "fx/parameter/11", 100);
                const auto shifted = engine.fxPreview(scope, part, 0, 0, 128);
                assert(!shifted.empty() && boosted != shifted);
                write(scope, part, 0, "fx/parameter/11", 64);
                assert(engine.fxPreview(scope, part, 0, 0, 128) == boosted);
            }
            if (type == 8) {
                const double addCutoff = read(engine, 0, 0, "add/filter/cutoff");
                write(scope, part, 0, "fx/filter/cutoff", 27);
                write(scope, part, 0, "fx/filter/category", 1);
                write(scope, part, 0, "fx/filter/formant/vowel/2/1/frequency", 97);
                assert(read(engine, 0, 0, "add/filter/cutoff") == addCutoff);
                assert(!engine.writeFx(scope, part, 0, 8, "fx/filter/formant/vowel/bad/0/frequency", 77));
                assert(!engine.writeFx(scope, part, 0, 8, "fx/filter/formant/sequence/bad", 4));
                assert(engine.fxAction(scope, part, 0, 8, "copyVowel", 2));
                assert(engine.fxAction(scope, part, 0, 8, "pasteVowel", 4));
                assert(readFx(engine, scope, part, 0, "fx/filter/formant/vowel/4/1/frequency") == 97);
                assert(!engine.fxAction(scope, part, 0, 8, "paste"));
                assert(engine.fxAction(scope, part, 0, 8, "copyFilter"));
                assert(engine.pasteModule(0, 0, 4, -1, 0, 0));
                assert(read(engine, 0, 0, "add/filter/cutoff") == 27);
                assert(engine.copyModule(0, 0, 4, -1, 0, 0));
                assert(engine.fxAction(scope, part, 0, 8, "pasteFilter"));
                assert(!engine.fxPreview(scope, part, 0, 4, 128).empty());
                for (int category : {0, 2, 3, 4}) {
                    write(scope, part, 0, "fx/filter/category", category);
                    const auto response = engine.fxPreview(scope, part, 0, 0, 32);
                    assert(!response.empty());
                    std::istringstream samples(response); std::string sample;
                    while (std::getline(samples, sample, ',')) assert(std::isfinite(std::stof(sample)));
                }
                write(scope, part, 0, "fx/filter/category", 1);
            }
            if (type == 2 || type == 3 || type == 4 || type == 5 || type == 8 || type == 10) {
                write(scope, part, 0, "fx/numerator", 1);
                write(scope, part, 0, "fx/denominator", 4);
                assert(!engine.writeFx(scope, part, 0, type, "fx/parameter/2", 12));
                const double bpm = readFx(engine, scope, part, 0, "@", 5);
                const double seconds = 60.0 / bpm;
                const double encoded = type == 2 ? seconds * 127.0 / 1.5 : type == 10 ? seconds * 128.0 / 4.0 - 1.0
                    : std::log2((bpm / 60.0) / .03 + 1.0) * 12.7;
                const double expected = std::clamp(std::round(encoded), 0.0, 127.0);
                assert(readFx(engine, scope, part, 0, "fx/parameter/2") == expected);
                write(scope, part, 0, "fx/preset", 0);
                assert(readFx(engine, scope, part, 0, "fx/parameter/2") == expected);
                write(scope, part, 0, "fx/numerator", 0);
                write(scope, part, 0, "fx/parameter/2", 23);
            }
            // Effect XML roundtrip into a different owner for every type and scope.
            const int targetScope = (scope + 1) % 3;
            const int targetPart = targetScope == 0 ? 4 : -1;
            const int targetSlot = targetScope == 0 ? 2 : targetScope == 1 ? 7 : 3;
            assert(engine.fxAction(scope, part, 0, type, "copy"));
            const int targetType = readFx(engine, targetScope, targetPart, targetSlot, "fx/type");
            assert(engine.fxAction(targetScope, targetPart, targetSlot, targetType, "paste"));
            assert(readFx(engine, targetScope, targetPart, targetSlot, "fx/type") == type);
            for (const auto &[path, bounds] : fields) {
                const auto sourceValue = readFx(engine, scope, part, 0, path);
                const auto targetValue = readFx(engine, targetScope, targetPart, targetSlot, path);
                if (sourceValue != targetValue) {
                    std::cerr << "FX clipboard type=" << type << " source scope=" << scope << " target scope=" << targetScope
                              << " path=" << path << " source=" << sourceValue << " target=" << targetValue << std::endl;
                    std::abort();
                }
            }
            write(targetScope, targetPart, targetSlot, "fx/type", 0);
            assert(readFx(engine, 0, 0, 1, "fx/parameter/5") == 33);
            write(scope, part, 0, "fx/type", 0);
            assert(readFx(engine, scope, part, 0, "fx/type") == 0);
            assert(!engine.writeFx(scope, part, 0, 0, "fx/parameter/0", 1));
        }
    }
    std::cerr << "FX clipboard" << std::endl;
    // Whole-effect clipboard across owners, including Dynamic Filter/Formant, without bypass/routing.
    write(0, 1, 2, "fx/type", 8);
    write(0, 1, 2, "fx/preset", 2);
    write(0, 1, 2, "fx/bypass", 1);
    write(0, 1, 2, "fx/filter/category", 1);
    write(0, 1, 2, "fx/filter/formant/vowel/1/2/frequency", 93);
    write(0, 1, 2, "fx/parameter/6", 101);
    assert(engine.fxAction(0, 1, 2, 8, "copy"));
    write(1, -1, 7, "fx/source", 1);
    assert(engine.fxAction(1, -1, 7, 0, "paste"));
    assert(readFx(engine, 1, -1, 7, "fx/source") == 1);
    assert(readFx(engine, 1, -1, 7, "fx/type") == 8);
    assert(readFx(engine, 1, -1, 7, "fx/parameter/6") == 101);
    assert(readFx(engine, 1, -1, 7, "fx/filter/formant/vowel/1/2/frequency") == 93);
    assert(engine.writeFx(1, -1, 7, 8, "fx/bypass", 1));
    assert(readFx(engine, 1, -1, 7, "fx/bypass") == 1);
    assert(engine.fxAction(1, -1, 7, 8, "copy"));
    assert(engine.fxAction(2, -1, 3, 0, "paste"));
    assert(readFx(engine, 2, -1, 3, "fx/bypass") == 0);
    write(0, 1, 0, "fx/type", 7);
    write(0, 1, 0, "fx/preset", 3);
    write(0, 1, 0, "fx/parameter/12", 74); // Preserve edits relative to the selected preset.
    const auto warmEdited = engine.fxPreview(0, 1, 0, 0, 128);
    write(1, -1, 6, "fx/type", 7);
    write(1, -1, 6, "fx/preset", 5);
    const auto telephone = engine.fxPreview(1, -1, 6, 0, 128);
    std::cerr << "FX export" << std::endl;
    assert(engine.exportInstrument(1, "fx-part.xiz"));
    assert(engine.loadPresetFile("fx-part.xiz", 2));
    assert(readFx(engine, 0, 2, 0, "fx/destination") == 2);
    assert(readFx(engine, 0, 2, 0, "fx/preset") == 3);
    assert(engine.fxPreview(0, 2, 0, 0, 128) == warmEdited);
    assert(read(engine, 2, 0, "kit/sendFx") == 3);
    assert(readFx(engine, 0, 2, 2, "fx/type") == 8);
    assert(readFx(engine, 0, 2, 2, "fx/preset") == 2);
    assert(readFx(engine, 0, 2, 2, "fx/bypass") == 1);
    assert(readFx(engine, 0, 2, 2, "fx/filter/formant/vowel/1/2/frequency") == 93);
    int compression = 0;
    std::cerr << "FX capture session" << std::endl;
    auto session = engine.captureSession(compression);
    assert(session && session->saveXMLfile("fx-session.xmz", compression) == 0);
    write(1, -1, 7, "fx/type", 0);
    write(2, -1, 3, "fx/type", 0);
    std::cerr << "FX load session" << std::endl;
    assert(engine.loadMasterXml("fx-session.xmz"));
    assert(readFx(engine, 1, -1, 6, "fx/preset") == 5);
    assert(engine.fxPreview(1, -1, 6, 0, 128) == telephone);
    assert(readFx(engine, 1, -1, 7, "fx/type") == 8);
    assert(readFx(engine, 2, -1, 0, "fx/send/2") == 83);
    assert(readFx(engine, 1, -1, 7, "fx/preset") == 2);
    assert(readFx(engine, 1, -1, 7, "fx/source") == 1);
    assert(readFx(engine, 1, -1, 7, "fx/bypass") == 1);
    assert(readFx(engine, 2, -1, 3, "fx/filter/formant/vowel/1/2/frequency") == 93);
    std::cerr << "FX concurrency" << std::endl;
    // Renderer exclusion: edits, previews and allocations never overlap the shared RT allocator.
    std::atomic<bool> done{false};
    std::thread renderer([&] {
        std::vector<float> pcm(512);
        while (!done.load()) engine.renderInterleavedFloat(pcm.data(), 256, 2, 48000);
    });
    for (int n = 0; n < 20; ++n) {
        write(0, 3, 0, "fx/type", n % 10 + 1);
        engine.fxPreview(0, 3, 0, 0, 32);
    }
    done.store(true);
    renderer.join();
    // Performance commands share the existing audio queue and target the keyboard channel.
    assert(engine.setPartEnabled(0, true));
    assert(engine.setPartReceiveChannel(0, 0));
    assert(engine.setPartEnabled(1, true));
    assert(engine.setPartReceiveChannel(1, 1));
    auto performance = [&](int channel, int field) {
        std::istringstream input(engine.performanceSnapshot(channel)); std::string item;
        for (int n = 0; n <= field; ++n) assert(bool(std::getline(input, item, '|')));
        return std::stoi(item);
    };
    const auto otherModulation = performance(1, 2);
    assert(engine.setPerformanceControl(0, 1, -8192));
    assert(engine.setPerformanceControl(0, 2, 107));
    assert(engine.setPerformanceControl(0, 3, 127));
    assert(performance(0, 1) == -8192);
    assert(performance(0, 2) == 107);
    assert(performance(0, 3) == 1);
    assert(performance(1, 2) == otherModulation);
    assert(engine.setPerformanceControl(0, 1, 0));
    assert(engine.setPerformanceControl(0, 3, 0));
    assert(performance(0, 1) == 0 && performance(0, 3) == 0);
    assert(!engine.setPerformanceControl(16, 2, 1));
    assert(!engine.setPerformanceControl(0, 1, 8192));
    assert(!engine.setPerformanceControl(0, 2, 128));
    assert(!engine.setPerformanceControl(0, 0, 0));
    assert(!engine.setPerformanceControl(0, 4, 0));
    write(0, 0, 0, "fx/type", 2);
    write(0, 0, 0, "fx/numerator", 1);
    write(0, 0, 0, "fx/denominator", 4);
    assert(engine.setPerformanceControl(0, 0, 90));
    assert(performance(0, 0) == 90);
    assert(readFx(engine, 0, 0, 0, "fx/parameter/2") == 56);
    auto performanceSession = engine.captureSession(compression);
    assert(performanceSession->saveXMLfile("performance.xmz", compression) == 0);
    assert(engine.setPerformanceControl(0, 0, 160));
    assert(performance(0, 0) == 160);
    assert(engine.loadMasterXml("performance.xmz"));
    assert(performance(0, 0) == 90);
    assert(readFx(engine, 0, 0, 0, "fx/parameter/2") == 56);
    std::cout << "Performance tempo/controllers/channel isolation/session: PASS\n";
    std::cout << "FX slot/parameters/clipboard/previews/XML/concurrency: PASS\n";
}

#include <set>
#include <string>

#include <rtosc/ports.h>

#include "Nio/Nio.h"
#include "Misc/PresetExtractor.h"
#include "Misc/WavFile.h"

namespace zyn {

// Upstream globals sometimes expected by partially integrated translation units.
// Keep this file as the central place for temporary compatibility shims.

namespace Nio {

bool autoConnect = false;
bool pidInClientName = false;
std::string defaultSource;
std::string defaultSink;

void init(const SYNTH_T &, const oss_devs_t &, Master *) {}
bool start(void) { return true; }
void stop(void) {}

void setDefaultSource(std::string name) { defaultSource = std::move(name); }
void setDefaultSink(std::string name) { defaultSink = std::move(name); }

bool setSource(std::string name) { defaultSource = std::move(name); return true; }
bool setSink(std::string name) { defaultSink = std::move(name); return true; }

void setPostfix(std::string) {}
std::string getPostfix(void) { return {}; }

std::set<std::string> getSources(void) { return {}; }
std::set<std::string> getSinks(void) { return {}; }

std::string getSource(void) { return defaultSource; }
std::string getSink(void) { return defaultSink; }

void preferredSampleRate(unsigned &) {}
void masterSwap(Master *) {}

void waveNew(class WavFile *) {}
void waveStart(void) {}
void waveStop(void) {}
void waveEnd(void) {}

void setAudioCompressor(bool) {}
bool getAudioCompressor(void) { return false; }

} // namespace Nio

extern const rtosc::Ports real_preset_ports = {};
extern const rtosc::Ports preset_ports = {};
extern const rtosc::Ports bankPorts = {};

Clipboard clipboardCopy(MiddleWare &, std::string)
{
    return {};
}

void presetCopy(MiddleWare &, std::string, std::string) {}
void presetPaste(MiddleWare &, std::string, std::string) {}
void presetCopyArray(MiddleWare &, std::string, int, std::string) {}
void presetPasteArray(MiddleWare &, std::string, int, std::string) {}
void presetPaste(std::string, int) {}
void presetDelete(int) {}
void presetRescan() {}
std::string presetClipboardType() { return {}; }
bool presetCheckClipboardType() { return false; }

} // namespace zyn

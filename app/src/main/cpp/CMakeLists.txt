cmake_minimum_required(VERSION 3.22.1)

project(zynbridge LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(ZYN_FFT_BACKEND "LIGHT_SHIM" CACHE STRING "FFT backend for Zyn port (LIGHT_SHIM, PROD_CANDIDATE, FFTW3F_NATIVE)")
set_property(CACHE ZYN_FFT_BACKEND PROPERTY STRINGS LIGHT_SHIM PROD_CANDIDATE FFTW3F_NATIVE)

get_filename_component(REPO_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../.." ABSOLUTE)
set(ZYN_UPSTREAM_DIR "${REPO_ROOT}/third_party/zynaddsubfx")
set(ZYN_RTOSC_INCLUDE_DIR "${ZYN_UPSTREAM_DIR}/rtosc/include")
set(ZYN_SRC_DIR "${ZYN_UPSTREAM_DIR}/src")
set(MXML_DIR "${REPO_ROOT}/third_party/mxml")
set(FFTW3_DIR "${REPO_ROOT}/third_party/fftw3")
set(ZYN_GENERATED_DIR "${CMAKE_CURRENT_BINARY_DIR}/zyn_generated")
set(RTOSC_GENERATED_DIR "${CMAKE_CURRENT_BINARY_DIR}/rtosc_generated")
set(MXML_GENERATED_DIR "${CMAKE_CURRENT_BINARY_DIR}/mxml_generated")

file(MAKE_DIRECTORY "${ZYN_GENERATED_DIR}")
file(MAKE_DIRECTORY "${RTOSC_GENERATED_DIR}/cpp")
file(MAKE_DIRECTORY "${MXML_GENERATED_DIR}")
set(VERSION_MAJOR 3)
set(VERSION_MINOR 0)
set(VERSION_REVISION 7)
set(ZynFusionDir "")
configure_file("${ZYN_SRC_DIR}/zyn-version.h.in" "${ZYN_GENERATED_DIR}/zyn-version.h")
configure_file("${ZYN_SRC_DIR}/zyn-config.h.in" "${ZYN_GENERATED_DIR}/zyn-config.h" @ONLY)

set(RTOSC_VERSION_MAJOR 0)
set(RTOSC_VERSION_MINOR 3)
set(RTOSC_VERSION_PATCH 1)
set(VERSION_MAJOR ${RTOSC_VERSION_MAJOR})
set(VERSION_MINOR ${RTOSC_VERSION_MINOR})
set(VERSION_PATCH ${RTOSC_VERSION_PATCH})
configure_file("${ZYN_UPSTREAM_DIR}/rtosc/src/cpp/version.c.in" "${RTOSC_GENERATED_DIR}/cpp/version.c")

# Restore Zyn version values for any later configure steps in this file
set(VERSION_MAJOR 3)
set(VERSION_MINOR 0)
set(VERSION_REVISION 7)

configure_file("${MXML_DIR}/linux/config.h" "${MXML_GENERATED_DIR}/config.h" COPYONLY)

set(ZYN_FFT_BACKEND_SRC "../../../../native/engine_adapter/fftw3f_shim.cpp")
set(ZYN_FFT_BACKEND_NAME "light-shim-radix2")
set(ZYN_FFT_NATIVE_LIB "")
if(ZYN_FFT_BACKEND STREQUAL "LIGHT_SHIM")
    # default bring-up backend
elseif(ZYN_FFT_BACKEND STREQUAL "PROD_CANDIDATE")
    set(ZYN_FFT_BACKEND_SRC "../../../../native/engine_adapter/fftw3f_backend_prod_candidate.cpp")
    set(ZYN_FFT_BACKEND_NAME "prod-candidate-bluestein")
elseif(ZYN_FFT_BACKEND STREQUAL "FFTW3F_NATIVE")
    # Build real single-precision FFTW3 in-tree for better PAD compatibility.
    file(GLOB FFTW3_CODELETS_DFT "${FFTW3_DIR}/dft/scalar/codelets/*.c")
    file(GLOB FFTW3_CODELETS_R2CF "${FFTW3_DIR}/rdft/scalar/r2cf/*.c")
    file(GLOB FFTW3_CODELETS_R2CB "${FFTW3_DIR}/rdft/scalar/r2cb/*.c")
    file(GLOB FFTW3_CODELETS_R2R "${FFTW3_DIR}/rdft/scalar/r2r/*.c")
    if(FFTW3_CODELETS_DFT STREQUAL "" OR FFTW3_CODELETS_R2CF STREQUAL "" OR FFTW3_CODELETS_R2CB STREQUAL "" OR FFTW3_CODELETS_R2R STREQUAL "")
        message(FATAL_ERROR
            "FFTW3F_NATIVE selected but third_party/fftw3 vendor is incomplete (missing generated codelets). "
            "Keep PROD_CANDIDATE or vendor a full FFTW source release with generated codelets.")
    endif()
    set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
    set(BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(ENABLE_THREADS OFF CACHE BOOL "" FORCE)
    set(ENABLE_OPENMP OFF CACHE BOOL "" FORCE)
    set(ENABLE_FLOAT ON CACHE BOOL "" FORCE)
    set(ENABLE_LONG_DOUBLE OFF CACHE BOOL "" FORCE)
    set(ENABLE_QUAD_PRECISION OFF CACHE BOOL "" FORCE)
    set(DISABLE_FORTRAN ON CACHE BOOL "" FORCE)
    add_subdirectory("${FFTW3_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/fftw3_build" EXCLUDE_FROM_ALL)
    if(TARGET fftw3f)
        # Ensure all FFTW objects use the float symbol prefix (fftwf_*).
        # Upstream CMake sets FFTW_SINGLE in config.h but does not add compile defs,
        # and some generated codelets rely on the macro directly.
        target_compile_definitions(fftw3f PRIVATE FFTW_SINGLE BENCHFFT_SINGLE)
    endif()
    set(ZYN_FFT_BACKEND_SRC)
    set(ZYN_FFT_BACKEND_NAME "fftw3f-native")
    set(ZYN_FFT_NATIVE_LIB fftw3f)
else()
    message(FATAL_ERROR "Unsupported ZYN_FFT_BACKEND='${ZYN_FFT_BACKEND}'")
endif()

message(STATUS "Zyn FFT backend: ${ZYN_FFT_BACKEND} -> ${ZYN_FFT_BACKEND_NAME}")

add_library(
    zynbridge
    SHARED
    native_bridge.cpp
    ../../../../native/engine_adapter/ZynAndroidEngine.cpp
    ../../../../native/engine_adapter/ZynUpstreamProbe.cpp
    ../../../../native/engine_adapter/ZynCompatStubs.cpp
    ${ZYN_FFT_BACKEND_SRC}
    "${ZYN_SRC_DIR}/globals.cpp"
    "${ZYN_SRC_DIR}/Containers/MultiPseudoStack.cpp"
    "${ZYN_SRC_DIR}/Containers/NotePool.cpp"
    "${ZYN_SRC_DIR}/Containers/ScratchString.cpp"
    "${ZYN_SRC_DIR}/Misc/Util.cpp"
    "${ZYN_SRC_DIR}/Misc/Allocator.cpp"
    "${ZYN_SRC_DIR}/Misc/Bank.cpp"
    "${ZYN_SRC_DIR}/Misc/BankDb.cpp"
    "${ZYN_SRC_DIR}/Misc/MsgParsing.cpp"
    "${ZYN_SRC_DIR}/Misc/Microtonal.cpp"
    "${ZYN_SRC_DIR}/Misc/Schema.cpp"
    "${ZYN_SRC_DIR}/Misc/CallbackRepeater.cpp"
    "${ZYN_SRC_DIR}/Misc/Config.cpp"
    "${ZYN_SRC_DIR}/Misc/MemLocker.cpp"
    "${ZYN_SRC_DIR}/Misc/Master.cpp"
    "${ZYN_SRC_DIR}/Misc/Recorder.cpp"
    "${ZYN_SRC_DIR}/Misc/WavFile.cpp"
    "${ZYN_SRC_DIR}/Misc/WaveShapeSmps.cpp"
    "${ZYN_SRC_DIR}/Misc/XMLwrapper.cpp"
    "${ZYN_SRC_DIR}/Misc/Part.cpp"
    "${ZYN_SRC_DIR}/Params/Controller.cpp"
    "${ZYN_SRC_DIR}/Params/ADnoteParameters.cpp"
    "${ZYN_SRC_DIR}/Params/EnvelopeParams.cpp"
    "${ZYN_SRC_DIR}/Params/FilterParams.cpp"
    "${ZYN_SRC_DIR}/Params/LFOParams.cpp"
    "${ZYN_SRC_DIR}/Params/PADnoteParameters.cpp"
    "${ZYN_SRC_DIR}/Params/Presets.cpp"
    "${ZYN_SRC_DIR}/Params/PresetsArray.cpp"
    "${ZYN_SRC_DIR}/Params/PresetsStore.cpp"
    "${ZYN_SRC_DIR}/Params/SUBnoteParameters.cpp"
    "${ZYN_SRC_DIR}/DSP/AnalogFilter.cpp"
    "${ZYN_SRC_DIR}/DSP/CombFilter.cpp"
    "${ZYN_SRC_DIR}/DSP/FFTwrapper.cpp"
    "${ZYN_SRC_DIR}/DSP/Filter.cpp"
    "${ZYN_SRC_DIR}/DSP/FormantFilter.cpp"
    "${ZYN_SRC_DIR}/DSP/MoogFilter.cpp"
    "${ZYN_SRC_DIR}/DSP/Reverter.cpp"
    "${ZYN_SRC_DIR}/DSP/SVFilter.cpp"
    "${ZYN_SRC_DIR}/DSP/Unison.cpp"
    "${ZYN_SRC_DIR}/DSP/Value_Smoothing_Filter.cpp"
    "${ZYN_SRC_DIR}/Synth/Envelope.cpp"
    "${ZYN_SRC_DIR}/Synth/LFO.cpp"
    "${ZYN_SRC_DIR}/Synth/ModFilter.cpp"
    "${ZYN_SRC_DIR}/Synth/ADnote.cpp"
    "${ZYN_SRC_DIR}/Synth/OscilGen.cpp"
    "${ZYN_SRC_DIR}/Synth/PADnote.cpp"
    "${ZYN_SRC_DIR}/Synth/Portamento.cpp"
    "${ZYN_SRC_DIR}/Synth/Resonance.cpp"
    "${ZYN_SRC_DIR}/Synth/SUBnote.cpp"
    "${ZYN_SRC_DIR}/Synth/SynthNote.cpp"
    "${ZYN_SRC_DIR}/Synth/WatchPoint.cpp"
    "${ZYN_SRC_DIR}/Effects/Alienwah.cpp"
    "${ZYN_SRC_DIR}/Effects/Chorus.cpp"
    "${ZYN_SRC_DIR}/Effects/CombFilterBank.cpp"
    "${ZYN_SRC_DIR}/Effects/Distortion.cpp"
    "${ZYN_SRC_DIR}/Effects/DynamicFilter.cpp"
    "${ZYN_SRC_DIR}/Effects/EQ.cpp"
    "${ZYN_SRC_DIR}/Effects/Echo.cpp"
    "${ZYN_SRC_DIR}/Effects/Effect.cpp"
    "${ZYN_SRC_DIR}/Effects/EffectLFO.cpp"
    "${ZYN_SRC_DIR}/Effects/EffectMgr.cpp"
    "${ZYN_SRC_DIR}/Effects/Phaser.cpp"
    "${ZYN_SRC_DIR}/Effects/Reverb.cpp"
    "${ZYN_SRC_DIR}/Effects/Reverse.cpp"
    "${ZYN_SRC_DIR}/Effects/Sympathetic.cpp"
    "${ZYN_UPSTREAM_DIR}/tlsf/tlsf.c"
    "${ZYN_UPSTREAM_DIR}/rtosc/src/rtosc.c"
    "${ZYN_UPSTREAM_DIR}/rtosc/src/dispatch.c"
    "${ZYN_UPSTREAM_DIR}/rtosc/src/rtosc-time.c"
    "${RTOSC_GENERATED_DIR}/cpp/version.c"
    "${ZYN_UPSTREAM_DIR}/rtosc/src/cpp/ports.cpp"
    "${ZYN_UPSTREAM_DIR}/rtosc/src/cpp/ports-runtime.cpp"
    "${ZYN_UPSTREAM_DIR}/rtosc/src/cpp/default-value.cpp"
    "${ZYN_UPSTREAM_DIR}/rtosc/src/cpp/savefile.cpp"
    "${ZYN_UPSTREAM_DIR}/rtosc/src/cpp/port-checker.cpp"
    "${ZYN_UPSTREAM_DIR}/rtosc/src/cpp/pretty-format.c"
    "${ZYN_UPSTREAM_DIR}/rtosc/src/cpp/arg-ext.c"
    "${ZYN_UPSTREAM_DIR}/rtosc/src/cpp/arg-val.c"
    "${ZYN_UPSTREAM_DIR}/rtosc/src/cpp/arg-val-math.c"
    "${ZYN_UPSTREAM_DIR}/rtosc/src/cpp/arg-val-cmp.c"
    "${ZYN_UPSTREAM_DIR}/rtosc/src/cpp/arg-val-itr.c"
    "${ZYN_UPSTREAM_DIR}/rtosc/src/cpp/util.c"
    "${ZYN_UPSTREAM_DIR}/rtosc/src/cpp/miditable.cpp"
    "${ZYN_UPSTREAM_DIR}/rtosc/src/cpp/automations.cpp"
    "${ZYN_UPSTREAM_DIR}/rtosc/src/cpp/midimapper.cpp"
    "${ZYN_UPSTREAM_DIR}/rtosc/src/cpp/thread-link.cpp"
    "${ZYN_UPSTREAM_DIR}/rtosc/src/cpp/undo-history.cpp"
    "${ZYN_UPSTREAM_DIR}/rtosc/src/cpp/subtree-serialize.cpp"
    "${ZYN_SRC_DIR}/version.cpp"
    "${MXML_DIR}/mxml-attr.c"
    "${MXML_DIR}/mxml-file.c"
    "${MXML_DIR}/mxml-get.c"
    "${MXML_DIR}/mxml-index.c"
    "${MXML_DIR}/mxml-node.c"
    "${MXML_DIR}/mxml-options.c"
    "${MXML_DIR}/mxml-private.c"
    "${MXML_DIR}/mxml-search.c"
    "${MXML_DIR}/mxml-set.c"
)

target_include_directories(
    zynbridge
    PRIVATE
    ../../../../native/engine_adapter
    "${ZYN_SRC_DIR}"
    "${ZYN_RTOSC_INCLUDE_DIR}"
    "${ZYN_GENERATED_DIR}"
    "${RTOSC_GENERATED_DIR}"
    "${MXML_GENERATED_DIR}"
    "${MXML_DIR}"
)

target_compile_definitions(
    zynbridge
    PRIVATE
    HAVE_CPP_STD_COMPLEX=1
    ZYN_FFT_BACKEND_NAME=\"${ZYN_FFT_BACKEND_NAME}\"
)

# Android 15+/Play requirement for 16 KB page-size compatibility.
# Apply on our JNI library now; repeat for future native deps we build ourselves.
target_link_options(
    zynbridge
    PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)

find_library(log-lib log)
find_library(aaudio-lib aaudio)
find_library(android-lib android)
find_library(zlib-lib z)

target_link_libraries(
    zynbridge
    ${ZYN_FFT_NATIVE_LIB}
    ${log-lib}
    ${aaudio-lib}
    ${android-lib}
    ${zlib-lib}
)

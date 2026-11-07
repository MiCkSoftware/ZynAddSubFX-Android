#!/usr/bin/env python3
"""Run the native module contract on the host, using the Android core source list.

Requires CMake and a C/C++17 toolchain. Uses the lightweight FFT shim; Android
ARM64/x86_64 builds separately validate the production FFTW backend and JNI.
"""
import argparse
from pathlib import Path
import subprocess
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--build-dir", type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parents[2]
build = args.build_dir or Path(tempfile.mkdtemp(prefix="zyn-common-modules-"))
source = build / "source"
source.mkdir(parents=True, exist_ok=True)
cmake = (root / "app/src/main/cpp/CMakeLists.txt").read_text()
cmake = cmake[:cmake.index("# Android 15+")]
cmake = cmake.replace(
    'get_filename_component(REPO_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../.." ABSOLUTE)',
    f'set(REPO_ROOT "{root.as_posix()}")',
)
# Keep C99 inline semantics in host headers; the vendored Android config erases it.
cmake = cmake.replace(
    'configure_file("${MXML_DIR}/linux/config.h" "${MXML_GENERATED_DIR}/config.h" COPYONLY)',
    '\n'.join([
        'file(READ "${MXML_DIR}/linux/config.h" MXML_HOST_CONFIG)',
        'string(REPLACE "#  define inline" "" MXML_HOST_CONFIG "${MXML_HOST_CONFIG}")',
        'file(WRITE "${MXML_GENERATED_DIR}/config.h" "${MXML_HOST_CONFIG}")',
    ]),
)
cmake = cmake.replace("../../../../native/engine_adapter", "${REPO_ROOT}/native/engine_adapter")
cmake = cmake.replace('    SHARED\n    native_bridge.cpp',
                      '    "${REPO_ROOT}/native/tests/common_modules_test.cpp"')
cmake = cmake.replace("add_library(\n    zynbridge", "add_executable(\n    zynbridge")
cmake += '\ntarget_link_libraries(zynbridge PRIVATE z)\n'
(source / "CMakeLists.txt").write_text(cmake)
subprocess.run(["cmake", "-S", str(source), "-B", str(build / "build"),
                "-DZYN_FFT_BACKEND=LIGHT_SHIM", "-DCMAKE_BUILD_TYPE=Debug"], check=True)
subprocess.run(["cmake", "--build", str(build / "build"), "--parallel", "4"], check=True)
subprocess.run([str(build / "build/zynbridge")], check=True, cwd=build)

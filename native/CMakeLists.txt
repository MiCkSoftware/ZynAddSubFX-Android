cmake_minimum_required(VERSION 3.22)
project(zyn_android_port_draft LANGUAGES C CXX)

# Draft only (M0 deliverable)
# This file is intentionally NOT wired into Gradle yet.
# It documents the intended native build shape for M1/M2.

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

message(STATUS "M0 draft CMake for ZynAddSubFX Android headless port")

# Expected layout:
#   third_party/zynaddsubfx/   -> vendored upstream
#   native/engine_adapter/     -> Android-specific wrappers/stubs
#   app/src/main/cpp/          -> JNI bridge (M1)

set(ZYN_UPSTREAM_DIR ${CMAKE_CURRENT_LIST_DIR}/../third_party/zynaddsubfx)
set(ZYN_ADAPTER_DIR  ${CMAKE_CURRENT_LIST_DIR}/engine_adapter)

# Future options for M1/M2
option(ZYN_ANDROID_EXCLUDE_MIDDLEWARE "Exclude MiddleWare/liblo path for first sound" ON)
option(ZYN_ANDROID_USE_MINIMAL_NIO "Compile minimal Nio subset for Master symbol closure" ON)

# Placeholder imported/third-party deps to be resolved in M1:
# - rtosc (submodule, likely add_subdirectory)
# - fftw3f
# - mxml
# - zlib
# - oboe (M2)

# Example target skeleton (disabled until source lists are finalized):
#
# add_library(zyn_android_core STATIC
#     ${ZYN_UPSTREAM_DIR}/src/globals.cpp
#     ${ZYN_UPSTREAM_DIR}/tlsf/tlsf.c
#     # ... curated source subset from DSP/Effects/Params/Synth/Misc/Containers
# )
#
# target_include_directories(zyn_android_core
#     PUBLIC
#         ${ZYN_UPSTREAM_DIR}/src
#         ${CMAKE_BINARY_DIR}/generated
# )
#
# if(ZYN_ANDROID_USE_MINIMAL_NIO)
#   target_sources(zyn_android_core PRIVATE
#       # Minimal Nio or adapter stubs (decision in M1)
#   )
# endif()
#
# add_library(zyn_android_jni SHARED
#     ../app/src/main/cpp/jni_bridge.cpp
#     ${ZYN_ADAPTER_DIR}/ZynAndroidEngine.cpp
# )
#
# target_link_libraries(zyn_android_jni
#     PRIVATE
#         zyn_android_core
#         oboe
#         log
#         android
# )


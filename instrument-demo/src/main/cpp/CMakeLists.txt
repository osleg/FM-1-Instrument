cmake_minimum_required(VERSION 3.22.1)
project(skiff_companion C CXX)

if(NOT SKIFF_SOURCE_DIR OR NOT EXISTS "${SKIFF_SOURCE_DIR}/web/emu/felucca_web.c")
    message(FATAL_ERROR "SKIFF_SOURCE_DIR must contain stock Skiff web/emu/felucca_web.c")
endif()
if(NOT SKIFF_GENERATED_DIR OR NOT EXISTS "${SKIFF_GENERATED_DIR}/felucca_tables.h" OR
        NOT EXISTS "${SKIFF_GENERATED_DIR}/felucca_samples.h")
    message(FATAL_ERROR "SKIFF_GENERATED_DIR must contain stock generated tables and samples")
endif()
if(NOT SKIFF_PROTOCOL_DIR OR NOT EXISTS "${SKIFF_PROTOCOL_DIR}/cafa_native_view.h")
    message(FATAL_ERROR "SKIFF_PROTOCOL_DIR must contain the companion native view wire contract")
endif()

set(plaits "${CMAKE_CURRENT_SOURCE_DIR}/engines/plaits")
file(GLOB_RECURSE plaits_models CONFIGURE_DEPENDS "${plaits}/thirdparty/eurorack/*.cc")
set(vocal_manifest "${CMAKE_CURRENT_SOURCE_DIR}/vocal-sources.txt")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${vocal_manifest}")
file(STRINGS "${vocal_manifest}" vocal_sources)
include(engines/dx386/build.cmake)

add_library(skiff-companion SHARED
    skiff_runtime.c
    engines/atek303/thirdparty/atek_voice.c
    engines/cz/thirdparty/cz_voice.c
    engines/dock_analog/thirdparty/arp_engine.c
    engines/dock_analog/thirdparty/em_engine.c
    engines/dock_modeled/thirdparty/kalimba.c
    engines/dock_modeled/thirdparty/monk.c
    engines/esq/thirdparty/esq_voice.c
    engines/obervave/thirdparty/obv_voice.c
    engines/opn/thirdparty/opn2.c
    engines/opn/thirdparty/psg.c
    engines/plaits/plaits_bridge.cc
    engines/sid/thirdparty/sid.c
    engines/synthex/thirdparty/sc_voice.c
    engines/x0x/thirdparty/drum808.c
    engines/x0x/thirdparty/drum909.c
    ${vocal_sources}
    ${plaits_models})
target_compile_features(skiff-companion PRIVATE c_std_99 cxx_std_11)
target_compile_options(skiff-companion PRIVATE -O2 -ffp-contract=off -w)
target_compile_definitions(skiff-companion PRIVATE $<$<COMPILE_LANGUAGE:CXX>:TEST>)
target_include_directories(skiff-companion PRIVATE
    "${SKIFF_SOURCE_DIR}/web/emu"
    "${SKIFF_SOURCE_DIR}/firmware/src"
    "${SKIFF_GENERATED_DIR}"
    "${plaits}/thirdparty/eurorack"
    "${SKIFF_PROTOCOL_DIR}")
target_link_libraries(skiff-companion PRIVATE dx386_runtime m)

# The binary emulator is available only for the two ABIs built by fm1-web.
# Other ABIs retain the existing instrument library without a stub emulator.
if(ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "x86_64")
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(emulator_target "aarch64-linux-android")
    else()
        set(emulator_target "x86_64-linux-android")
    endif()
    set(emulator_archive "${FM1_EMULATOR_DIR}/target/${emulator_target}/release/libfm1_web.a")
    if(NOT EXISTS "${emulator_archive}")
        message(FATAL_ERROR "FM-1 emulator archive missing: ${emulator_archive}. Run instrument-demo/tools/build-emulator.ps1 first.")
    endif()
    add_library(fm1-web STATIC IMPORTED)
    set_target_properties(fm1-web PROPERTIES IMPORTED_LOCATION "${emulator_archive}")
    add_library(fm1-emulator SHARED emulator/fm1_emulator_jni.c)
    target_include_directories(fm1-emulator PRIVATE emulator)
    target_compile_features(fm1-emulator PRIVATE c_std_99)
    target_link_libraries(fm1-emulator PRIVATE fm1-web log android dl m)
endif()

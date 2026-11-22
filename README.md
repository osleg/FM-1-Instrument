# FM-1 Instrument

FM-1 Instrument is an Android companion app for the M-VAVE FM-1 running Windjammer. The FM-1 provides the controls and display. The phone runs the Skiff 2.5.1 sequencer, synth engines, additional instruments, and project storage.

Download the signed APK from [Releases](https://github.com/osleg/FM-1-Instrument/releases). Version 0.8.0 upgrades the signed v0.7.0 release in place. An existing development APK signed with the debug key requires a reinstall. Back up any projects and recordings you want to keep before uninstalling it.

Use [Windjammer firmware](https://github.com/osleg/Windjammer/releases/tag/v0.9.2) on the FM-1. Choose its USB MIDI endpoint with both MIDI directions. **FM-1 · direct USB (synth only)** sends the app's audio to the FM-1 MASTER output after Android grants permission. **Phone** lists the phone speaker and wired headphones. **FM-1 · Android audio (shared)** lists Android-managed USB audio outputs. Select the FM-1 output explicitly in shared mode. Optional external USB MIDI input and output selectors exclude the selected companion endpoint to prevent feedback.

All routes generate 44.1 kHz stereo PCM16 in bounded native blocks. The app validates the selected output before starting sound. Sound starts after display synchronization. Windjammer + Instruments also waits for the first physical MASTER reading; its connection requires the instrument, native display, native panel, and panel MIDI capabilities. The app requests idle keepalive and display deltas when available. Disconnect before changing the destination. Reconnect manually if the active route disappears.

The playback service owns MIDI, audio, audio focus and the active-session CPU wake lock. Leaving the app or turning off the screen leaves an active session running. Reopening the app attaches to the same connection. The ongoing playback notification identifies the destination and offers **Stop**; the app also has **Disconnect**. The phone screen shows the current route, connection status, and Skiff screen title. Notification permission can be declined without blocking playback.

External USB MIDI and FM-1 TRS MIDI input feed the stock Skiff engine. Channel voice messages, song position, and realtime transport/clock are passed as ordered USB-MIDI packets. Generated MIDI retains its position inside each audio block. Android timestamps events against the playback position and flushes pending output on disconnect before sending the final releases. The FM-1 TRS jack receives MIDI. Sending notes and clock to another instrument uses the Android USB MIDI output, typically through a hub.

Skiff's emulated NOR image is stored in the app's private `skiff-nor.bin`. Changed revisions are written atomically while playing and the final save includes changes made by Skiff’s stop routine. The service does not restart or resume automatically after process death; reconnect manually and the saved image is restored.

Route removal, route mismatch, focus loss and session failure stop playback and require manual reconnection. Android may change a physical route before delivering its callback, so actual USB routing, disconnect behavior, interruptions and screen-off operation must be qualified on the phone before accepting pocket use.

## Remote UI demo

**FM-1 firmware emulator** is an optional reference demo for the open [Remote UI wire-6 profile](https://github.com/Osleg/Windjammer/blob/main/docs/remote-ui-wire.md). Import a user-selected `.fwsc` to run it on the phone and use the connected FM-1 as its screen and controls. The app sends the guest's pixels and LEDs over Remote UI and applies the controller's raw panel input to the guest. Apps can use the same profile to present their own UI. Guest firmware and any changed flash stay on the phone, with storage separated by package.

Select **Windjammer + Instruments** for the native Skiff UI on Windjammer v0.9.0 or later. The Remote UI demo requires Windjammer v0.9.2 or another build that offers Remote UI capability 16. Playback starts after the firmware acknowledges the first complete screen and the app verifies the selected route. Direct USB remains the initial audio choice, with output volume controlled by the physical MASTER. The demo uses a fixed guest MASTER level and has no Instrument sample capture, hardware measurement or external MIDI routing. Full physical qualification, including sustained audio during display changes, remains open.

## Build from source

Use JDK 21, Android SDK Platform 35, Build Tools 35.0.0, NDK 27.2.12479018, CMake 3.22.1, and Python 3 with Pillow. From this repository, clone [Windjammer v0.9.2](https://github.com/osleg/Windjammer/tree/v0.9.2) into the sibling directory `../Windjammer`. The full history provides the stock Skiff source commit pinned in the preparation script.

Before Gradle configures the 64-bit Android targets, build the Rust static libraries from the pinned [Windjammer fork of `fm1-emu`](https://github.com/Osleg/fm1-emu/commit/54ddec2). On Windows, use Rust 1.93.1 with the `aarch64-linux-android` and `x86_64-linux-android` targets, then run `instrument-demo/tools/build-emulator.ps1` in PowerShell. It prepares the checkout and both archives under `../.cache/fm1-emu-windjammer`. Use `-ToolchainRoot` and `-NdkRoot` to point the script at existing tool installations. CMake reports the archive path if a required library is missing. Firmware emulation requires a 64-bit Android process. Native Instrument mode also supports 32-bit Android.

```powershell
.\instrument-demo\tools\build-emulator.ps1
```

```sh
git clone --branch v0.9.2 https://github.com/osleg/Windjammer.git ../Windjammer
bash instrument-demo/tools/prepare-skiff.sh ../Windjammer ../.cache/skiff-2.5.1 python3
./gradlew :companion:testDebugUnitTest :instrument-demo:testReleaseUnitTest :instrument-demo:lintRelease :instrument-demo:assembleRelease
```

For an existing Windjammer checkout at another path, pass that path to `prepare-skiff.sh` and set Gradle's `-PwindjammerDir=<path>`. Gradle produces an unsigned APK under `instrument-demo/build/outputs/apk/release`. The [Android signing guide](https://developer.android.com/build/building-cmdline#sign_cmdline) describes how to sign a local build.

For native interaction and stock audio checks, run `instrument-demo/src/main/cpp/run-host-check.sh` and `instrument-demo/src/main/cpp/run-release-oracle.sh` from this checkout with host C and C++ compilers and `rg` on PATH. Set `WINDJAMMER_DIR` if using a different firmware checkout path. Generate the Windjammer tables per its [build guide](https://github.com/osleg/Windjammer/blob/v0.9.2/BUILDING.md) before running `instrument-demo/tools/run-paired-session-check.sh`.

## Source and credits

The initial source comes from the [original 0.7.0 tag](https://github.com/osleg/loom-groovebox/tree/instrument-v0.7.0). The Android app and the companion protocol library build together in this repository. See the [GPL license](LICENSE.md) and the [engine sources](instrument-demo/src/main/cpp/engines) for credits and third-party notices.
The firmware emulator is based on Joakim Eriksson's [fm1-emu](https://github.com/joakimeriksson/fm1-emu). The APK assets include its MIT license notice.

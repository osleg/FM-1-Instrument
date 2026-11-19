# FM-1 Instrument

FM-1 Instrument is an Android companion app for the M-VAVE FM-1 running Windjammer. The FM-1 provides the controls and display. The phone runs the Skiff 2.5.1 sequencer, synth engines, additional instruments, and project storage.

Download the signed APK from [Releases](https://github.com/osleg/FM-1-Instrument/releases). Version 0.7.0 uses the same Android package and release signature as the original 0.7.0 APK. An existing development APK signed with the debug key requires a reinstall. Back up any projects and recordings you want to keep before uninstalling it.

Use [Windjammer firmware](https://github.com/osleg/Windjammer/releases/tag/v0.9.0) on the FM-1. Choose its USB MIDI endpoint with both MIDI directions. **FM-1 · direct USB (synth only)** sends the app's audio to the FM-1 MASTER output after Android grants permission. **Phone** lists the phone speaker and wired headphones. **FM-1 · Android audio (shared)** lists Android-managed USB audio outputs. Select the FM-1 output explicitly in shared mode. Optional external USB MIDI input and output selectors exclude the selected companion endpoint to prevent feedback.

All routes generate 44.1 kHz stereo PCM16 in bounded native blocks. The app validates the selected output before starting sound. Sound starts after display synchronization and the first physical MASTER reading. The connection requires the instrument, native display, native panel, and panel MIDI capabilities. The app requests idle keepalive and display deltas when available. Disconnect before changing the destination. Reconnect manually if the active route disappears.

The playback service owns MIDI, audio, audio focus and the active-session CPU wake lock. Leaving the app or turning off the screen leaves an active session running. Reopening the app attaches to the same connection. The ongoing playback notification identifies the destination and offers **Stop**; the app also has **Disconnect**. The phone screen shows the current route, connection status, and Skiff screen title. Notification permission can be declined without blocking playback.

External USB MIDI and FM-1 TRS MIDI input feed the stock Skiff engine. Channel voice messages, song position, and realtime transport/clock are passed as ordered USB-MIDI packets. Generated MIDI retains its position inside each audio block. Android timestamps events against the playback position and flushes pending output on disconnect before sending the final releases. The FM-1 TRS jack receives MIDI. Sending notes and clock to another instrument uses the Android USB MIDI output, typically through a hub.

Skiff's emulated NOR image is stored in the app's private `skiff-nor.bin`. Changed revisions are written atomically while playing and the final save includes changes made by Skiff’s stop routine. The service does not restart or resume automatically after process death; reconnect manually and the saved image is restored.

Route removal, route mismatch, focus loss and session failure stop playback and require manual reconnection. Android may change a physical route before delivering its callback, so actual USB routing, disconnect behavior, interruptions and screen-off operation must be qualified on the phone before accepting pocket use.

## Build from source

Use JDK 21, Android SDK Platform 35, Build Tools 35.0.0, NDK 27.2.12479018, CMake 3.22.1, and Python 3 with Pillow. From this repository, clone [Windjammer v0.9.0](https://github.com/osleg/Windjammer/tree/v0.9.0) into the sibling directory `../Windjammer`. The full history provides the stock Skiff source commit pinned in the preparation script.

```sh
git clone --branch v0.9.0 https://github.com/osleg/Windjammer.git ../Windjammer
bash instrument-demo/tools/prepare-skiff.sh ../Windjammer ../.cache/skiff-2.5.1 python3
./gradlew :companion:testDebugUnitTest :instrument-demo:testReleaseUnitTest :instrument-demo:lintRelease :instrument-demo:assembleRelease
```

For an existing Windjammer checkout at another path, pass that path to `prepare-skiff.sh` and set Gradle's `-PwindjammerDir=<path>`. Gradle produces an unsigned APK under `instrument-demo/build/outputs/apk/release`. The [Android signing guide](https://developer.android.com/build/building-cmdline#sign_cmdline) describes how to sign a local build.

For native interaction and stock audio checks, run `instrument-demo/src/main/cpp/run-host-check.sh` and `instrument-demo/src/main/cpp/run-release-oracle.sh` from this checkout with host C and C++ compilers and `rg` on PATH. Set `WINDJAMMER_DIR` if using a different firmware checkout path. Generate the Windjammer tables per its [build guide](https://github.com/osleg/Windjammer/blob/v0.9.0/BUILDING.md) before running `instrument-demo/tools/run-paired-session-check.sh`.

## Source and credits

The initial source comes from the [original 0.7.0 tag](https://github.com/osleg/loom-groovebox/tree/instrument-v0.7.0). The Android app and the companion protocol library build together in this repository. See the [GPL license](LICENSE.md) and the [engine sources](instrument-demo/src/main/cpp/engines) for credits and third-party notices.

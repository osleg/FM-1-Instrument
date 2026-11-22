# Remote UI demo validation

Firmware emulation ships as the Remote UI demo in FM-1 Instrument v0.8.0 with Windjammer v0.9.2. The checks below cover the native engine, the Android build, and protocol behavior.

## Implemented path

- Manual selection between the existing Instrument mode and firmware emulation.
- User-selected `.fwsc` packages, with separate changed-flash records keyed by package SHA-256. Raw `.bin` loading and bundled third-party firmware are not included.
- Pinned `fm1-emu` core with native JIT on Android arm64 and x86-64. The existing Instrument mode remains available on all four Android ABIs.
- Existing audio destination handling, including isolated direct USB PCM. Guest MASTER is fixed at maximum and physical MASTER continues controlling Windjammer output.
- Negotiated public Remote UI capability 16: RGB565 tiles, 128-entry cache, complete first-screen recovery, display-commit acknowledgments, physical panel input and LED forwarding.
- Existing native UI remains the default. External MIDI routing and the hardware timing diagnostic remain available in Instrument mode; they are not exposed in the initial emulator mode.

## Automated evidence

- Android companion and Instrument unit suites: 87 tests, no failures. Android lint and four-ABI debug APK assembly passed.
- Both Android emulator static libraries and JNI libraries compile and link. APK contains both 64-bit emulator libraries and the upstream MIT notice. APK 16 KiB ZIP alignment passed.
- `RemoteSessionCheck.java` runs the production Java session against Windjammer's real USB mailbox and screen hooks with simulated hardware. It verifies the complete LCD pixel hash, cache eviction, lost-ACK retry, incremental updates, physical key input and an idle lease with no unchanged-pixel retransmissions.
- Independent paired validation passed 73 existing native screen/overlay cases (72 distinct controller framebuffers), native input/audio ownership, fragment-credit recovery and key-timing lifecycle, followed by the remote-screen checks.
- Matching Windjammer controller build passed image, audio, native UI, companion protocol, USB drift, installer and package-identity gates. The matching controller image is approximately 281 kB. A Remote UI session borrows 181,760 bytes from the controller pool and releases them on teardown. The resource and USB checks run against the build and simulator.
- `smoke-emulator.mjs` executes the pinned core with a public Felucca 1.8.2 package. It verifies boot, LCD output, real I2S frames and nonzero note audio. See the adjacent README for package provenance. This WASM-core check does not execute the Android JNI bridge or native JIT.

## Hardware acceptance still required

Use Windjammer v0.9.2 or another build advertising Remote UI. Check guest startup, all panel buttons/held gestures and encoders, colors and LEDs, simultaneous screen updates and direct USB playback, disconnect/reconnect, explicit guest saving and restoration, and switching back to native Instrument mode. Measure latency and underruns on the phone; the earlier native Instrument latency measurement does not apply to emulation.

No universal firmware compatibility, sustained native-JIT performance or physical USB acceptance is claimed by these automated checks.

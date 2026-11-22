# FM-1 binary emulator backend

The Android 64-bit backend links the MIT-licensed [Windjammer fork of fm1-emu](https://github.com/Osleg/fm1-emu) at commit `54ddec2056e028cc9061f981f5d9623065e83a23` through the `fm1-web` C ABI. The fork retains the stable Rust compatibility and explicit guest shutdown changes alongside the TIMER4 and dual-core dispatch optimizations. `instrument-demo/tools/build-emulator.ps1` builds arm64 and x86_64 static libraries under the sibling workspace's `.cache/fm1-emu-windjammer/target` using Rust 1.93.1 and Android NDK 27.2. Guest packages are supplied by the user.

The JNI bridge owns one serialized machine. It boots user-supplied FM-1 `.fwsc` bytes with fm1-core's native Android JIT enabled, renders 44.1 kHz stereo PCM16 with the upstream worklet's pacing budget, supplies the 240×240 RGB565 LCD and 41 LED levels, and forwards keys, encoders, pot and MIDI. Flash persistence stores only changed 4 KiB sectors against the pristine package image. An SMK-37 package is rejected because the app exposes an FM-1 panel.

Every guest boots at 160 MHz. Melodee 1.6.1 met its audio deadlines in the 16-key core test at this clock. The clock sets the guest's CPU budget, while the output uses 44.1 kHz and the existing audio queues. Phone processing load and real-time playback require a device check.

The timer fast path uses the same TIMER4 handler for both emulated cores and avoids the generic peripheral lookup for reads of the count register. Stopping a guest drops the machine and uses upstream's native JIT memory cleanup. The phone saves changed flash sectors before shutting down the guest.

The dual-core dispatch patch caches the instruction counts used by core 1’s polling probes and avoids repeated JIT lookups for already compiled blocks. It preserves the existing cycle budgets, interrupt checks, and lock handoffs.

Core smoke: build `fm1-web` for `wasm32-unknown-unknown`, then run `node instrument-demo/tools/smoke-emulator.mjs E:\Development\fm1\.cache\fm1-emu-windjammer <downloaded-felucca.fwsc>`. The public reference guest is `https://thefirmwarehub.com/fw/felucca-1.8.2.fwsc`, SHA-256 `770ea7c756088d08ac59a193f1ae78126476c3c8251a86938c837ead5ab0048a` (610,286 bytes). The check requires the pinned core to boot, produce LCD pixels and render audio in response to a note. Android runtime, native JIT performance, and physical USB operation require a device check.

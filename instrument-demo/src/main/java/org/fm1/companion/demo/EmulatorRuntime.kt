/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.companion.demo

import android.util.AtomicFile
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** One firmware session, including its own changed flash sectors. */
class EmulatorRuntime(private val file: File, private val firmware: ByteArray,
                      private val onFailure: (String) -> Unit) : PcmRuntime {
    private val lock = ReentrantLock()
    private val saveLock = Any()
    private val inputLock = Any()
    private val scans = ArrayDeque<() -> Unit>()
    private var acceptingInput = false
    private var started = false
    private var failed = false
    private var savedRevision = -1
    private val heldKeys = BooleanArray(41)
    private var previousScanMs: Int? = null
    private var frameRevision = -1
    @Volatile private var lastFrame: ShortArray? = null
    @Volatile private var lastLedLevels = FloatArray(41)

    fun boot() = lock.withLock {
        check(!started) { "FM-1 emulator is already running" }
        val saved = if (file.exists()) AtomicFile(file).readFully() else null
        check(Fm1EmulatorNative.bootFirmware(firmware, saved)) {
            "FM-1 firmware package or saved flash could not be loaded"
        }
        started = true
        failed = false
        savedRevision = Fm1EmulatorNative.flashRevision()
        // The remote display needs its first frame before the service enables audio.
        refreshFrame()
        synchronized(inputLock) { acceptingInput = true }
    }

    override fun render(out: ShortArray): LongArray {
        var notify = false
        lock.withLock {
            if (started && !failed) {
                // Rendering can return already buffered audio without advancing guest time.
                // Wait for the guest clock before applying the next physical scan.
                if (synchronized(inputLock) { scans.isNotEmpty() }) {
                    val now = Fm1EmulatorNative.guestMilliseconds()
                    if (now != previousScanMs) {
                        previousScanMs = now
                        synchronized(inputLock) { scans.removeFirst() }.invoke()
                    }
                }
                Fm1EmulatorNative.render(out)
                if (Fm1EmulatorNative.halted()) {
                    failed = true
                    synchronized(inputLock) { acceptingInput = false }
                    out.fill(0)
                    notify = true
                }
            } else out.fill(0)
        }
        if (notify) onFailure("FM-1 emulator halted while running the firmware")
        return longArrayOf()
    }

    private fun refreshFrame() {
        val revision = Fm1EmulatorNative.lcdRevision()
        if (revision == frameRevision && lastFrame != null) return
        val pixels = ShortArray(Fm1EmulatorNative.LCD_WIDTH * Fm1EmulatorNative.LCD_HEIGHT)
        if (Fm1EmulatorNative.copyLcdRgb565(pixels)) {
            frameRevision = revision
            lastFrame = pixels
        }
    }

    fun frame(): ShortArray? {
        if (lock.tryLock()) try {
            if (started && !failed) refreshFrame()
        } finally {
            lock.unlock()
        }
        return lastFrame
    }

    fun ledLevels(): FloatArray {
        if (lock.tryLock()) try {
            if (started && !failed) {
                val levels = FloatArray(41)
                if (Fm1EmulatorNative.copyLedLevels(levels)) lastLedLevels = levels
            }
        } finally {
            lock.unlock()
        }
        return lastLedLevels
    }

    internal fun enqueueScan(apply: () -> Unit): Boolean = synchronized(inputLock) {
        if (!acceptingInput || scans.size >= MAX_PENDING_SCANS) return@synchronized false
        scans.addLast(apply)
        true
    }

    // Scan handlers run with the machine lock held.
    internal fun key(id: Int, down: Boolean) {
        Fm1EmulatorNative.key(id, down)
        heldKeys[id] = down
    }

    internal fun resetControls(resetScan: () -> Unit = {}) = lock.withLock {
        synchronized(inputLock) { scans.clear() }
        previousScanMs = null
        if (started) for (id in heldKeys.indices) if (heldKeys[id]) {
            key(id, false)
        }
        resetScan()
    }

    internal fun encoder(id: Int, clicks: Int) {
        Fm1EmulatorNative.encoder(id, clicks)
    }

    fun pot(value: Int) = lock.withLock {
        if (started) Fm1EmulatorNative.pot(value)
    }

    fun midi(bytes: ByteArray) = lock.withLock {
        if (started) Fm1EmulatorNative.midi(bytes)
    }

    /** Call off the audio thread, after the firmware has finished its flash write. */
    fun checkpoint(): Boolean = synchronized(saveLock) save@{
        val snapshot = lock.withLock {
            if (!started || Fm1EmulatorNative.flashBusy()) return@save false
            val revision = Fm1EmulatorNative.flashRevision()
            if (revision == savedRevision) return@save true
            val records = Fm1EmulatorNative.flashDelta() ?: return@save false
            revision to records
        }
        val store = AtomicFile(file)
        var stream: java.io.FileOutputStream? = null
        try {
            stream = store.startWrite()
            stream.write(snapshot.second)
            store.finishWrite(stream)
            lock.withLock { savedRevision = snapshot.first }
            true
        } catch (error: Exception) {
            if (stream != null) runCatching { store.failWrite(stream) }
            onFailure("FM-1 emulator could not save flash: ${error.message}")
            false
        }
    }

    fun retire() = checkpoint()

    fun stop() = synchronized(saveLock) stop@{
        lock.withLock {
            if (!started) return@stop
            synchronized(inputLock) { acceptingInput = false }
            resetControls()
            val discard = ShortArray(512)
            for (attempt in 0 until 128) {
                if (!Fm1EmulatorNative.flashBusy()) break
                Fm1EmulatorNative.render(discard)
            }
        }
        var saved = false
        try {
            saved = checkpoint()
        } finally {
            lock.withLock {
                if (started) Fm1EmulatorNative.stop()
                started = false
                lastFrame = null
                lastLedLevels = FloatArray(41)
            }
        }
        check(saved) { "FM-1 emulator flash save is still pending" }
    }

    companion object {
        private const val MAX_PENDING_SCANS = 64
    }
}

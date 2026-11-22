/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.companion.demo

/** Native FM-1 binary emulator. All calls serialize through the native bridge. */
object Fm1EmulatorNative {
    const val LCD_WIDTH = 240
    const val LCD_HEIGHT = 240
    const val SAMPLE_RATE = 44100

    init { System.loadLibrary("fm1-emulator") }

    /** Boots a user supplied .fwsc. Saved flash is the changed-sector record stream. */
    external fun bootFirmware(packageBytes: ByteArray, savedFlash: ByteArray?): Boolean

    /** Fills interleaved stereo PCM16; returns frames produced by the emulated I2S DMA. */
    external fun render(out: ShortArray): Int
    external fun lcdRevision(): Int
    external fun guestMilliseconds(): Int
    /** Copies exactly 240 x 240 RGB565 pixels when running. */
    external fun copyLcdRgb565(out: ShortArray): Boolean
    /** 41 LED levels, normalized to 0..1. */
    external fun copyLedLevels(out: FloatArray): Boolean
    external fun key(id: Int, down: Boolean)
    external fun encoder(id: Int, clicks: Int)
    external fun pot(value: Int)
    external fun midi(bytes: ByteArray)
    external fun flashRevision(): Int
    external fun flashBusy(): Boolean
    /** Changed 4 KiB sectors, each prefixed with its 32-bit little-endian flash offset. */
    external fun flashDelta(): ByteArray?
    external fun halted(): Boolean
    external fun stop()
}

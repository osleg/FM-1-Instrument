/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.companion.demo

import org.fm1.companion.protocol.OpenMidiConnection
import org.fm1.companion.protocol.Wire6

/** Physical panel forwarding. The guest owns all page navigation and held-button behavior. */
class EmulatorSurface(private val runtime: EmulatorRuntime) : OpenMidiConnection.RemoteSurface {
    private val buttons = intArrayOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 0, 1)
    private val encoders = intArrayOf(0, 1, 6, 2, 3, 4, 5)
    private var first = true
    private var heldButtons = 0
    private var heldKeys = 0
    private var suppressedButtons = 0
    private var suppressedKeys = 0

    override fun frame(): ShortArray? = runtime.frame()

    override fun leds(): ByteArray {
        val levels = runtime.ledLevels()
        val result = ByteArray(29)
        for (plane in 0..3) {
            var b = 0
            var k = 0
            for (i in buttons.indices) {
                if (ledPlane(levels[buttons[i]]) == plane) b = b or (1 shl i)
            }
            for (i in 0..26) {
                if (ledPlane(levels[i + 14]) == plane) k = k or (1 shl i)
            }
            // Native LED section: three button-mask bytes and four note-mask bytes per plane.
            Wire6.put14(result, plane * 7, b)
            Wire6.put28(result, plane * 7 + 3, k)
        }
        result[28] = 1
        return result
    }
    private fun ledPlane(level: Float): Int = when {
        level >= .65f -> 0 // full
        level >= .30f -> 3 // medium
        level > .03f -> 1 // dim, without synthesizing a breathing animation
        else -> -1
    }

    override fun input(serial: Int, scan: ByteArray): Boolean {
        if (scan.size != 24 || (scan[5].toInt() and 64) != 0) return false
        return runtime.enqueueScan { applyScan(scan) }
    }

    private fun applyScan(scan: ByteArray) {
        val b = Wire6.u14(scan, 0)
        val k = Wire6.u28(scan, 2) and 0x07ffffff
        if (first) {
            first = false
            suppressedButtons = b
            suppressedKeys = k
            return
        }
        suppressedButtons = suppressedButtons and b
        suppressedKeys = suppressedKeys and k
        val activeButtons = b and suppressedButtons.inv()
        val activeKeys = k and suppressedKeys.inv()
        for (i in buttons.indices) if (((heldButtons xor activeButtons) and (1 shl i)) != 0)
            runtime.key(buttons[i], (activeButtons and (1 shl i)) != 0)
        for (i in 0..26) if (((heldKeys xor activeKeys) and (1 shl i)) != 0)
            runtime.key(i + 14, (activeKeys and (1 shl i)) != 0)
        heldButtons = activeButtons
        heldKeys = activeKeys
        for (i in encoders.indices) {
            val delta = Wire6.u14(scan, 6 + i * 2) - 8192
            if (delta != 0) runtime.encoder(encoders[i], delta)
        }
    }

    override fun reset() = runtime.resetControls {
        first = true
        heldButtons = 0; heldKeys = 0; suppressedButtons = 0; suppressedKeys = 0
    }
}

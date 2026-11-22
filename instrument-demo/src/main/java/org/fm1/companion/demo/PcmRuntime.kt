/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.companion.demo

/** Produces interleaved stereo PCM16 at 44.1 kHz and optional timed MIDI packets. */
interface PcmRuntime {
    fun render(out: ShortArray): LongArray
}

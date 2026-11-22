/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.companion.protocol;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;

/** One immutable screen transaction at a time. Cache references are ordered after their uploads. */
final class RemoteDisplay {
    private final ArrayDeque<byte[]> commands = new ArrayDeque<>();
    private final short[][] cache = new short[128][];
    private short[] previous;
    private byte[] previousLeds;
    private int replacement;
    private boolean reset = true;

    void recover() {
        commands.clear(); Arrays.fill(cache, null); previous = null; previousLeds = null;
        replacement = 0; reset = true;
    }
    boolean pending() { return !commands.isEmpty(); }
    byte[] next() { return commands.pollFirst(); }

    void sample(short[] frame, byte[] leds, boolean compact) {
        if (pending()) return;
        if (frame == null) return;
        if (frame.length != 240 * 240 || leds == null || leds.length != 29)
            throw new IllegalArgumentException("remote display shape");
        for (byte value : leds) if (value < 0) throw new IllegalArgumentException("LED value");
        if (reset) { commands.add(new byte[]{0}); reset = false; }
        boolean changed = previous == null || !Arrays.equals(previousLeds, leds);
        byte[] blits = compact ? new byte[154] : null;
        boolean[] queuedSlots = compact ? new boolean[cache.length] : null;
        int blitEnd = 1;
        if (compact) blits[0] = 6;
        for (int tile = 0; tile < 225; tile++) {
            int x = tile % 15 * 16, y = tile / 15 * 16;
            boolean same = previous != null;
            for (int row = 0; same && row < 16; row++) {
                int offset = (y + row) * 240 + x;
                for (int col = 0; col < 16; col++)
                    if (frame[offset + col] != previous[offset + col]) { same = false; break; }
            }
            if (same) continue;
            short[] pixels = new short[256];
            for (int row = 0; row < 16; row++)
                System.arraycopy(frame, (y + row) * 240 + x, pixels, row * 16, 16);
            changed = true;
            int slot = -1;
            for (int i = 0; i < cache.length; i++) if (Arrays.equals(cache[i], pixels)) { slot = i; break; }
            if (slot < 0) {
                // A pending draw may reference the slot that this upload will replace.
                if (compact && queuedSlots[replacement]) {
                    commands.add(Arrays.copyOf(blits, blitEnd));
                    blitEnd = 1;
                    Arrays.fill(queuedSlots, false);
                }
                slot = replacement; replacement = (replacement + 1) % cache.length;
                cache[slot] = pixels;
                upload(slot, pixels, compact);
            }
            if (compact) {
                queuedSlots[slot] = true;
                blits[blitEnd++] = (byte)slot;
                blits[blitEnd++] = (byte)(tile % 15);
                blits[blitEnd++] = (byte)(tile / 15);
                if (blitEnd == blits.length) {
                    commands.add(blits.clone());
                    blitEnd = 1;
                    Arrays.fill(queuedSlots, false);
                }
            } else commands.add(new byte[]{2, (byte)slot, (byte)(tile % 15), (byte)(tile / 15)});
        }
        if (blitEnd > 1) commands.add(Arrays.copyOf(blits, blitEnd));
        if (changed) {
            byte[] present = new byte[34]; present[0] = 3;
            // Raw physical controls are not semantic edits against a native page context.
            Wire6.put28(present, 1, 1);
            System.arraycopy(leds, 0, present, 5, 29); commands.add(present);
            previous = frame.clone(); previousLeds = leds.clone();
        }
    }

    private void upload(int slot, short[] pixels, boolean compact) {
        if (compact) {
            ArrayList<byte[]> packed = new ArrayList<>();
            int bytes = 0;
            for (int offset = 0; offset < pixels.length;) {
                byte[] part = new byte[Wire6.MAX_PAYLOAD];
                part[0] = 5; part[1] = (byte)slot; Wire6.put14(part, 2, offset);
                int end = 4;
                while (offset < pixels.length && part.length - end >= 4) {
                    int run = runLength(pixels, offset);
                    if (run > 1) {
                        part[end++] = (byte)(63 + run);
                        putPixel(part, end, pixels[offset]);
                        end += 3; offset += run;
                    } else {
                        int limit = Math.min((part.length - end - 1) / 3, pixels.length - offset);
                        int count = 1;
                        while (count < limit && runLength(pixels, offset + count) == 1) count++;
                        part[end++] = (byte)(count - 1);
                        for (int i = 0; i < count; i++, end += 3) putPixel(part, end, pixels[offset++]);
                    }
                }
                packed.add(Arrays.copyOf(part, end));
                bytes += end;
            }
            // Raw upload takes six messages and 792 payload bytes.
            if (packed.size() < 6 || packed.size() == 6 && bytes < 792) {
                commands.addAll(packed);
                return;
            }
        }
        for (int offset = 0; offset < 256; offset += 50) {
            int count = Math.min(50, 256 - offset);
            byte[] payload = new byte[4 + count * 3];
            payload[0] = 1; payload[1] = (byte)slot; Wire6.put14(payload, 2, offset);
            for (int i = 0; i < count; i++) putPixel(payload, 4 + i * 3, pixels[offset + i]);
            commands.add(payload);
        }
    }

    private static int runLength(short[] pixels, int at) {
        int count = 1;
        while (count < 64 && at + count < pixels.length && pixels[at + count] == pixels[at]) count++;
        return count;
    }

    private static void putPixel(byte[] out, int at, short pixel) {
        int value = pixel & 65535;
        out[at] = (byte)(value & 127);
        out[at + 1] = (byte)((value >> 7) & 127);
        out[at + 2] = (byte)(value >> 14);
    }

    static void validate(byte[] p) {
        if (p.length == 0) throw new IllegalArgumentException("remote operation");
        switch (p[0]) {
            case 0: case 4: if (p.length == 1) return; break;
            case 1:
                if (p.length < 7 || (p.length - 4) % 3 != 0 || p.length > 154 ||
                        Wire6.u14(p, 2) + (p.length - 4) / 3 > 256) break;
                for (int i = 6; i < p.length; i += 3) if (p[i] > 3)
                    throw new IllegalArgumentException("RGB565 pixel");
                return;
            case 2: if (p.length == 4 && p[2] < 15 && p[3] < 15) return; break;
            case 3:
                if (p.length != 34 || p[33] > 1) break;
                for (int i = 0; i < 4; i++) if (p[7 + i * 7] > 1 || p[11 + i * 7] > 63)
                    throw new IllegalArgumentException("LED mask");
                return;
            case 5:
                if (p.length < 8) break;
                int pixels = Wire6.u14(p, 2);
                for (int at = 4; at < p.length;) {
                    int tag = p[at++];
                    boolean run = tag >= 64;
                    int count = run ? tag - 63 : tag + 1;
                    int end = at + 3 * (run ? 1 : count);
                    pixels += count;
                    if (end > p.length || pixels > 256) throw new IllegalArgumentException("packed tile range");
                    for (int i = at + 2; i < end; i += 3) if (p[i] > 3)
                        throw new IllegalArgumentException("RGB565 pixel");
                    at = end;
                }
                return;
            case 6:
                if (p.length < 4 || (p.length - 1) % 3 != 0) break;
                for (int at = 1; at < p.length; at += 3) if (p[at + 1] >= 15 || p[at + 2] >= 15)
                    throw new IllegalArgumentException("blit position");
                return;
            default: break;
        }
        throw new IllegalArgumentException("remote payload");
    }
}

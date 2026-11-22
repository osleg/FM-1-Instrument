// SPDX-License-Identifier: GPL-3.0-only
// node instrument-demo/tools/smoke-emulator.mjs <fm1-emu checkout> <Felucca .fwsc>
import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const [checkoutArg, packageArg] = process.argv.slice(2);
if (!checkoutArg || !packageArg) throw new Error('Expected fm1-emu checkout and .fwsc path');
const checkout = resolve(checkoutArg);
const bytes = readFileSync(packageArg);
const sha256 = createHash('sha256').update(bytes).digest('hex');
const wasm = readFileSync(resolve(checkout, 'target/wasm32-unknown-unknown/release/fm1_web.wasm'));
const { jitImports } = await import(pathToFileURL(resolve(checkout, 'web/jit.js')).href);
let x;
const { instance } = await WebAssembly.instantiate(wasm, { env: jitImports(() => x) });
x = instance.exports;
const ptr = x.fm1_alloc(bytes.length);
new Uint8Array(x.memory.buffer, ptr, bytes.length).set(bytes);
const clockMhz = 160;
if (x.fm1_boot_fwsc(ptr, bytes.length, 0, 0, clockMhz) !== 0 || x.fm1_board() !== 0)
  throw new Error('Package did not boot as an FM-1');
x.fm1_ahead(1024);
x.fm1_pot(1023);

let started = false;
let frames = 0;
function render(count) {
  let power = 0;
  for (let at = 0; at < count; at += 256) {
    const budget = started ? Math.floor(clockMhz * 1_000_000 * 3 * 256 / (2 * 44100)) : 2_000_000;
    const have = x.fm1_render(256, budget);
    frames += have;
    if (have) started = true;
    const left = new Float32Array(x.memory.buffer, x.fm1_out_l(), 256);
    for (const value of left) power += value * value;
  }
  return Math.sqrt(power / (Math.ceil(count / 256) * 256));
}
render(3 * 44100);
const bootFrames = frames;
const lcd = new Uint16Array(x.memory.buffer, x.fm1_lcd(), 240 * 240);
const lit = lcd.reduce((sum, pixel) => sum + (pixel !== 0), 0);
const quiet = render(22050);
for (const b of [0x90, 60, 100]) x.fm1_midi(b);
const note = render(22050);
const halted = x.fm1_halted();
console.log(JSON.stringify({ packageBytes: bytes.length, sha256, bootFrames, lit, quiet, note, halted }));
if (bootFrames < 2 * 44100 || lit < 1000 || halted || note <= quiet * 10 + 0.001)
  throw new Error('FM-1 core smoke failed');

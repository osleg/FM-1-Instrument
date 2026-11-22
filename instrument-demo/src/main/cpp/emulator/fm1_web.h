/* SPDX-License-Identifier: MIT */
#ifndef FM1_WEB_H
#define FM1_WEB_H

#include <stdint.h>

/* fm1-web C exports, pinned to 54ddec2056e028cc9061f981f5d9623065e83a23. */
uint8_t *fm1_alloc(uint32_t len);
uint32_t fm1_boot_fwsc(uint8_t *pkg, uint32_t len, uint8_t *syx, uint32_t syx_len, uint32_t mhz);
void fm1_shutdown(void);
void fm1_jit(uint32_t on, uint32_t per_render);
uint32_t fm1_flash_restore(uint8_t *records, uint32_t len);
const uint8_t *fm1_flash(void);
const uint8_t *fm1_flash_base(void);
uint32_t fm1_flash_len(void);
uint32_t fm1_flash_revision(void);
uint32_t fm1_flash_busy(void);
uint32_t fm1_render(uint32_t frames, uint32_t max_insns);
void fm1_ahead(uint32_t frames);
const float *fm1_out_l(void);
const float *fm1_out_r(void);
const uint16_t *fm1_lcd(void);
uint32_t fm1_lcd_revision(void);
uint32_t fm1_ms(void);
const float *fm1_leds(void);
void fm1_key(uint32_t id, uint32_t down);
void fm1_enc(uint32_t enc, int32_t clicks);
void fm1_pot(uint32_t value);
void fm1_midi(uint32_t byte);
uint32_t fm1_halted(void);
uint32_t fm1_board(void);

#endif

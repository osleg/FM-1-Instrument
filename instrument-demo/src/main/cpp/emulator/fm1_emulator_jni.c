/* SPDX-License-Identifier: GPL-3.0-only */
#include <jni.h>
#include <pthread.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include "fm1_web.h"

/* fm1-web keeps one process-global machine and is not internally thread safe. */
static pthread_mutex_t machine_lock = PTHREAD_MUTEX_INITIALIZER;
static int running;
static int audio_started;
#define FM1_GUEST_CLOCK_MHZ 160u

#define JNI_FN(name) Java_org_fm1_companion_demo_Fm1EmulatorNative_##name

JNIEXPORT jboolean JNICALL JNI_FN(bootFirmware)(JNIEnv *env, jobject self,
                                                jbyteArray package, jbyteArray saved)
{
    (void)self;
    if (!package) return JNI_FALSE;
    const jsize len = (*env)->GetArrayLength(env, package);
    if (len <= 0) return JNI_FALSE;
    pthread_mutex_lock(&machine_lock);
    running = 0;
    audio_started = 0;
    uint8_t *bytes = fm1_alloc((uint32_t)len);
    (*env)->GetByteArrayRegion(env, package, 0, len, (jbyte *)bytes);
    fm1_jit(1, 0); /* fm1-core enables its native block compiler on Android arm64/x86_64. */
    int ok = !(*env)->ExceptionCheck(env) && fm1_boot_fwsc(bytes, (uint32_t)len, NULL, 0, FM1_GUEST_CLOCK_MHZ) == 0;
    if (ok && fm1_board() != 0) ok = 0; /* The phone panel is an FM-1, not an SMK-37. */
    if (ok && saved) {
        const jsize count = (*env)->GetArrayLength(env, saved);
        if (count > 0) {
            uint8_t *records = fm1_alloc((uint32_t)count);
            (*env)->GetByteArrayRegion(env, saved, 0, count, (jbyte *)records);
            ok = !(*env)->ExceptionCheck(env) && fm1_flash_restore(records, (uint32_t)count) == 0;
        }
    }
    if (ok) {
        fm1_ahead(1024);
        fm1_pot(1023); /* Software output owns final volume; avoid scaling it twice. */
    } else fm1_shutdown();
    running = ok;
    pthread_mutex_unlock(&machine_lock);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL JNI_FN(render)(JNIEnv *env, jobject self, jshortArray out)
{
    (void)self;
    if (!out) return 0;
    jsize samples = (*env)->GetArrayLength(env, out);
    samples &= ~1;
    int frames = samples / 2;
    if (frames > 1024) frames = 1024;
    jshort pcm[2048] = {0};
    int have = 0;
    pthread_mutex_lock(&machine_lock);
    if (running && frames) {
        const uint32_t budget = audio_started
            ? (uint32_t)(FM1_GUEST_CLOCK_MHZ * 1000000ULL * 3 * (uint32_t)frames / (2 * 44100))
            : 2000000;
        have = (int)fm1_render((uint32_t)frames, budget);
        if (have > 0) audio_started = 1;
        const float *left = fm1_out_l(), *right = fm1_out_r();
        for (int i = 0; i < frames; ++i) {
            float l = left[i], r = right[i];
            pcm[2 * i] = (jshort)(l <= -1.f ? -32768 : l >= 1.f ? 32767 : l * 32768.f);
            pcm[2 * i + 1] = (jshort)(r <= -1.f ? -32768 : r >= 1.f ? 32767 : r * 32768.f);
        }
    }
    pthread_mutex_unlock(&machine_lock);
    if (frames) (*env)->SetShortArrayRegion(env, out, 0, 2 * frames, pcm);
    if (samples > 2 * frames) {
        jshort zeros[2048] = {0};
        for (jsize at = 2 * frames; at < samples;) {
            jsize n = samples - at;
            if (n > 2048) n = 2048;
            (*env)->SetShortArrayRegion(env, out, at, n, zeros);
            at += n;
        }
    }
    return have;
}

JNIEXPORT jint JNICALL JNI_FN(lcdRevision)(JNIEnv *env, jobject self)
{
    (void)env; (void)self;
    pthread_mutex_lock(&machine_lock);
    jint revision = running ? (jint)fm1_lcd_revision() : -1;
    pthread_mutex_unlock(&machine_lock);
    return revision;
}

JNIEXPORT jint JNICALL JNI_FN(guestMilliseconds)(JNIEnv *env, jobject self)
{
    (void)env; (void)self;
    pthread_mutex_lock(&machine_lock);
    jint ms = running ? (jint)fm1_ms() : 0;
    pthread_mutex_unlock(&machine_lock);
    return ms;
}

JNIEXPORT jboolean JNICALL JNI_FN(copyLcdRgb565)(JNIEnv *env, jobject self, jshortArray out)
{
    (void)self;
    if (!out || (*env)->GetArrayLength(env, out) < 240 * 240) return JNI_FALSE;
    pthread_mutex_lock(&machine_lock);
    const uint16_t *pixels = running ? fm1_lcd() : NULL;
    if (pixels) (*env)->SetShortArrayRegion(env, out, 0, 240 * 240, (const jshort *)pixels);
    pthread_mutex_unlock(&machine_lock);
    return pixels ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL JNI_FN(copyLedLevels)(JNIEnv *env, jobject self, jfloatArray out)
{
    (void)self;
    if (!out || (*env)->GetArrayLength(env, out) < 41) return JNI_FALSE;
    pthread_mutex_lock(&machine_lock);
    const float *levels = running ? fm1_leds() : NULL;
    if (levels) (*env)->SetFloatArrayRegion(env, out, 0, 41, levels);
    pthread_mutex_unlock(&machine_lock);
    return levels ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL JNI_FN(key)(JNIEnv *env, jobject self, jint id, jboolean down)
{
    (void)env; (void)self;
    if (id < 0 || id > 40) return;
    pthread_mutex_lock(&machine_lock);
    if (running) fm1_key((uint32_t)id, down ? 1 : 0);
    pthread_mutex_unlock(&machine_lock);
}

JNIEXPORT void JNICALL JNI_FN(encoder)(JNIEnv *env, jobject self, jint id, jint clicks)
{
    (void)env; (void)self;
    if (id < 0 || id > 6 || !clicks) return;
    pthread_mutex_lock(&machine_lock);
    if (running) fm1_enc((uint32_t)id, clicks);
    pthread_mutex_unlock(&machine_lock);
}

JNIEXPORT void JNICALL JNI_FN(pot)(JNIEnv *env, jobject self, jint value)
{
    (void)env; (void)self;
    if (value < 0) value = 0;
    if (value > 1023) value = 1023;
    pthread_mutex_lock(&machine_lock);
    if (running) fm1_pot((uint32_t)value);
    pthread_mutex_unlock(&machine_lock);
}

JNIEXPORT void JNICALL JNI_FN(midi)(JNIEnv *env, jobject self, jbyteArray bytes)
{
    (void)self;
    if (!bytes) return;
    jsize n = (*env)->GetArrayLength(env, bytes);
    if (!n) return;
    jbyte *copy = (jbyte *)malloc((size_t)n);
    if (!copy) return;
    (*env)->GetByteArrayRegion(env, bytes, 0, n, copy);
    if (!(*env)->ExceptionCheck(env)) {
        pthread_mutex_lock(&machine_lock);
        if (running) for (jsize i = 0; i < n; ++i) fm1_midi((uint8_t)copy[i]);
        pthread_mutex_unlock(&machine_lock);
    }
    free(copy);
}

JNIEXPORT jint JNICALL JNI_FN(flashRevision)(JNIEnv *env, jobject self)
{
    (void)env; (void)self;
    pthread_mutex_lock(&machine_lock);
    jint value = running ? (jint)fm1_flash_revision() : -1;
    pthread_mutex_unlock(&machine_lock);
    return value;
}

JNIEXPORT jboolean JNICALL JNI_FN(flashBusy)(JNIEnv *env, jobject self)
{
    (void)env; (void)self;
    pthread_mutex_lock(&machine_lock);
    int busy = running && fm1_flash_busy();
    pthread_mutex_unlock(&machine_lock);
    return busy ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL JNI_FN(flashDelta)(JNIEnv *env, jobject self)
{
    (void)self;
    pthread_mutex_lock(&machine_lock);
    if (!running || fm1_flash_busy()) {
        pthread_mutex_unlock(&machine_lock);
        return NULL;
    }
    const uint8_t *flash = fm1_flash(), *base = fm1_flash_base();
    const uint32_t size = fm1_flash_len();
    if (!flash || !base || size % 4096) {
        pthread_mutex_unlock(&machine_lock);
        return NULL;
    }
    const size_t capacity = (size_t)(size / 4096) * 4100;
    uint8_t *records = (uint8_t *)malloc(capacity);
    if (!records) {
        pthread_mutex_unlock(&machine_lock);
        return NULL;
    }
    size_t length = 0;
    for (uint32_t at = 0; at < size; at += 4096) {
        if (memcmp(flash + at, base + at, 4096) == 0) continue;
        records[length++] = (uint8_t)at;
        records[length++] = (uint8_t)(at >> 8);
        records[length++] = (uint8_t)(at >> 16);
        records[length++] = (uint8_t)(at >> 24);
        memcpy(records + length, flash + at, 4096);
        length += 4096;
    }
    pthread_mutex_unlock(&machine_lock);
    jbyteArray result = (*env)->NewByteArray(env, (jsize)length);
    if (result) (*env)->SetByteArrayRegion(env, result, 0, (jsize)length, (jbyte *)records);
    free(records);
    return result;
}

JNIEXPORT jboolean JNICALL JNI_FN(halted)(JNIEnv *env, jobject self)
{
    (void)env; (void)self;
    pthread_mutex_lock(&machine_lock);
    int value = running && fm1_halted();
    pthread_mutex_unlock(&machine_lock);
    return value ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL JNI_FN(stop)(JNIEnv *env, jobject self)
{
    (void)env; (void)self;
    pthread_mutex_lock(&machine_lock);
    running = 0;
    fm1_shutdown();
    pthread_mutex_unlock(&machine_lock);
}

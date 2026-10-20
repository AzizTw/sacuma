/*
 * Virtual gamepads via /dev/uinput, one per connected GameCube port.
 * Ported from reference/gcadapter.c (parse, calibrate, mapping, vpad_*).
 *
 * Kotlin calls VirtualPads.processPacket() with every 37-byte adapter packet
 * (always from the same reader thread), and releaseAll() when it stops.
 */
#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/uinput.h>
#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/ioctl.h>
#include <unistd.h>
#include <android/log.h>

#define TAG "Sacuma"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

#define PKT_LEN 37
#define NPORTS 4

/* Identity of the virtual pads: Xbox 360 wired controller, so Android uses
 * its stock Vendor_045e_Product_028e key layout. */
#define VPAD_VID 0x045e
#define VPAD_PID 0x028e

/* A port can read "empty" briefly without being unplugged: holding X+Y+Start
 * makes the controller itself reset for a moment, and wiggly cables glitch.
 * While empty, the pad is held neutral; it is only removed after this long. */
#define UNPLUG_PACKETS 375  /* ~3 s */
#define REAPPEAR_RECAL 25   /* back after >= ~200 ms empty: recalibrate */
#define RETRY_PACKETS 125   /* ~1 s between attempts if creating a pad fails */
#define RECAL_PACKETS 375   /* ~3 s holding X+Y+Start */

/* button bits */
#define B1_A 0x01
#define B1_B 0x02
#define B1_X 0x04
#define B1_Y 0x08
#define B1_LEFT 0x10
#define B1_RIGHT 0x20
#define B1_DOWN 0x40
#define B1_UP 0x80
#define B2_START 0x01
#define B2_Z 0x02
#define B2_R 0x04
#define B2_L 0x08

struct gcpad {
    int type;               /* 0 none, 1 wired, 2 wireless */
    uint8_t b1, b2;
    uint8_t sx, sy, cx, cy, lt, rt;
};

struct vpad {
    int fd;                 /* -1 when not created */
    uint8_t csx, csy, ccx, ccy, clt, crt;   /* calibration */
    int recal_hold;         /* packets X+Y+Start held */
    int missing;            /* consecutive packets with the port empty */
    int retry_wait;         /* packets to wait before retrying a failed create */
    int32_t last[32];       /* last emitted values, to skip duplicates */
};

static struct vpad g_pads[NPORTS] = {
    {.fd = -1}, {.fd = -1}, {.fd = -1}, {.fd = -1},
};
/* Settings, written from the UI thread via JNI, read on the reader thread. */
static volatile int g_layout_xbox = 0;
static volatile int g_stick_dz_pct = 6;    /* radial stick deadzone, % of full tilt */
static volatile int g_trigger_dz = 10;     /* analog units above resting value */
static volatile int g_recal_request = 0;   /* recalibrate all pads on next packet */

static const int vpad_keys[] = {
    BTN_A, BTN_B, BTN_X, BTN_Y, BTN_TL, BTN_TR,
    BTN_SELECT, BTN_START, BTN_MODE, BTN_THUMBL, BTN_THUMBR,
};

static void parse(const uint8_t *pkt, struct gcpad pads[NPORTS])
{
    for (int i = 0; i < NPORTS; i++) {
        const uint8_t *p = pkt + 1 + 9 * i;
        pads[i].type = p[0] >> 4;
        pads[i].b1 = p[1];
        pads[i].b2 = p[2];
        pads[i].sx = p[3];
        pads[i].sy = p[4];
        pads[i].cx = p[5];
        pads[i].cy = p[6];
        pads[i].lt = p[7];
        pads[i].rt = p[8];
    }
}

static void abs_setup(int fd, int code, int min, int max, int flat)
{
    struct uinput_abs_setup a = {0};
    a.code = code;
    a.absinfo.minimum = min;
    a.absinfo.maximum = max;
    a.absinfo.flat = flat;
    ioctl(fd, UI_ABS_SETUP, &a);
}

static void calibrate(struct vpad *v, const struct gcpad *g)
{
    v->csx = g->sx; v->csy = g->sy;
    v->ccx = g->cx; v->ccy = g->cy;
    v->clt = g->lt; v->crt = g->rt;
}

static int vpad_create(struct vpad *v, int port, const struct gcpad *g)
{
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK | O_CLOEXEC);
    if (fd < 0) {
        LOGW("open /dev/uinput: %s", strerror(errno));
        return -1;
    }
    ioctl(fd, UI_SET_EVBIT, EV_KEY);
    ioctl(fd, UI_SET_EVBIT, EV_ABS);
    for (size_t i = 0; i < sizeof vpad_keys / sizeof *vpad_keys; i++)
        ioctl(fd, UI_SET_KEYBIT, vpad_keys[i]);
    int abses[] = {ABS_X, ABS_Y, ABS_RX, ABS_RY, ABS_Z, ABS_RZ, ABS_HAT0X, ABS_HAT0Y};
    for (size_t i = 0; i < sizeof abses / sizeof *abses; i++)
        ioctl(fd, UI_SET_ABSBIT, abses[i]);

    /* flat = 0: the deadzone is applied here (adjustable live), not by Android */
    abs_setup(fd, ABS_X, -32768, 32767, 0);
    abs_setup(fd, ABS_Y, -32768, 32767, 0);
    abs_setup(fd, ABS_RX, -32768, 32767, 0);
    abs_setup(fd, ABS_RY, -32768, 32767, 0);
    abs_setup(fd, ABS_Z, 0, 255, 0);
    abs_setup(fd, ABS_RZ, 0, 255, 0);
    abs_setup(fd, ABS_HAT0X, -1, 1, 0);
    abs_setup(fd, ABS_HAT0Y, -1, 1, 0);

    struct uinput_setup us = {0};
    us.id.bustype = BUS_USB;
    us.id.vendor = VPAD_VID;
    us.id.product = VPAD_PID;
    us.id.version = 0x0110;
    snprintf(us.name, sizeof us.name, "GameCube Controller P%d", port + 1);
    if (ioctl(fd, UI_DEV_SETUP, &us) < 0 || ioctl(fd, UI_DEV_CREATE) < 0) {
        LOGW("create virtual pad: %s", strerror(errno));
        close(fd);
        return -1;
    }
    v->fd = fd;
    v->recal_hold = 0;
    v->missing = 0;
    for (int i = 0; i < 32; i++) v->last[i] = INT32_MIN;
    calibrate(v, g);
    LOGI("port %d connected -> virtual pad created (center stick=(%u,%u) c=(%u,%u))",
         port + 1, g->sx, g->sy, g->cx, g->cy);
    return 0;
}

static void vpad_destroy(struct vpad *v, int port)
{
    if (v->fd < 0) return;
    ioctl(v->fd, UI_DEV_DESTROY);
    close(v->fd);
    v->fd = -1;
    LOGI("port %d disconnected -> virtual pad removed", port + 1);
}

static void emit(struct vpad *v, int slot, int type, int code, int val)
{
    if (v->last[slot] == val) return;
    v->last[slot] = val;
    struct input_event ev = {0};
    ev.type = type;
    ev.code = code;
    ev.value = val;
    if (write(v->fd, &ev, sizeof ev) < 0) { /* ignore */ }
}

static int clampi(int x, int lo, int hi) { return x < lo ? lo : x > hi ? hi : x; }

/* GC sticks travel roughly +-100 (main) / +-85 (C) from center.
 * Radial deadzone: inside it the stick reads 0; outside, the remaining travel is
 * rescaled so full tilt still reaches the edge. Y is inverted (GC up = larger). */
static void stick(int rx, int ry, int cx, int cy, int range, int *out_x, int *out_y)
{
    float x = (float) (rx - cx) / range;
    float y = (float) (cy - ry) / range;
    float mag = sqrtf(x * x + y * y);
    float dz = g_stick_dz_pct / 100.0f;
    if (mag <= dz || mag == 0.0f) {
        *out_x = *out_y = 0;
        return;
    }
    float scale = (mag - dz) / (1.0f - dz) / mag;
    *out_x = clampi((int) (x * scale * 32767), -32768, 32767);
    *out_y = clampi((int) (y * scale * 32767), -32768, 32767);
}

static int trigger(int raw, int base, int digital)
{
    if (digital) return 255;
    /* resting analog values jitter by ~+-5, so ignore the first few units */
    base += g_trigger_dz;
    int span = 225 - base;
    if (span < 64) span = 64;
    return clampi((raw - base) * 255 / span, 0, 255);
}

static void vpad_update(struct vpad *v, const struct gcpad *g)
{
    /* X+Y+Start held ~3s: recalibrate sticks/triggers (like the console) */
    if ((g->b1 & B1_X) && (g->b1 & B1_Y) && (g->b2 & B2_START)) {
        if (++v->recal_hold == RECAL_PACKETS) {
            calibrate(v, g);
            LOGI("recalibrated");
        }
    } else {
        v->recal_hold = 0;
    }

    int start = !!(g->b2 & B2_START);
    int z = !!(g->b2 & B2_Z);
    int s = 0;
    /* Face buttons. "nintendo" (default) keeps Nintendo label positions:
     * A=east, B=south, X=north, Y=west. "xbox" maps by label: A=south,
     * B=east, X=west, Y=north. Codes are what Android reads as each position. */
    int a = !!(g->b1 & B1_A), b = !!(g->b1 & B1_B);
    int x = !!(g->b1 & B1_X), y = !!(g->b1 & B1_Y);
    if (g_layout_xbox) {
        emit(v, s++, EV_KEY, BTN_A, a);
        emit(v, s++, EV_KEY, BTN_B, b);
        emit(v, s++, EV_KEY, BTN_X, x);
        emit(v, s++, EV_KEY, BTN_Y, y);
    } else {
        emit(v, s++, EV_KEY, BTN_A, b);   /* south */
        emit(v, s++, EV_KEY, BTN_B, a);   /* east  */
        emit(v, s++, EV_KEY, BTN_X, y);   /* west  */
        emit(v, s++, EV_KEY, BTN_Y, x);   /* north */
    }
    emit(v, s++, EV_KEY, BTN_TR, z);
    emit(v, s++, EV_KEY, BTN_START, start);

    int mx, my, cx, cy;
    stick(g->sx, g->sy, v->csx, v->csy, 100, &mx, &my);
    stick(g->cx, g->cy, v->ccx, v->ccy, 85, &cx, &cy);
    emit(v, s++, EV_ABS, ABS_X, mx);
    emit(v, s++, EV_ABS, ABS_Y, my);
    emit(v, s++, EV_ABS, ABS_RX, cx);
    emit(v, s++, EV_ABS, ABS_RY, cy);
    emit(v, s++, EV_ABS, ABS_Z, trigger(g->lt, v->clt, g->b2 & B2_L));
    emit(v, s++, EV_ABS, ABS_RZ, trigger(g->rt, v->crt, g->b2 & B2_R));

    int hx = (g->b1 & B1_RIGHT ? 1 : 0) - (g->b1 & B1_LEFT ? 1 : 0);
    int hy = (g->b1 & B1_DOWN ? 1 : 0) - (g->b1 & B1_UP ? 1 : 0);
    emit(v, s++, EV_ABS, ABS_HAT0X, hx);
    emit(v, s++, EV_ABS, ABS_HAT0Y, hy);

    struct input_event syn = {0};
    syn.type = EV_SYN;
    syn.code = SYN_REPORT;
    if (write(v->fd, &syn, sizeof syn) < 0) { /* ignore */ }
}

/* Returns a bitmask of ports that currently have a virtual pad (bit 0 = port 1). */
JNIEXPORT jint JNICALL
Java_io_github_aziztw_sacuma_VirtualPads_processPacket(JNIEnv *env, jobject thiz, jbyteArray packet)
{
    (void) thiz;
    uint8_t pkt[PKT_LEN];
    if ((*env)->GetArrayLength(env, packet) < PKT_LEN) return -1;
    (*env)->GetByteArrayRegion(env, packet, 0, PKT_LEN, (jbyte *) pkt);
    if (pkt[0] != 0x21) return -1;

    struct gcpad pads[NPORTS];
    parse(pkt, pads);

    if (g_recal_request) {
        g_recal_request = 0;
        for (int i = 0; i < NPORTS; i++)
            if (g_pads[i].fd >= 0 && pads[i].type) calibrate(&g_pads[i], &pads[i]);
        LOGI("recalibrated from the app");
    }

    int active = 0;
    for (int i = 0; i < NPORTS; i++) {
        struct vpad *v = &g_pads[i];
        if (pads[i].type) {
            if (v->fd >= 0 && v->missing >= REAPPEAR_RECAL) {
                /* controller reset or replugged: same pad, fresh center */
                calibrate(v, &pads[i]);
                LOGI("port %d back after %d ms -> recalibrated", i + 1, v->missing * 8);
            }
            v->missing = 0;
            if (v->fd < 0) {
                if (v->retry_wait > 0) v->retry_wait--;
                else if (vpad_create(v, i, &pads[i]) < 0) v->retry_wait = RETRY_PACKETS;
            }
            if (v->fd >= 0) vpad_update(v, &pads[i]);
        } else if (v->fd >= 0) {
            if (++v->missing >= UNPLUG_PACKETS) {
                vpad_destroy(v, i);
            } else if (v->missing == 1) {
                /* release everything: buttons up, sticks centered, triggers 0 */
                struct gcpad rest = {
                    .type = 1, .sx = v->csx, .sy = v->csy, .cx = v->ccx, .cy = v->ccy,
                    .lt = v->clt, .rt = v->crt,
                };
                vpad_update(v, &rest);
                LOGI("port %d empty -> holding pad neutral", i + 1);
            }
        }
        if (v->fd >= 0) active |= 1 << i;
    }
    return active;
}

JNIEXPORT void JNICALL
Java_io_github_aziztw_sacuma_VirtualPads_releaseAll(JNIEnv *env, jobject thiz)
{
    (void) env; (void) thiz;
    for (int i = 0; i < NPORTS; i++) {
        vpad_destroy(&g_pads[i], i);
        g_pads[i].retry_wait = 0;
    }
}

JNIEXPORT void JNICALL
Java_io_github_aziztw_sacuma_VirtualPads_setXboxLayout(JNIEnv *env, jobject thiz, jboolean xbox)
{
    (void) env; (void) thiz;
    g_layout_xbox = xbox ? 1 : 0;
}

JNIEXPORT void JNICALL
Java_io_github_aziztw_sacuma_VirtualPads_setStickDeadzone(JNIEnv *env, jobject thiz, jint percent)
{
    (void) env; (void) thiz;
    g_stick_dz_pct = clampi(percent, 0, 50);
}

JNIEXPORT void JNICALL
Java_io_github_aziztw_sacuma_VirtualPads_setTriggerDeadzone(JNIEnv *env, jobject thiz, jint units)
{
    (void) env; (void) thiz;
    g_trigger_dz = clampi(units, 0, 100);
}

/* Fills out[24] with each port's calibration: sx sy cx cy lt rt (zeros if no pad). */
JNIEXPORT void JNICALL
Java_io_github_aziztw_sacuma_VirtualPads_getCalibration(JNIEnv *env, jobject thiz, jintArray out)
{
    (void) thiz;
    jint c[NPORTS * 6] = {0};
    for (int i = 0; i < NPORTS; i++) {
        const struct vpad *v = &g_pads[i];
        if (v->fd < 0) continue;
        jint *p = c + 6 * i;
        p[0] = v->csx; p[1] = v->csy; p[2] = v->ccx; p[3] = v->ccy; p[4] = v->clt; p[5] = v->crt;
    }
    if ((*env)->GetArrayLength(env, out) >= NPORTS * 6)
        (*env)->SetIntArrayRegion(env, out, 0, NPORTS * 6, c);
}

JNIEXPORT void JNICALL
Java_io_github_aziztw_sacuma_VirtualPads_recalibrate(JNIEnv *env, jobject thiz)
{
    (void) env; (void) thiz;
    g_recal_request = 1;
}

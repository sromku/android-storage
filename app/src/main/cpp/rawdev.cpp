// JNI bridge to LibRaw: open a RAW once, then re-develop with live params, run a
// post-process pass (tone/color) on the developed pixels, and export JPEG/TIFF.
// Kotlin owns the preview Bitmap; we fill it to avoid huge copies.
#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <cmath>
#include <algorithm>
#include "libraw/libraw.h"

#define LOG_TAG "rawdev"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Param array layout (kept in sync with RawDeveloper.kt PARAM_*).
enum {
    P_EXPOSURE = 0, P_HL_MODE, P_HL_LEVEL, P_WB_MODE, P_WB_TEMP, P_WB_TINT,
    P_BRIGHT, P_DEMOSAIC, P_FBDD, P_THRESHOLD, P_COLORSPACE,
    P_CONTRAST, P_SATURATION, P_VIBRANCE, P_SHADOWS, P_BLACKS, P_HIGHLIGHTS, P_WHITES,
    P_WB_R, P_WB_G, P_WB_B,  // gray-point multipliers (wb mode 3)
    P_COUNT
};

struct RawCtx {
    LibRaw *raw = nullptr;
    libraw_processed_image_t *img = nullptr;
};

static inline float clampf(float x) { return x < 0 ? 0 : (x > 1 ? 1 : x); }

// A soft, Gaussian-masked tonal lift centred on part of the range (Lightroom-ish).
static inline float toneLift(float x, float amt, float center, float width) {
    if (amt == 0) return x;
    float d = (x - center) / width;
    return x + amt * expf(-d * d);
}

// Apply exposure/WB/highlight/demosaic/NR/colorspace onto LibRaw's params.
static void applyParams(LibRaw *raw, const float *p, int half, int outputBps) {
    auto &q = raw->imgdata.params;
    q.output_color = (int) p[P_COLORSPACE] > 0 ? (int) p[P_COLORSPACE] : 1;
    q.gamm[0] = 1.0 / 2.4; q.gamm[1] = 12.92; // sRGB transfer
    q.no_auto_bright = 1;
    q.half_size = half;
    q.user_qual = (int) p[P_DEMOSAIC];
    q.output_bps = outputBps;
    q.output_tiff = 0;
    q.bright = p[P_BRIGHT];
    q.fbdd_noiserd = (int) p[P_FBDD];
    q.threshold = p[P_THRESHOLD];
    q.med_passes = 0;

    int hlMode = (int) p[P_HL_MODE];
    q.highlight = hlMode >= 3 ? (int) p[P_HL_LEVEL] : hlMode; // 3..9 = rebuild strength

    if (p[P_EXPOSURE] != 0.0f) {
        q.exp_correc = 1;
        q.exp_shift = powf(2.0f, p[P_EXPOSURE]);
        q.exp_preser = 1.0f;
    } else {
        q.exp_correc = 0;
    }

    // White balance.
    q.use_camera_wb = 0; q.use_auto_wb = 0;
    int wb = (int) p[P_WB_MODE];
    if (wb == 0) {
        q.use_camera_wb = 1;
    } else if (wb == 1) {
        q.use_auto_wb = 1;
    } else if (wb == 3) {
        q.user_mul[0] = p[P_WB_R]; q.user_mul[1] = p[P_WB_G];
        q.user_mul[2] = p[P_WB_B]; q.user_mul[3] = p[P_WB_G];
    } else {
        // Temp/tint relative to the as-shot camera neutral.
        float base[4];
        for (int i = 0; i < 4; i++) base[i] = raw->imgdata.color.cam_mul[i] > 0 ? raw->imgdata.color.cam_mul[i] : 1.0f;
        float temp = p[P_WB_TEMP] > 1000 ? p[P_WB_TEMP] : 5500.0f;
        float warm = powf(temp / 5500.0f, 0.6f);        // higher K warms the image (raw-editor convention)
        float tint = 1.0f + (p[P_WB_TINT] / 100.0f) * 0.4f; // + = magenta bias (less green)
        q.user_mul[0] = base[0] * warm;
        q.user_mul[1] = base[1] / tint;
        q.user_mul[2] = base[2] / warm;
        q.user_mul[3] = base[3] / tint;
    }
}

// Tone + colour grade on the developed RGB buffer (8 or 16 bit), in place.
static void postProcess(libraw_processed_image_t *img, const float *p) {
    float contrast = p[P_CONTRAST] / 100.0f;
    float sat = p[P_SATURATION] / 100.0f;
    float vib = p[P_VIBRANCE] / 100.0f;
    float shadows = p[P_SHADOWS] / 100.0f * 0.35f;
    float blacks = p[P_BLACKS] / 100.0f * 0.30f;
    float highs = p[P_HIGHLIGHTS] / 100.0f * 0.35f;
    float whites = p[P_WHITES] / 100.0f * 0.30f;
    bool anyTone = shadows || blacks || highs || whites;
    bool anyColor = sat != 0 || vib != 0;
    if (!anyTone && contrast == 0 && !anyColor) return;

    int maxv = img->bits == 16 ? 65535 : 255;
    float inv = 1.0f / maxv;
    int n = img->width * img->height;

    if (img->bits == 16) {
        auto *d = reinterpret_cast<uint16_t *>(img->data);
        for (int i = 0; i < n; i++) {
            float c[3];
            for (int k = 0; k < 3; k++) c[k] = d[i * 3 + k] * inv;
            for (int k = 0; k < 3; k++) {
                float x = c[k];
                if (anyTone) {
                    x = toneLift(x, shadows, 0.25f, 0.22f);
                    x = toneLift(x, highs, 0.78f, 0.22f);
                    x = toneLift(x, blacks, 0.05f, 0.12f);
                    x = toneLift(x, whites, 0.97f, 0.12f);
                }
                if (contrast != 0) x = (x - 0.5f) * (1.0f + contrast) + 0.5f;
                c[k] = x;
            }
            if (anyColor) {
                float luma = 0.299f * c[0] + 0.587f * c[1] + 0.114f * c[2];
                float mx = std::max(c[0], std::max(c[1], c[2]));
                float mn = std::min(c[0], std::min(c[1], c[2]));
                float curSat = mx > 0 ? (mx - mn) / mx : 0;
                float amt = sat + vib * (1.0f - curSat);
                for (int k = 0; k < 3; k++) c[k] = luma + (c[k] - luma) * (1.0f + amt);
            }
            for (int k = 0; k < 3; k++) d[i * 3 + k] = (uint16_t) (clampf(c[k]) * maxv + 0.5f);
        }
    } else {
        auto *d = img->data;
        for (int i = 0; i < n; i++) {
            float c[3];
            for (int k = 0; k < 3; k++) c[k] = d[i * 3 + k] * inv;
            for (int k = 0; k < 3; k++) {
                float x = c[k];
                if (anyTone) {
                    x = toneLift(x, shadows, 0.25f, 0.22f);
                    x = toneLift(x, highs, 0.78f, 0.22f);
                    x = toneLift(x, blacks, 0.05f, 0.12f);
                    x = toneLift(x, whites, 0.97f, 0.12f);
                }
                if (contrast != 0) x = (x - 0.5f) * (1.0f + contrast) + 0.5f;
                c[k] = x;
            }
            if (anyColor) {
                float luma = 0.299f * c[0] + 0.587f * c[1] + 0.114f * c[2];
                float mx = std::max(c[0], std::max(c[1], c[2]));
                float mn = std::min(c[0], std::min(c[1], c[2]));
                float curSat = mx > 0 ? (mx - mn) / mx : 0;
                float amt = sat + vib * (1.0f - curSat);
                for (int k = 0; k < 3; k++) c[k] = luma + (c[k] - luma) * (1.0f + amt);
            }
            for (int k = 0; k < 3; k++) d[i * 3 + k] = (uint8_t) (clampf(c[k]) * maxv + 0.5f);
        }
    }
}

// Minimal baseline little-endian 16-bit RGB TIFF, single strip.
static bool writeTiff16(const char *path, const libraw_processed_image_t *img) {
    FILE *f = fopen(path, "wb");
    if (!f) return false;
    uint32_t w = img->width, h = img->height;
    uint32_t pixBytes = (uint32_t) w * h * 3 * 2;
    auto u16 = [&](uint16_t v) { fwrite(&v, 2, 1, f); };
    auto u32 = [&](uint32_t v) { fwrite(&v, 4, 1, f); };
    // Header
    fwrite("II", 1, 2, f); u16(42); u32(8);
    const uint16_t nTags = 10;
    // BitsPerSample array (3 shorts) stored right after the IFD.
    uint32_t ifdEnd = 8 + 2 + nTags * 12 + 4;
    uint32_t bpsOff = ifdEnd;
    uint32_t dataOff = bpsOff + 6;
    u16(nTags);
    auto tag = [&](uint16_t id, uint16_t type, uint32_t count, uint32_t val) { u16(id); u16(type); u32(count); u32(val); };
    tag(256, 3, 1, w);          // ImageWidth
    tag(257, 3, 1, h);          // ImageLength
    tag(258, 3, 3, bpsOff);     // BitsPerSample -> [16,16,16]
    tag(259, 3, 1, 1);          // Compression = none
    tag(262, 3, 1, 2);          // Photometric = RGB
    tag(273, 4, 1, dataOff);    // StripOffsets
    tag(277, 3, 1, 3);          // SamplesPerPixel
    tag(278, 3, 1, h);          // RowsPerStrip
    tag(279, 4, 1, pixBytes);   // StripByteCounts
    tag(284, 3, 1, 1);          // PlanarConfig = chunky
    u32(0);                     // next IFD
    u16(16); u16(16); u16(16);  // BitsPerSample values
    size_t wrote = fwrite(img->data, 1, pixBytes, f);
    fclose(f);
    return wrote == pixBytes;
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_snatik_storage_app_feature_media_RawDeveloper_nativeOpen(JNIEnv *env, jobject, jstring jpath) {
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    auto *ctx = new RawCtx();
    ctx->raw = new LibRaw();
    int ret = ctx->raw->open_file(path);
    env->ReleaseStringUTFChars(jpath, path);
    if (ret != LIBRAW_SUCCESS) { LOGE("open_file failed: %d", ret); delete ctx->raw; delete ctx; return 0; }
    ret = ctx->raw->unpack();
    if (ret != LIBRAW_SUCCESS) { LOGE("unpack failed: %d", ret); delete ctx->raw; delete ctx; return 0; }
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jlong JNICALL
Java_com_snatik_storage_app_feature_media_RawDeveloper_nativeRender(
        JNIEnv *env, jobject, jlong handle, jint half, jfloatArray jparams) {
    auto *ctx = reinterpret_cast<RawCtx *>(handle);
    if (!ctx || !ctx->raw) return 0;
    float p[P_COUNT] = {0};
    int len = env->GetArrayLength(jparams);
    env->GetFloatArrayRegion(jparams, 0, len < P_COUNT ? len : P_COUNT, p);
    applyParams(ctx->raw, p, half, 8);
    if (ctx->raw->dcraw_process() != LIBRAW_SUCCESS) { LOGE("dcraw_process failed"); return 0; }
    if (ctx->img) { LibRaw::dcraw_clear_mem(ctx->img); ctx->img = nullptr; }
    int errc = 0;
    ctx->img = ctx->raw->dcraw_make_mem_image(&errc);
    if (!ctx->img || errc != LIBRAW_SUCCESS) { LOGE("make_mem_image failed: %d", errc); return 0; }
    postProcess(ctx->img, p);
    jlong w = ctx->img->width, h = ctx->img->height;
    return (w << 32) | (h & 0xffffffffL);
}

JNIEXPORT jboolean JNICALL
Java_com_snatik_storage_app_feature_media_RawDeveloper_nativeFill(JNIEnv *env, jobject, jlong handle, jobject bitmap) {
    auto *ctx = reinterpret_cast<RawCtx *>(handle);
    if (!ctx || !ctx->img || ctx->img->colors != 3 || ctx->img->bits != 8) return JNI_FALSE;
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) return JNI_FALSE;
    if ((int) info.width != ctx->img->width || (int) info.height != ctx->img->height) return JNI_FALSE;
    void *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) return JNI_FALSE;
    const unsigned char *src = ctx->img->data;
    auto *dst = reinterpret_cast<uint32_t *>(pixels);
    const int n = ctx->img->width * ctx->img->height;
    for (int i = 0; i < n; i++) {
        uint32_t r = src[0], g = src[1], b = src[2];
        src += 3;
        dst[i] = 0xff000000u | (b << 16) | (g << 8) | r;
    }
    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_snatik_storage_app_feature_media_RawDeveloper_nativeExportTiff(
        JNIEnv *env, jobject, jlong handle, jstring jpath, jfloatArray jparams) {
    auto *ctx = reinterpret_cast<RawCtx *>(handle);
    if (!ctx || !ctx->raw) return JNI_FALSE;
    float p[P_COUNT] = {0};
    int len = env->GetArrayLength(jparams);
    env->GetFloatArrayRegion(jparams, 0, len < P_COUNT ? len : P_COUNT, p);
    applyParams(ctx->raw, p, 0, 16);
    if (ctx->raw->dcraw_process() != LIBRAW_SUCCESS) return JNI_FALSE;
    int errc = 0;
    libraw_processed_image_t *img = ctx->raw->dcraw_make_mem_image(&errc);
    if (!img || errc != LIBRAW_SUCCESS) return JNI_FALSE;
    postProcess(img, p);
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    bool ok = writeTiff16(path, img);
    env->ReleaseStringUTFChars(jpath, path);
    LibRaw::dcraw_clear_mem(img);
    return ok ? JNI_TRUE : JNI_FALSE;
}

// The camera as-shot WB multipliers, so the UI can seed a sensible neutral. Returns [r,g,b].
JNIEXPORT jfloatArray JNICALL
Java_com_snatik_storage_app_feature_media_RawDeveloper_nativeCamMul(JNIEnv *env, jobject, jlong handle) {
    auto *ctx = reinterpret_cast<RawCtx *>(handle);
    jfloatArray out = env->NewFloatArray(3);
    if (!ctx || !ctx->raw) return out;
    float m[3];
    for (int i = 0; i < 3; i++) m[i] = ctx->raw->imgdata.color.cam_mul[i];
    env->SetFloatArrayRegion(out, 0, 3, m);
    return out;
}

JNIEXPORT void JNICALL
Java_com_snatik_storage_app_feature_media_RawDeveloper_nativeClose(JNIEnv *, jobject, jlong handle) {
    auto *ctx = reinterpret_cast<RawCtx *>(handle);
    if (!ctx) return;
    if (ctx->img) LibRaw::dcraw_clear_mem(ctx->img);
    if (ctx->raw) { ctx->raw->recycle(); delete ctx->raw; }
    delete ctx;
}

} // extern "C"

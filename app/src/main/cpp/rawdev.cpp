// JNI bridge to LibRaw: open a RAW once, then re-develop with live params and
// export a 16-bit TIFF. Kotlin owns the Bitmap; we fill it to avoid huge copies.
#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <cstring>
#include <cstdlib>
#include "libraw/libraw.h"

#define LOG_TAG "rawdev"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct RawCtx {
    LibRaw *raw = nullptr;
    libraw_processed_image_t *img = nullptr;
};

// Apply the user-facing develop controls onto LibRaw's output params.
static void applyParams(LibRaw *raw, jint half, jfloat exposure, jint highlight,
                        jint wbMode, jfloat wbTemp, jfloat bright,
                        jint quality, jint outputBps, jint outputTiff) {
    auto &p = raw->imgdata.params;
    p.output_color = 1;      // sRGB
    p.gamm[0] = 1.0 / 2.4;   // sRGB gamma
    p.gamm[1] = 12.92;
    p.no_auto_bright = 1;    // we drive brightness ourselves
    p.half_size = half;
    p.user_qual = quality;   // 0 linear (fast) .. 3 AHD (quality)
    p.output_bps = outputBps;
    p.output_tiff = outputTiff;
    p.highlight = highlight;  // 0 clip, 1 unclip, 2 blend, 3-9 rebuild
    p.bright = bright;        // overall brightness multiplier

    // Exposure: exp_shift is a linear multiplier (2^stops), 0.25..8 range.
    if (exposure != 0.0f) {
        p.exp_correc = 1;
        p.exp_shift = powf(2.0f, exposure);
        p.exp_preser = 1.0f; // protect highlights while pushing exposure
    } else {
        p.exp_correc = 0;
    }

    // White balance: 0 = camera, 1 = auto, 2 = custom temp bias.
    p.use_camera_wb = 0;
    p.use_auto_wb = 0;
    if (wbMode == 0) {
        p.use_camera_wb = 1;
    } else if (wbMode == 1) {
        p.use_auto_wb = 1;
    } else {
        // Simple warm/cool bias around the camera neutral: wbTemp in [-1,1].
        float warm = 1.0f + 0.6f * wbTemp;
        float cool = 1.0f - 0.6f * wbTemp;
        p.user_mul[0] = warm; // R
        p.user_mul[1] = 1.0f; // G
        p.user_mul[2] = cool; // B
        p.user_mul[3] = 1.0f; // G2
    }
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

// Develop with the given params; returns packed dims ((w<<32)|h) or 0 on failure.
JNIEXPORT jlong JNICALL
Java_com_snatik_storage_app_feature_media_RawDeveloper_nativeRender(
        JNIEnv *, jobject, jlong handle, jint half, jfloat exposure, jint highlight,
        jint wbMode, jfloat wbTemp, jfloat bright, jint quality) {
    auto *ctx = reinterpret_cast<RawCtx *>(handle);
    if (!ctx || !ctx->raw) return 0;
    applyParams(ctx->raw, half, exposure, highlight, wbMode, wbTemp, bright, quality, 8, 0);
    int ret = ctx->raw->dcraw_process();
    if (ret != LIBRAW_SUCCESS) { LOGE("dcraw_process failed: %d", ret); return 0; }
    if (ctx->img) { LibRaw::dcraw_clear_mem(ctx->img); ctx->img = nullptr; }
    int errc = 0;
    ctx->img = ctx->raw->dcraw_make_mem_image(&errc);
    if (!ctx->img || errc != LIBRAW_SUCCESS) { LOGE("make_mem_image failed: %d", errc); return 0; }
    jlong w = ctx->img->width, h = ctx->img->height;
    return (w << 32) | (h & 0xffffffffL);
}

// Copy the last rendered image (RGB8) into a pre-sized ARGB_8888 Bitmap.
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
        dst[i] = 0xff000000u | (b << 16) | (g << 8) | r; // ARGB_8888 little-endian = RGBA bytes
    }
    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}

// Full-quality develop straight to a 16-bit TIFF on disk.
JNIEXPORT jboolean JNICALL
Java_com_snatik_storage_app_feature_media_RawDeveloper_nativeExportTiff(
        JNIEnv *env, jobject, jlong handle, jstring jpath, jfloat exposure, jint highlight,
        jint wbMode, jfloat wbTemp, jfloat bright) {
    auto *ctx = reinterpret_cast<RawCtx *>(handle);
    if (!ctx || !ctx->raw) return JNI_FALSE;
    applyParams(ctx->raw, 0, exposure, highlight, wbMode, wbTemp, bright, 3, 16, 1);
    if (ctx->raw->dcraw_process() != LIBRAW_SUCCESS) return JNI_FALSE;
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    int ret = ctx->raw->dcraw_ppm_tiff_writer(path);
    env->ReleaseStringUTFChars(jpath, path);
    return ret == LIBRAW_SUCCESS ? JNI_TRUE : JNI_FALSE;
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

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
#include <vector>
#include "libraw/libraw.h"

#define LOG_TAG "rawdev"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Param array layout (kept in sync with RawDeveloper.kt PARAM_*).
enum {
    P_EXPOSURE = 0, P_HL_MODE, P_HL_LEVEL, P_WB_MODE, P_WB_TEMP, P_WB_TINT,
    P_BRIGHT, P_DEMOSAIC, P_FBDD, P_THRESHOLD, P_COLORSPACE,
    P_CONTRAST, P_SATURATION, P_VIBRANCE, P_SHADOWS, P_BLACKS, P_HIGHLIGHTS, P_WHITES,
    P_WB_R, P_WB_G, P_WB_B,  // gray-point multipliers (wb mode 3)
    P_TEXTURE, P_CLARITY, P_DEHAZE, P_SHARPEN, P_SHARPEN_RADIUS, P_SHARPEN_MASK,
    P_VIGNETTE, P_GRAIN, P_CA_RED, P_CA_BLUE,
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
    q.aber[0] = 1.0 + (double) p[P_CA_RED] * 0.0005;   // lateral CA: red scale
    q.aber[2] = 1.0 + (double) p[P_CA_BLUE] * 0.0005;  // lateral CA: blue scale

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
// ---- HSL helpers (h in [0,360), s,l in [0,1]) ----
static void rgb2hsl(float r, float g, float b, float& h, float& s, float& l) {
    float mx = std::max(r, std::max(g, b)), mn = std::min(r, std::min(g, b));
    l = (mx + mn) * 0.5f;
    float d = mx - mn;
    if (d < 1e-6f) { h = 0; s = 0; return; }
    s = l > 0.5f ? d / (2.0f - mx - mn) : d / (mx + mn);
    if (mx == r) h = (g - b) / d + (g < b ? 6.0f : 0.0f);
    else if (mx == g) h = (b - r) / d + 2.0f;
    else h = (r - g) / d + 4.0f;
    h *= 60.0f;
}
static float hue2rgb(float p, float q, float t) {
    if (t < 0) t += 1; if (t > 1) t -= 1;
    if (t < 1.0f/6) return p + (q - p) * 6 * t;
    if (t < 0.5f) return q;
    if (t < 2.0f/3) return p + (q - p) * (2.0f/3 - t) * 6;
    return p;
}
static void hsl2rgb(float h, float s, float l, float& r, float& g, float& b) {
    if (s < 1e-6f) { r = g = b = l; return; }
    float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
    float p = 2 * l - q;
    float hn = h / 360.0f;
    r = hue2rgb(p, q, hn + 1.0f/3); g = hue2rgb(p, q, hn); b = hue2rgb(p, q, hn - 1.0f/3);
}

// The 8 HSL colour bands (Lightroom order): centre hue in degrees.
static const float HSL_CENTERS[8] = {0, 30, 60, 120, 180, 240, 285, 315};

// Everything the per-pixel pass needs, precomputed once.
struct PP {
    float contrast, sat, vib, shadows, blacks, highs, whites;
    bool anyTone, anyColor, hasCurve, hasHsl, hasVignette, hasGrain;
    const float* curve;  // 3*256 LUT (R,G,B), values 0..1
    const float* hsl;    // 24 = 8 bands * (hue,sat,lum) in -100..100
    float vignette, grain;
    int w, h; float cx, cy, invMaxR;
};

static inline uint32_t hash2(int x, int y) {
    uint32_t n = (uint32_t) x * 374761393u + (uint32_t) y * 668265263u;
    n = (n ^ (n >> 13)) * 1274126177u;
    return n ^ (n >> 16);
}

static inline void applyPixel(const PP& pp, float c[3], int x, int y) {
    // Basic tone (shadows/blacks/highlights/whites) + contrast.
    if (pp.anyTone || pp.contrast != 0) {
        for (int k = 0; k < 3; k++) {
            float v = c[k];
            if (pp.anyTone) {
                v = toneLift(v, pp.shadows, 0.25f, 0.22f);
                v = toneLift(v, pp.highs, 0.78f, 0.22f);
                v = toneLift(v, pp.blacks, 0.05f, 0.12f);
                v = toneLift(v, pp.whites, 0.97f, 0.12f);
            }
            if (pp.contrast != 0) v = (v - 0.5f) * (1.0f + pp.contrast) + 0.5f;
            c[k] = v;
        }
    }
    // Tone curve (per-channel LUT with linear interpolation).
    if (pp.hasCurve) {
        for (int k = 0; k < 3; k++) {
            float v = clampf(c[k]) * 255.0f;
            int i0 = (int) v; float fr = v - i0;
            int i1 = i0 < 255 ? i0 + 1 : 255;
            const float* lut = pp.curve + k * 256;
            c[k] = lut[i0] * (1 - fr) + lut[i1] * fr;
        }
    }
    // HSL colour mixer (per hue band).
    if (pp.hasHsl) {
        float h, s, l; rgb2hsl(clampf(c[0]), clampf(c[1]), clampf(c[2]), h, s, l);
        if (s > 0.02f) {
            float hueShift = 0, satMul = 0, lumAdd = 0, wsum = 0;
            for (int b = 0; b < 8; b++) {
                float dh = fabsf(h - HSL_CENTERS[b]);
                if (dh > 180) dh = 360 - dh;
                float w = 1.0f - dh / 45.0f;
                if (w <= 0) continue;
                wsum += w;
                hueShift += w * pp.hsl[b*3+0] * 0.35f;         // deg
                satMul   += w * pp.hsl[b*3+1] / 100.0f;
                lumAdd   += w * pp.hsl[b*3+2] / 100.0f * 0.30f;
            }
            if (wsum > 0) {
                h = fmodf(h + hueShift + 360.0f, 360.0f);
                s = clampf(s * (1.0f + satMul));
                l = clampf(l + lumAdd);
                hsl2rgb(h, s, l, c[0], c[1], c[2]);
            }
        }
    }
    // Saturation / vibrance.
    if (pp.anyColor) {
        float luma = 0.299f * c[0] + 0.587f * c[1] + 0.114f * c[2];
        float mx = std::max(c[0], std::max(c[1], c[2]));
        float mn = std::min(c[0], std::min(c[1], c[2]));
        float curSat = mx > 0 ? (mx - mn) / mx : 0;
        float amt = pp.sat + pp.vib * (1.0f - curSat);
        for (int k = 0; k < 3; k++) c[k] = luma + (c[k] - luma) * (1.0f + amt);
    }
    // Post-crop vignette (radial, darken corners for negative values).
    if (pp.hasVignette) {
        float dx = (x - pp.cx) * pp.invMaxR, dy = (y - pp.cy) * pp.invMaxR;
        float r2 = dx * dx + dy * dy;
        float f = 1.0f + pp.vignette * r2;
        for (int k = 0; k < 3; k++) c[k] *= f;
    }
    // Film grain (monochromatic, luminance-ish).
    if (pp.hasGrain) {
        float g = ((float) (hash2(x, y) & 0xffff) / 32768.0f - 1.0f) * pp.grain;
        for (int k = 0; k < 3; k++) c[k] += g;
    }
}

// Separable box blur on a single-channel float plane (radius r, in place via temp).
static void boxBlur(const float* src, float* dst, float* tmp, int w, int h, int r) {
    if (r < 1) { memcpy(dst, src, (size_t) w * h * sizeof(float)); return; }
    float norm = 1.0f / (2 * r + 1);
    for (int y = 0; y < h; y++) {                       // horizontal
        const float* srow = src + (size_t) y * w;
        float* trow = tmp + (size_t) y * w;
        float sum = 0;
        for (int x = -r; x <= r; x++) sum += srow[x < 0 ? 0 : (x >= w ? w - 1 : x)];
        for (int x = 0; x < w; x++) {
            trow[x] = sum * norm;
            int xa = x - r, xb = x + r + 1;
            sum -= srow[xa < 0 ? 0 : xa];
            sum += srow[xb >= w ? w - 1 : xb];
        }
    }
    for (int x = 0; x < w; x++) {                        // vertical
        float sum = 0;
        for (int y = -r; y <= r; y++) sum += tmp[(size_t) (y < 0 ? 0 : (y >= h ? h - 1 : y)) * w + x];
        for (int y = 0; y < h; y++) {
            dst[(size_t) y * w + x] = sum * norm;
            int ya = y - r, yb = y + r + 1;
            sum -= tmp[(size_t) (ya < 0 ? 0 : ya) * w + x];
            sum += tmp[(size_t) (yb >= h ? h - 1 : yb) * w + x];
        }
    }
}

// Presence (texture/clarity/dehaze) + sharpening: local-contrast passes that need neighbours.
static void spatialProcess(libraw_processed_image_t *img, const float *p) {
    float texture = p[P_TEXTURE] / 100.0f;
    float clarity = p[P_CLARITY] / 100.0f;
    float dehaze = p[P_DEHAZE] / 100.0f;
    float sharpen = p[P_SHARPEN] / 100.0f;
    int sharpR = (int) p[P_SHARPEN_RADIUS]; if (sharpR < 1) sharpR = 1;
    float sharpMask = p[P_SHARPEN_MASK] / 100.0f;
    if (texture == 0 && clarity == 0 && dehaze == 0 && sharpen == 0) return;

    int w = img->width, h = img->height;
    size_t n = (size_t) w * h;
    int maxv = img->bits == 16 ? 65535 : 255;
    float inv = 1.0f / maxv;

    float* luma = (float*) malloc(n * sizeof(float));
    float* blur = (float*) malloc(n * sizeof(float));
    float* tmp = (float*) malloc(n * sizeof(float));
    float* delta = (float*) calloc(n, sizeof(float));
    if (!luma || !blur || !tmp || !delta) { free(luma); free(blur); free(tmp); free(delta); return; }

    auto lumaAt = [&](size_t i) -> float {
        if (img->bits == 16) { auto* d = (uint16_t*) img->data; return (0.299f*d[i*3]+0.587f*d[i*3+1]+0.114f*d[i*3+2]) * inv; }
        auto* d = img->data; return (0.299f*d[i*3]+0.587f*d[i*3+1]+0.114f*d[i*3+2]) * inv;
    };
    for (size_t i = 0; i < n; i++) luma[i] = lumaAt(i);

    if (clarity != 0) {                                  // large-radius, midtone-weighted
        int r = std::max(4, std::min(w, h) / 40);
        boxBlur(luma, blur, tmp, w, h, r);
        for (size_t i = 0; i < n; i++) {
            float mid = 1.0f - fabsf(luma[i] - 0.5f) * 2.0f; if (mid < 0) mid = 0;
            delta[i] += clarity * (luma[i] - blur[i]) * mid * 1.4f;
        }
    }
    if (texture != 0) {                                  // fine detail
        boxBlur(luma, blur, tmp, w, h, 2);
        for (size_t i = 0; i < n; i++) delta[i] += texture * (luma[i] - blur[i]) * 1.2f;
    }
    if (sharpen != 0) {                                  // unsharp mask, edge-gated
        boxBlur(luma, blur, tmp, w, h, sharpR);
        for (size_t i = 0; i < n; i++) {
            float d = luma[i] - blur[i];
            float mask = 1.0f;
            if (sharpMask > 0) { float e = fabsf(d) * 8.0f; mask = e / (e + (1.0f - sharpMask)); }
            delta[i] += sharpen * d * mask * 1.5f;
        }
    }

    // Apply the accumulated luminance delta as a per-pixel ratio (keeps hue/sat).
    if (clarity != 0 || texture != 0 || sharpen != 0) {
        for (size_t i = 0; i < n; i++) {
            float L = luma[i], Ln = L + delta[i];
            float ratio = L > 1e-4f ? Ln / L : 1.0f;
            if (img->bits == 16) { auto* d = (uint16_t*) img->data; for (int k=0;k<3;k++){ float v=d[i*3+k]*inv*ratio; d[i*3+k]=(uint16_t)(clampf(v)*maxv+0.5f);} }
            else { auto* d = img->data; for (int k=0;k<3;k++){ float v=d[i*3+k]*inv*ratio; d[i*3+k]=(uint8_t)(clampf(v)*maxv+0.5f);} }
        }
    }

    // Dehaze: pull toward a global atmospheric light with a transmission estimate.
    if (dehaze != 0) {
        // atmospheric light ~ 95th-ish percentile luma (approx by max of the blurred plane).
        boxBlur(luma, blur, tmp, w, h, std::max(4, std::min(w, h) / 60));
        float A = 0; for (size_t i = 0; i < n; i++) if (blur[i] > A) A = blur[i];
        if (A < 0.3f) A = 0.3f;
        float strength = dehaze * 0.9f;
        for (size_t i = 0; i < n; i++) {
            float t = 1.0f - strength * (blur[i] / A);   // more haze where bright & flat
            if (t < 0.25f) t = 0.25f;
            if (img->bits == 16) { auto* d = (uint16_t*) img->data; for (int k=0;k<3;k++){ float v=d[i*3+k]*inv; v=(v-A)/t+A; d[i*3+k]=(uint16_t)(clampf(v)*maxv+0.5f);} }
            else { auto* d = img->data; for (int k=0;k<3;k++){ float v=d[i*3+k]*inv; v=(v-A)/t+A; d[i*3+k]=(uint8_t)(clampf(v)*maxv+0.5f);} }
        }
    }

    free(luma); free(blur); free(tmp); free(delta);
}

static void postProcess(libraw_processed_image_t *img, const float *p, const float *hsl, const float *curve) {
    PP pp{};
    pp.contrast = p[P_CONTRAST] / 100.0f;
    pp.sat = p[P_SATURATION] / 100.0f;
    pp.vib = p[P_VIBRANCE] / 100.0f;
    pp.shadows = p[P_SHADOWS] / 100.0f * 0.35f;
    pp.blacks = p[P_BLACKS] / 100.0f * 0.30f;
    pp.highs = p[P_HIGHLIGHTS] / 100.0f * 0.35f;
    pp.whites = p[P_WHITES] / 100.0f * 0.30f;
    pp.vignette = p[P_VIGNETTE] / 100.0f;
    pp.grain = p[P_GRAIN] / 100.0f * 0.12f;
    pp.anyTone = pp.shadows || pp.blacks || pp.highs || pp.whites;
    pp.anyColor = pp.sat != 0 || pp.vib != 0;
    pp.hasVignette = pp.vignette != 0;
    pp.hasGrain = pp.grain != 0;
    pp.curve = curve;
    pp.hsl = hsl;
    // curve is "has" if any LUT entry departs from identity; caller passes null when identity.
    pp.hasCurve = curve != nullptr;
    pp.hasHsl = false;
    if (hsl != nullptr) for (int i = 0; i < 24; i++) if (hsl[i] != 0) { pp.hasHsl = true; break; }

    pp.w = img->width; pp.h = img->height;
    pp.cx = img->width * 0.5f; pp.cy = img->height * 0.5f;
    pp.invMaxR = 1.0f / sqrtf(pp.cx * pp.cx + pp.cy * pp.cy);

    if (!pp.anyTone && pp.contrast == 0 && !pp.anyColor && !pp.hasCurve && !pp.hasHsl && !pp.hasVignette && !pp.hasGrain) return;

    int maxv = img->bits == 16 ? 65535 : 255;
    float inv = 1.0f / maxv;
    int w = img->width, h = img->height;

    if (img->bits == 16) {
        auto *d = reinterpret_cast<uint16_t *>(img->data);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = (y * w + x) * 3;
            float c[3] = { d[i] * inv, d[i+1] * inv, d[i+2] * inv };
            applyPixel(pp, c, x, y);
            for (int k = 0; k < 3; k++) d[i+k] = (uint16_t) (clampf(c[k]) * maxv + 0.5f);
        }
    } else {
        auto *d = img->data;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = (y * w + x) * 3;
            float c[3] = { d[i] * inv, d[i+1] * inv, d[i+2] * inv };
            applyPixel(pp, c, x, y);
            for (int k = 0; k < 3; k++) d[i+k] = (uint8_t) (clampf(c[k]) * maxv + 0.5f);
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


// ---- Minimal raw (mosaiced) DNG writer, built from LibRaw's unpacked sensor data ----
namespace {
struct Tag { uint16_t id, type; uint32_t count; uint32_t inlineVal; bool useOverflow; uint32_t ovOff; };
struct DngWriter {
    std::vector<Tag> tags;
    std::vector<uint8_t> overflow;
    static uint32_t typeSize(uint16_t t) {
        switch (t) { case 1: case 2: return 1; case 3: return 2; case 4: return 4; case 5: case 10: return 8; default: return 1; }
    }
    void put(std::vector<uint8_t>&v, const void*p, size_t n){ const uint8_t*b=(const uint8_t*)p; v.insert(v.end(), b, b+n); }
    // add a tag; data points to count*typeSize bytes already little-endian
    void add(uint16_t id, uint16_t type, uint32_t count, const void* data) {
        uint32_t bytes = count * typeSize(type);
        Tag t{id, type, count, 0, false, 0};
        if (bytes <= 4) {
            memcpy(&t.inlineVal, data, bytes);
        } else {
            t.useOverflow = true;
            t.ovOff = (uint32_t) overflow.size();
            const uint8_t* b = (const uint8_t*) data;
            overflow.insert(overflow.end(), b, b + bytes);
            if (overflow.size() & 1) overflow.push_back(0);
        }
        tags.push_back(t);
    }
    void addShort(uint16_t id, uint16_t v){ uint16_t a=v; add(id,3,1,&a); }
    void addLong(uint16_t id, uint32_t v){ add(id,4,1,&v); }
    void addAscii(uint16_t id, const char* s){ uint32_t n=(uint32_t)strlen(s)+1; add(id,2,n,s); }
    void addBytes(uint16_t id, const uint8_t* b, uint32_t n){ add(id,1,n,b); }
    void addShorts(uint16_t id, const uint16_t* b, uint32_t n){ add(id,3,n,b); }
    void addSRational(uint16_t id, const int32_t* nd, uint32_t pairs){ add(id,10,pairs,nd); }
    void addRational(uint16_t id, const uint32_t* nd, uint32_t pairs){ add(id,5,pairs,nd); }
};
}

static bool writeDng(LibRaw* raw, const char* path) {
    auto& S = raw->imgdata.sizes;
    auto& C = raw->imgdata.color;
    auto& I = raw->imgdata.idata;
    unsigned filters = I.filters;
    if (filters == 0 || filters == 9) { LOGE("dng: unsupported filters=%u", filters); return false; } // only Bayer
    const ushort* rawimg = raw->imgdata.rawdata.raw_image;
    if (!rawimg) { LOGE("dng: raw_image is null"); return false; }

    int rw = S.raw_width, W = S.width, H = S.height;
    int top = S.top_margin, left = S.left_margin;
    if (W <= 0 || H <= 0 || rw <= 0) { LOGE("dng: bad size W=%d H=%d rw=%d", W, H, rw); return false; }

    // CFA 2x2 pattern at the active-area origin, mapped to DNG 0=R,1=G,2=B.
    auto FC = [&](int r, int c) { return (filters >> ((((r << 1) & 14) | (c & 1)) << 1)) & 3; };
    auto toRGB = [&](int fcIdx) -> uint8_t {
        char ch = I.cdesc[fcIdx];
        return ch == 'R' ? 0 : (ch == 'B' ? 2 : 1);
    };
    uint8_t cfa[4] = {
        toRGB(FC(top, left)), toRGB(FC(top, left + 1)),
        toRGB(FC(top + 1, left)), toRGB(FC(top + 1, left + 1)),
    };

    // Levels.
    uint32_t white = C.maximum > 0 ? C.maximum : 16383;
    uint32_t cbAvg = (C.cblack[0] + C.cblack[1] + C.cblack[2] + C.cblack[3]) / 4;
    uint32_t black = C.black + cbAvg;

    // ColorMatrix1 = XYZ->camera (LibRaw cam_xyz), SRATIONAL /10000.
    int32_t cm[18]; int has = 0;
    for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) { cm[2*(i*3+j)] = (int32_t) lroundf(C.cam_xyz[i][j] * 10000.0f); cm[2*(i*3+j)+1] = 10000; if (C.cam_xyz[i][j] != 0) has = 1; }

    // AsShotNeutral = normalize(1/cam_mul), RATIONAL.
    uint32_t asn[6];
    float g = C.cam_mul[1] > 0 ? C.cam_mul[1] : 1.0f;
    for (int i = 0; i < 3; i++) {
        float m = C.cam_mul[i] > 0 ? C.cam_mul[i] : 1.0f;
        float neutral = g / m;            // green normalized to 1
        asn[2*i] = (uint32_t) lroundf(neutral * 10000.0f); asn[2*i+1] = 10000;
    }

    // Camera crop rectangle (relative to the full raw frame) -> relative to our written image.
    auto& crop = S.raw_inset_crops[0];
    uint32_t cropX = 0, cropY = 0, cropW = (uint32_t) W, cropH = (uint32_t) H;
    if (crop.cwidth > 0 && crop.cheight > 0 && crop.cwidth <= W && crop.cheight <= H) {
        cropX = crop.cleft >= left ? (uint32_t)(crop.cleft - left) : 0;
        cropY = crop.ctop >= top ? (uint32_t)(crop.ctop - top) : 0;
        cropW = crop.cwidth; cropH = crop.cheight;
    }

    DngWriter d;
    d.addLong(254, 0);                              // NewSubfileType
    d.addLong(256, (uint32_t) W);                   // ImageWidth
    d.addLong(257, (uint32_t) H);                   // ImageLength
    d.addShort(258, 16);                            // BitsPerSample
    d.addShort(259, 1);                             // Compression = none
    d.addShort(262, 32803);                         // Photometric = CFA
    if (I.make[0]) d.addAscii(271, I.make);         // Make
    if (I.model[0]) d.addAscii(272, I.model);       // Model
    d.addLong(273, 0);                              // StripOffsets (patched below) -> index tracked
    size_t stripTagIdx = d.tags.size() - 1;
    d.addShort(277, 1);                             // SamplesPerPixel
    d.addLong(278, (uint32_t) H);                   // RowsPerStrip
    d.addLong(279, (uint32_t) (W * H * 2));         // StripByteCounts
    uint16_t cfaDim[2] = {2, 2};
    d.addShorts(33421, cfaDim, 2);                  // CFARepeatPatternDim
    d.addBytes(33422, cfa, 4);                      // CFAPattern
    uint8_t dngv[4] = {1, 4, 0, 0};  d.addBytes(50706, dngv, 4);   // DNGVersion
    uint8_t dngb[4] = {1, 1, 0, 0};  d.addBytes(50707, dngb, 4);   // DNGBackwardVersion
    char ucm[130]; snprintf(ucm, sizeof(ucm), "%s %s", I.make, I.model); d.addAscii(50708, ucm); // UniqueCameraModel
    uint8_t planeColor[3] = {0, 1, 2}; d.addBytes(50710, planeColor, 3); // CFAPlaneColor
    d.addShort(50711, 1);                           // CFALayout = rectangular
    uint16_t blDim[2] = {1, 1}; d.addShorts(50713, blDim, 2);      // BlackLevelRepeatDim
    d.addLong(50714, black);                        // BlackLevel
    d.addLong(50717, white);                        // WhiteLevel
    uint32_t dco[4] = {cropX, 1, cropY, 1};         // DefaultCropOrigin (x,y) RATIONAL
    d.addRational(50719, dco, 2);
    uint32_t dcs[4] = {cropW, 1, cropH, 1};         // DefaultCropSize (w,h) RATIONAL
    d.addRational(50720, dcs, 2);
    if (has) d.addSRational(50721, cm, 9);          // ColorMatrix1
    d.addRational(50728, asn, 3);                   // AsShotNeutral
    d.addShort(50778, 21);                          // CalibrationIlluminant1 = D65
    uint32_t active[4] = {0, 0, (uint32_t) H, (uint32_t) W}; // ActiveArea = whole written image
    d.add(50829, 4, 4, active);

    // Layout: header(8) + IFD + overflow + strip.
    uint32_t nTags = (uint32_t) d.tags.size();
    uint32_t ifdEnd = 8 + 2 + nTags * 12 + 4;
    uint32_t ovBase = ifdEnd;
    uint32_t stripOff = ovBase + (uint32_t) d.overflow.size();
    if (stripOff & 1) stripOff++;
    // patch StripOffsets inline value
    memcpy(&d.tags[stripTagIdx].inlineVal, &stripOff, 4);

    FILE* f = fopen(path, "wb");
    if (!f) { LOGE("dng: fopen failed %s", path); return false; }
    auto u16 = [&](uint16_t v){ fwrite(&v, 2, 1, f); };
    auto u32 = [&](uint32_t v){ fwrite(&v, 4, 1, f); };
    fwrite("II", 1, 2, f); u16(42); u32(8);
    u16((uint16_t) nTags);
    for (auto& t : d.tags) {
        u16(t.id); u16(t.type); u32(t.count);
        uint32_t val = t.useOverflow ? (ovBase + t.ovOff) : t.inlineVal;
        u32(val);
    }
    u32(0); // next IFD
    fwrite(d.overflow.data(), 1, d.overflow.size(), f);
    // pad to stripOff
    long pos = ftell(f);
    while (pos < (long) stripOff) { fputc(0, f); pos++; }
    // strip data: active-area CFA, cropped from the full raw frame.
    std::vector<uint16_t> row(W);
    for (int r = 0; r < H; r++) {
        const ushort* src = rawimg + (size_t)(r + top) * rw + left;
        memcpy(row.data(), src, (size_t) W * 2);
        fwrite(row.data(), 2, W, f);
    }
    fclose(f);
    return true;
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
        JNIEnv *env, jobject, jlong handle, jint half, jfloatArray jparams, jfloatArray jhsl, jfloatArray jcurve) {
    auto *ctx = reinterpret_cast<RawCtx *>(handle);
    if (!ctx || !ctx->raw) return 0;
    float p[P_COUNT] = {0};
    int len = env->GetArrayLength(jparams);
    env->GetFloatArrayRegion(jparams, 0, len < P_COUNT ? len : P_COUNT, p);
    float hsl[24] = {0}; const float* hslp = nullptr;
    if (jhsl && env->GetArrayLength(jhsl) >= 24) { env->GetFloatArrayRegion(jhsl, 0, 24, hsl); hslp = hsl; }
    float curve[768]; const float* curvep = nullptr;
    if (jcurve && env->GetArrayLength(jcurve) >= 768) { env->GetFloatArrayRegion(jcurve, 0, 768, curve); curvep = curve; }
    applyParams(ctx->raw, p, half, 8);
    if (ctx->raw->dcraw_process() != LIBRAW_SUCCESS) { LOGE("dcraw_process failed"); return 0; }
    if (ctx->img) { LibRaw::dcraw_clear_mem(ctx->img); ctx->img = nullptr; }
    int errc = 0;
    ctx->img = ctx->raw->dcraw_make_mem_image(&errc);
    if (!ctx->img || errc != LIBRAW_SUCCESS) { LOGE("make_mem_image failed: %d", errc); return 0; }
    spatialProcess(ctx->img, p);
    postProcess(ctx->img, p, hslp, curvep);
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
        JNIEnv *env, jobject, jlong handle, jstring jpath, jfloatArray jparams, jfloatArray jhsl, jfloatArray jcurve) {
    auto *ctx = reinterpret_cast<RawCtx *>(handle);
    if (!ctx || !ctx->raw) return JNI_FALSE;
    float p[P_COUNT] = {0};
    int len = env->GetArrayLength(jparams);
    env->GetFloatArrayRegion(jparams, 0, len < P_COUNT ? len : P_COUNT, p);
    float hsl[24] = {0}; const float* hslp = nullptr;
    if (jhsl && env->GetArrayLength(jhsl) >= 24) { env->GetFloatArrayRegion(jhsl, 0, 24, hsl); hslp = hsl; }
    float curve[768]; const float* curvep = nullptr;
    if (jcurve && env->GetArrayLength(jcurve) >= 768) { env->GetFloatArrayRegion(jcurve, 0, 768, curve); curvep = curve; }
    applyParams(ctx->raw, p, 0, 16);
    if (ctx->raw->dcraw_process() != LIBRAW_SUCCESS) return JNI_FALSE;
    int errc = 0;
    libraw_processed_image_t *img = ctx->raw->dcraw_make_mem_image(&errc);
    if (!img || errc != LIBRAW_SUCCESS) return JNI_FALSE;
    spatialProcess(img, p);
    postProcess(img, p, hslp, curvep);
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

JNIEXPORT jboolean JNICALL
Java_com_snatik_storage_app_feature_media_RawDeveloper_nativeExportDng(
        JNIEnv *env, jobject, jstring jsrc, jstring jdst) {
    // Fresh unpack so the CFA/black data is pristine (the live session has been dcraw_process'd).
    const char *src = env->GetStringUTFChars(jsrc, nullptr);
    const char *dst = env->GetStringUTFChars(jdst, nullptr);
    LibRaw tmp;
    bool ok = false;
    if (tmp.open_file(src) == LIBRAW_SUCCESS && tmp.unpack() == LIBRAW_SUCCESS) {
        ok = writeDng(&tmp, dst);
    } else {
        LOGE("dng: open/unpack failed for %s", src);
    }
    tmp.recycle();
    env->ReleaseStringUTFChars(jsrc, src);
    env->ReleaseStringUTFChars(jdst, dst);
    return ok ? JNI_TRUE : JNI_FALSE;
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

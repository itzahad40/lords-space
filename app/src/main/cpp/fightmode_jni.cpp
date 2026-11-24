// Lord's-space Fight Mode: JNI glue. Runs inside the guest app's process (hooked AudioRecord.read).
#include <jni.h>
#include <atomic>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <vector>

#include "FightModeDsp.h"

using namespace fightmode;

namespace {

constexpr int kMaxChannels = 2;

struct SharedParams {
    std::atomic<bool> enabled{false};
    std::atomic<float> gainDb{0.0f};
    std::atomic<float> volume{1.0f};
    std::atomic<float> eq[6];
    std::atomic<float> clarity{0.0f};
    std::atomic<float> punch{0.0f};
    SharedParams() { for (auto &e : eq) e.store(0.0f); }
};

SharedParams gParams;
std::mutex gDspLock;
FightModeDsp gDsp[kMaxChannels];
int gDspRate = 0;

FightModeParams snapshot() {
    FightModeParams p;
    p.enabled = gParams.enabled.load();
    p.gainDb = gParams.gainDb.load();
    p.volume = gParams.volume.load();
    for (int i = 0; i < 6; i++) p.eqDb[i] = gParams.eq[i].load();
    p.clarity = gParams.clarity.load();
    p.punch = gParams.punch.load();
    return p;
}

// samples: interleaved float in int16 scale. Processes every channel with its own filter state.
void processInterleaved(float *samples, int frames, int channels, int rate) {
    if (frames <= 0 || channels <= 0 || rate <= 0) return;
    const FightModeParams p = snapshot();
    std::lock_guard<std::mutex> guard(gDspLock);
    if (rate != gDspRate) {          // sample rate changed: drop filter state
        for (auto &d : gDsp) d = FightModeDsp();
        gDspRate = rate;
    }
    const int used = channels < kMaxChannels ? channels : kMaxChannels;
    std::vector<float> mono(frames);
    float level = 0.0f;
    for (int c = 0; c < used; c++) {
        for (int i = 0; i < frames; i++) mono[i] = samples[i * channels + c];
        level = std::max(level, gDsp[c].process(mono.data(), frames, rate, p));
        for (int i = 0; i < frames; i++) samples[i * channels + c] = mono[i];
    }
    fightModeLevelStore().store(level);
}

inline int16_t toS16(float v) {
    if (v > 32767.0f) v = 32767.0f;
    if (v < -32768.0f) v = -32768.0f;
    return (int16_t)(v >= 0 ? v + 0.5f : v - 0.5f);
}

void processS16(int16_t *data, int samples, int channels, int rate) {
    const int frames = samples / channels;
    std::vector<float> f(frames * channels);
    for (int i = 0; i < frames * channels; i++) f[i] = (float)data[i];
    processInterleaved(f.data(), frames, channels, rate);
    for (int i = 0; i < frames * channels; i++) data[i] = toS16(f[i]);
}

} // namespace

extern "C" {

JNIEXPORT void JNICALL Java_org_lordspace_fightmode_FightModeNative_setParams(
        JNIEnv *env, jclass, jboolean enabled, jfloat gainDb, jfloat volume, jfloatArray eq8) {
    float v[8] = {0, 0, 0, 0, 0, 0, 0, 0};
    if (eq8 != nullptr) {
        jsize len = env->GetArrayLength(eq8);
        if (len > 8) len = 8;
        env->GetFloatArrayRegion(eq8, 0, len, v);
    }
    gParams.gainDb.store(gainDb);
    gParams.volume.store(volume);
    for (int i = 0; i < 6; i++) gParams.eq[i].store(v[i]);
    gParams.clarity.store(v[6]);
    gParams.punch.store(v[7]);
    gParams.enabled.store(enabled == JNI_TRUE);
}

// AudioRecord.read(short[] ...): processes `count` shorts starting at `offset`.
JNIEXPORT void JNICALL Java_org_lordspace_fightmode_FightModeNative_processShorts(
        JNIEnv *env, jclass, jshortArray arr, jint offset, jint count, jint channels, jint rate) {
    if (arr == nullptr || count <= 0 || offset < 0 || channels <= 0) return;
    if (offset + count > env->GetArrayLength(arr)) return;
    std::vector<int16_t> buf(count);
    env->GetShortArrayRegion(arr, offset, count, buf.data());
    processS16(buf.data(), count, channels, rate);
    env->SetShortArrayRegion(arr, offset, count, buf.data());
}

// AudioRecord.read(byte[] ...): 16-bit little-endian PCM, `count` is in bytes.
JNIEXPORT void JNICALL Java_org_lordspace_fightmode_FightModeNative_processBytes(
        JNIEnv *env, jclass, jbyteArray arr, jint offset, jint count, jint channels, jint rate) {
    if (arr == nullptr || count < 2 || offset < 0 || channels <= 0) return;
    if (offset + count > env->GetArrayLength(arr)) return;
    const int samples = count / 2;
    std::vector<int16_t> buf(samples);
    env->GetByteArrayRegion(arr, offset, samples * 2, reinterpret_cast<jbyte *>(buf.data()));
    processS16(buf.data(), samples, channels, rate);
    env->SetByteArrayRegion(arr, offset, samples * 2, reinterpret_cast<const jbyte *>(buf.data()));
}

// AudioRecord.read(ByteBuffer ...): direct buffer, `position` and `count` in bytes (what WebRTC uses).
JNIEXPORT void JNICALL Java_org_lordspace_fightmode_FightModeNative_processDirect(
        JNIEnv *env, jclass, jobject buffer, jint position, jint count, jint channels, jint rate) {
    if (buffer == nullptr || count < 2 || position < 0 || channels <= 0) return;
    auto *base = static_cast<uint8_t *>(env->GetDirectBufferAddress(buffer));
    if (base == nullptr) return;                       // heap buffer: not supported here
    if ((jlong)position + count > env->GetDirectBufferCapacity(buffer)) return;
    processS16(reinterpret_cast<int16_t *>(base + position), count / 2, channels, rate);
}

// AudioRecord.read(float[] ...): samples are -1..1.
JNIEXPORT void JNICALL Java_org_lordspace_fightmode_FightModeNative_processFloats(
        JNIEnv *env, jclass, jfloatArray arr, jint offset, jint count, jint channels, jint rate) {
    if (arr == nullptr || count <= 0 || offset < 0 || channels <= 0) return;
    if (offset + count > env->GetArrayLength(arr)) return;
    std::vector<float> buf(count);
    env->GetFloatArrayRegion(arr, offset, count, buf.data());
    for (auto &s : buf) s *= 32768.0f;
    processInterleaved(buf.data(), count / channels, channels, rate);
    for (auto &s : buf) { s /= 32768.0f; if (s > 1.0f) s = 1.0f; if (s < -1.0f) s = -1.0f; }
    env->SetFloatArrayRegion(arr, offset, count, buf.data());
}

JNIEXPORT jfloat JNICALL Java_org_lordspace_fightmode_FightModeNative_getLevel(JNIEnv *, jclass) {
    return fightModeLevelStore().load();
}

} // extern "C"

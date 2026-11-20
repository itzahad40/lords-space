// Lord's-space "Fight mode" mic processing (loud, clear PA / announcement style voice).
// Header-only so it can be unit-tested without WebRTC.
// Chain: high-pass 40 Hz -> noise gate (fades in from 18 dB gain, full at 36 dB) -> 6-band EQ -> clarity (cut mud, boost presence)
//        -> punch (compressor) -> gain (dB) -> volume -> soft limiter (never exceeds int16 range).
#ifndef LORDSPACE_FIGHT_MODE_DSP_H
#define LORDSPACE_FIGHT_MODE_DSP_H

#include <algorithm>
#include <atomic>
#include <cmath>

namespace fightmode {

// Last output level (0..1) of the fight mode chain, readable from any thread (UI level meter).
inline std::atomic<float> &fightModeLevelStore() {
    static std::atomic<float> level{0.0f};
    return level;
}

struct FightModeParams {
    bool enabled = false;
    float gainDb = 0.0f;      // 0 .. 100
    float volume = 1.0f;      // 0 .. 1 (master output)
    float eqDb[6] = {0, 0, 0, 0, 0, 0}; // -12 .. +12 at 60 / 400 / 1000 / 2500 / 6300 / 16000 Hz
    float clarity = 0.0f;     // 0 .. 1  (cuts 250 Hz mud, boosts 3.2 kHz presence)
    float punch = 0.0f;       // 0 .. 1  (compressor amount: evens out quiet and loud words)
};

// Works on float samples in int16 scale (-32768..32768), mono.
class FightModeDsp {
public:
    static constexpr int kBands = 6;
    static constexpr float kMaxGainDb = 100.0f;
    static constexpr float kMaxEqDb = 12.0f;

    // Processes one frame in place. Returns the output peak (0..1), 0 when bypassed.
    float process(float *samples, int count, int sampleRate, const FightModeParams &p) {
        if (!samples || count <= 0 || sampleRate <= 0) {
            return 0.0f;
        }
        if (!p.enabled && _mix <= 0.0f) {
            resetState();
            return 0.0f;
        }

        const float gainDb = clampf(p.gainDb, 0.0f, kMaxGainDb);
        const float targetGain = std::pow(10.0f, gainDb / 20.0f);
        const float targetVolume = clampf(p.volume, 0.0f, 1.0f);
        const float clarity = clampf(p.clarity, 0.0f, 1.0f);
        updateEq(p, sampleRate);
        updateClarity(clarity, sampleRate);

        const float fadeStep = 1.0f / (0.020f * (float)sampleRate);                    // 20 ms wet/dry fade
        const float smooth = 1.0f - std::exp(-1.0f / (0.010f * (float)sampleRate));    // 10 ms param smoothing
        const float hpCoef = std::exp(-2.0f * 3.14159265f * 40.0f / (float)sampleRate);

        // Noise gate: suppresses mic hiss between words without compression, limiting, or clipping.
        // It fades in from normal gain and reaches full attenuation at high gain.
        const float gateAmount = clampf((gainDb - 18.0f) / 18.0f, 0.0f, 1.0f);
        const float envRelease = std::exp(-1.0f / (0.050f * (float)sampleRate));
        const float gateAttack = 1.0f - std::exp(-1.0f / (0.002f * (float)sampleRate));
        const float gateRelease = 1.0f - std::exp(-1.0f / (0.060f * (float)sampleRate));
        const float kGateThreshold = 90.0f; // int16 scale, about -51 dBFS; above the mic noise floor
        // Punch: feed-forward compressor, threshold about -36 dBFS, ratio 1:1 .. 8:1, with half auto makeup.
        const float punch = clampf(p.punch, 0.0f, 1.0f);
        const float ratio = 1.0f + 7.0f * punch;
        const float kCompThresholdDb = -36.0f;
        const float makeupDb = -kCompThresholdDb * (1.0f - 1.0f / ratio) * 0.5f;
        const float compAttack = 1.0f - std::exp(-1.0f / (0.005f * (float)sampleRate));
        const float compRelease = 1.0f - std::exp(-1.0f / (0.080f * (float)sampleRate));
        float peak = 0.0f;
        for (int i = 0; i < count; i++) {
            _mix = clampf(_mix + (p.enabled ? fadeStep : -fadeStep), 0.0f, 1.0f);
            _gain += (targetGain - _gain) * smooth;
            _volume += (targetVolume - _volume) * smooth;

            const float dry = samples[i];

            // high-pass at 40 Hz: removes DC / rumble before the gain stage
            float x = hpCoef * (_hpPrevOut + dry - _hpPrevIn);
            _hpPrevIn = dry;
            _hpPrevOut = x;

            // noise gate (follows the level before any gain)
            _env = std::max(std::fabs(x), _env * envRelease);
            const float gateTarget = _env > kGateThreshold ? 1.0f : 0.0f;
            _gate += (gateTarget - _gate) * (gateTarget > _gate ? gateAttack : gateRelease);
            x *= 1.0f - gateAmount * (1.0f - _gate);

            // 6-band peaking EQ
            for (int b = 0; b < kBands; b++) {
                if (_bandActive[b]) {
                    x = runBiquad(_bq[b], x);
                }
            }

            // clarity: cut mud, boost presence
            if (_clarityActive) {
                x = runBiquad(_clarityBq[0], x);
                x = runBiquad(_clarityBq[1], x);
            }
            // punch: compressor on the level after EQ/clarity, before the big gain stage
            if (punch > 0.01f) {
                const float lvl = std::fabs(x) / 32768.0f;
                _compEnv += (lvl - _compEnv) * (lvl > _compEnv ? compAttack : compRelease);
                const float envDb = 20.0f * std::log10(std::max(_compEnv, 1e-6f));
                const float over = std::max(envDb - kCompThresholdDb, 0.0f);
                const float reductionDb = over * (1.0f - 1.0f / ratio);
                x *= std::pow(10.0f, (makeupDb - reductionDb) / 20.0f);
            }
            float wet = (x / 32768.0f) * _gain * _volume;   // normalised, may be far above 1.0 at high gain
            wet = softLimit(wet) * 32767.0f;                 // never leaves the int16 range
            const float out = dry + (wet - dry) * _mix;
            samples[i] = out;
            peak = std::max(peak, std::fabs(out));
        }
        return std::min(peak / 32768.0f, 1.0f);
    }

private:
    struct Biquad {
        float b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0, z1 = 0, z2 = 0;
    };

    static float runBiquad(Biquad &q, float x) {
        const float y = q.b0 * x + q.z1;
        q.z1 = q.b1 * x - q.a1 * y + q.z2;
        q.z2 = q.b2 * x - q.a2 * y;
        return y;
    }

    // Linear up to 0.7 full scale, then a smooth tanh knee towards +-1.0 (no hard square-wave clipping).
    static float softLimit(float v) {
        const float a = std::fabs(v);
        if (a <= 0.7f) {
            return v;
        }
        const float y = 0.7f + 0.3f * std::tanh((a - 0.7f) / 0.3f);
        return v < 0.0f ? -y : y;
    }

    static float clampf(float v, float lo, float hi) {
        return std::min(std::max(v, lo), hi);
    }

    // RBJ peaking EQ
    static void designPeaking(Biquad &q, float freq, float db, float Q, int sampleRate) {
        const float A = std::pow(10.0f, db / 40.0f);
        const float w0 = 2.0f * 3.14159265f * freq / (float)sampleRate;
        const float alpha = std::sin(w0) / (2.0f * Q);
        const float cs = std::cos(w0);
        const float a0 = 1.0f + alpha / A;
        q.b0 = (1.0f + alpha * A) / a0;
        q.b1 = (-2.0f * cs) / a0;
        q.b2 = (1.0f - alpha * A) / a0;
        q.a1 = (-2.0f * cs) / a0;
        q.a2 = (1.0f - alpha / A) / a0;
    }

    void resetState() {
        _hpPrevIn = _hpPrevOut = 0.0f;
        _gain = 1.0f;
        _env = 0.0f;
        _gate = 1.0f;
        _compEnv = 0.0f;
        for (auto &q : _bq) {
            q.z1 = q.z2 = 0.0f;
        }
        for (auto &q : _clarityBq) {
            q.z1 = q.z2 = 0.0f;
        }
    }

    void updateEq(const FightModeParams &p, int sampleRate) {
        static const float kFreq[kBands] = {60.f, 400.f, 1000.f, 2500.f, 6300.f, 16000.f};
        for (int b = 0; b < kBands; b++) {
            const float db = clampf(p.eqDb[b], -kMaxEqDb, kMaxEqDb);
            const bool active = std::fabs(db) >= 0.05f && kFreq[b] < 0.45f * (float)sampleRate;
            if (!active) {
                _bandActive[b] = false;
                _cachedDb[b] = 0.0f;
                _bq[b].z1 = _bq[b].z2 = 0.0f;
                continue;
            }
            if (_bandActive[b] && _cachedDb[b] == db && _cachedRate == sampleRate) {
                continue;
            }
            designPeaking(_bq[b], kFreq[b], db, 1.0f, sampleRate);
            _bandActive[b] = true;
            _cachedDb[b] = db;
        }
        _cachedRate = sampleRate;
    }

    void updateClarity(float clarity, int sampleRate) {
        if (clarity < 0.01f) {
            _clarityActive = false;
            _cachedClarity = 0.0f;
            _clarityBq[0].z1 = _clarityBq[0].z2 = _clarityBq[1].z1 = _clarityBq[1].z2 = 0.0f;
            return;
        }
        if (_clarityActive && _cachedClarity == clarity && _clarityRate == sampleRate) {
            return;
        }
        designPeaking(_clarityBq[0], 250.0f, -4.0f * clarity, 0.8f, sampleRate);   // less mud / boominess
        designPeaking(_clarityBq[1], 3200.0f, 9.0f * clarity, 0.9f, sampleRate);   // presence: words cut through
        _clarityActive = true;
        _cachedClarity = clarity;
        _clarityRate = sampleRate;
    }

    float _mix = 0.0f;
    float _gain = 1.0f;
    float _volume = 1.0f;
    float _hpPrevIn = 0.0f;
    float _hpPrevOut = 0.0f;
    float _env = 0.0f;
    float _gate = 1.0f;
    float _compEnv = 0.0f;
    Biquad _bq[kBands];
    Biquad _clarityBq[2];
    bool _bandActive[kBands] = {false, false, false, false, false, false};
    float _cachedDb[kBands] = {0, 0, 0, 0, 0, 0};
    int _cachedRate = 0;
    bool _clarityActive = false;
    float _cachedClarity = 0.0f;
    int _clarityRate = 0;
};

} // namespace fightmode

#endif


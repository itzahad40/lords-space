package org.waxmoon.fightmode;

import android.util.Log;

/** JNI bridge to the native Fight Mode DSP (app/src/main/cpp). */
public final class FightModeNative {
    private static final String TAG = "FightMode";
    private static boolean available;

    static {
        try {
            System.loadLibrary("fightmode");
            available = true;
        } catch (Throwable e) {
            Log.e(TAG, "libfightmode not loaded", e);
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    /** eq8 = 6 EQ bands (dB), clarity (0..1), punch (0..1). */
    public static native void setParams(boolean enabled, float gainDb, float volume, float[] eq8);

    public static native void processShorts(short[] data, int offset, int count, int channels, int rate);

    public static native void processBytes(byte[] data, int offset, int count, int channels, int rate);

    /** Direct ByteBuffer, position and count in bytes. */
    public static native void processDirect(java.nio.ByteBuffer buffer, int position, int count, int channels, int rate);

    public static native void processFloats(float[] data, int offset, int count, int channels, int rate);

    public static native float getLevel();

    private FightModeNative() {}
}

package org.waxmoon.fightmode;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;

/**
 * Runs INSIDE a guest app's process (installed from the engine's ApplicationCallback).
 * Hooks the leaf variants of android.media.AudioRecord.read(...) and runs the recorded samples through
 * the native Fight Mode DSP before the app sees them. The 2/3-argument read() overloads delegate to these
 * leaf methods, so hooking only the leaves avoids processing a buffer twice.
 *
 * Covers apps that record through the Java AudioRecord API (Telegram/WebRTC does). Apps that record through
 * native AAudio / OpenSL ES (parts of Discord, WhatsApp, games) never call these methods and are not affected.
 */
public final class FightModeHook {
    private static final String TAG = "FightMode";
    private static boolean installed;

    public static synchronized void install(Context hostContext) {
        if (installed) {
            return;
        }
        installed = true;
        if (!FightModeNative.isAvailable()) {
            Log.w(TAG, "native DSP missing, hook not installed");
            return;
        }
        FightModeConfig.init(hostContext);
        int ok = 0;
        String error = "";
        try {
            ok += hook(byte[].class, int.class, int.class, int.class);          // read(byte[], off, size, mode)
            ok += hook(short[].class, int.class, int.class, int.class);         // read(short[], off, size, mode)
            ok += hook(float[].class, int.class, int.class, int.class);         // read(float[], off, size, mode)
            ok += hook(ByteBuffer.class, int.class, int.class);                 // read(ByteBuffer, size, mode)
        } catch (Throwable e) {
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
            Log.e(TAG, "hook failed", e);
        }
        if (lastError != null && error.isEmpty()) {
            error = lastError;
        }
        String proc = processName();
        String line = (ok > 0 ? "OK" : "FAIL") + "|" + proc + "|" + Build.VERSION.SDK_INT + "|" + ok + "/4|" + error;
        FightModeConfig.writeHookStatus(line);
        Log.i(TAG, "AudioRecord.read hooks installed: " + line);
    }

    private static String lastError;

    private static int hook(Class<?>... params) {
        try {
            Method m = AudioRecord.class.getDeclaredMethod("read", params);
            Pine.hook(m, new ReadHook(params[0]));
            return 1;
        } catch (Throwable e) {
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            Log.w(TAG, "cannot hook AudioRecord.read(" + params[0].getSimpleName() + ")", e);
            return 0;
        }
    }

    private static String processName() {
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader("/proc/self/cmdline"))) {
            String l = r.readLine();
            return l == null ? "?" : l.trim().replace("\u0000", "");
        } catch (Throwable e) {
            return "?";
        }
    }

    private static final class ReadHook extends MethodHook {
        private final Class<?> kind;

        ReadHook(Class<?> kind) {
            this.kind = kind;
        }

        @Override
        public void afterCall(Pine.CallFrame frame) {
            try {
                Object result = frame.getResult();
                if (!(result instanceof Integer)) {
                    return;
                }
                final int n = (Integer) result;
                if (n <= 0 || !FightModeConfig.pollEnabled()) {
                    return;
                }
                final AudioRecord rec = (AudioRecord) frame.thisObject;
                final int channels = Math.max(1, rec.getChannelCount());
                final int rate = rec.getSampleRate();
                final int format = rec.getAudioFormat();
                final Object[] a = frame.args;

                if (kind == short[].class && format == AudioFormat.ENCODING_PCM_16BIT) {
                    FightModeNative.processShorts((short[]) a[0], (Integer) a[1], n, channels, rate);
                } else if (kind == byte[].class && format == AudioFormat.ENCODING_PCM_16BIT) {
                    FightModeNative.processBytes((byte[]) a[0], (Integer) a[1], n, channels, rate);
                } else if (kind == float[].class && format == AudioFormat.ENCODING_PCM_FLOAT) {
                    FightModeNative.processFloats((float[]) a[0], (Integer) a[1], n, channels, rate);
                } else if (kind == ByteBuffer.class && format == AudioFormat.ENCODING_PCM_16BIT) {
                    ByteBuffer b = (ByteBuffer) a[0];
                    if (b != null && b.isDirect()) {
                        // AudioRecord writes direct buffers from address 0 (it ignores position()).
                        FightModeNative.processDirect(b, 0, n, channels, rate);
                    }
                } else {
                    return;
                }
                FightModeConfig.publishLevel(FightModeNative.getLevel());
            } catch (Throwable e) {
                Log.e(TAG, "process failed", e);
            }
        }
    }

    private FightModeHook() {}
}

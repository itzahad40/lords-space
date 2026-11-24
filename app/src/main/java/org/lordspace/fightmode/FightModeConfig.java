package org.lordspace.fightmode;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Properties;

/**
 * Fight Mode settings shared between the Lord's-space UI process (writes) and the guest app processes (poll).
 * Both run under the same Linux uid, so a plain file in the host's files dir is the channel.
 * The guest also publishes the live mic level through a tiny memory-mapped file.
 */
public final class FightModeConfig {
    private static final String TAG = "FightMode";
    public static final int BANDS = 6;
    public static final float MAX_GAIN_DB = 100f;
    public static final float MAX_EQ_DB = 12f;

    public static final String[] PRESET_NAMES = {
            "Flat", "Easy Listening", "Voice", "Bass Boost", "Treble Boost", "Rock",
            "Pop", "Dance", "Hip Hop", "Jazz", "Classical", "Heavy Metal", "Megaphone"};
    public static final float[][] PRESETS = {
            {0, 0, 0, 0, 0, 0}, {3, -3, 0, 3, 0, 6}, {-6, -3, 2, 5, 3, 0}, {8, 5, 0, 0, 0, 0},
            {0, 0, 0, 4, 7, 9}, {5, 3, -2, 2, 4, 5}, {-2, 2, 4, 3, -1, -2}, {6, 4, 0, 2, 3, 1},
            {6, 3, -1, 2, 0, 2}, {3, 2, -2, 2, 4, 4}, {4, 2, -1, -1, 2, 4}, {4, 1, -1, 3, 5, 2},
            {-8, -4, 4, 9, 4, -4}};
    public static final int MEGAPHONE_PRESET = 12;
    public static final String[] VOICE_NAMES = {"Natural", "Clear", "Loud & Clear", "Megaphone"};
    public static final float[] VOICE_CLARITY = {0f, 0.6f, 0.8f, 1f};
    public static final float[] VOICE_PUNCH = {0f, 0.4f, 0.7f, 1f};

    public static volatile boolean enabled;
    /** Experimental: install the mic hook inside cloned apps. Off by default, a bad hook can stop a cloned app from opening. */
    public static volatile boolean hookEnabled;
    public static volatile float gainDb = 24f;
    public static volatile float volume = 1f;
    public static volatile float clarity;
    public static volatile float punch;
    public static final float[] eqDb = new float[BANDS];

    private static File configFile;
    private static File levelFile;
    private static long lastModified = -1;
    private static long lastPoll;
    private static MappedByteBuffer levelMap;
    private static long lastLevelWrite;

    /** Call with the HOST application context (its files dir is the shared place). */
    public static synchronized void init(Context hostContext) {
        if (configFile != null) {
            return;
        }
        File dir = new File(hostContext.getFilesDir(), "fightmode");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        configFile = new File(dir, "config.properties");
        levelFile = new File(dir, "level.bin");
        load();
    }

    public static synchronized void save() {
        if (configFile == null) {
            return;
        }
        Properties p = new Properties();
        p.setProperty("enabled", String.valueOf(enabled));
        p.setProperty("hook", String.valueOf(hookEnabled));
        p.setProperty("gainDb", String.valueOf(gainDb));
        p.setProperty("volume", String.valueOf(volume));
        p.setProperty("clarity", String.valueOf(clarity));
        p.setProperty("punch", String.valueOf(punch));
        for (int i = 0; i < BANDS; i++) {
            p.setProperty("eq" + i, String.valueOf(eqDb[i]));
        }
        File tmp = new File(configFile.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            p.store(out, null);
        } catch (Throwable e) {
            Log.e(TAG, "save failed", e);
            return;
        }
        if (!tmp.renameTo(configFile)) {
            Log.e(TAG, "rename failed");
        }
    }

    public static synchronized void load() {
        if (configFile == null || !configFile.exists()) {
            return;
        }
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(configFile)) {
            p.load(in);
        } catch (Throwable e) {
            Log.e(TAG, "load failed", e);
            return;
        }
        lastModified = configFile.lastModified();
        enabled = Boolean.parseBoolean(p.getProperty("enabled", "false"));
        hookEnabled = Boolean.parseBoolean(p.getProperty("hook", "false"));
        gainDb = clamp(parse(p, "gainDb", 24f), 0f, MAX_GAIN_DB);
        volume = clamp(parse(p, "volume", 1f), 0f, 1f);
        clarity = clamp(parse(p, "clarity", 0f), 0f, 1f);
        punch = clamp(parse(p, "punch", 0f), 0f, 1f);
        for (int i = 0; i < BANDS; i++) {
            eqDb[i] = clamp(parse(p, "eq" + i, 0f), -MAX_EQ_DB, MAX_EQ_DB);
        }
        pushToNative();
    }

    /** Guest side: reloads at most every 300 ms when the file changed. Returns whether Fight Mode is ON. */
    public static boolean pollEnabled() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastPoll > 300) {
            lastPoll = now;
            File f = configFile;
            if (f != null && f.lastModified() != lastModified) {
                load();
            }
        }
        return enabled;
    }

    public static void pushToNative() {
        if (!FightModeNative.isAvailable()) {
            return;
        }
        float[] v = new float[BANDS + 2];
        System.arraycopy(eqDb, 0, v, 0, BANDS);
        v[BANDS] = clarity;
        v[BANDS + 1] = punch;
        FightModeNative.setParams(enabled, gainDb, volume, v);
    }

    public static void applyPreset(int index) {
        System.arraycopy(PRESETS[index], 0, eqDb, 0, BANDS);
    }

    public static void applyVoice(int index) {
        clarity = VOICE_CLARITY[index];
        punch = VOICE_PUNCH[index];
        if (index == VOICE_NAMES.length - 1) {
            applyPreset(MEGAPHONE_PRESET);
        }
    }

    // ---- hook status (guest writes, UI reads) ----

    /** Guest side: remember whether the AudioRecord hook is active in this process. */
    public static void writeHookStatus(String line) {
        if (levelFile == null) {
            return;
        }
        File f = new File(levelFile.getParentFile(), "hook.status");
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(line.getBytes("UTF-8"));
        } catch (Throwable e) {
            Log.w(TAG, "status write failed", e);
        }
    }

    /** UI side: last status line written by any guest process, or null. */
    public static String readHookStatus() {
        if (levelFile == null) {
            return null;
        }
        File f = new File(levelFile.getParentFile(), "hook.status");
        if (!f.exists()) {
            return null;
        }
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) Math.min(f.length(), 512)];
            int n = in.read(b);
            return n > 0 ? new String(b, 0, n, "UTF-8") : null;
        } catch (Throwable e) {
            return null;
        }
    }

    // ---- live level (guest writes, UI reads) ----

    private static MappedByteBuffer map() {
        if (levelMap == null && levelFile != null) {
            try (RandomAccessFile raf = new RandomAccessFile(levelFile, "rw")) {
                raf.setLength(16);
                levelMap = raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, 16);
            } catch (Throwable e) {
                Log.e(TAG, "level map failed", e);
            }
        }
        return levelMap;
    }

    public static void publishLevel(float level) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastLevelWrite < 50) {
            return;
        }
        lastLevelWrite = now;
        MappedByteBuffer m = map();
        if (m != null) {
            m.putFloat(0, level);
            m.putLong(8, now);
        }
    }

    /** UI side: 0 when the guest has not published for 500 ms (mic idle or Fight Mode off). */
    public static float readLevel() {
        MappedByteBuffer m = map();
        if (m == null || SystemClock.elapsedRealtime() - m.getLong(8) > 500) {
            return 0f;
        }
        return m.getFloat(0);
    }

    private static float parse(Properties p, String key, float def) {
        try {
            return Float.parseFloat(p.getProperty(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.min(Math.max(v, lo), hi);
    }

    private FightModeConfig() {}
}

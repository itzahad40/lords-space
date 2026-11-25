package org.lordspace;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Saves the stack trace of a Java crash to a file, so it can be shown and shared (no computer or adb needed).
 *
 * Crashes of Lord's-Space itself (main process) are shown in a dialog on the next start. Crashes of the cloned apps
 * (the engine's other processes) never pop up: they are written to a separate log that can be opened from the
 * Home menu ("Cloned app crash log").
 */
public final class CrashReporter {
    private static final String FILE = "last_crash.txt";
    private static final String GUEST_FILE = "last_guest_crash.txt";

    private static boolean isMainProcess(Context c) {
        return processName().equals(c.getPackageName());
    }

    public static void install(final Context context) {
        final boolean main = isMainProcess(context);
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    write(context, t, e, main ? FILE : GUEST_FILE);
                } catch (Throwable ignored) {
                }
                if (previous != null) {
                    previous.uncaughtException(t, e);
                }
            }
        });
    }

    private static void write(Context c, Thread t, Throwable e, String fileName) throws Exception {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("Lord's-Space crash report");
        pw.println("Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        pw.println("Phone: " + Build.MANUFACTURER + " " + Build.MODEL);
        pw.println("Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        pw.println("ABIs: " + android.text.TextUtils.join(",", Build.SUPPORTED_ABIS));
        pw.println("Process: " + processName());
        pw.println("Thread: " + t.getName());
        pw.println();
        e.printStackTrace(pw);
        pw.flush();
        try (FileOutputStream out = new FileOutputStream(new File(c.getFilesDir(), fileName))) {
            out.write(sw.toString().getBytes("UTF-8"));
        }
    }

    /**
     * What is known about the last crash of Lord's-Space itself (Java stack trace and, on Android 11+, how the system
     * saw its main process die), or null when there is nothing new. Cloned apps are not included.
     */
    public static String take(Context c) {
        String java = readAndDelete(c, FILE);
        String exits = exitReasons(c, true);
        if (java == null && exits == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (java != null) {
            sb.append(java);
        } else {
            sb.append("Lord's-Space crash report\nPhone: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                    .append("\nAndroid: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        }
        if (exits != null) {
            sb.append("\n--- How the system saw the last exits ---\n").append(exits);
        }
        return sb.toString();
    }

    /** Crash log of the cloned apps: last Java crash plus recent exits of the engine's other processes. */
    public static String guestLog(Context c) {
        String java = readKeep(c, GUEST_FILE);
        String exits = exitReasons(c, false);
        if (java == null && exits == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (exits != null) {
            sb.append("Recent exits of cloned-app processes:\n").append(exits).append('\n');
        }
        if (java != null) {
            sb.append("Last Java crash inside a cloned app:\n").append(java);
        }
        return sb.toString();
    }

    private static String readKeep(Context c, String name) {
        File f = new File(c.getFilesDir(), name);
        if (!f.exists()) {
            return null;
        }
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) Math.min(f.length(), 12000)];
            int n = in.read(b);
            return n > 0 ? new String(b, 0, n, "UTF-8") : null;
        } catch (Throwable e) {
            return null;
        }
    }

    private static String readAndDelete(Context c, String name) {
        String s = readKeep(c, name);
        //noinspection ResultOfMethodCallIgnored
        new File(c.getFilesDir(), name).delete();
        return s;
    }

    /** mainOnly: exits of the main process that are new since last shown. Otherwise: recent exits of all other processes. */
    private static String exitReasons(Context c, boolean mainOnly) {
        if (Build.VERSION.SDK_INT < 30) {
            return null;
        }
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            java.util.List<android.app.ApplicationExitInfo> list = am.getHistoricalProcessExitReasons(c.getPackageName(), 0, 12);
            android.content.SharedPreferences sp = c.getSharedPreferences("lords_space_ui", Context.MODE_PRIVATE);
            long seen = mainOnly ? sp.getLong("exit_seen", 0) : 0;
            long newest = seen;
            StringBuilder sb = new StringBuilder();
            int shown = 0;
            for (android.app.ApplicationExitInfo i : list) {
                boolean isMain = c.getPackageName().equals(i.getProcessName());
                if (isMain != mainOnly || i.getTimestamp() <= seen) {
                    continue;
                }
                newest = Math.max(newest, i.getTimestamp());
                String name;
                switch (i.getReason()) {
                    case android.app.ApplicationExitInfo.REASON_CRASH: name = "JAVA CRASH"; break;
                    case android.app.ApplicationExitInfo.REASON_CRASH_NATIVE: name = "NATIVE CRASH"; break;
                    case android.app.ApplicationExitInfo.REASON_ANR: name = "NOT RESPONDING (ANR)"; break;
                    case android.app.ApplicationExitInfo.REASON_INITIALIZATION_FAILURE: name = "START FAILED"; break;
                    case android.app.ApplicationExitInfo.REASON_SIGNALED: name = "KILLED BY SIGNAL"; break;
                    default: continue;
                }
                sb.append(name).append(" | ").append(i.getProcessName()).append(" | ").append(i.getDescription())
                        .append(" | ")
                        .append(new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date(i.getTimestamp())))
                        .append('\n');
                if (++shown >= 6) {
                    break;
                }
            }
            if (mainOnly) {
                sp.edit().putLong("exit_seen", Math.max(newest, System.currentTimeMillis())).apply();
            }
            return sb.length() == 0 ? null : sb.toString();
        } catch (Throwable e) {
            return null;
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

    private CrashReporter() {}
}

package com.hack;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Tiny timeline file (files/engine_trace.txt) to see where and how long the engine start takes, per process. */
public final class EngineTrace {
    private static final int MAX = 6000;

    public static synchronized void log(Context c, String msg) {
        try {
            File f = new File(c.getFilesDir(), "engine_trace.txt");
            String line = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date())
                    + " [" + proc() + "] " + msg + "\n";
            String old = f.exists() ? read(c) : "";
            String all = old + line;
            if (all.length() > MAX) {
                all = all.substring(all.length() - MAX);
                int nl = all.indexOf('\n');
                if (nl >= 0) {
                    all = all.substring(nl + 1);
                }
            }
            try (FileOutputStream out = new FileOutputStream(f, false)) {
                out.write(all.getBytes("UTF-8"));
            }
        } catch (Throwable ignored) {
        }
    }

    public static String read(Context c) {
        try {
            File f = new File(c.getFilesDir(), "engine_trace.txt");
            if (!f.exists()) {
                return "";
            }
            byte[] b = new byte[(int) Math.min(f.length(), MAX)];
            try (FileInputStream in = new FileInputStream(f)) {
                int n = in.read(b);
                return n > 0 ? new String(b, 0, n, "UTF-8") : "";
            }
        } catch (Throwable e) {
            return "";
        }
    }

    private static String proc() {
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/self/cmdline"))) {
            String l = r.readLine();
            if (l == null) {
                return "?";
            }
            l = l.trim().replace("\u0000", "");
            int colon = l.indexOf(':');
            return colon >= 0 ? l.substring(colon) : "main";
        } catch (Throwable e) {
            return "?";
        }
    }

    private EngineTrace() {}
}

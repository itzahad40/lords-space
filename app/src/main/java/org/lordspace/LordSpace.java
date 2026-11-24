package org.lordspace;

import android.content.Context;
import android.util.Log;

import org.lordspace.fightmode.FightModeLifecycle;

import top.niunaijun.blackbox.BlackBoxCore;

/** Called once per process (main and every cloned-app process) from the engine loader. */
public final class LordSpace {
    private static boolean done;

    public static synchronized void init() {
        if (done) {
            return;
        }
        done = true;
        try {
            Context c = BlackBoxCore.getContext();
            if (c != null) {
                CrashReporter.install(c);
            }
        } catch (Throwable t) {
            Log.w("LordSpace", "crash reporter not installed", t);
        }
        try {
            BlackBoxCore.get().addAppLifecycleCallback(new FightModeLifecycle());
        } catch (Throwable t) {
            Log.w("LordSpace", "fight mode callback not registered", t);
        }
    }

    private LordSpace() {}
}

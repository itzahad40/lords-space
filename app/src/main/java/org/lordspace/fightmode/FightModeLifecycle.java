package org.lordspace.fightmode;

import android.app.Application;
import android.util.Log;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.configuration.AppLifecycleCallback;

/** Runs inside every cloned app's process, right before its Application starts. */
public class FightModeLifecycle extends AppLifecycleCallback {
    @Override
    public void beforeApplicationOnCreate(String packageName, String processName, Application application, int userId) {
        try {
            FightModeHook.install(BlackBoxCore.getContext());
        } catch (Throwable t) {
            Log.e("FightMode", "hook install failed", t);
        }
    }
}

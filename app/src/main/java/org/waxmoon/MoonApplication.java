package org.waxmoon;

import android.app.Application;
import android.content.Context;

import com.hack.Slog;
import com.hack.opensdk.HackApi;
import com.hack.opensdk.HackApplication;
import org.waxmoon.fightmode.FightModeHook;

public class MoonApplication extends HackApplication {

    private static MoonApplication sInstance;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        sInstance = this;
        CrashReporter.install(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        monitorApplicationEvent();
    }

    public static MoonApplication INSTANCE() {
        return sInstance;
    }

    private void monitorApplicationEvent() {
        final String TAG = "EVENT";
        HackApi.registerApplicationCallback(new HackApi.ApplicationCallback() {
            @Override
            public void onInitAppContext(Object loadedApk, Context appContext) {
                Slog.d(TAG, "onInitAppContext %s %s", loadedApk, appContext);
            }

            @Override
            public void onAttachBaseContext(Application app) {
                Slog.d(TAG, "onAttachBaseContext %s", app);
            }

            @Override
            public void onInstallProviders(Application app) {
                Slog.d(TAG, "onInstallProviders %s", app);
            }

            @Override
            public void onCreate(Application app) {
                Slog.d(TAG, "onCreate %s", app);
                // Fight Mode: runs inside the guest app's process, hooks AudioRecord.read
                FightModeHook.install(MoonApplication.this);
            }
        });
    }
}

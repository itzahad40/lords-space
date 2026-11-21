package com.hack.server.core;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;

import com.hack.EngineTrace;
import com.hack.Features;
import com.hack.agent.ProviderBase;
import com.hack.opensdk.Cmd;
import com.hack.opensdk.CmdConstants;
import com.hack.utils.ThreadUtils;

public class HackServerProvider extends ProviderBase {
    private static final boolean DEBUG = Features.DEBUG;
    private static final String TAG = HackServerProvider.class.getSimpleName();

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public boolean onCreate() {
        ThreadUtils.postOnBackgroundThread(()->{
            getContext().startService(new Intent(getContext(), ForgroundService.class));
        });
        final android.content.Context ctx = getContext();
        final long t0 = android.os.SystemClock.elapsedRealtime();
        EngineTrace.log(ctx, "core provider: engine start BEGIN");
        try {
            boolean ok = (boolean) Cmd.INSTANCE().exec(CmdConstants.CMD_CORE_PROVIDER_CREATE, ctx);
            EngineTrace.log(ctx, "core provider: engine start DONE in "
                    + (android.os.SystemClock.elapsedRealtime() - t0) + " ms, result=" + ok);
            return ok;
        } catch (Throwable t) {
            EngineTrace.log(ctx, "core provider: engine start FAILED after "
                    + (android.os.SystemClock.elapsedRealtime() - t0) + " ms: " + t);
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            }
            if (t instanceof Error) {
                throw (Error) t;
            }
            throw new RuntimeException(t);
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        return 0;
    }

    @Override
    public int getProviderCallType() {
        return CmdConstants.CMD_CORE_PROVIDER_CALL;
    }
}
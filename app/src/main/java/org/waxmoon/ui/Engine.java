package org.waxmoon.ui;

import android.os.SystemClock;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs engine (HackApi) calls with a time limit, so a stuck engine becomes a message instead of a frozen screen.
 * Call only from a background thread: it waits for the result.
 */
public final class Engine {
    private static final ExecutorService POOL = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "engine-call");
            t.setDaemon(true);
            return t;
        }
    });

    public static final class Result<T> {
        public T value;
        public Throwable error;
        public boolean timedOut;
        public long ms;

        public boolean ok() {
            return !timedOut && error == null;
        }

        /** Short human text for failures, or null when the call worked. */
        public String problem() {
            if (timedOut) {
                return "no answer after " + (ms / 1000) + " s";
            }
            if (error != null) {
                return error.getClass().getSimpleName() + ": " + error.getMessage();
            }
            return null;
        }
    }

    public static <T> Result<T> call(Callable<T> c, long timeoutMs) {
        Result<T> r = new Result<>();
        long t0 = SystemClock.elapsedRealtime();
        Future<T> f = POOL.submit(c);
        try {
            r.value = f.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            r.timedOut = true;          // the engine call keeps running in the background
        } catch (ExecutionException e) {
            r.error = e.getCause() != null ? e.getCause() : e;
        } catch (InterruptedException e) {
            r.error = e;
        }
        r.ms = SystemClock.elapsedRealtime() - t0;
        return r;
    }

    private Engine() {}
}

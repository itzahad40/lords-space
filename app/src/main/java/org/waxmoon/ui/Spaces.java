package org.waxmoon.ui;

import android.content.Context;
import android.content.SharedPreferences;

/** Which space (0, 1, 2 ...) the user is looking at. Remembered between launches. */
public final class Spaces {
    public static final int MAX = 99;
    private static final String PREFS = "lords_space_ui";
    private static int current = -1;

    public static synchronized int get(Context c) {
        if (current < 0) {
            current = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("space", 0);
        }
        return current;
    }

    public static synchronized void set(Context c, int space) {
        current = Math.max(0, Math.min(MAX, space));
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("space", current).apply();
    }

    private Spaces() {}
}

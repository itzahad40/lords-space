package org.waxmoon.ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Apps installed on the phone itself (the ones that can be cloned into a space). */
public final class HostApps {
    public static final class Entry {
        public String pkg;
        public String label;
        public Drawable icon;
    }

    /** Launchable, non-system apps, same rule as the classic list. */
    public static List<Entry> load(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(i, 0);
        Set<String> seen = new HashSet<>();
        List<Entry> out = new ArrayList<>();
        for (ResolveInfo r : infos) {
            ApplicationInfo ai = r.activityInfo.applicationInfo;
            if (ai.packageName.equals(ctx.getPackageName()) || (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                continue;
            }
            if (!seen.add(ai.packageName)) {
                continue;
            }
            Entry e = new Entry();
            e.pkg = ai.packageName;
            e.label = String.valueOf(r.loadLabel(pm));
            e.icon = r.loadIcon(pm);
            out.add(e);
        }
        Collections.sort(out, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return out;
    }

    /** Label and icon for one package, falling back to the package name. */
    public static Entry describe(Context ctx, String pkg) {
        PackageManager pm = ctx.getPackageManager();
        Entry e = new Entry();
        e.pkg = pkg;
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            e.label = String.valueOf(pm.getApplicationLabel(ai));
            e.icon = pm.getApplicationIcon(ai);
        } catch (Throwable t) {
            e.label = pkg;
            e.icon = pm.getDefaultActivityIcon();
        }
        return e;
    }

    private HostApps() {}
}

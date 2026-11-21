package org.waxmoon.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lord's-Space keeps its own copy of every cloned app (APK files, name, icon) and its own list of what is
 * cloned into which space. That makes a clone independent of the original app on the phone, and lets Home
 * install it again if the engine ever forgets it.
 *
 * Layout: files/clones/&lt;package&gt;/apk/*.apk and files/clones/&lt;package&gt;/meta/{label.txt,icon.png}
 */
public final class CloneStore {
    private static final String PREFS = "lords_space_clones";
    private static final int ICON_PX = 192;

    public static final class Item {
        public String pkg;
        public String label;
        public File apkDir;
        public File iconFile;

        public boolean hasApk() {
            File[] f = apkDir.listFiles();
            return f != null && f.length > 0;
        }

        public Drawable loadIcon(Context c) {
            Bitmap b = iconFile.exists() ? BitmapFactory.decodeFile(iconFile.getPath()) : null;
            return b != null ? new BitmapDrawable(c.getResources(), b) : c.getPackageManager().getDefaultActivityIcon();
        }
    }

    private static File dirOf(Context c, String pkg) {
        return new File(new File(c.getFilesDir(), "clones"), pkg);
    }

    private static Item item(Context c, String pkg) {
        File d = dirOf(c, pkg);
        Item it = new Item();
        it.pkg = pkg;
        it.apkDir = new File(d, "apk");
        it.iconFile = new File(new File(d, "meta"), "icon.png");
        it.label = pkg;
        try (InputStream in = new FileInputStream(new File(new File(d, "meta"), "label.txt"))) {
            byte[] b = new byte[(int) Math.min(new File(new File(d, "meta"), "label.txt").length(), 400)];
            int n = in.read(b);
            if (n > 0) {
                it.label = new String(b, 0, n, "UTF-8");
            }
        } catch (IOException ignored) {
        }
        return it;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(int space) {
        return "space_" + space;
    }

    /** Everything recorded as cloned into this space. */
    public static synchronized List<Item> list(Context c, int space) {
        List<Item> out = new ArrayList<>();
        for (String pkg : new HashSet<>(prefs(c).getStringSet(key(space), new HashSet<String>()))) {
            out.add(item(c, pkg));
        }
        java.util.Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    public static synchronized boolean isRecorded(Context c, int space, String pkg) {
        return prefs(c).getStringSet(key(space), new HashSet<String>()).contains(pkg);
    }

    public static synchronized void add(Context c, int space, String pkg) {
        Set<String> s = new HashSet<>(prefs(c).getStringSet(key(space), new HashSet<String>()));
        s.add(pkg);
        prefs(c).edit().putStringSet(key(space), s).apply();
    }

    /** Forgets the clone in this space. The files are deleted when no other space uses the app. */
    public static synchronized void remove(Context c, int space, String pkg) {
        Set<String> s = new HashSet<>(prefs(c).getStringSet(key(space), new HashSet<String>()));
        s.remove(pkg);
        prefs(c).edit().putStringSet(key(space), s).apply();
        for (int sp = 0; sp <= Spaces.MAX; sp++) {
            if (sp != space && prefs(c).getStringSet(key(sp), new HashSet<String>()).contains(pkg)) {
                return;
            }
        }
        deleteRecursive(dirOf(c, pkg));
    }

    /** Copies the phone's installed APK(s) of this app, plus its name and icon, into our own storage. */
    public static void copyFromPhone(Context c, String pkg, String label, Drawable icon) throws IOException {
        PackageManager pm = c.getPackageManager();
        ApplicationInfo ai;
        try {
            ai = pm.getApplicationInfo(pkg, 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new IOException("the app is not installed on this phone");
        }
        List<File> sources = new ArrayList<>();
        sources.add(new File(ai.sourceDir));
        if (ai.splitSourceDirs != null) {
            for (String s : ai.splitSourceDirs) {
                sources.add(new File(s));
            }
        }
        long need = 0;
        for (File f : sources) {
            need += f.length();
        }
        if (c.getFilesDir().getUsableSpace() < need + need / 10 + 10_000_000L) {
            throw new IOException("not enough free storage (needs about " + (need / 1048576) + " MB)");
        }

        File dir = dirOf(c, pkg);
        File apkDir = new File(dir, "apk");
        File metaDir = new File(dir, "meta");
        deleteRecursive(apkDir);
        if (!apkDir.mkdirs() && !apkDir.isDirectory()) {
            throw new IOException("cannot create the storage folder");
        }
        //noinspection ResultOfMethodCallIgnored
        metaDir.mkdirs();
        for (File src : sources) {
            File dst = new File(apkDir, src.getName());
            File tmp = new File(apkDir, src.getName() + ".tmp");
            try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            }
            if (!tmp.renameTo(dst)) {
                throw new IOException("cannot save " + src.getName());
            }
        }
        try (OutputStream out = new FileOutputStream(new File(metaDir, "label.txt"))) {
            out.write(label.getBytes("UTF-8"));
        }
        saveIcon(icon, new File(metaDir, "icon.png"));
    }

    /**
     * Installs the app into the space from OUR copy (not from the phone's app). Background thread only.
     * One APK is passed as a file, several (split APKs) as the folder, as the engine expects.
     */
    public static Engine.Result<Integer> installFromCopy(Context c, String pkg, final int space, boolean force) {
        Item it = item(c, pkg);
        File[] apks = it.apkDir.listFiles();
        if (apks == null || apks.length == 0) {
            Engine.Result<Integer> r = new Engine.Result<>();
            r.error = new IOException("no saved copy of the app");
            return r;
        }
        final String path = apks.length == 1 ? apks[0].getAbsolutePath() : it.apkDir.getAbsolutePath();
        final boolean f = force;
        return Engine.call(() -> com.hack.opensdk.HackApi.installApkFiles(path, space, f), 120000);
    }

    private static void saveIcon(Drawable d, File out) {
        try {
            Bitmap b = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            d.setBounds(0, 0, ICON_PX, ICON_PX);
            d.draw(cv);
            try (OutputStream o = new FileOutputStream(out)) {
                b.compress(Bitmap.CompressFormat.PNG, 100, o);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void deleteRecursive(File f) {
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                deleteRecursive(k);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private CloneStore() {}
}

package org.lordspace.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.lordspace.fightmode.FightModeActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.system.user.BUserInfo;
import top.niunaijun.blackbox.entity.pm.InstallResult;
import top.niunaijun.blackboxa.R;

/** Home: logo, space switcher, Fight Mode card and the apps cloned into the current space (NewBlackbox engine). */
public class HomeActivity extends Activity {
    private static final String VENDING = "com.android.vending";
    private static final String[] GOOGLE = {"com.google.android.gms", "com.google.android.gsf", VENDING};

    private LinearLayout apps;
    private TextView spaceText;
    private int refreshGen;
    private int renderedGen;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private static final class Snapshot {
        final List<HostApps.Entry> rows = new ArrayList<>();
        boolean playInstalled;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.systemBars(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(Ui.pageBackground());
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        col.setPadding(pad, pad, pad, pad + Ui.dp(this, 8));
        scroll.addView(col, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        // header
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.logoBadge(this, 40));
        TextView title = Ui.title(this, "LORD'S SPACE", 18);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = Ui.dp(this, 10);
        head.addView(title, tl);
        FrameLayout mic = Ui.iconButton(this, R.drawable.ls_ic_mic, Ui.RED_LIGHT, Color.TRANSPARENT, Ui.RED, "Fight Mode");
        mic.setOnClickListener(v -> startActivity(new Intent(this, FightModeActivity.class)));
        head.addView(mic);
        final FrameLayout more = Ui.iconButton(this, R.drawable.ls_ic_more, Ui.TEXT, 0xFF17171B, 0xFF2A2A30, "More options");
        LinearLayout.LayoutParams ml = (LinearLayout.LayoutParams) more.getLayoutParams();
        ml.leftMargin = Ui.dp(this, 10);
        more.setOnClickListener(v -> showMenu(more));
        head.addView(more);
        col.addView(head, new LinearLayout.LayoutParams(-1, -2));

        // hero logo
        ImageView hero = Ui.logo(this, 24);
        Ui.glow(hero, Ui.RED);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(Ui.dp(this, 210), Ui.dp(this, 210));
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        hl.topMargin = Ui.dp(this, 16);
        col.addView(hero, hl);

        // space switcher
        LinearLayout sw = new LinearLayout(this);
        sw.setGravity(Gravity.CENTER_VERTICAL);
        sw.setBackground(Ui.rect(this, Ui.CARD, 14, Ui.BORDER, 1));
        sw.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
        FrameLayout prev = Ui.iconButton(this, R.drawable.ls_ic_chevron_left, Ui.TEXT, Color.TRANSPARENT, Color.TRANSPARENT, "Previous space");
        FrameLayout next = Ui.iconButton(this, R.drawable.ls_ic_chevron_right, Ui.TEXT, Color.TRANSPARENT, Color.TRANSPARENT, "Next space");
        spaceText = Ui.text(this, "", 15, Ui.TEXT);
        spaceText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        spaceText.setGravity(Gravity.CENTER);
        prev.setOnClickListener(v -> switchSpace(-1));
        next.setOnClickListener(v -> switchSpace(1));
        sw.addView(prev);
        sw.addView(spaceText, new LinearLayout.LayoutParams(0, -2, 1f));
        sw.addView(next);
        LinearLayout.LayoutParams swl = new LinearLayout.LayoutParams(-1, -2);
        swl.topMargin = Ui.dp(this, 16);
        col.addView(sw, swl);

        // fight mode card
        LinearLayout fm = new LinearLayout(this);
        fm.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable fmBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF2A0A0F, Ui.CARD});
        fmBg.setCornerRadius(Ui.dp(this, 14));
        fmBg.setStroke(Ui.dp(this, 1), 0xFF4A1118);
        fm.setBackground(fmBg);
        fm.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        LinearLayout fmText = new LinearLayout(this);
        fmText.setOrientation(LinearLayout.VERTICAL);
        fmText.addView(Ui.title(this, "FIGHT MODE", 15));
        fmText.addView(Ui.text(this, "Loud, clear mic for apps in this space", 13, Ui.DIM));
        fm.addView(fmText, new LinearLayout.LayoutParams(0, -2, 1f));
        Button open = Ui.pill(this, "Open", true);
        open.setOnClickListener(v -> startActivity(new Intent(this, FightModeActivity.class)));
        fm.addView(open);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-1, -2);
        fl.topMargin = Ui.dp(this, 14);
        col.addView(fm, fl);

        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-2, -2);
        ll.topMargin = Ui.dp(this, 20);
        col.addView(Ui.label(this, "APPS IN THIS SPACE"), ll);

        apps = new LinearLayout(this);
        apps.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-1, -2);
        al.topMargin = Ui.dp(this, 10);
        col.addView(apps, al);
    }


    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void switchSpace(int delta) {
        Spaces.set(this, Spaces.get(this) + delta);
        refresh();
    }

    private static boolean isGoogle(String pkg) {
        for (String g : GOOGLE) {
            if (g.equals(pkg)) {
                return true;
            }
        }
        return false;
    }

    /** A space is a BlackBox "user". Create it the first time it is used. */
    static void ensureUser(int space) {
        BlackBoxCore core = BlackBoxCore.get();
        List<BUserInfo> users = core.getUsers();
        if (users != null) {
            for (BUserInfo u : users) {
                if (u.id == space) {
                    return;
                }
            }
        }
        core.createUser(space);
    }

    private Snapshot load(int space) {
        BlackBoxCore core = BlackBoxCore.get();
        core.waitForServicesAvailable(8000);
        ensureUser(space);
        Snapshot s = new Snapshot();
        List<ApplicationInfo> list = core.getInstalledApplications(0, space);
        PackageManager pm = BlackBoxCore.getPackageManager();
        if (list != null) {
            for (ApplicationInfo ai : list) {
                if (ai.packageName.equals(getPackageName()) || isGoogle(ai.packageName)) {
                    continue;
                }
                HostApps.Entry e = new HostApps.Entry();
                e.pkg = ai.packageName;
                try {
                    e.label = String.valueOf(pm.getApplicationLabel(ai));
                } catch (Throwable t) {
                    e.label = ai.packageName;
                }
                try {
                    e.icon = pm.getApplicationIcon(ai);
                } catch (Throwable t) {
                    e.icon = pm.getDefaultActivityIcon();
                }
                s.rows.add(e);
            }
        }
        Collections.sort(s.rows, new Comparator<HostApps.Entry>() {
            @Override
            public int compare(HostApps.Entry a, HostApps.Entry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        try {
            s.playInstalled = core.isInstallGms(space);
        } catch (Throwable ignored) {
        }
        return s;
    }

    /** Never talks to the engine on the UI thread: the screen is drawn at once, the list arrives later. */
    private void refresh() {
        final int space = Spaces.get(this);
        spaceText.setText("Space " + space);
        final int gen = ++refreshGen;
        if (apps.getChildCount() == 0) {
            render(space, null, null, true);
        }
        handler.postDelayed(() -> {
            if (gen == refreshGen && renderedGen != gen && !isFinishing()) {
                render(space, null, "The engine is slow to respond (still waiting).", true);
            }
        }, 6000);
        new Thread(() -> {
            final Engine.Result<Snapshot> r = Engine.call(() -> load(space), 25000);
            runOnUiThread(() -> {
                if (gen == refreshGen && !isFinishing()) {
                    renderedGen = gen;
                    render(space, r.ok() ? r.value : null, r.ok() ? null : r.problem(), false);
                }
            });
        }).start();
    }

    private void render(final int space, Snapshot s, String error, boolean loading) {
        apps.removeAllViews();
        if (error != null) {
            TextView note = Ui.text(this, "Engine problem: " + error, 12, Ui.RED_LIGHT);
            note.setBackground(Ui.rect(this, 0xFF1A0C10, 14, 0xFF4A1118, 1));
            note.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
            np.bottomMargin = Ui.dp(this, 10);
            apps.addView(note, np);
        }
        String playSub = loading ? "CHECKING..." : (s != null && s.playInstalled) ? "PRE-INSTALLED" : "TAP TO ENABLE";
        addRow(null, "Play Store", playSub, true, v -> openPlay(), null);

        if (s != null) {
            for (final HostApps.Entry e : s.rows) {
                addRow(e, e.label, null, false, v -> openApp(e.pkg), v -> confirmRemove(e));
            }
        }

        Button add = new Button(this);
        add.setText("+ Add app");
        add.setAllCaps(false);
        add.setTextSize(15);
        add.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        add.setTextColor(Ui.RED_LIGHT);
        GradientDrawable dash = Ui.rect(this, Color.TRANSPARENT, 14, 0xFF5A1A22, 1);
        dash.setStroke(Ui.dp(this, 1), 0xFF5A1A22, Ui.dp(this, 5), Ui.dp(this, 4));
        add.setBackground(dash);
        add.setStateListAnimator(null);
        add.setMinHeight(Ui.dp(this, 52));
        add.setOnClickListener(v -> startActivity(new Intent(this, AddAppsActivity.class)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 52));
        lp.topMargin = Ui.dp(this, 6);
        apps.addView(add, lp);

        if (error != null) {
            Button retry = Ui.pill(this, "Retry", false);
            retry.setOnClickListener(v -> refresh());
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-2, -2);
            rl.topMargin = Ui.dp(this, 10);
            apps.addView(retry, rl);
        }
    }

    private void addRow(HostApps.Entry e, String name, String sub, boolean special,
                        View.OnClickListener open, View.OnClickListener remove) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(special
                ? Ui.rect(this, 0xFF1A0C10, 14, 0xFF4A1118, 1)
                : Ui.rect(this, Ui.CARD, 14, Ui.BORDER, 1));
        row.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));

        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(special ? Ui.rect(this, 0xFF2A0A10, 10, Ui.RED, 1) : Ui.rect(this, 0xFF1D1D22, 10, 0, 0));
        ImageView icon = new ImageView(this);
        if (e != null) {
            icon.setImageDrawable(e.icon);
        } else {
            icon.setImageResource(R.drawable.ls_ic_play);
            icon.setColorFilter(Ui.RED_LIGHT);
        }
        int p = e != null ? 0 : Ui.dp(this, 9);
        icon.setPadding(p, p, p, p);
        tile.addView(icon, new FrameLayout.LayoutParams(-1, -1));
        row.addView(tile, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));

        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        TextView n = Ui.text(this, name, 16, Ui.TEXT);
        n.setSingleLine(true);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        txt.addView(n);
        if (sub != null) {
            TextView s = Ui.text(this, sub, 11, Ui.DIM);
            s.setLetterSpacing(0.1f);
            txt.addView(s);
        }
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
        tp.leftMargin = Ui.dp(this, 12);
        row.addView(txt, tp);

        Button b = Ui.pill(this, "Open", false);
        b.setOnClickListener(open);
        row.addView(b);
        if (remove != null) {
            row.setOnLongClickListener(v -> {
                remove.onClick(v);
                return true;
            });
        }
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.bottomMargin = Ui.dp(this, 10);
        apps.addView(row, rp);
    }


    private void openApp(final String pkg) {
        final int space = Spaces.get(this);
        toast("Starting...");
        new Thread(() -> {
            final Engine.Result<Boolean> r = Engine.call(() -> BlackBoxCore.get().launchApk(pkg, space), 40000);
            if (!r.ok()) {
                runOnUiThread(() -> showText("Could not start the app", "The engine did not start it: " + r.problem()));
            } else if (!Boolean.TRUE.equals(r.value)) {
                runOnUiThread(() -> showText("Could not start the app",
                        "The engine found no launch screen for " + pkg + ". Try removing it and cloning it again."));
            }
        }).start();
    }

    private void openPlay() {
        final int space = Spaces.get(this);
        toast("Getting Google Play ready in Space " + space + "...");
        new Thread(() -> {
            final BlackBoxCore core = BlackBoxCore.get();
            Engine.Result<Boolean> have = Engine.call(() -> {
                ensureUser(space);
                return core.isInstallGms(space);
            }, 20000);
            if (!have.ok()) {
                runOnUiThread(() -> showText("Google Play", "The engine did not answer: " + have.problem()));
                return;
            }
            if (!Boolean.TRUE.equals(have.value)) {
                Engine.Result<Boolean> sup = Engine.call(() -> core.isSupportGms(), 10000);
                if (sup.ok() && Boolean.FALSE.equals(sup.value)) {
                    runOnUiThread(() -> showText("Google Play", "Google Play services are not available on this phone."));
                    return;
                }
                Engine.Result<InstallResult> inst = Engine.call(() -> core.installGms(space), 300000);
                if (!inst.ok() || inst.value == null || !inst.value.success) {
                    final String why = !inst.ok() ? inst.problem() : inst.value == null ? "no result" : String.valueOf(inst.value.msg);
                    runOnUiThread(() -> showText("Google Play", "Could not set up Google Play: " + why));
                    return;
                }
            }
            final Engine.Result<Boolean> r = Engine.call(() -> core.launchApk(VENDING, space), 40000);
            if (!r.ok() || !Boolean.TRUE.equals(r.value)) {
                runOnUiThread(() -> showText("Google Play",
                        "The engine could not start Google Play" + (r.ok() ? "." : ": " + r.problem())));
            }
            runOnUiThread(this::refresh);
        }).start();
    }

    private void confirmRemove(final HostApps.Entry e) {
        final int space = Spaces.get(this);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Remove " + e.label + "?")
                .setMessage("Removes it from Space " + space + ". Your phone's own copy is not touched.")
                .setPositiveButton("Remove", (d, w) -> new Thread(() -> {
                    try {
                        BlackBoxCore.get().uninstallPackageAsUser(e.pkg, space);
                    } catch (Throwable ignored) {
                    }
                    runOnUiThread(this::refresh);
                }).start())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showText(String title, final String text) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(title)
                .setMessage(text)
                .setPositiveButton("OK", null)
                .setNegativeButton("Share", (d, w) -> {
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("text/plain");
                    send.putExtra(Intent.EXTRA_TEXT, text);
                    startActivity(Intent.createChooser(send, "Send"));
                })
                .show();
    }

    /** Asks the engine a few simple questions with timers, to see whether it is alive on this phone. */
    private void engineCheck() {
        final int space = Spaces.get(this);
        toast("Checking the engine (up to 30 s)...");
        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("Phone: ").append(android.os.Build.MANUFACTURER).append(' ').append(android.os.Build.MODEL)
                    .append("\nAndroid: ").append(android.os.Build.VERSION.RELEASE)
                    .append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n\n");
            final BlackBoxCore core = BlackBoxCore.get();
            Engine.Result<Boolean> a = Engine.call(() -> core.waitForServicesAvailable(10000), 15000);
            sb.append("1) engine services ready: ").append(a.ok() ? a.value + " (" + a.ms + " ms)" : "FAILED, " + a.problem()).append('\n');
            Engine.Result<List<BUserInfo>> u = Engine.call(() -> core.getUsers(), 10000);
            sb.append("2) spaces: ");
            if (u.ok() && u.value != null) {
                for (BUserInfo x : u.value) {
                    sb.append(x.id).append(' ');
                }
                sb.append("(").append(u.ms).append(" ms)\n");
            } else {
                sb.append("FAILED, ").append(u.problem()).append('\n');
            }
            Engine.Result<List<ApplicationInfo>> l = Engine.call(() -> core.getInstalledApplications(0, space), 10000);
            sb.append("3) apps in space ").append(space).append(": ");
            if (l.ok()) {
                sb.append(l.value == null ? 0 : l.value.size()).append(" (").append(l.ms).append(" ms)\n");
                if (l.value != null) {
                    for (ApplicationInfo ai : l.value) {
                        sb.append("   ").append(ai.packageName).append('\n');
                    }
                }
            } else {
                sb.append("FAILED, ").append(l.problem()).append('\n');
            }
            Engine.Result<Boolean> g = Engine.call(() -> core.isSupportGms(), 10000);
            sb.append("4) Google Play supported here: ").append(g.ok() ? String.valueOf(g.value) : g.problem()).append('\n');
            Engine.Result<Boolean> f = Engine.call(() -> core.hasAllFilesAccess(), 10000);
            sb.append("5) file access allowed: ").append(f.ok() ? String.valueOf(f.value) : f.problem()).append('\n');
            final String report = sb.toString();
            runOnUiThread(() -> showText("Engine check", report));
        }).start();
    }

    private void showMenu(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 0, 0, "Enable Google Play in this space");
        m.getMenu().add(0, 1, 1, "Allow file access for cloned apps");
        m.getMenu().add(0, 2, 2, "Engine check");
        m.getMenu().add(0, 3, 3, "Original BlackBox screen");
        m.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0:
                    openPlay();
                    break;
                case 1:
                    try {
                        if (BlackBoxCore.get().hasAllFilesAccess()) {
                            toast("File access is already allowed");
                        } else {
                            BlackBoxCore.get().requestAllFilesAccess(this);
                        }
                    } catch (Throwable t) {
                        toast("Could not ask for file access: " + t.getMessage());
                    }
                    break;
                case 2:
                    engineCheck();
                    break;
                default:
                    try {
                        startActivity(new Intent().setClassName(getPackageName(), "top.niunaijun.blackboxa.view.main.MainActivity"));
                    } catch (Throwable t) {
                        toast("Could not open it: " + t.getMessage());
                    }
            }
            return true;
        });
        m.show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}

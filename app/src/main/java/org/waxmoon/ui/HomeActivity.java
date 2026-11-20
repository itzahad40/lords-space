package org.waxmoon.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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

import com.hack.opensdk.HackApi;

import org.waxmoon.R;
import org.waxmoon.fightmode.FightModeActivity;

import java.util.ArrayList;
import java.util.List;

/** Home: logo, space switcher, Fight Mode card and the apps cloned into the current space. */
public class HomeActivity extends Activity {
    private static final String VENDING = "com.android.vending";
    private static final String[] GOOGLE = {"com.google.android.gms", "com.google.android.gsf", VENDING};

    private LinearLayout apps;
    private TextView spaceText;

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
        FrameLayout mic = Ui.iconButton(this, R.drawable.ic_mic, Ui.RED_LIGHT, Color.TRANSPARENT, Ui.RED, "Fight Mode");
        mic.setOnClickListener(v -> startActivity(new Intent(this, FightModeActivity.class)));
        head.addView(mic);
        final FrameLayout more = Ui.iconButton(this, R.drawable.ic_more, Ui.TEXT, 0xFF17171B, 0xFF2A2A30, "More options");
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
        FrameLayout prev = Ui.iconButton(this, R.drawable.ic_chevron_left, Ui.TEXT, Color.TRANSPARENT, Color.TRANSPARENT, "Previous space");
        FrameLayout next = Ui.iconButton(this, R.drawable.ic_chevron_right, Ui.TEXT, Color.TRANSPARENT, Color.TRANSPARENT, "Next space");
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
        // Google Play is only enabled when the user taps the Play Store row or the menu entry.
        refresh();
    }

    private void switchSpace(int delta) {
        Spaces.set(this, Spaces.get(this) + delta);
        refresh();
    }

    private List<String> installedHere(int space) {
        try {
            List<String> l = HackApi.getInstalledPackages(0, space);
            return l == null ? new ArrayList<String>() : l;
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    private boolean hostHasPlay() {
        return getPackageManager().getLaunchIntentForPackage(VENDING) != null;
    }

    /** Installs the Google Play components into the space (same as the classic "Enable GP" menu). */
    private void ensureGoogle(int space) {
        List<String> inst = installedHere(space);
        for (String pkg : GOOGLE) {
            if (!inst.contains(pkg)) {
                try {
                    HackApi.installPackageFromHost(pkg, space, false);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private int refreshGen;
    private int renderedGen;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

    /**
     * Never talks to the engine on the UI thread: the screen is drawn at once and the list of apps arrives
     * from a background thread. If the engine does not answer, a note says so instead of a black screen.
     */
    private void refresh() {
        final int space = Spaces.get(this);
        spaceText.setText("Space " + space);
        final int gen = ++refreshGen;
        if (apps.getChildCount() == 0) {
            render(space, new ArrayList<String>(), null, true);
        }
        handler.postDelayed(() -> {
            if (gen == refreshGen && renderedGen != gen && !isFinishing()) {
                render(space, new ArrayList<String>(), "The engine is slow to respond (still waiting).", true);
            }
        }, 4000);
        new Thread(() -> {
            List<String> inst = new ArrayList<>();
            String err = null;
            try {
                List<String> l = HackApi.getInstalledPackages(0, space);
                if (l != null) {
                    inst = l;
                }
            } catch (Throwable t) {
                err = t.getClass().getSimpleName() + ": " + t.getMessage();
            }
            final List<String> fi = inst;
            final String fe = err;
            runOnUiThread(() -> {
                if (gen == refreshGen && !isFinishing()) {
                    renderedGen = gen;
                    render(space, fi, fe, false);
                }
            });
        }).start();
    }

    private void render(final int space, List<String> inst, String error, boolean loading) {
        apps.removeAllViews();
        if (error != null) {
            TextView note = Ui.text(this, "Engine problem: " + error, 12, Ui.RED_LIGHT);
            note.setBackground(Ui.rect(this, 0xFF1A0C10, 14, 0xFF4A1118, 1));
            note.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
            np.bottomMargin = Ui.dp(this, 10);
            apps.addView(note, np);
        }

        String playSub = loading ? "CHECKING..."
                : inst.contains(VENDING) ? "PRE-INSTALLED"
                : (hostHasPlay() ? "TAP TO ENABLE" : "NOT ON THIS PHONE");
        addRow(null, "Play Store", playSub, true, v -> openPlay(), null);

        for (final String pkg : inst) {
            if (pkg.equals(getPackageName()) || isGoogleCore(pkg)) {
                continue;
            }
            final HostApps.Entry e = HostApps.describe(this, pkg);
            addRow(e, e.label, null, false, v -> openApp(pkg), v -> confirmRemove(e));
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

    private static boolean isGoogleCore(String pkg) {
        for (String g : GOOGLE) {
            if (g.equals(pkg)) {
                return true;
            }
        }
        return false;
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
            icon.setImageResource(R.drawable.ic_play);
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

    private void openApp(String pkg) {
        int space = Spaces.get(this);
        Intent it = getPackageManager().getLaunchIntentForPackage(pkg);
        if (it == null) {
            try {
                it = HackApi.getLaunchIntentForPackage(pkg, space);
            } catch (Throwable ignored) {
            }
        }
        if (it == null) {
            toast("Cannot open this app");
            return;
        }
        it.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            if (HackApi.startActivity(it, space) != 0) {
                toast("Could not start the app");
            }
        } catch (Throwable t) {
            toast("Engine error: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private void openPlay() {
        final int space = Spaces.get(this);
        if (!hostHasPlay()) {
            toast("Google Play is not installed on this phone");
            return;
        }
        new Thread(() -> {
            ensureGoogle(space);
            runOnUiThread(() -> {
                Intent it = getPackageManager().getLaunchIntentForPackage(VENDING);
                if (it != null) {
                    it.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    try {
                        if (HackApi.startActivity(it, space) != 0) {
                            toast("Could not open Google Play");
                        }
                    } catch (Throwable t) {
                        toast("Engine error: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                    }
                }
                refresh();
            });
        }).start();
    }

    private void confirmRemove(final HostApps.Entry e) {
        final int space = Spaces.get(this);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Remove " + e.label + "?")
                .setMessage("Removes it from Space " + space + ". Your phone's own copy is not touched.")
                .setPositiveButton("Remove", (d, w) -> {
                    new Thread(() -> {
                        try {
                            HackApi.uninstallPackage(e.pkg, space);
                        } catch (Throwable ignored) {
                        }
                        runOnUiThread(this::refresh);
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showMenu(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 0, 0, "Enable Google Play in this space");
        m.getMenu().add(0, 1, 1, "Classic app list");
        m.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 0) {
                final int space = Spaces.get(this);
                new Thread(() -> {
                    ensureGoogle(space);
                    runOnUiThread(this::refresh);
                }).start();
            } else {
                startActivity(new Intent(this, org.waxmoon.AppListActivity.class));
            }
            return true;
        });
        m.show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}

package org.waxmoon.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.hack.opensdk.HackApi;

import org.waxmoon.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** "Add to Space N": pick phone apps, then CLONE them into the space. */
public class AddAppsActivity extends Activity {
    private static final int INSTALL_SUCCEEDED = 1;
    private static final int INSTALL_FAILED_ALREADY_EXISTS = -1;

    private final List<HostApps.Entry> all = new ArrayList<>();
    private final List<HostApps.Entry> shown = new ArrayList<>();
    private final Set<String> picked = new HashSet<>();
    private final Set<String> already = new HashSet<>();
    private int space;
    private TextView sub;
    private Button clone;
    private GridAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.systemBars(this);
        space = Spaces.get(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.pageBackground());
        int pad = Ui.dp(this, 20);
        root.setPadding(pad, pad, pad, pad);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout back = Ui.iconButton(this, R.drawable.ic_back, Ui.TEXT, 0xFF17171B, 0xFF2A2A30, "Back");
        back.setOnClickListener(v -> finish());
        head.addView(back);
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(Ui.title(this, "ADD TO SPACE " + space, 18));
        sub = Ui.text(this, "0 selected", 13, Ui.DIM);
        t.addView(sub);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = Ui.dp(this, 12);
        head.addView(t, tl);
        head.addView(Ui.logoBadge(this, 40));
        root.addView(head, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout search = new LinearLayout(this);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setBackground(Ui.rect(this, Ui.CARD, 14, Ui.BORDER, 1));
        search.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        ImageView mag = new ImageView(this);
        mag.setImageResource(R.drawable.ic_search);
        mag.setColorFilter(Ui.DIM);
        search.addView(mag, new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20)));
        EditText q = new EditText(this);
        q.setHint("Search installed apps");
        q.setHintTextColor(Ui.DIM);
        q.setTextColor(Ui.TEXT);
        q.setTextSize(15);
        q.setSingleLine(true);
        q.setInputType(InputType.TYPE_CLASS_TEXT);
        q.setBackground(null);
        q.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        LinearLayout.LayoutParams ql = new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f);
        ql.leftMargin = Ui.dp(this, 10);
        search.addView(q, ql);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, Ui.dp(this, 48));
        sl.topMargin = Ui.dp(this, 16);
        root.addView(search, sl);

        GridView grid = new GridView(this);
        grid.setNumColumns(3);
        grid.setVerticalSpacing(Ui.dp(this, 14));
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setClipToPadding(false);
        grid.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 8));
        adapter = new GridAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, pos, id) -> toggle(shown.get(pos)));
        root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));

        clone = new Button(this);
        clone.setAllCaps(false);
        clone.setTypeface(Ui.heading(this));
        clone.setTextSize(16);
        clone.setLetterSpacing(0.12f);
        clone.setStateListAnimator(null);
        clone.setOnClickListener(v -> doClone());
        root.addView(clone, new LinearLayout.LayoutParams(-1, Ui.dp(this, 56)));
        updateButton();
        setContentView(root);

        new Thread(() -> {
            List<HostApps.Entry> apps = HostApps.load(this);
            Set<String> here = new HashSet<>();
            try {
                List<String> l = HackApi.getInstalledPackages(0, space);
                if (l != null) {
                    here.addAll(l);
                }
            } catch (Throwable ignored) {
            }
            runOnUiThread(() -> {
                all.clear();
                all.addAll(apps);
                already.clear();
                already.addAll(here);
                filter("");
            });
        }).start();
    }

    private void filter(String s) {
        String needle = s.trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (HostApps.Entry e : all) {
            if (needle.isEmpty() || e.label.toLowerCase(Locale.ROOT).contains(needle)) {
                shown.add(e);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void toggle(HostApps.Entry e) {
        if (already.contains(e.pkg)) {
            Toast.makeText(this, e.label + " is already in Space " + space, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!picked.remove(e.pkg)) {
            picked.add(e.pkg);
        }
        updateButton();
        adapter.notifyDataSetChanged();
    }

    private void updateButton() {
        int n = picked.size();
        sub.setText(n + " selected");
        clone.setText("CLONE (" + n + ")");
        clone.setEnabled(n > 0);
        clone.setTextColor(n > 0 ? Color.WHITE : Ui.DIM);
        clone.setBackground(Ui.rect(this, n > 0 ? Ui.RED : 0xFF26262C, 16, 0, 0));
    }

    private void doClone() {
        final List<String> pkgs = new ArrayList<>(picked);
        clone.setEnabled(false);
        clone.setText("CLONING...");
        new Thread(() -> {
            int ok = 0, exists = 0, fail = 0;
            for (String pkg : pkgs) {
                int r;
                try {
                    r = HackApi.installPackageFromHost(pkg, space, false);
                } catch (Throwable t) {
                    r = -99;
                }
                if (r == INSTALL_SUCCEEDED) {
                    ok++;
                } else if (r == INSTALL_FAILED_ALREADY_EXISTS) {
                    exists++;
                } else {
                    fail++;
                }
            }
            final StringBuilder msg = new StringBuilder("Added " + ok + (ok == 1 ? " app" : " apps"));
            if (exists > 0) {
                msg.append(", ").append(exists).append(" already there");
            }
            if (fail > 0) {
                msg.append(", ").append(fail).append(" failed");
            }
            final boolean anyFail = fail > 0;
            runOnUiThread(() -> {
                Toast.makeText(this, msg.toString(), anyFail ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
                finish();
            });
        }).start();
    }

    private final class GridAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int i) {
            return shown.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            Context ctx = AddAppsActivity.this;
            HostApps.Entry e = shown.get(pos);
            boolean sel = picked.contains(e.pkg);
            boolean here = already.contains(e.pkg);

            LinearLayout cell = new LinearLayout(ctx);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setAlpha(here ? 0.45f : 1f);

            FrameLayout tile = new FrameLayout(ctx);
            tile.setBackground(Ui.rect(ctx, Ui.CARD, 16, sel ? Ui.RED : Color.TRANSPARENT, sel ? 2 : 0));
            ImageView icon = new ImageView(ctx);
            icon.setImageDrawable(e.icon);
            int p = Ui.dp(ctx, 8);
            icon.setPadding(p, p, p, p);
            tile.addView(icon, new FrameLayout.LayoutParams(-1, -1));
            if (sel) {
                FrameLayout badge = new FrameLayout(ctx);
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                bg.setColor(Ui.RED);
                bg.setStroke(Ui.dp(ctx, 1.5f), Ui.BG);
                badge.setBackground(bg);
                ImageView ck = new ImageView(ctx);
                ck.setImageResource(R.drawable.ic_check);
                ck.setColorFilter(Color.WHITE);
                int c = Ui.dp(ctx, 4);
                ck.setPadding(c, c, c, c);
                badge.addView(ck, new FrameLayout.LayoutParams(-1, -1));
                FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(Ui.dp(ctx, 24), Ui.dp(ctx, 24), Gravity.TOP | Gravity.END);
                bl.topMargin = -Ui.dp(ctx, 6);
                bl.rightMargin = -Ui.dp(ctx, 6);
                tile.addView(badge, bl);
            }
            cell.addView(tile, new LinearLayout.LayoutParams(Ui.dp(ctx, 68), Ui.dp(ctx, 68)));

            TextView label = Ui.text(ctx, e.label, 12, Ui.TEXT);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(ctx, 88), -2);
            lp.topMargin = Ui.dp(ctx, 6);
            cell.addView(label, lp);
            return cell;
        }
    }
}

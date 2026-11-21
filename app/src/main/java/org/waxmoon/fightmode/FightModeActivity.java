package org.waxmoon.fightmode;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.waxmoon.MoonApplication;
import org.waxmoon.R;
import org.waxmoon.ui.Ui;

/** Fight Mode screen: power, live level, gain/volume, voice style, clarity/punch, preset and 6-band EQ. */
public class FightModeActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout col;
    private TextView power;
    private final View[] segs = new View[10];
    private Slider gain;
    private Slider volume;
    private Slider clarity;
    private Slider punch;
    private final TextView[] chips = new TextView[FightModeConfig.VOICE_NAMES.length];
    private TextView presetText;
    private TextView hookStatus;
    private android.widget.Switch hookSwitch;
    private TextView testText;
    private android.widget.Button testBtn;
    private volatile boolean testing;
    private volatile float testIn;
    private volatile float testOut;
    private int tickCount;
    private EqView eq;
    private boolean syncing;

    private final Runnable levelTick = new Runnable() {
        @Override
        public void run() {
            float level = testing ? Math.min(1f, testOut) : (FightModeConfig.enabled ? FightModeConfig.readLevel() : 0f);
            if (testing) {
                testText.setText("Mic test:  in " + Math.round(testIn * 100) + "%   out " + Math.round(testOut * 100)
                        + "%   (Fight Mode " + (FightModeConfig.enabled ? "ON" : "OFF") + ")");
            }
            for (int i = 0; i < segs.length; i++) {
                segs[i].setBackgroundColor(level * segs.length > i ? Ui.RED : 0xFF2A2A30);
            }
            if (tickCount++ % 12 == 0) {
                updateHookStatus();
            }
            handler.postDelayed(this, 80);
        }
    };

    private final class Slider {
        final SeekBar bar;
        final TextView value;
        final String unit;

        Slider(SeekBar bar, TextView value, String unit) {
            this.bar = bar;
            this.value = value;
            this.unit = unit;
        }

        void set(int v) {
            bar.setProgress(v);
            value.setText(v + unit);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.systemBars(this);
        FightModeConfig.init(MoonApplication.INSTANCE());
        FightModeConfig.load();
        build();
        syncFromConfig();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(levelTick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(levelTick);
        stopTest();
    }

    private void build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(Ui.pageBackground());
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        col.setPadding(pad, pad, pad, pad * 2);
        scroll.addView(col, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        // header
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout back = Ui.iconButton(this, R.drawable.ic_back, Ui.TEXT, 0xFF17171B, 0xFF2A2A30, "Back");
        back.setOnClickListener(v -> finish());
        head.addView(back);
        TextView title = Ui.title(this, "FIGHT MODE", 20);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = Ui.dp(this, 12);
        head.addView(title, tl);
        head.addView(Ui.logoBadge(this, 40));
        col.addView(head, new LinearLayout.LayoutParams(-1, -2));

        // experimental hook switch (default off)
        hookSwitch = new android.widget.Switch(this);
        hookSwitch.setText("Mic hook inside cloned apps (experimental)");
        hookSwitch.setTextColor(Ui.TEXT);
        hookSwitch.setTextSize(14);
        hookSwitch.setOnCheckedChangeListener((b, on) -> {
            if (!syncing) {
                FightModeConfig.hookEnabled = on;
                commit();
                hookStatus.setText(on ? "Hook ON. Close the cloned app completely and open it again from Home."
                        : "Hook OFF. Cloned apps open without it.");
            }
        });
        LinearLayout.LayoutParams hsw = new LinearLayout.LayoutParams(-1, -2);
        hsw.topMargin = Ui.dp(this, 14);
        col.addView(hookSwitch, hsw);

        hookStatus = Ui.text(this, "", 12, Ui.DIM);
        LinearLayout.LayoutParams hsl = new LinearLayout.LayoutParams(-1, -2);
        hsl.topMargin = Ui.dp(this, 12);
        col.addView(hookStatus, hsl);

        // power + level
        LinearLayout pr = new LinearLayout(this);
        pr.setGravity(Gravity.CENTER_VERTICAL);
        power = new TextView(this);
        power.setGravity(Gravity.CENTER);
        power.setTypeface(Ui.heading(this));
        power.setTextSize(18);
        power.setClickable(true);
        power.setOnClickListener(v -> {
            FightModeConfig.enabled = !FightModeConfig.enabled;
            styleBars();
            commit();
        });
        pr.addView(power, new LinearLayout.LayoutParams(Ui.dp(this, 84), Ui.dp(this, 84)));
        LinearLayout meter = new LinearLayout(this);
        meter.setOrientation(LinearLayout.VERTICAL);
        meter.addView(Ui.label(this, "LIVE MIC LEVEL"));
        LinearLayout bars = new LinearLayout(this);
        bars.setGravity(Gravity.BOTTOM);
        for (int i = 0; i < segs.length; i++) {
            segs[i] = new View(this);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, Ui.dp(this, 8 + i * 2), 1f);
            sp.rightMargin = i < segs.length - 1 ? Ui.dp(this, 3) : 0;
            bars.addView(segs[i], sp);
        }
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, Ui.dp(this, 28));
        bl.topMargin = Ui.dp(this, 10);
        meter.addView(bars, bl);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(0, -2, 1f);
        ml.leftMargin = Ui.dp(this, 16);
        pr.addView(meter, ml);
        LinearLayout.LayoutParams prl = new LinearLayout.LayoutParams(-1, -2);
        prl.topMargin = Ui.dp(this, 16);
        col.addView(pr, prl);

        // microphone test (works without any cloned app)
        LinearLayout tr = new LinearLayout(this);
        tr.setGravity(Gravity.CENTER_VERTICAL);
        testBtn = Ui.pill(this, "Test microphone", false);
        testBtn.setOnClickListener(v -> {
            if (testing) {
                stopTest();
            } else {
                startTest();
            }
        });
        tr.addView(testBtn);
        testText = Ui.text(this, FightModeNative.isAvailable()
                ? "Speak and watch the level meter. Compare ON and OFF."
                : "Native DSP not loaded (libfightmode)!", 12, FightModeNative.isAvailable() ? Ui.DIM : Ui.RED_LIGHT);
        LinearLayout.LayoutParams ttl = new LinearLayout.LayoutParams(0, -2, 1f);
        ttl.leftMargin = Ui.dp(this, 12);
        tr.addView(testText, ttl);
        LinearLayout.LayoutParams trl = new LinearLayout.LayoutParams(-1, -2);
        trl.topMargin = Ui.dp(this, 12);
        col.addView(tr, trl);

        // gain / volume
        LinearLayout c1 = card();
        gain = slider(c1, "Gain", 100, " dB", v -> FightModeConfig.gainDb = v);
        volume = slider(c1, "Volume", 100, "%", v -> FightModeConfig.volume = v / 100f);
        c1.addView(Ui.text(this, "Above 18 dB a noise gate fades in. Output is soft-limited, it never exceeds full scale.", 11, Ui.DIM));

        // voice style
        section("VOICE STYLE");
        LinearLayout chipRow = new LinearLayout(this);
        for (int i = 0; i < chips.length; i++) {
            final int idx = i;
            TextView c = Ui.text(this, FightModeConfig.VOICE_NAMES[i], 13, Ui.TEXT);
            c.setGravity(Gravity.CENTER);
            c.setSingleLine(true);
            c.setClickable(true);
            c.setOnClickListener(v -> {
                FightModeConfig.applyVoice(idx);
                syncFromConfig();
                commit();
            });
            chips[i] = c;
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f);
            cp.rightMargin = i < chips.length - 1 ? Ui.dp(this, 8) : 0;
            chipRow.addView(c, cp);
        }
        LinearLayout.LayoutParams crl = new LinearLayout.LayoutParams(-1, -2);
        crl.topMargin = Ui.dp(this, 10);
        col.addView(chipRow, crl);

        // clarity / punch
        LinearLayout c2 = card();
        clarity = slider(c2, "Clarity", 100, "%", v -> FightModeConfig.clarity = v / 100f);
        punch = slider(c2, "Punch", 100, "%", v -> FightModeConfig.punch = v / 100f);

        // equalizer
        LinearLayout eqHead = new LinearLayout(this);
        eqHead.setGravity(Gravity.CENTER_VERTICAL);
        eqHead.addView(Ui.label(this, "EQUALIZER"), new LinearLayout.LayoutParams(0, -2, 1f));
        presetText = Ui.text(this, "", 13, Ui.RED_LIGHT);
        presetText.setTypeface(Typeface.DEFAULT_BOLD);
        presetText.setMinHeight(Ui.dp(this, 44));
        presetText.setGravity(Gravity.CENTER_VERTICAL);
        presetText.setClickable(true);
        presetText.setOnClickListener(v -> pickPreset());
        eqHead.addView(presetText);
        LinearLayout.LayoutParams ehl = new LinearLayout.LayoutParams(-1, -2);
        ehl.topMargin = Ui.dp(this, 10);
        col.addView(eqHead, ehl);

        LinearLayout c3 = card();
        c3.setPadding(Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 6));
        eq = new EqView(this);
        eq.setListener(() -> {
            presetText.setText(presetLabel());
            commit();
        });
        c3.addView(eq, new LinearLayout.LayoutParams(-1, Ui.dp(this, 150)));

        TextView note = Ui.text(this, "Works with apps that record through the Java AudioRecord API (Telegram does). "
                + "Apps that record through native AAudio / OpenSL ES are not affected.", 11, Ui.DIM);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
        nl.topMargin = Ui.dp(this, 14);
        col.addView(note, nl);
    }

    private void startTest() {
        if (!FightModeNative.isAvailable()) {
            testText.setText("Native DSP not loaded (libfightmode)!");
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 7);
            return;
        }
        testing = true;
        testBtn.setText("Stop test");
        new Thread(() -> {
            final int rate = 44100;
            android.media.AudioRecord rec = null;
            try {
                int min = android.media.AudioRecord.getMinBufferSize(rate,
                        android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT);
                rec = new android.media.AudioRecord(android.media.MediaRecorder.AudioSource.MIC, rate,
                        android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT,
                        Math.max(min, 8192));
                if (rec.getState() != android.media.AudioRecord.STATE_INITIALIZED) {
                    throw new IllegalStateException("microphone could not be opened");
                }
                rec.startRecording();
                short[] buf = new short[2048];
                while (testing) {
                    int n = rec.read(buf, 0, buf.length);
                    if (n <= 0) {
                        continue;
                    }
                    testIn = peak(buf, n);
                    FightModeConfig.pushToNative();
                    FightModeNative.processShorts(buf, 0, n, 1, rate);
                    testOut = peak(buf, n);
                }
            } catch (final Throwable t) {
                runOnUiThread(() -> testText.setText("Mic test failed: " + t.getMessage()));
            } finally {
                testing = false;
                if (rec != null) {
                    try {
                        rec.stop();
                    } catch (Throwable ignored) {
                    }
                    rec.release();
                }
                runOnUiThread(() -> testBtn.setText("Test microphone"));
            }
        }).start();
    }

    private void stopTest() {
        testing = false;
    }

    private static float peak(short[] b, int n) {
        int p = 0;
        for (int i = 0; i < n; i++) {
            p = Math.max(p, Math.abs((int) b[i]));
        }
        return p / 32768f;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == 7 && results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startTest();
        } else if (code == 7) {
            testText.setText("Microphone permission was not given.");
        }
    }

    /** Shows whether the mic hook is active inside a guest app on this Android version. */
    private void updateHookStatus() {
        String st = FightModeConfig.readHookStatus();
        String ver = "Android " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")";
        if (st == null) {
            hookStatus.setText("Mic hook in cloned apps: not seen yet. It only starts when you open a cloned app from Home. " + ver);
            hookStatus.setTextColor(Ui.DIM);
            return;
        }
        String[] p = st.split("\\|", -1);
        if (p.length > 0 && "OFF".equals(p[0])) {
            hookStatus.setText("Mic hook is switched off (cloned apps open without it). " + ver);
            hookStatus.setTextColor(Ui.DIM);
            return;
        }
        boolean ok = p.length > 0 && "OK".equals(p[0]);
        String proc = p.length > 1 ? p[1] : "?";
        String api = p.length > 2 ? p[2] : "?";
        String err = p.length > 4 && !p[4].isEmpty() ? " " + p[4] : "";
        hookStatus.setText(ok
                ? "Mic hook: active in " + proc + " (API " + api + ")"
                : "Mic hook: FAILED in " + proc + " on API " + api + "." + err);
        hookStatus.setTextColor(ok ? 0xFF7DDC9A : Ui.RED_LIGHT);
    }

    private interface IntSetter {
        void set(int v);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.rect(this, Ui.CARD, 14, Ui.BORDER, 1));
        c.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(this, 14);
        col.addView(c, lp);
        return c;
    }

    private void section(String s) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = Ui.dp(this, 18);
        col.addView(Ui.label(this, s), lp);
    }

    private Slider slider(LinearLayout parent, String name, int max, String unit, IntSetter setter) {
        LinearLayout row = new LinearLayout(this);
        TextView n = Ui.text(this, name, 14, Ui.TEXT);
        n.setTypeface(Typeface.DEFAULT_BOLD);
        TextView v = Ui.text(this, "", 14, Ui.RED_LIGHT);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(v);
        parent.addView(row);
        SeekBar bar = new SeekBar(this);
        bar.setMax(max);
        bar.setProgressTintList(ColorStateList.valueOf(Ui.RED));
        bar.setThumbTintList(ColorStateList.valueOf(Ui.RED));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF3A3A42));
        final Slider s = new Slider(bar, v, unit);
        bar.setContentDescription(name);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar b, int p, boolean fromUser) {
                if (fromUser && !syncing) {
                    setter.set(p);
                    s.value.setText(p + s.unit);
                    syncVoiceAndPreset();
                    commit();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar b) {}

            @Override
            public void onStopTrackingTouch(SeekBar b) {}
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 36));
        parent.addView(bar, bp);
        return s;
    }

    private void pickPreset() {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Equalizer preset")
                .setItems(FightModeConfig.PRESET_NAMES, (d, which) -> {
                    FightModeConfig.applyPreset(which);
                    syncFromConfig();
                    commit();
                })
                .show();
    }

    private void styleBars() {
        boolean on = FightModeConfig.enabled;
        power.setText(on ? "ON" : "OFF");
        power.setTextColor(on ? Color.WHITE : Ui.DIM);
        power.setContentDescription(on ? "Fight Mode is on, tap to turn off" : "Fight Mode is off, tap to turn on");
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientCenter(0.5f, 0.35f);
        g.setGradientRadius(Ui.dp(this, 60));
        g.setColors(on ? new int[]{0xFFFF3B4B, Ui.RED_DARK} : new int[]{0xFF2A2A30, 0xFF17171B});
        g.setStroke(Ui.dp(this, 3), on ? Ui.RED_LIGHT : 0xFF3A3A42);
        power.setBackground(g);
        if (on) {
            Ui.glow(power, Ui.RED);
        } else if (android.os.Build.VERSION.SDK_INT >= 21) {
            power.setElevation(0);
        }
    }

    private String presetLabel() {
        int p = matchingPreset();
        return "Preset: " + (p < 0 ? "Custom" : FightModeConfig.PRESET_NAMES[p]);
    }

    private void syncVoiceAndPreset() {
        int v = matchingVoice();
        for (int i = 0; i < chips.length; i++) {
            boolean sel = i == v;
            chips[i].setTextColor(sel ? Color.WHITE : Ui.TEXT);
            chips[i].setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            chips[i].setBackground(sel ? Ui.rect(this, Ui.RED, 10, Ui.RED_LIGHT, 1) : Ui.rect(this, Ui.CHIP, 10, Ui.CHIP_BORDER, 1));
        }
        presetText.setText(presetLabel());
    }

    private void syncFromConfig() {
        syncing = true;
        hookSwitch.setChecked(FightModeConfig.hookEnabled);
        styleBars();
        gain.set(Math.round(FightModeConfig.gainDb));
        volume.set(Math.round(FightModeConfig.volume * 100f));
        clarity.set(Math.round(FightModeConfig.clarity * 100f));
        punch.set(Math.round(FightModeConfig.punch * 100f));
        syncVoiceAndPreset();
        eq.invalidate();
        syncing = false;
    }

    private int matchingPreset() {
        for (int p = 0; p < FightModeConfig.PRESETS.length; p++) {
            boolean same = true;
            for (int i = 0; i < FightModeConfig.BANDS; i++) {
                if (Math.abs(FightModeConfig.PRESETS[p][i] - FightModeConfig.eqDb[i]) > 0.01f) {
                    same = false;
                    break;
                }
            }
            if (same) {
                return p;
            }
        }
        return -1;
    }

    private int matchingVoice() {
        for (int i = 0; i < FightModeConfig.VOICE_NAMES.length; i++) {
            if (Math.abs(FightModeConfig.VOICE_CLARITY[i] - FightModeConfig.clarity) < 0.02f
                    && Math.abs(FightModeConfig.VOICE_PUNCH[i] - FightModeConfig.punch) < 0.02f) {
                return i;
            }
        }
        return -1;
    }

    private void commit() {
        FightModeConfig.save();
    }
}

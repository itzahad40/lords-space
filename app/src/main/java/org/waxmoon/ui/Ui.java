package org.waxmoon.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.waxmoon.R;

/** Shared look of Lord's-Space: black + crimson, Cinzel headings, rounded cards. */
public final class Ui {
    public static final int BG = 0xFF0A0A0C;
    public static final int CARD = 0xFF131316;
    public static final int BORDER = 0xFF26262C;
    public static final int CHIP = 0xFF17171B;
    public static final int CHIP_BORDER = 0xFF2A2A30;
    public static final int TEXT = 0xFFF3EFEF;
    public static final int DIM = 0xFFB3A9AC;
    public static final int RED = 0xFFE11D2E;
    public static final int RED_LIGHT = 0xFFFF5A67;
    public static final int RED_DARK = 0xFFA30F1D;
    public static final int WINE = 0xFF2A0A10;

    private static Typeface heading;

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable rect(Context c, int fill, float radiusDp, int stroke, float strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        if (strokeDp > 0) {
            d.setStroke(dp(c, strokeDp), stroke);
        }
        return d;
    }

    public static Drawable pageBackground() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF2E0A12, BG, BG});
    }

    public static Typeface heading(Context c) {
        if (heading == null) {
            try {
                heading = Typeface.createFromAsset(c.getAssets(), "fonts/Cinzel.ttf");
            } catch (Throwable e) {
                heading = Typeface.SERIF;
            }
        }
        return Typeface.create(heading, Typeface.BOLD);
    }

    public static TextView text(Context c, CharSequence s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    public static TextView title(Context c, CharSequence s, float sp) {
        TextView t = text(c, s, sp, TEXT);
        t.setTypeface(heading(c));
        t.setLetterSpacing(0.1f);
        return t;
    }

    public static TextView label(Context c, CharSequence s) {
        TextView t = text(c, s, 12, DIM);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.14f);
        return t;
    }

    /** Logo as a rounded square (radiusDp) or circle (radiusDp < 0). */
    public static ImageView logo(Context c, float radiusDp) {
        ImageView v = new ImageView(c);
        v.setImageResource(R.drawable.lords_logo);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setContentDescription("Lords Community logo");
        final float r = radiusDp < 0 ? -1 : dp(c, radiusDp);
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline o) {
                float rad = r < 0 ? Math.min(view.getWidth(), view.getHeight()) / 2f : r;
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), rad);
            }
        });
        v.setClipToOutline(true);
        return v;
    }

    /** Small round logo with a crimson ring (header). */
    public static View logoBadge(Context c, int sizeDp) {
        FrameLayout f = new FrameLayout(c);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Color.BLACK);
        ring.setStroke(dp(c, 1.5f), RED);
        f.setBackground(ring);
        int m = dp(c, 2);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(c, sizeDp) - 2 * m, dp(c, sizeDp) - 2 * m);
        lp.setMargins(m, m, m, m);
        f.addView(logo(c, -1), lp);
        f.setLayoutParams(new android.widget.LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return f;
    }

    public static void glow(View v, int color) {
        if (Build.VERSION.SDK_INT >= 28) {
            v.setElevation(dp(v.getContext(), 24));
            v.setOutlineAmbientShadowColor(color);
            v.setOutlineSpotShadowColor(color);
        }
    }

    /** 44dp square icon button. */
    public static FrameLayout iconButton(Context c, int iconRes, int tint, int fill, int border, String desc) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(rect(c, fill, 12, border, 1));
        f.setClickable(true);
        f.setFocusable(true);
        f.setContentDescription(desc);
        ImageView i = new ImageView(c);
        i.setImageResource(iconRes);
        i.setColorFilter(tint);
        int s = dp(c, 22);
        f.addView(i, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
        f.setLayoutParams(new android.widget.LinearLayout.LayoutParams(dp(c, 44), dp(c, 44)));
        return f;
    }

    /** Text button, min height 44dp. filled = crimson, otherwise outlined. */
    public static Button pill(Context c, CharSequence s, boolean filled) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(filled ? Color.WHITE : TEXT);
        b.setBackground(filled ? rect(c, RED, 10, 0, 0) : rect(c, Color.TRANSPARENT, 10, 0xFF3A3A42, 1));
        b.setStateListAnimator(null);
        b.setMinHeight(dp(c, 44));
        b.setMinimumHeight(dp(c, 44));
        b.setMinWidth(dp(c, 64));
        b.setMinimumWidth(dp(c, 64));
        b.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        return b;
    }

    public static void systemBars(android.app.Activity a) {
        a.getWindow().setStatusBarColor(BG);
        a.getWindow().setNavigationBarColor(BG);
    }

    private Ui() {}
}

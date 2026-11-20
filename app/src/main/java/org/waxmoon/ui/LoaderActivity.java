package org.waxmoon.ui;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** App loader / splash: logo, tagline and a progress bar, then opens Home. */
public class LoaderActivity extends Activity {
    private static final int BAR_DP = 250;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.systemBars(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF3A0A18, 0xFF0C0509, 0xFF050507}));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        ImageView logo = Ui.logo(this, 26);
        Ui.glow(logo, Ui.RED);
        center.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 220), Ui.dp(this, 220)));
        TextView name = Ui.title(this, "Lord's-Space", 34);
        name.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(-2, -2);
        nameLp.topMargin = Ui.dp(this, 24);
        center.addView(name, nameLp);
        TextView tag = Ui.text(this, "Multiple Spaces. One App.", 15, 0xFFD9CFD2);
        tag.setLetterSpacing(0.04f);
        LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(-2, -2);
        tagLp.topMargin = Ui.dp(this, 10);
        center.addView(tag, tagLp);
        root.addView(center, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        bottom.addView(Ui.text(this, "Loading your spaces...", 13, 0xFFCFC4C7));
        FrameLayout track = new FrameLayout(this);
        track.setBackground(Ui.rect(this, 0xFF26181B, 3, 0, 0));
        final View fill = new View(this);
        GradientDrawable fillBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Ui.RED_DARK, Ui.RED, Ui.RED_LIGHT});
        fillBg.setCornerRadius(Ui.dp(this, 3));
        fill.setBackground(fillBg);
        track.addView(fill, new FrameLayout.LayoutParams(0, -1));
        track.setContentDescription("Loading your spaces");
        LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(Ui.dp(this, BAR_DP), Ui.dp(this, 6));
        trackLp.topMargin = Ui.dp(this, 12);
        bottom.addView(track, trackLp);
        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        bottomLp.bottomMargin = Ui.dp(this, 64);
        root.addView(bottom, bottomLp);
        setContentView(root);

        final int full = Ui.dp(this, BAR_DP);
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(1500);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            fill.getLayoutParams().width = Math.round(full * (float) a.getAnimatedValue());
            fill.requestLayout();
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (!isFinishing()) {
                    startActivity(new Intent(LoaderActivity.this, HomeActivity.class));
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    finish();
                }
            }
        });
        anim.start();
    }
}

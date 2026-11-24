package org.lordspace.fightmode;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import org.lordspace.ui.Ui;

/** Six vertical EQ bars (-12..+12 dB). Drag on a bar to set its gain. */
public class EqView extends View {
    public interface Listener {
        void onChanged();
    }

    private static final String[] LABELS = {"60 Hz", "400 Hz", "1 kHz", "2.5 kHz", "6.3 kHz", "16 kHz"};
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float labelH;
    private final float gap;
    private Listener listener;

    public EqView(Context c) {
        super(c);
        labelH = Ui.dp(c, 38);
        gap = Ui.dp(c, 8);
        setContentDescription("Equalizer, six bands");
    }

    public void setListener(Listener l) {
        listener = l;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int n = FightModeConfig.BANDS;
        float colW = (getWidth() - gap * (n - 1)) / n;
        float trackH = getHeight() - labelH;
        float mid = trackH / 2f;
        float radius = Ui.dp(getContext(), 6);
        for (int i = 0; i < n; i++) {
            float x = i * (colW + gap);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF1D1D22);
            r.set(x, 0, x + colW, trackH);
            canvas.drawRoundRect(r, radius, radius, p);

            float db = FightModeConfig.eqDb[i];
            float amount = db / FightModeConfig.MAX_EQ_DB * mid;
            p.setColor(Ui.RED);
            if (Math.abs(amount) < 2) {
                r.set(x, mid - 1, x + colW, mid + 1);
            } else if (amount > 0) {
                r.set(x, mid - amount, x + colW, mid);
            } else {
                r.set(x, mid, x + colW, mid - amount);
            }
            canvas.drawRoundRect(r, 3, 3, p);

            p.setColor(0x33FFFFFF);
            canvas.drawRect(x, mid - 0.5f, x + colW, mid + 0.5f, p);

            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(Ui.dp(getContext(), 11));
            p.setColor(Ui.DIM);
            canvas.drawText(LABELS[i], x + colW / 2f, trackH + Ui.dp(getContext(), 16), p);
            p.setColor(Ui.RED_LIGHT);
            int v = Math.round(db);
            canvas.drawText((v > 0 ? "+" : "") + v + " dB", x + colW / 2f, trackH + Ui.dp(getContext(), 31), p);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) {
            getParent().requestDisallowInterceptTouchEvent(true);
            final int n = FightModeConfig.BANDS;
            float colW = (getWidth() - gap * (n - 1)) / n;
            int col = (int) (e.getX() / (colW + gap));
            col = Math.max(0, Math.min(n - 1, col));
            float trackH = getHeight() - labelH;
            float db = (0.5f - e.getY() / trackH) * 2f * FightModeConfig.MAX_EQ_DB;
            db = Math.round(Math.max(-FightModeConfig.MAX_EQ_DB, Math.min(FightModeConfig.MAX_EQ_DB, db)));
            if (FightModeConfig.eqDb[col] != db) {
                FightModeConfig.eqDb[col] = db;
                invalidate();
                if (listener != null) {
                    listener.onChanged();
                }
            }
            return true;
        }
        if (a == MotionEvent.ACTION_UP) {
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}

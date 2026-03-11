package com.frontline.shooter;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Right-side aim pad. Sliding finger rotates the aim angle.
 * Touching = shooting.
 */
public class AimPadView extends View {

    public interface Listener {
        void onAim(float angleRadians, boolean shooting);
    }

    private Listener listener;
    private float centerX, centerY;
    private float curX, curY;
    private boolean touching = false;

    private final Paint bgPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public AimPadView(Context ctx, AttributeSet attrs) { super(ctx, attrs); init(); }
    public AimPadView(Context ctx) { super(ctx); init(); }

    private void init() {
        bgPaint.setColor(Color.argb(50, 255, 100, 100));
        bgPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(Color.argb(180, 255, 120, 120));
        dotPaint.setStyle(Paint.Style.FILL);
        linePaint.setColor(Color.argb(120, 255, 80, 80));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(3);
    }

    public void setListener(Listener l) { this.listener = l; }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        centerX = w / 2f; centerY = h / 2f;
        curX = centerX; curY = centerY;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);
        // Crosshair
        linePaint.setAlpha(60);
        canvas.drawLine(centerX - 60, centerY, centerX + 60, centerY, linePaint);
        canvas.drawLine(centerX, centerY - 60, centerX, centerY + 60, linePaint);
        linePaint.setAlpha(120);
        if (touching) {
            canvas.drawLine(centerX, centerY, curX, curY, linePaint);
            canvas.drawCircle(curX, curY, 18, dotPaint);
        }
        // Label
        Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG);
        lp.setColor(Color.argb(100, 255, 255, 255));
        lp.setTextSize(22); lp.setTextAlign(Paint.Align.CENTER);
        lp.setTypeface(Typeface.MONOSPACE);
        canvas.drawText("AIM", centerX, centerY - 80, lp);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                curX = e.getX(); curY = e.getY();
                touching = true;
                float dx = curX - centerX, dy = curY - centerY;
                float angle = (float) Math.atan2(dy, dx);
                if (listener != null) listener.onAim(angle, true);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                touching = false;
                if (listener != null) listener.onAim(0, false);
                invalidate();
                return true;
        }
        return false;
    }
}

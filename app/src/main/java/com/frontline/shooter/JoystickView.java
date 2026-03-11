package com.frontline.shooter;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Virtual joystick – fixed position, reports normalized dx/dy (-1..1).
 */
public class JoystickView extends View {

    public interface Listener {
        void onMove(float dx, float dy);
    }

    private Listener listener;

    // Geometry
    private float baseX, baseY, baseRadius;
    private float knobX, knobY, knobRadius;

    private int activePointerId = -1;

    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);

    public JoystickView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        init();
    }
    public JoystickView(Context ctx) {
        super(ctx);
        init();
    }

    private void init() {
        basePaint.setColor(Color.argb(80, 255, 255, 255));
        basePaint.setStyle(Paint.Style.FILL);

        rimPaint.setColor(Color.argb(140, 80, 180, 80));
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(3);

        knobPaint.setColor(Color.argb(200, 80, 200, 80));
        knobPaint.setStyle(Paint.Style.FILL);
    }

    public void setListener(Listener l) { this.listener = l; }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        baseX = w / 2f;
        baseY = h / 2f;
        baseRadius  = Math.min(w, h) / 2f - 10;
        knobRadius  = baseRadius * 0.42f;
        knobX = baseX; knobY = baseY;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Base circle
        canvas.drawCircle(baseX, baseY, baseRadius, basePaint);
        canvas.drawCircle(baseX, baseY, baseRadius, rimPaint);
        // Knob
        canvas.drawCircle(knobX, knobY, knobRadius, knobPaint);
        // Cross-hair lines on base
        rimPaint.setAlpha(60);
        canvas.drawLine(baseX - baseRadius*0.8f, baseY, baseX + baseRadius*0.8f, baseY, rimPaint);
        canvas.drawLine(baseX, baseY - baseRadius*0.8f, baseX, baseY + baseRadius*0.8f, rimPaint);
        rimPaint.setAlpha(140);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int idx = event.getActionIndex();
                activePointerId = event.getPointerId(idx);
                moveKnob(event.getX(idx), event.getY(idx));
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                int idx = event.findPointerIndex(activePointerId);
                if (idx >= 0) moveKnob(event.getX(idx), event.getY(idx));
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL: {
                int idx = event.getActionIndex();
                if (event.getPointerId(idx) == activePointerId) {
                    resetKnob();
                }
                return true;
            }
        }
        return false;
    }

    private void moveKnob(float tx, float ty) {
        float dx = tx - baseX, dy = ty - baseY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float maxDist = baseRadius - knobRadius;
        if (dist > maxDist) { dx = dx / dist * maxDist; dy = dy / dist * maxDist; dist = maxDist; }
        knobX = baseX + dx; knobY = baseY + dy;
        if (listener != null && maxDist > 0) listener.onMove(dx / maxDist, dy / maxDist);
        invalidate();
    }

    private void resetKnob() {
        knobX = baseX; knobY = baseY;
        activePointerId = -1;
        if (listener != null) listener.onMove(0, 0);
        invalidate();
    }
}

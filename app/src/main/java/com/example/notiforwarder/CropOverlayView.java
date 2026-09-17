package com.example.notiforwarder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class CropOverlayView extends View {

    private final Paint dimPaint   = new Paint();
    private final Paint clearPaint = new Paint();
    private final Paint borderPaint = new Paint();

    private float startX, startY, curX, curY;
    private boolean dragging = false;
    private boolean hasRect  = false;
    private RectF rect = new RectF();

    public CropOverlayView(Context c) { super(c); init(); }
    public CropOverlayView(Context c, AttributeSet a) { super(c, a); init(); }
    public CropOverlayView(Context c, AttributeSet a, int d) { super(c, a, d); init(); }

    private void init() {
        dimPaint.setColor(0x80000000);
        clearPaint.setColor(Color.TRANSPARENT);
        clearPaint.setXfermode(new android.graphics.PorterDuffXfermode(
                android.graphics.PorterDuff.Mode.CLEAR));
        borderPaint.setColor(0xFFFFEB3B);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(4f);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!hasRect) return;
        // Dim everything, clear the crop rect
        canvas.drawRect(0, 0, getWidth(), getHeight(), dimPaint);
        canvas.drawRect(rect, clearPaint);
        canvas.drawRect(rect, borderPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startX = e.getX(); startY = e.getY();
                curX = startX;     curY = startY;
                dragging = true;
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging) return false;
                curX = e.getX(); curY = e.getY();
                updateRect();
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                dragging = false;
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void updateRect() {
        float l = Math.max(0, Math.min(startX, curX));
        float t = Math.max(0, Math.min(startY, curY));
        float r = Math.min(getWidth(),  Math.max(startX, curX));
        float b = Math.min(getHeight(), Math.max(startY, curY));
        if (r - l < 8 || b - t < 8) { hasRect = false; return; }
        rect.set(l, t, r, b);
        hasRect = true;
    }

    public boolean hasCrop() { return hasRect; }

    public RectF getCropRect() { return new RectF(rect); }

    public void clear() {
        hasRect = false;
        invalidate();
    }
}
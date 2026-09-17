package com.example.notiforwarder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class TextLensView extends View {

    public static class TextLineBox {
        public RectF rect;      // in view coordinates
        public String text;
        public TextLineBox(RectF r, String t) { rect = r; text = t; }
    }

    public interface OnTextSelectedListener {
        void onSelected(String text);
        void onCleared();
    }

    private final List<TextLineBox> boxes = new ArrayList<>();
    private int selectedIndex = -1;

    private final Paint boxPaint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);

    private OnTextSelectedListener listener;

    public TextLensView(Context c) { super(c); init(); }
    public TextLensView(Context c, AttributeSet a) { super(c, a); init(); }
    public TextLensView(Context c, AttributeSet a, int d) { super(c, a, d); init(); }

    private void init() {
        boxPaint.setColor(0xFF00E5FF);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(4f);

        selectedPaint.setColor(0xFFFFEB3B);
        selectedPaint.setStyle(Paint.Style.STROKE);
        selectedPaint.setStrokeWidth(6f);

        fillPaint.setColor(0x2200E5FF);
        fillPaint.setStyle(Paint.Style.FILL);

        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setOnTextSelectedListener(OnTextSelectedListener l) { listener = l; }

    public void setBoxes(List<TextLineBox> newBoxes) {
        boxes.clear();
        boxes.addAll(newBoxes);
        selectedIndex = -1;
        invalidate();
    }

    public void clear() {
        boxes.clear();
        selectedIndex = -1;
        invalidate();
    }

    public String getAllText() {
        StringBuilder sb = new StringBuilder();
        for (TextLineBox b : boxes) sb.append(b.text).append("\n");
        return sb.toString().trim();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (int i = 0; i < boxes.size(); i++) {
            RectF r = boxes.get(i).rect;
            if (i == selectedIndex) {
                canvas.drawRect(r, fillPaint);
                canvas.drawRect(r, selectedPaint);
            } else {
                canvas.drawRect(r, boxPaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_UP) return true;

        float x = e.getX(), y = e.getY();
        // Find the smallest box containing the tap
        int best = -1;
        float bestArea = Float.MAX_VALUE;
        for (int i = 0; i < boxes.size(); i++) {
            RectF r = boxes.get(i).rect;
            // Expand touch area slightly
            RectF r2 = new RectF(r.left - 12, r.top - 12, r.right + 12, r.bottom + 12);
            if (r2.contains(x, y)) {
                float area = r.width() * r.height();
                if (area < bestArea) { bestArea = area; best = i; }
            }
        }

        if (best >= 0) {
            selectedIndex = best;
            invalidate();
            if (listener != null) listener.onSelected(boxes.get(best).text);
        } else {
            selectedIndex = -1;
            invalidate();
            if (listener != null) listener.onCleared();
        }
        return true;
    }
}
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
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class TextLensView extends View {

    public static class TextLineBox {
        public RectF rect;
        public String text;
        public int index;   // original reading order
        public TextLineBox(RectF r, String t, int idx) {
            rect = r; text = t; index = idx;
        }
    }

    public interface OnSelectionChangedListener {
        void onSelectionChanged(String selectedText, int lineCount);
        void onSelectionCleared();
    }

    private final List<TextLineBox> boxes = new ArrayList<>();
    private final Set<Integer> selected = new LinkedHashSet<>();
    private int anchorIndex = -1;

    private final Paint idlePaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selFill       = new Paint(Paint.ANTI_ALIAS_FLAG);

    private OnSelectionChangedListener listener;

    public TextLensView(Context c) { super(c); init(); }
    public TextLensView(Context c, AttributeSet a) { super(c, a); init(); }
    public TextLensView(Context c, AttributeSet a, int d) { super(c, a, d); init(); }

    private void init() {
        idlePaint.setColor(0xFF00E5FF);
        idlePaint.setStyle(Paint.Style.STROKE);
        idlePaint.setStrokeWidth(3f);

        selectedPaint.setColor(0xFFFFC107);
        selectedPaint.setStyle(Paint.Style.STROKE);
        selectedPaint.setStrokeWidth(6f);

        selFill.setColor(0x55FFC107);
        selFill.setStyle(Paint.Style.FILL);

        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setListener(OnSelectionChangedListener l) { listener = l; }

    public void setBoxes(List<TextLineBox> newBoxes) {
        boxes.clear();
        boxes.addAll(newBoxes);
        selected.clear();
        anchorIndex = -1;
        invalidate();
    }

    public void clearSelection() {
        selected.clear();
        anchorIndex = -1;
        invalidate();
        if (listener != null) listener.onSelectionCleared();
    }

    public String getSelectedText() {
        List<Integer> sorted = new ArrayList<>(selected);
        Collections.sort(sorted);
        StringBuilder sb = new StringBuilder();
        for (int i : sorted) sb.append(boxes.get(i).text).append("\n");
        return sb.toString().trim();
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
            if (selected.contains(i)) {
                canvas.drawRect(r, selFill);
                canvas.drawRect(r, selectedPaint);
            } else {
                canvas.drawRect(r, idlePaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                int hit = findBoxAt(e.getX(), e.getY());
                if (hit >= 0) {
                    anchorIndex = hit;
                    selected.clear();
                    selected.add(hit);
                    invalidate();
                    notifyChanged();
                } else {
                    // cleared tap outside
                    if (!selected.isEmpty()) {
                        selected.clear();
                        anchorIndex = -1;
                        invalidate();
                        if (listener != null) listener.onSelectionCleared();
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (anchorIndex < 0) return true;
                int hit = findBoxAt(e.getX(), e.getY());
                if (hit < 0) return true;
                int lo = Math.min(anchorIndex, hit);
                int hi = Math.max(anchorIndex, hit);
                selected.clear();
                for (int i = lo; i <= hi; i++) selected.add(i);
                invalidate();
                notifyChanged();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void notifyChanged() {
        if (listener == null) return;
        if (selected.isEmpty()) listener.onSelectionCleared();
        else listener.onSelectionChanged(getSelectedText(), selected.size());
    }

    private int findBoxAt(float x, float y) {
        int best = -1;
        float bestArea = Float.MAX_VALUE;
        for (int i = 0; i < boxes.size(); i++) {
            RectF r = boxes.get(i).rect;
            RectF r2 = new RectF(r.left - 14, r.top - 14, r.right + 14, r.bottom + 14);
            if (r2.contains(x, y)) {
                float area = r.width() * r.height();
                if (area < bestArea) { bestArea = area; best = i; }
            }
        }
        return best;
    }

    /** Sort boxes in natural reading order (top-to-bottom, left-to-right). */
    public void sortReadingOrder() {
        Collections.sort(boxes, new Comparator<TextLineBox>() {
            @Override public int compare(TextLineBox a, TextLineBox b) {
                int rowDelta = (int) (a.rect.top - b.rect.top);
                if (Math.abs(rowDelta) > 20) return rowDelta;
                return (int) (a.rect.left - b.rect.left);
            }
        });
    }
}
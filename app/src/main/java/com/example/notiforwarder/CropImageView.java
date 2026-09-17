package com.example.notiforwarder;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

public class CropImageView extends View {

    private Bitmap bitmap;
    private final Matrix matrix = new Matrix();
    private final Matrix baseMatrix = new Matrix();

    private final Paint bmpPaint   = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint dimPaint   = new Paint();
    private final Paint borderPaint = new Paint();
    private final Paint cornerPaint = new Paint();

    private RectF cropRect = new RectF();

    private float lastX, lastY;
    private boolean dragging = false;

    private ScaleGestureDetector scaleDetector;

    public CropImageView(Context c) { super(c); init(); }
    public CropImageView(Context c, AttributeSet a) { super(c, a); init(); }
    public CropImageView(Context c, AttributeSet a, int d) { super(c, a, d); init(); }

    private void init() {
        dimPaint.setColor(0xAA000000);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3f);
        cornerPaint.setColor(0xFFFFEB3B);
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(6f);
        cornerPaint.setStrokeCap(Paint.Cap.ROUND);

        scaleDetector = new ScaleGestureDetector(getContext(),
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                float factor = d.getScaleFactor();
                matrix.postScale(factor, factor, d.getFocusX(), d.getFocusY());
                constrain();
                invalidate();
                return true;
            }
        });
    }

    public void setBitmap(Bitmap bmp) {
        this.bitmap = bmp;
        requestLayout();
    }

    public Bitmap getBitmap() { return bitmap; }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        computeCropRect(w, h);
        resetMatrix(w, h);
        invalidate();
    }

    private void computeCropRect(int w, int h) {
        float pad = Math.min(w, h) * 0.08f;
        float left = pad;
        float top = pad + 60;              // leave space for hint at top
        float right = w - pad;
        float bottom = h - pad - 100;      // leave space for buttons at bottom
        cropRect.set(left, top, right, bottom);
    }

    private void resetMatrix(int viewW, int viewH) {
        if (bitmap == null) return;
        float scale = Math.max(
            cropRect.width()  / bitmap.getWidth(),
            cropRect.height() / bitmap.getHeight());
        float dx = cropRect.centerX() - bitmap.getWidth()  * scale / 2f;
        float dy = cropRect.centerY() - bitmap.getHeight() * scale / 2f;
        matrix.reset();
        matrix.postScale(scale, scale);
        matrix.postTranslate(dx, dy);
        baseMatrix.set(matrix);
        constrain();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null) return;
        canvas.drawBitmap(bitmap, matrix, bmpPaint);

        // Dim outside crop
        canvas.save();
        canvas.clipRect(cropRect, android.graphics.Region.Op.DIFFERENCE);
        canvas.drawRect(0, 0, getWidth(), getHeight(), dimPaint);
        canvas.restore();

        // Crop border
        canvas.drawRect(cropRect, borderPaint);

        // Corner marks
        float c = 30f;
        float r = cropRect.left, t = cropRect.top, rr = cropRect.right, b = cropRect.bottom;
        // top-left
        canvas.drawLine(r, t, r + c, t, cornerPaint);
        canvas.drawLine(r, t, r, t + c, cornerPaint);
        // top-right
        canvas.drawLine(rr, t, rr - c, t, cornerPaint);
        canvas.drawLine(rr, t, rr, t + c, cornerPaint);
        // bottom-left
        canvas.drawLine(r, b, r + c, b, cornerPaint);
        canvas.drawLine(r, b, r, b - c, cornerPaint);
        // bottom-right
        canvas.drawLine(rr, b, rr - c, b, cornerPaint);
        canvas.drawLine(rr, b, rr, b - c, cornerPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaleDetector.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = e.getX(); lastY = e.getY();
                dragging = true;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging && e.getPointerCount() == 1) {
                    float dx = e.getX() - lastX;
                    float dy = e.getY() - lastY;
                    matrix.postTranslate(dx, dy);
                    constrain();
                    invalidate();
                }
                lastX = e.getX(); lastY = e.getY();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void constrain() {
        if (bitmap == null) return;
        float[] v = new float[9];
        matrix.getValues(v);
        float scale = v[Matrix.MSCALE_X];
        float transX = v[Matrix.MTRANS_X];
        float transY = v[Matrix.MTRANS_Y];

        float bmpW = bitmap.getWidth()  * scale;
        float bmpH = bitmap.getHeight() * scale;

        // Don't allow crop rect to go outside bitmap
        float minX = cropRect.right  - bmpW;
        float maxX = cropRect.left;
        float minY = cropRect.bottom - bmpH;
        float maxY = cropRect.top;

        if (bmpW < cropRect.width()) {
            transX = cropRect.centerX() - bmpW / 2f;
        } else {
            transX = Math.max(minX, Math.min(maxX, transX));
        }
        if (bmpH < cropRect.height()) {
            transY = cropRect.centerY() - bmpH / 2f;
        } else {
            transY = Math.max(minY, Math.min(maxY, transY));
        }

        v[Matrix.MTRANS_X] = transX;
        v[Matrix.MTRANS_Y] = transY;
        matrix.setValues(v);
    }

    /** Returns the cropped bitmap based on crop rect. */
    public Bitmap getCroppedBitmap() {
        if (bitmap == null) return null;
        float[] v = new float[9];
        matrix.getValues(v);
        float scale = v[Matrix.MSCALE_X];
        float transX = v[Matrix.MTRANS_X];
        float transY = v[Matrix.MTRANS_Y];

        float x = (cropRect.left   - transX) / scale;
        float y = (cropRect.top    - transY) / scale;
        float w = cropRect.width()  / scale;
        float h = cropRect.height() / scale;

        x = Math.max(0, x);
        y = Math.max(0, y);
        w = Math.min(bitmap.getWidth()  - x, w);
        h = Math.min(bitmap.getHeight() - y, h);

        if (w <= 0 || h <= 0) return null;
        return Bitmap.createBitmap(bitmap, (int) x, (int) y, (int) w, (int) h);
    }

    /** Rotates the image 90 degrees clockwise. */
    public void rotate90() {
        if (bitmap == null) return;
        Matrix m = new Matrix();
        m.postRotate(90);
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0,
                bitmap.getWidth(), bitmap.getHeight(), m, true);
        bitmap = rotated;
        resetMatrix(getWidth(), getHeight());
        invalidate();
    }
}
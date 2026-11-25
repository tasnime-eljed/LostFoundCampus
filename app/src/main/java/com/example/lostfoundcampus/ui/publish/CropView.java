package com.example.lostfoundcampus.ui.publish;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.lostfoundcampus.R;

/**
 * Photo affichée en entier avec un cadre réglable : glisser un coin pour redimensionner,
 * glisser l'intérieur pour déplacer. Le cadre est exposé en coordonnées relatives (0..1) de l'image.
 */
public class CropView extends View {

    public interface Listener {
        void onCropChanged();
    }

    private static final int NONE = 0, MOVE = 1, TL = 2, TR = 3, BL = 4, BR = 5;

    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint dimPaint = new Paint();
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path dimPath = new Path();

    private final float handleLen;
    private final float touchRadius;
    private final float minSize;

    @Nullable
    private Bitmap bitmap;
    /** Zone où l'image est dessinée (fitCenter), en pixels de la vue. */
    private final RectF imageRect = new RectF();
    /** Cadre en pixels de la vue. */
    private final RectF frame = new RectF();
    /** Cadre relatif à appliquer au prochain calcul de mise en page. */
    @Nullable
    private RectF pendingRelative;
    private int mode = NONE;
    private float lastX, lastY;
    @Nullable
    private Listener listener;

    public CropView(Context context) {
        this(context, null);
    }

    public CropView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float d = getResources().getDisplayMetrics().density;
        handleLen = 22 * d;
        touchRadius = 32 * d;
        minSize = 56 * d;
        dimPaint.setColor(ContextCompat.getColor(context, R.color.ai_scrim));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2 * d);
        borderPaint.setColor(ContextCompat.getColor(context, R.color.lf_on_dark));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1 * d);
        gridPaint.setColor(ContextCompat.getColor(context, R.color.ai_band));
        handlePaint.setStyle(Paint.Style.STROKE);
        handlePaint.setStrokeWidth(4 * d);
        handlePaint.setStrokeCap(Paint.Cap.ROUND);
        handlePaint.setColor(ContextCompat.getColor(context, R.color.lf_peach));
    }

    public void setListener(@Nullable Listener l) {
        listener = l;
    }

    public void setImage(@NonNull Bitmap bmp, @Nullable RectF relative) {
        bitmap = bmp;
        pendingRelative = relative != null ? new RectF(relative) : defaultRelative();
        layoutImage();
        invalidate();
    }

    /** Cadre par défaut : la partie centrale, là où l'on vise d'habitude l'objet. */
    private static RectF defaultRelative() {
        return new RectF(0.15f, 0.15f, 0.85f, 0.85f);
    }

    public void resetToFullImage() {
        pendingRelative = new RectF(0f, 0f, 1f, 1f);
        layoutImage();
        invalidate();
        if (listener != null) listener.onCropChanged();
    }

    /** Cadre relatif (0..1) de l'image, ou {@code null} si l'image n'est pas encore chargée. */
    @Nullable
    public RectF getRelativeCrop() {
        if (bitmap == null || imageRect.isEmpty()) return pendingRelative;
        return new RectF(
                (frame.left - imageRect.left) / imageRect.width(),
                (frame.top - imageRect.top) / imageRect.height(),
                (frame.right - imageRect.left) / imageRect.width(),
                (frame.bottom - imageRect.top) / imageRect.height());
    }

    /** Vrai si le cadre couvre (presque) toute la photo. */
    public boolean isFullImage() {
        RectF r = getRelativeCrop();
        return r == null || (r.left < 0.02f && r.top < 0.02f && r.right > 0.98f && r.bottom > 0.98f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (pendingRelative == null && bitmap != null) pendingRelative = getRelativeCrop();
        layoutImage();
    }

    private void layoutImage() {
        if (bitmap == null || getWidth() == 0 || getHeight() == 0) return;
        float pad = touchRadius / 2f;
        float availW = getWidth() - 2 * pad, availH = getHeight() - 2 * pad;
        float scale = Math.min(availW / bitmap.getWidth(), availH / bitmap.getHeight());
        float w = bitmap.getWidth() * scale, h = bitmap.getHeight() * scale;
        float left = (getWidth() - w) / 2f, top = (getHeight() - h) / 2f;
        imageRect.set(left, top, left + w, top + h);
        RectF rel = pendingRelative != null ? pendingRelative : defaultRelative();
        frame.set(imageRect.left + rel.left * w, imageRect.top + rel.top * h,
                imageRect.left + rel.right * w, imageRect.top + rel.bottom * h);
        pendingRelative = null;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null || imageRect.isEmpty()) return;
        canvas.drawBitmap(bitmap, null, imageRect, bitmapPaint);

        dimPath.reset();
        dimPath.setFillType(Path.FillType.EVEN_ODD);
        dimPath.addRect(imageRect, Path.Direction.CW);
        dimPath.addRect(frame, Path.Direction.CW);
        canvas.drawPath(dimPath, dimPaint);

        float tw = frame.width() / 3f, th = frame.height() / 3f;
        for (int i = 1; i < 3; i++) {
            canvas.drawLine(frame.left + tw * i, frame.top, frame.left + tw * i, frame.bottom, gridPaint);
            canvas.drawLine(frame.left, frame.top + th * i, frame.right, frame.top + th * i, gridPaint);
        }
        canvas.drawRect(frame, borderPaint);

        float l = Math.min(handleLen, Math.min(frame.width(), frame.height()) / 3f);
        drawCorner(canvas, frame.left, frame.top, l, l);
        drawCorner(canvas, frame.right, frame.top, -l, l);
        drawCorner(canvas, frame.left, frame.bottom, l, -l);
        drawCorner(canvas, frame.right, frame.bottom, -l, -l);
    }

    private void drawCorner(Canvas c, float x, float y, float dx, float dy) {
        c.drawLine(x, y, x + dx, y, handlePaint);
        c.drawLine(x, y, x, y + dy, handlePaint);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (bitmap == null) return false;
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mode = hitTest(x, y);
                lastX = x;
                lastY = y;
                if (mode != NONE) getParent().requestDisallowInterceptTouchEvent(true);
                return mode != NONE;
            case MotionEvent.ACTION_MOVE:
                if (mode == NONE) return false;
                drag(x - lastX, y - lastY);
                lastX = x;
                lastY = y;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mode != NONE && listener != null) listener.onCropChanged();
                mode = NONE;
                return true;
            default:
                return false;
        }
    }

    private int hitTest(float x, float y) {
        if (near(x, y, frame.left, frame.top)) return TL;
        if (near(x, y, frame.right, frame.top)) return TR;
        if (near(x, y, frame.left, frame.bottom)) return BL;
        if (near(x, y, frame.right, frame.bottom)) return BR;
        if (frame.contains(x, y)) return MOVE;
        return NONE;
    }

    private boolean near(float x, float y, float cx, float cy) {
        return Math.abs(x - cx) <= touchRadius && Math.abs(y - cy) <= touchRadius;
    }

    private void drag(float dx, float dy) {
        float minW = Math.min(minSize, imageRect.width());
        float minH = Math.min(minSize, imageRect.height());
        switch (mode) {
            case MOVE:
                dx = Math.max(imageRect.left - frame.left, Math.min(imageRect.right - frame.right, dx));
                dy = Math.max(imageRect.top - frame.top, Math.min(imageRect.bottom - frame.bottom, dy));
                frame.offset(dx, dy);
                break;
            case TL:
                frame.left = clamp(frame.left + dx, imageRect.left, frame.right - minW);
                frame.top = clamp(frame.top + dy, imageRect.top, frame.bottom - minH);
                break;
            case TR:
                frame.right = clamp(frame.right + dx, frame.left + minW, imageRect.right);
                frame.top = clamp(frame.top + dy, imageRect.top, frame.bottom - minH);
                break;
            case BL:
                frame.left = clamp(frame.left + dx, imageRect.left, frame.right - minW);
                frame.bottom = clamp(frame.bottom + dy, frame.top + minH, imageRect.bottom);
                break;
            case BR:
                frame.right = clamp(frame.right + dx, frame.left + minW, imageRect.right);
                frame.bottom = clamp(frame.bottom + dy, frame.top + minH, imageRect.bottom);
                break;
            default:
                break;
        }
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}

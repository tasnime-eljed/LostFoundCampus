package com.example.lostfoundcampus.ui.publish;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.lostfoundcampus.R;
import com.example.lostfoundcampus.util.MotionUtil;

/**
 * Le moment mémorable de l'app : un cadre lumineux dont le dégradé multicolore
 * (violet, rose, bleu, pêche) tourne autour de la photo, un halo flou, et une bande
 * de balayage qui parcourt l'objet. L'extérieur du cadre est assombri.
 * Avec « Supprimer les animations », l'indicateur reste statique.
 */
public class AiScanView extends View {

    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF frame = new RectF();
    private final Path scrimPath = new Path();
    private final Path framePath = new Path();
    private final Matrix sweepMatrix = new Matrix();
    private final Matrix bandMatrix = new Matrix();

    private final int[] colors;
    private final float frameRadius;
    private final float frameMargin;
    private final float bandHeight;
    private final boolean reducedMotion;

    @Nullable
    private SweepGradient sweep;
    @Nullable
    private LinearGradient band;
    private float rotation;
    private float scan;
    @Nullable
    private ValueAnimator rotationAnimator;
    @Nullable
    private ValueAnimator scanAnimator;
    private boolean running;

    public AiScanView(Context context) {
        this(context, null);
    }

    public AiScanView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        Resources r = getResources();
        int violet = ContextCompat.getColor(context, R.color.ai_violet);
        colors = new int[]{
                violet,
                ContextCompat.getColor(context, R.color.ai_pink),
                ContextCompat.getColor(context, R.color.ai_peach),
                ContextCompat.getColor(context, R.color.ai_blue),
                violet
        };
        frameRadius = r.getDimension(R.dimen.ai_frame_radius);
        frameMargin = r.getDimension(R.dimen.ai_frame_margin);
        bandHeight = r.getDimension(R.dimen.ai_band_height);
        reducedMotion = MotionUtil.isReducedMotion(context);

        scrimPaint.setColor(ContextCompat.getColor(context, R.color.ai_scrim));
        scrimPaint.setStyle(Paint.Style.FILL);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(r.getDimension(R.dimen.ai_glow_stroke));
        glowPaint.setMaskFilter(new BlurMaskFilter(r.getDimension(R.dimen.ai_glow_blur), BlurMaskFilter.Blur.NORMAL));

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(r.getDimension(R.dimen.ai_frame_stroke));

        bandPaint.setStyle(Paint.Style.FILL);

        // Le flou (BlurMaskFilter) n'est accéléré matériellement qu'à partir d'Android 9.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float width = w - 2 * frameMargin;
        float height = Math.min(h * 0.48f, width * 1.15f);
        float top = Math.max(frameMargin * 2.5f, h * 0.40f - height / 2f);
        frame.set(frameMargin, top, frameMargin + width, top + height);

        framePath.reset();
        framePath.addRoundRect(frame, frameRadius, frameRadius, Path.Direction.CW);

        scrimPath.reset();
        scrimPath.setFillType(Path.FillType.EVEN_ODD);
        scrimPath.addRect(0, 0, w, h, Path.Direction.CW);
        scrimPath.addRoundRect(frame, frameRadius, frameRadius, Path.Direction.CW);

        sweep = new SweepGradient(frame.centerX(), frame.centerY(), colors, null);
        glowPaint.setShader(sweep);
        strokePaint.setShader(sweep);

        int bandColor = ContextCompat.getColor(getContext(), R.color.ai_band);
        band = new LinearGradient(0, 0, 0, bandHeight,
                new int[]{Color.TRANSPARENT, bandColor, Color.TRANSPARENT}, null, Shader.TileMode.CLAMP);
        bandPaint.setShader(band);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (sweep == null) return;

        canvas.drawPath(scrimPath, scrimPaint);

        sweepMatrix.setRotate(rotation, frame.centerX(), frame.centerY());
        sweep.setLocalMatrix(sweepMatrix);
        canvas.drawPath(framePath, glowPaint);
        canvas.drawPath(framePath, strokePaint);

        if (!reducedMotion && band != null) {
            float y = frame.top - bandHeight + (frame.height() + bandHeight) * scan;
            bandMatrix.setTranslate(0, y);
            band.setLocalMatrix(bandMatrix);
            int save = canvas.save();
            canvas.clipPath(framePath);
            canvas.drawRect(frame.left, y, frame.right, y + bandHeight, bandPaint);
            canvas.restoreToCount(save);
        }
    }

    public void start() {
        if (running) return;
        running = true;
        if (reducedMotion) {
            invalidate();
            return;
        }
        Resources r = getResources();
        rotationAnimator = ValueAnimator.ofFloat(0f, 360f);
        rotationAnimator.setDuration(r.getInteger(R.integer.ai_rotation_duration_ms));
        rotationAnimator.setRepeatCount(ValueAnimator.INFINITE);
        rotationAnimator.setInterpolator(new LinearInterpolator());
        rotationAnimator.addUpdateListener(a -> {
            rotation = (float) a.getAnimatedValue();
            invalidate();
        });
        rotationAnimator.start();

        scanAnimator = ValueAnimator.ofFloat(0f, 1f);
        scanAnimator.setDuration(r.getInteger(R.integer.ai_scan_duration_ms));
        scanAnimator.setRepeatCount(ValueAnimator.INFINITE);
        scanAnimator.setRepeatMode(ValueAnimator.REVERSE);
        scanAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        scanAnimator.addUpdateListener(a -> scan = (float) a.getAnimatedValue());
        scanAnimator.start();
    }

    public void stop() {
        running = false;
        if (rotationAnimator != null) rotationAnimator.cancel();
        if (scanAnimator != null) scanAnimator.cancel();
        rotationAnimator = null;
        scanAnimator = null;
    }

    public boolean isReducedMotion() {
        return reducedMotion;
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }
}

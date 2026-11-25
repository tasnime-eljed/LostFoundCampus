package com.example.lostfoundcampus.ui.publish;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.components.containers.Classifications;
import com.google.mediapipe.tasks.components.containers.Detection;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier;
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector;

import java.util.HashMap;
import java.util.Map;

/**
 * Modèles MediaPipe embarqués (assets) : EfficientDet-Lite2 (COCO) localise l'objet,
 * EfficientNet-Lite2 (ImageNet) classe la zone. Seuls les objets utiles à l'app sont gardés
 * (voir {@link VisionLabels}). Méthodes bloquantes : à appeler hors du fil principal.
 */
final class ObjectVision {

    private static final String TAG = "ObjectVision";
    private static final String DETECTOR = "efficientdet_lite2.tflite";
    private static final String CLASSIFIER = "efficientnet_lite2.tflite";
    private static final float MIN_DETECTION = 0.30f;
    private static final float MIN_CLASS = 0.08f;

    static final class Found {
        final String label;
        final float score;
        /** Zone relative (0..1). */
        final RectF box;

        Found(String label, float score, RectF box) {
            this.label = label;
            this.score = score;
            this.box = box;
        }
    }

    private final Context app;
    @Nullable
    private ObjectDetector detector;
    @Nullable
    private ImageClassifier classifier;
    private boolean opened;
    private boolean closed;

    ObjectVision(Context context) {
        app = context.getApplicationContext();
    }

    /** Chargement au premier usage ; en cas d'échec, l'analyse continue avec ML Kit seul. */
    private void open() {
        if (opened) return;
        opened = true;
        try {
            detector = ObjectDetector.createFromOptions(app, ObjectDetector.ObjectDetectorOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath(DETECTOR).build())
                    .setScoreThreshold(MIN_DETECTION).setMaxResults(10).build());
        } catch (RuntimeException | LinkageError e) {
            Log.w(TAG, "Détecteur indisponible", e);
        }
        try {
            classifier = ImageClassifier.createFromOptions(app, ImageClassifier.ImageClassifierOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath(CLASSIFIER).build())
                    .setMaxResults(10).build());
        } catch (RuntimeException | LinkageError e) {
            Log.w(TAG, "Classifieur indisponible", e);
        }
    }

    /** Objet principal de la photo : sûr, proche du centre et pas minuscule. */
    @Nullable
    synchronized Found mainObject(Bitmap bitmap) {
        if (closed) return null;
        open();
        if (detector == null) return null;
        Found best = null;
        float bestRank = 0f;
        try {
            float w = bitmap.getWidth(), h = bitmap.getHeight();
            for (Detection d : detector.detect(image(bitmap)).detections()) {
                if (d.categories().isEmpty()) continue;
                Category c = d.categories().get(0);
                String label = VisionLabels.canonical(c.categoryName());
                if (label == null) continue;
                RectF px = d.boundingBox();
                RectF box = new RectF(clamp(px.left / w), clamp(px.top / h), clamp(px.right / w), clamp(px.bottom / h));
                if (box.isEmpty()) continue;
                double off = Math.hypot(box.centerX() - 0.5f, box.centerY() - 0.5f) / 0.71;
                double size = Math.sqrt(box.width() * box.height());
                float rank = (float) (c.score() * (1 - 0.5 * off) * (0.5 + 0.5 * size));
                if (rank > bestRank) {
                    bestRank = rank;
                    best = new Found(label, c.score(), box);
                }
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Détection impossible", e);
        }
        return best;
    }

    /** Étiquettes utiles de l'image ; les classes voisines (sac à dos, sacoche…) s'additionnent. */
    @NonNull
    synchronized Map<String, Float> classify(Bitmap bitmap) {
        Map<String, Float> out = new HashMap<>();
        if (closed) return out;
        open();
        if (classifier == null) return out;
        try {
            for (Classifications cs : classifier.classify(image(bitmap)).classificationResult().classifications()) {
                for (Category c : cs.categories()) {
                    String label = VisionLabels.canonical(c.categoryName());
                    if (label == null || c.score() < MIN_CLASS) continue;
                    Float prev = out.get(label);
                    out.put(label, Math.min(1f, (prev == null ? 0f : prev) + c.score()));
                }
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Classement impossible", e);
        }
        return out;
    }

    private static MPImage image(Bitmap b) {
        // MediaPipe n'accepte que ARGB_8888.
        return new BitmapImageBuilder(b.getConfig() == Bitmap.Config.ARGB_8888
                ? b : b.copy(Bitmap.Config.ARGB_8888, false)).build();
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    synchronized void close() {
        closed = true;
        if (detector != null) detector.close();
        if (classifier != null) classifier.close();
        detector = null;
        classifier = null;
    }
}

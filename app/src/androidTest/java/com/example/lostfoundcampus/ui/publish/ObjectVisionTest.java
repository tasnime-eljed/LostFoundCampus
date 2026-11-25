package com.example.lostfoundcampus.ui.publish;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.lostfoundcampus.data.model.ImageAnalysis;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier;
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Les modèles se chargent et s'exécutent ; photos réelles évaluées si présentes (nom attendu = préfixe). */
@RunWith(AndroidJUnit4.class)
public class ObjectVisionTest {

    private final Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();

    private static Bitmap drawing() {
        Bitmap b = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(Color.rgb(200, 180, 150));
        Paint p = new Paint();
        p.setColor(Color.BLACK);
        c.drawRoundRect(new RectF(280, 130, 520, 670), 40, 40, p);
        p.setColor(Color.rgb(60, 110, 200));
        c.drawRect(new RectF(295, 170, 505, 630), p);
        return b;
    }

    @Test
    public void modelsLoadAndRun() {
        // Sans le repli de ObjectVision : un échec de chargement doit faire échouer le test.
        try (ObjectDetector d = ObjectDetector.createFromOptions(app, ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("efficientdet_lite2.tflite").build())
                .setMaxResults(3).build());
             ImageClassifier k = ImageClassifier.createFromOptions(app, ImageClassifier.ImageClassifierOptions.builder()
                     .setBaseOptions(BaseOptions.builder().setModelAssetPath("efficientnet_lite2.tflite").build())
                     .setMaxResults(5).build())) {
            Bitmap b = drawing();
            assertNotNull(d.detect(new BitmapImageBuilder(b).build()));
            String classes = k.classify(new BitmapImageBuilder(b).build()).classificationResult()
                    .classifications().get(0).categories().toString();
            Log.i("ObjectVisionTest", classes);
            assertFalse(classes.isEmpty());
        }
    }

    @Test
    public void visionNeverCrashesAndCloses() {
        ObjectVision v = new ObjectVision(app);
        long t = System.currentTimeMillis();
        ObjectVision.Found f = v.mainObject(drawing());
        Log.i("ObjectVisionTest", "dessin -> " + (f == null ? "rien" : f.label + " " + f.score)
                + " " + v.classify(drawing()) + " en " + (System.currentTimeMillis() - t) + " ms");
        v.close();
        assertTrue(v.classify(drawing()).isEmpty());
        assertEquals(null, v.mainObject(drawing()));
    }

    /** Analyse complète des JPEG de files/vision_test (stockage externe de l'app), s'il y en a. */
    @Test
    public void realPhotosIfAny() throws Exception {
        File[] files = new File(app.getExternalFilesDir(null), "vision_test").listFiles();
        if (files == null) return;
        PhotoAnalyzer[] analyzer = new PhotoAnalyzer[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> analyzer[0] = new PhotoAnalyzer(app));
        for (File f : files) {
            CompletableFuture<ImageAnalysis> result = new CompletableFuture<>();
            analyzer[0].analyze(Uri.fromFile(f), null, new PhotoAnalyzer.Callback() {
                @Override
                public void onResult(ImageAnalysis a) {
                    result.complete(a);
                }

                @Override
                public void onError() {
                    result.completeExceptionally(new AssertionError(f.getName()));
                }
            });
            ImageAnalysis a = result.get(60, TimeUnit.SECONDS);
            Log.i("ObjectVisionTest", f.getName() + " | " + a.suggestedCategory + " | " + a.suggestedTitle + " | "
                    + a.confidence + "% | " + a.dominantColor + " | " + a.labels);
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync(analyzer[0]::close);
    }
}

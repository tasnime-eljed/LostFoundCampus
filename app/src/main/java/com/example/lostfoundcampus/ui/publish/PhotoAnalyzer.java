package com.example.lostfoundcampus.ui.publish;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.lostfoundcampus.R;
import com.example.lostfoundcampus.data.model.Category;
import com.example.lostfoundcampus.data.model.DetectedColor;
import com.example.lostfoundcampus.data.model.ImageAnalysis;
import com.example.lostfoundcampus.util.AppExecutors;
import com.example.lostfoundcampus.util.PhotoCompressor;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Analyse d'une photo sur l'appareil (RG03 : les résultats ne sont que des suggestions) :
 * objets MediaPipe ({@link ObjectVision}), étiquettes et texte ML Kit, couleur dominante Palette.
 *
 * <p>Pour viser l'objet plutôt que ce qui l'entoure (la main qui le tient, la table, le fond) :
 * <ul>
 *   <li>la zone utile est analysée en priorité : le cadre choisi par l'utilisateur, sinon l'objet localisé, sinon le centre ;</li>
 *   <li>la photo entière sert de second avis (fusion des étiquettes, confiance maximale) ;</li>
 *   <li>les étiquettes « non-objet » (main, doigt, personne, pièce…) sont écartées ;</li>
 *   <li>une étiquette qui correspond à une catégorie d'objet passe devant une étiquette générique ;</li>
 *   <li>une marque lue par l'OCR (iPhone, Samsung…) peut confirmer la catégorie.</li>
 * </ul>
 */
final class PhotoAnalyzer {

    interface Callback {
        void onResult(@NonNull ImageAnalysis analysis);

        void onError();
    }

    /** Seuil bas côté ML Kit : on récupère plus d'étiquettes, puis on trie nous-mêmes. */
    private static final float MLKIT_THRESHOLD = 0.30f;
    /** Confiance minimale pour garder une étiquette (correspondances entre annonces). */
    private static final float MIN_LABEL_CONFIDENCE = 0.45f;
    /** Confiance minimale pour qu'une étiquette d'objet connu devienne l'étiquette principale. */
    private static final float MIN_OBJECT_CONFIDENCE = 0.35f;
    /** Confiance affichée (en %) quand seul le texte lu désigne la catégorie. */
    private static final int OCR_CONFIDENCE = 50;
    private static final int MAX_LABELS = 6;
    /** Sans cadre : part centrale de la photo analysée en priorité (là où l'on vise l'objet). */
    private static final float CENTER_FRACTION = 0.6f;
    /** ML Kit (modèle généraliste) complète MediaPipe, surtout pour les vêtements. */
    private static final float MLKIT_FOCUS_WEIGHT = 0.7f;
    private static final float MLKIT_FULL_WEIGHT = 0.5f;
    /** À partir de ce score, l'objet MediaPipe prime sur ML Kit (qui voit des vestes partout). */
    private static final float SURE_OBJECT = 0.50f;
    /** Marge autour de l'objet localisé. */
    private static final float BOX_MARGIN = 0.08f;

    /** Étiquettes qui décrivent le contexte et jamais l'objet perdu. */
    private static final Set<String> IGNORED = new HashSet<>(Arrays.asList(
            "hand", "finger", "nail", "thumb", "skin", "arm", "flesh", "gesture", "person", "selfie",
            "smile", "leg", "foot", "jaw", "eyelash", "beard", "hair", "muscle", "tattoo", "lip", "ear",
            "fun", "event", "room", "floor", "wall", "table", "desk", "sky", "pattern", "darkness",
            "sitting", "standing", "flooring", "interior design", "metal", "wood", "plastic",
            "font", "text", "rectangle", "circle", "symbol", "logo", "brand", "material property", "close-up",
            "still life photography", "macro photography", "photography", "carpet", "textile", "linens",
            "tablecloth", "countertop", "bed", "couch", "chair", "grass", "plant", "tree", "road", "asphalt"));

    private final ImageLabeler labeler = ImageLabeling.getClient(new ImageLabelerOptions.Builder()
            .setConfidenceThreshold(MLKIT_THRESHOLD)
            .build());
    private final TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private final ObjectVision vision;
    private final Context app;
    private final String[] labelKeys;
    private final String[] labelFr;
    private volatile boolean closed;

    PhotoAnalyzer(Context context) {
        app = context.getApplicationContext();
        vision = new ObjectVision(app);
        Resources r = app.getResources();
        labelKeys = r.getStringArray(R.array.mlkit_label_keys);
        labelFr = r.getStringArray(R.array.mlkit_label_fr);
    }

    /**
     * @param crop zone de l'objet choisie par l'utilisateur, en coordonnées relatives (0..1) de la photo
     *             redressée ; {@code null} pour laisser l'analyse viser le centre.
     */
    void analyze(Uri uri, @Nullable RectF crop, Callback cb) {
        AppExecutors.io(() -> {
            Bitmap full;
            Bitmap focus;
            List<DetectedColor> colors;
            Map<String, Float> objects = new HashMap<>();
            try {
                full = PhotoCompressor.decode(app, uri, PhotoCompressor.ANALYSIS_MAX_SIDE);
                Bitmap searched = crop != null ? cropBitmap(full, crop) : full;
                ObjectVision.Found found = vision.mainObject(searched);
                if (crop != null) focus = searched;
                else focus = found != null ? cropBitmap(full, withMargin(found.box)) : centerBitmap(full);
                objects.putAll(vision.classify(focus));
                if (found != null) {
                    Float prev = objects.get(found.label);
                    objects.put(found.label, prev == null ? found.score : Math.max(prev, found.score));
                }
                // Sans cadre, les couleurs du bord de la photo sont celles du fond (table, sol, mur).
                colors = ColorMatcher.detect(focus, crop == null,
                        crop == null ? ColorMatcher.backgroundColors(full) : java.util.Collections.<String>emptySet());
            } catch (Exception e) {
                AppExecutors.main(cb::onError);
                return;
            }
            final List<DetectedColor> detected = colors;
            AppExecutors.main(() -> runMlKit(full, focus, crop != null, objects, detected, cb));
        });
    }

    private void runMlKit(Bitmap full, Bitmap focus, boolean userCrop, Map<String, Float> objects,
                          List<DetectedColor> colors, Callback cb) {
        if (closed) return;
        Task<List<ImageLabel>> focusTask = labeler.process(InputImage.fromBitmap(focus, 0));
        Task<List<ImageLabel>> fullTask = labeler.process(InputImage.fromBitmap(full, 0));
        // Avec un cadre, on ne lit que le texte de l'objet ; sinon toute la photo.
        Task<Text> textTask = recognizer.process(InputImage.fromBitmap(userCrop ? focus : full, 0));
        Tasks.whenAllComplete(focusTask, fullTask, textTask).addOnCompleteListener(done -> {
            if (closed) return;
            boolean anyLabels = focusTask.isSuccessful() || fullTask.isSuccessful();
            if (!anyLabels && !textTask.isSuccessful() && objects.isEmpty() && colors.isEmpty()) {
                cb.onError();
                return;
            }
            Map<String, Float> merged = new HashMap<>(objects);
            Category sure = sureCategory(objects);
            if (focusTask.isSuccessful()) merge(merged, focusTask.getResult(), MLKIT_FOCUS_WEIGHT, sure);
            if (fullTask.isSuccessful()) {
                merge(merged, fullTask.getResult(), MLKIT_FULL_WEIGHT, sure);
            }
            Text text = textTask.isSuccessful() ? textTask.getResult() : null;
            cb.onResult(build(merged, text, colors));
        });
    }

    /** Catégorie d'un objet MediaPipe sûr, sinon {@code null}. */
    @Nullable
    private static Category sureCategory(Map<String, Float> objects) {
        Category sure = null;
        float best = SURE_OBJECT;
        for (Map.Entry<String, Float> e : objects.entrySet()) {
            if (e.getValue() >= best) {
                best = e.getValue();
                sure = categoryOf(e.getKey());
            }
        }
        return sure;
    }

    /** @param sure catégorie déjà sûre : ML Kit peut la confirmer, pas la contredire. */
    private static void merge(Map<String, Float> into, List<ImageLabel> labels, float weight,
                              @Nullable Category sure) {
        for (ImageLabel l : labels) {
            String key = l.getText().trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty() || IGNORED.contains(key)) continue;
            if (sure != null && (sure == Category.OTHER || categoryOf(key) != sure)) continue;
            float score = l.getConfidence() * weight;
            Float prev = into.get(key);
            if (prev == null || score > prev) into.put(key, score);
        }
    }

    private ImageAnalysis build(Map<String, Float> merged, @Nullable Text text, List<DetectedColor> colors) {
        List<Map.Entry<String, Float>> sorted = new ArrayList<>(merged.entrySet());
        sorted.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));

        List<String> texts = readTexts(text);
        // Vote pondéré de toutes les étiquettes + mots-clés lus (marque, « carte étudiant »…).
        Category category = ObjectClassifier.classify(merged, texts);

        // Étiquette principale : la plus sûre de la catégorie retenue (précise avant générique), sinon la plus sûre.
        Map.Entry<String, Float> top = null;
        if (category != Category.OTHER) {
            for (int pass = 0; pass < 2 && top == null; pass++) {
                for (Map.Entry<String, Float> e : sorted) {
                    boolean generic = ObjectClassifier.isGeneric(e.getKey());
                    if ((pass == 0) == generic) continue;
                    if (e.getValue() >= MIN_OBJECT_CONFIDENCE && categoryOf(e.getKey()) == category) {
                        top = e;
                        break;
                    }
                }
            }
        }
        // « Autres » : un objet connu (gourde, livre…) passe devant une étiquette de contexte.
        if (category == Category.OTHER) {
            for (Map.Entry<String, Float> e : sorted) {
                if (e.getValue() >= MIN_OBJECT_CONFIDENCE && ObjectClassifier.isKnownOther(e.getKey())) {
                    top = e;
                    break;
                }
            }
        }
        if (top == null && !sorted.isEmpty()) top = sorted.get(0);

        List<String> labels = new ArrayList<>();
        if (top != null) labels.add(top.getKey());
        for (Map.Entry<String, Float> e : sorted) {
            if (labels.size() >= MAX_LABELS) break;
            if (e.getValue() < MIN_LABEL_CONFIDENCE || labels.contains(e.getKey())) continue;
            labels.add(e.getKey());
        }

        String topKey = top == null ? null : top.getKey();
        int confidence = top == null ? 0 : Math.round(Math.min(1f, top.getValue()) * 100);
        // Catégorie tranchée par l'OCR seul : la confiance d'une étiquette sans rapport ne vaut rien ici.
        if (category != Category.OTHER && (topKey == null || categoryOf(topKey) != category)) {
            confidence = OCR_CONFIDENCE;
        }
        String display;
        if (category != Category.OTHER && topKey != null && categoryOf(topKey) == category && !ObjectClassifier.isGeneric(topKey)) {
            display = translate(topKey);
        } else if (category != Category.OTHER) {
            // L'OCR ou une étiquette vague a tranché : on affiche la catégorie.
            display = app.getString(category.labelRes);
        } else {
            // Objet non reconnu : « Autres » plutôt qu'une étiquette sans rapport (évier, ciel…).
            boolean known = topKey != null && ObjectClassifier.isKnownOther(topKey);
            display = known ? translate(topKey) : app.getString(category.labelRes);
            if (!known) confidence = 0;
        }

        // Seule une marque connue entre dans le titre (jamais un mot quelconque lu sur la photo).
        String brand = ObjectClassifier.brandIn(texts);
        String title = brand != null && !display.toLowerCase(Locale.ROOT).contains(brand.toLowerCase(Locale.ROOT))
                ? display + " " + brand : display;
        return new ImageAnalysis(labels, display, confidence, texts, colors, category, title);
    }

    /** Lignes de texte lisibles (2 à 30 caractères, au moins une lettre), sans doublon, 5 au plus. */
    private static List<String> readTexts(@Nullable Text text) {
        List<String> texts = new ArrayList<>();
        if (text == null) return texts;
        Set<String> seen = new HashSet<>();
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                String t = line.getText().trim().replaceAll("\\s+", " ");
                if (t.length() < 2 || t.length() > 30 || !t.matches(".*\\p{L}.*")) continue;
                if (!seen.add(t.toLowerCase(Locale.ROOT))) continue;
                texts.add(t);
                if (texts.size() >= 5) return texts;
            }
        }
        return texts;
    }

    /** Traduction des étiquettes ML Kit (anglais) ; à défaut, l'étiquette avec une majuscule. */
    private String translate(String english) {
        for (int i = 0; i < labelKeys.length && i < labelFr.length; i++) {
            if (labelKeys[i].equals(english)) return labelFr[i];
        }
        return english.substring(0, 1).toUpperCase(Locale.FRANCE) + english.substring(1);
    }

    static Category categoryOf(String l) {
        return ObjectClassifier.categoryOf(l);
    }

    /** Découpe la zone relative (0..1) ; une zone minuscule est élargie à 32 px pour rester analysable. */
    static Bitmap cropBitmap(Bitmap src, RectF rel) {
        int w = src.getWidth(), h = src.getHeight();
        int left = clamp(Math.round(rel.left * w), 0, w - 1);
        int top = clamp(Math.round(rel.top * h), 0, h - 1);
        int right = clamp(Math.round(rel.right * w), left + 1, w);
        int bottom = clamp(Math.round(rel.bottom * h), top + 1, h);
        int cw = Math.max(Math.min(32, w - left), right - left);
        int ch = Math.max(Math.min(32, h - top), bottom - top);
        return Bitmap.createBitmap(src, left, top, cw, ch);
    }

    private static RectF withMargin(RectF r) {
        float mx = r.width() * BOX_MARGIN, my = r.height() * BOX_MARGIN;
        return new RectF(Math.max(0f, r.left - mx), Math.max(0f, r.top - my),
                Math.min(1f, r.right + mx), Math.min(1f, r.bottom + my));
    }

    private static Bitmap centerBitmap(Bitmap src) {
        float m = (1f - CENTER_FRACTION) / 2f;
        return cropBitmap(src, new RectF(m, m, 1f - m, 1f - m));
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    void close() {
        closed = true;
        labeler.close();
        // Attend la fin d'une analyse en cours : jamais sur le fil principal.
        AppExecutors.io(vision::close);
        recognizer.close();
    }
}

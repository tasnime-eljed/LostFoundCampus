package com.example.lostfoundcampus.ui.publish;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.lostfoundcampus.data.model.Category;
import com.example.lostfoundcampus.util.DateUtil;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ObjectClassifierTest {

    private static final List<String> NO_TEXT = Collections.emptyList();

    @Test
    public void wholeWordsOnly() {
        assertEquals(Category.ELECTRONICS, ObjectClassifier.categoryOf("headphones"));
        assertEquals(Category.CLOTHES, ObjectClassifier.categoryOf("cardigan"));
        assertEquals(Category.OTHER, ObjectClassifier.categoryOf("landscape"));
        assertEquals(Category.ELECTRONICS, ObjectClassifier.categoryOf("keyboard"));
        assertEquals(Category.PHONES, ObjectClassifier.categoryOf("mobile phone"));
        assertEquals(Category.CARDS, ObjectClassifier.categoryOf("id card"));
    }

    @Test
    public void headNounDecides() {
        assertEquals(Category.BAGS, ObjectClassifier.categoryOf("laptop bag"));
        assertEquals(Category.PHONES, ObjectClassifier.categoryOf("phone"));
    }

    @Test
    public void phoneBeatsGenericElectronics() {
        Map<String, Float> labels = new HashMap<>();
        labels.put("gadget", 0.9f);
        labels.put("electronic device", 0.85f);
        labels.put("mobile phone", 0.6f);
        assertEquals(Category.PHONES, ObjectClassifier.classify(labels, NO_TEXT));
    }

    @Test
    public void strongestLabelWins() {
        Map<String, Float> labels = new HashMap<>();
        labels.put("backpack", 0.73f);
        labels.put("jacket", 0.57f);
        labels.put("jeans", 0.55f);
        labels.put("shorts", 0.5f);
        assertEquals(Category.BAGS, ObjectClassifier.classify(labels, NO_TEXT));
    }

    @Test
    public void knownOtherObjectBeatsWeakLabel() {
        Map<String, Float> labels = new HashMap<>();
        labels.put("umbrella", 0.86f);
        labels.put("hat", 0.45f);
        assertEquals(Category.OTHER, ObjectClassifier.classify(labels, NO_TEXT));
    }

    @Test
    public void ocrConfirmsCategory() {
        Map<String, Float> labels = new HashMap<>();
        labels.put("rectangle", 0.8f);
        assertEquals(Category.CARDS, ObjectClassifier.classify(labels, Arrays.asList("CARTE ÉTUDIANT")));
        assertEquals(Category.OTHER, ObjectClassifier.classify(labels, NO_TEXT));
        assertEquals(Category.OTHER, ObjectClassifier.classify(labels, Arrays.asList("UNIVERSITÉ")));
    }

    @Test
    public void onlyKnownBrands() {
        assertEquals("Samsung", ObjectClassifier.brandIn(Arrays.asList("SAMSUNG Galaxy")));
        assertEquals("Dell", ObjectClassifier.brandIn(Arrays.asList("Latitude", "DELL")));
        assertNull(ObjectClassifier.brandIn(Arrays.asList("Hello", "Bonjour")));
    }

    @Test
    public void isoOffsetIsApplied() {
        long utc = DateUtil.parseIso("2026-11-16T15:04:05.123+00:00");
        assertEquals(utc, DateUtil.parseIso("2026-11-16T16:04:05+01:00"));
        assertEquals(utc, DateUtil.parseIso("2026-11-16T15:04:05Z"));
        assertEquals(utc, DateUtil.parseIso("2026-11-16 15:04:05.5"));
    }
}

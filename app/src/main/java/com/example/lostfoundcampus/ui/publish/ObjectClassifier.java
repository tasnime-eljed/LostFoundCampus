package com.example.lostfoundcampus.ui.publish;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.lostfoundcampus.data.model.Category;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Déduit la catégorie d'un objet à partir des étiquettes ML Kit (anglais) et du texte lu par l'OCR.
 * Java pur (sans Android) : testable par JUnit.
 *
 * <p>Pourquoi pas un simple {@code contains} : « headphones » contient « phone », « cardigan »
 * contient « card », « landscape » contient « cap ». On compare donc des mots entiers, et dans
 * une étiquette composée (« laptop bag ») c'est le dernier mot, le nom principal en anglais, qui décide.
 *
 * <p>La catégorie retenue est celle de l'étiquette la plus sûre, texte lu compris.
 */
final class ObjectClassifier {

    /** Score minimal (confiance la plus forte) pour proposer une catégorie plutôt que « Autres ». */
    static final float MIN_CATEGORY_SCORE = 0.40f;
    /** Poids d'un mot-clé lu par l'OCR (marque, « carte étudiant »…). */
    private static final float OCR_WEIGHT = 0.5f;
    /** Étiquettes vagues (« gadget », « electronic device ») : elles comptent moins qu'une étiquette précise. */
    private static final float GENERIC_WEIGHT = 0.5f;

    private static final Map<String, Category> WORDS = new HashMap<>();
    private static final Map<String, Category> PHRASES = new HashMap<>();
    private static final Map<String, Category> OCR_WORDS = new HashMap<>();
    /** Marque lue → nom affiché dans le titre suggéré. */
    private static final Map<String, String> BRANDS = new HashMap<>();
    private static final String[] GENERIC = {"gadget", "bag", "electronic device", "electronics", "clothing", "fashion accessory"};

    static {
        words(Category.PHONES, "phone", "phones", "smartphone", "smartphones", "iphone", "cellphone", "telephone");
        words(Category.KEYS, "key", "keys", "keychain", "keyring");
        words(Category.WALLETS, "wallet", "wallets", "purse", "billfold");
        words(Category.BAGS, "bag", "bags", "backpack", "handbag", "luggage", "suitcase", "briefcase", "satchel",
                "tote", "pouch");
        words(Category.CARDS, "card", "cards", "passport", "badge");
        words(Category.ELECTRONICS, "computer", "laptop", "headphone", "headphones", "headset",
                "earphone", "earphones", "earbud", "earbuds", "camera", "tablet", "watch", "wristwatch",
                "smartwatch", "gadget", "television", "monitor", "keyboard", "mouse", "electronic", "electronics",
                "remote", "player", "drive", "microphone", "charger", "cable", "speaker", "calculator", "usb", "powerbank");
        words(Category.CLOTHES, "jacket", "shirt", "jeans", "coat", "cap", "hat", "shoe", "shoes", "sneaker",
                "sneakers", "scarf", "glove", "gloves", "clothing", "sweater", "hoodie", "dress", "skirt",
                "glasses", "sunglasses", "eyewear", "boot", "boots", "sock", "socks", "sweatshirt", "trousers", "cardigan", "jersey", "suit", "tie", "beanie",
                "blazer", "shorts", "swimwear", "tuxedo", "sari", "tights");

        // Étiquettes composées dont le dernier mot ne suffit pas.
        PHRASES.put("mobile phone", Category.PHONES);
        PHRASES.put("cell phone", Category.PHONES);
        PHRASES.put("tablet computer", Category.ELECTRONICS);
        PHRASES.put("id card", Category.CARDS);
        PHRASES.put("credit card", Category.CARDS);
        PHRASES.put("musical keyboard", Category.OTHER);
        PHRASES.put("computer keyboard", Category.ELECTRONICS);
        PHRASES.put("coin purse", Category.WALLETS);
        PHRASES.put("fashion accessory", Category.OTHER);

        // Texte lu sur l'objet (français et anglais).
        ocr(Category.CARDS, "carte", "card", "etudiant", "étudiant", "student", "universite", "université",
                "university", "identite", "identité", "cin", "visa", "mastercard", "bancaire", "republique",
                "république", "tunisienne", "passeport", "passport", "permis", "faculte", "faculté");
        ocr(Category.ELECTRONICS, "dell", "lenovo", "asus", "acer", "macbook", "thinkpad", "hp", "msi", "airpods",
                "jbl", "sony", "beats", "logitech", "casio", "anker");
        ocr(Category.PHONES, "iphone", "samsung", "galaxy", "xiaomi", "redmi", "huawei", "honor", "oppo",
                "realme", "pixel", "nokia", "tecno", "infinix", "oneplus", "motorola", "vivo", "itel");

        brands("iPhone", "iphone");
        brands("Samsung", "samsung", "galaxy");
        brands("Xiaomi", "xiaomi");
        brands("Redmi", "redmi");
        brands("Huawei", "huawei");
        brands("Honor", "honor");
        brands("Oppo", "oppo");
        brands("Realme", "realme");
        brands("Pixel", "pixel");
        brands("Nokia", "nokia");
        brands("Tecno", "tecno");
        brands("Infinix", "infinix");
        brands("OnePlus", "oneplus");
        brands("Motorola", "motorola");
        brands("Vivo", "vivo");
        brands("Dell", "dell");
        brands("Lenovo", "lenovo", "thinkpad");
        brands("Asus", "asus");
        brands("Acer", "acer");
        brands("MacBook", "macbook");
        brands("HP", "hp");
        brands("AirPods", "airpods");
        brands("JBL", "jbl");
        brands("Sony", "sony");
        brands("Beats", "beats");
        brands("Casio", "casio");
        brands("Nike", "nike");
        brands("Adidas", "adidas");
        brands("Puma", "puma");
        brands("Eastpak", "eastpak");
        brands("Ray-Ban", "rayban", "ray-ban");
    }

    private ObjectClassifier() {
    }

    /** Catégorie d'une seule étiquette ML Kit (minuscules), ou {@link Category#OTHER}. */
    @NonNull
    static Category categoryOf(@Nullable String label) {
        if (label == null) return Category.OTHER;
        String l = label.trim().toLowerCase(Locale.ROOT);
        Category phrase = PHRASES.get(l);
        if (phrase != null) return phrase;
        String[] tokens = l.split("[^a-z]+");
        // Le dernier mot reconnu est le nom principal (« laptop bag » → sac, « phone case » → téléphone).
        for (int i = tokens.length - 1; i >= 0; i--) {
            Category c = WORDS.get(tokens[i]);
            if (c != null) return c;
        }
        return Category.OTHER;
    }

    /**
     * Chaque catégorie garde sa meilleure étiquette (les étiquettes vagues comptent moitié), plus un
     * bonus si l'OCR la confirme. Pas d'addition : dix étiquettes faibles ne font pas une certitude.
     */
    @NonNull
    static Category classify(@NonNull Map<String, Float> labelScores, @NonNull List<String> texts) {
        Map<Category, Float> votes = new EnumMap<>(Category.class);
        for (Map.Entry<String, Float> e : labelScores.entrySet()) {
            Category c = categoryOf(e.getKey());
            if (c == Category.OTHER && !isKnownOther(e.getKey())) continue;
            float w = isGeneric(e.getKey()) ? GENERIC_WEIGHT : 1f;
            votes.put(c, Math.max(get(votes, c), e.getValue() * w));
        }
        for (Category c : ocrCategories(texts).keySet()) add(votes, c, OCR_WEIGHT);

        Category best = Category.OTHER;
        float bestScore = 0f;
        for (Map.Entry<Category, Float> e : votes.entrySet()) {
            if (e.getValue() >= MIN_CATEGORY_SCORE && e.getValue() > bestScore) {
                best = e.getKey();
                bestScore = e.getValue();
            }
        }
        return best;
    }

    /** Catégories suggérées par le texte lu (une seule voix par catégorie). */
    static Map<Category, Boolean> ocrCategories(@NonNull List<String> texts) {
        Map<Category, Boolean> out = new EnumMap<>(Category.class);
        // « carte », « université »… sont courants (affiche, sweat) : il en faut deux pour parler d'une carte.
        Set<String> cardWords = new HashSet<>();
        for (String t : texts) {
            for (String token : tokens(t)) {
                Category c = OCR_WORDS.get(token);
                if (c == Category.CARDS) cardWords.add(token);
                else if (c != null) out.put(c, true);
            }
        }
        if (cardWords.size() >= 2) out.put(Category.CARDS, true);
        return out;
    }

    /** Marque connue lue sur l'objet (« Samsung », « Dell »…), sinon {@code null}. */
    @Nullable
    static String brandIn(@NonNull List<String> texts) {
        for (String t : texts) {
            String low = t.toLowerCase(Locale.ROOT);
            if (low.contains("ray-ban")) return BRANDS.get("ray-ban");
            for (String token : tokens(t)) {
                String b = BRANDS.get(token);
                if (b != null) return b;
            }
        }
        return null;
    }

    /** Objets reconnus sans catégorie propre : ils votent pour « Autres ». */
    private static final Set<String> KNOWN_OTHER = new HashSet<>(java.util.Arrays.asList(
            "umbrella", "water bottle", "mug", "book", "binder", "pen", "pencil case", "eraser", "ruler", "scissors",
            "necklace", "bracelet", "jewellery", "lipstick", "perfume", "lighter", "helmet", "ball", "padlock",
            "racket", "plush"));

    static boolean isKnownOther(String label) {
        return KNOWN_OTHER.contains(label.toLowerCase(Locale.ROOT));
    }

    static boolean isGeneric(String label) {
        String l = label.toLowerCase(Locale.ROOT);
        for (String g : GENERIC) if (g.equals(l)) return true;
        return false;
    }

    private static String[] tokens(String text) {
        return text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{Nd}]+");
    }

    private static void add(Map<Category, Float> votes, Category c, float v) {
        votes.put(c, get(votes, c) + v);
    }

    private static float get(Map<Category, Float> votes, Category c) {
        Float v = votes.get(c);
        return v == null ? 0f : v;
    }

    private static void words(Category c, String... ws) {
        for (String w : ws) WORDS.put(w, c);
    }

    private static void ocr(Category c, String... ws) {
        for (String w : ws) OCR_WORDS.put(w, c);
    }

    private static void brands(String display, String... keys) {
        for (String k : keys) BRANDS.put(k, display);
    }
}

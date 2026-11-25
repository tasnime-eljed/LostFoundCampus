package com.example.lostfoundcampus.ui.publish;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Classes COCO et ImageNet utiles à l'app → étiquette commune (traduite dans strings.xml).
 * Toute autre classe (animaux, véhicules, aliments, meubles…) est ignorée : un objet perdu n'est
 * pas un bernard-l'ermite. Chaque clé existe dans la liste de classes d'un des deux modèles.
 */
final class VisionLabels {

    private static final Map<String, String> MAP = new HashMap<>();

    static {
        put("mobile phone", "cell phone", "cellular telephone", "hand-held computer");
        put("backpack", "backpack");
        put("handbag", "handbag", "purse");
        put("bag", "mailbag", "plastic bag", "shopping basket");
        put("suitcase", "suitcase");
        put("wallet", "wallet");
        put("laptop", "laptop", "notebook");
        put("mouse", "mouse");
        put("keyboard", "keyboard", "computer keyboard", "space bar", "typewriter keyboard");
        put("remote control", "remote", "remote control");
        put("music player", "ipod", "cassette player", "cd player");
        put("hard drive", "hard disc");
        put("camera", "reflex camera", "polaroid camera");
        put("watch", "digital watch", "stopwatch");
        put("speaker", "loudspeaker");
        put("microphone", "microphone");
        put("glasses", "sunglass", "sunglasses");
        put("sneakers", "running shoe");
        put("shoe", "loafer", "clog", "cowboy boot", "sandal");
        put("hat", "cowboy hat", "sombrero", "bonnet");
        put("sock", "sock", "christmas stocking");
        put("glove", "mitten");
        put("jeans", "jean");
        put("sweatshirt", "sweatshirt");
        put("cardigan", "cardigan");
        put("jersey", "jersey");
        put("coat", "trench coat", "fur coat");
        put("lab coat", "lab coat");
        put("suit", "suit");
        put("tie", "tie", "windsor tie", "bow tie", "bolo tie");
        put("scarf", "stole", "feather boa");
        put("umbrella", "umbrella");
        put("water bottle", "bottle", "water bottle", "pop bottle", "wine bottle", "beer bottle", "water jug");
        put("mug", "cup", "coffee mug");
        put("book", "book", "comic book", "book jacket");
        put("binder", "binder");
        put("pen", "ballpoint", "fountain pen", "quill");
        put("pencil case", "pencil box");
        put("eraser", "rubber eraser");
        put("ruler", "rule", "slide rule");
        put("scissors", "scissors");
        put("necklace", "necklace");
        put("lipstick", "lipstick");
        put("perfume", "perfume");
        put("lighter", "lighter");
        put("helmet", "crash helmet");
        put("ball", "sports ball", "soccer ball", "basketball", "volleyball", "tennis ball", "rugby ball");
        put("padlock", "padlock", "combination lock");
        put("racket", "tennis racket", "racket");
        put("plush", "teddy bear", "teddy");
    }

    private VisionLabels() {
    }

    @Nullable
    static String canonical(@Nullable String modelClass) {
        return modelClass == null ? null : MAP.get(modelClass.trim().toLowerCase(Locale.ROOT));
    }

    static Map<String, String> all() {
        return MAP;
    }

    private static void put(String label, String... classes) {
        for (String c : classes) MAP.put(c, label);
    }
}

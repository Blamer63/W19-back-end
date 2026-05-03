package com.example.demo.service.scanner;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class LabelAlignmentUtil {

    private static final Map<String, Set<String>> SYNONYMS = Map.ofEntries(
        Map.entry("shoe", Set.of("shoe", "sneaker", "running shoe", "boots", "footwear", "high heels", "leather shoe")),
        Map.entry("bottle", Set.of("bottle", "water bottle", "plastic bottle", "drink bottle", "soda bottle", "thermos", "flask", "glass bottle")),
        Map.entry("cup", Set.of("cup", "coffee mug", "teacup", "paper cup", "plastic cup", "glass")),
        Map.entry("laptop", Set.of("laptop", "macbook", "windows laptop", "gaming laptop", "notebook computer")),
        Map.entry("cell phone", Set.of("cell phone", "iphone", "android phone", "smartphone", "mobile phone")),
        Map.entry("book", Set.of("book", "paperback book", "hardcover book", "novel", "textbook", "magazine")),
        Map.entry("person", Set.of("person", "man", "woman", "child", "boy", "girl", "human", "people"))
    );

    public static boolean hasAgreement(String yoloLabel, List<ClipServiceClient.ClipPrediction> clipTopK) {
        if (yoloLabel == null || clipTopK == null || clipTopK.isEmpty()) {
            return false;
        }
        
        String lowerYolo = yoloLabel.toLowerCase().trim();
        Set<String> synonyms = SYNONYMS.getOrDefault(lowerYolo, Set.of(lowerYolo));

        return clipTopK.stream()
                .anyMatch(p -> synonyms.contains(p.getLabel().toLowerCase().trim()));
    }

    public static boolean hasStrongSemanticConflict(String yoloLabel, ClipServiceClient.ClipPrediction top1) {
        if (yoloLabel == null || top1 == null) {
            return false;
        }

        String lowerYolo = yoloLabel.toLowerCase().trim();
        String lowerTop1 = top1.getLabel().toLowerCase().trim();

        Set<String> synonyms = SYNONYMS.getOrDefault(lowerYolo, Set.of(lowerYolo));
        
        // If the Top1 label is NOT in the synonyms list of the YOLO label, it's a conflict
        return !synonyms.contains(lowerTop1);
    }
}

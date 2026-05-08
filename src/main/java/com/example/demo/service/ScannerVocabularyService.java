package com.example.demo.service;

import com.example.demo.enums.ScannerTranslationSource;
import com.example.demo.entity.ScannerTranslationCache;
import com.example.demo.repository.ScannerTranslationCacheRepository;
import com.example.demo.service.translation.TranslationClient;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScannerVocabularyService {

    private static final String DEFAULT_LANGUAGE_CODE = "en";

    private static final Map<String, Map<String, String>> OBJECT_DICTIONARY = Map.ofEntries(
            entry("apple", translations("ko", "\uC0AC\uACFC", "ja", "\u308A\u3093\u3054", "vi", "t\u00E1o",
                    "zh", "\u82F9\u679C", "es", "manzana", "fr", "pomme")),
            entry("banana", translations("ko", "\uBC14\uB098\uB098", "ja", "\u30D0\u30CA\u30CA", "vi", "chu\u1ED1i",
                    "zh", "\u9999\u8549", "es", "platano", "fr", "banane")),
            entry("book", translations("ko", "\uCC45", "ja", "\u672C", "vi", "s\u00E1ch",
                    "zh", "\u4E66", "es", "libro", "fr", "livre")),
            entry("bottle", translations("ko", "\uBCD1", "ja", "\u30DC\u30C8\u30EB", "vi", "chai",
                    "zh", "\u74F6\u5B50", "es", "botella", "fr", "bouteille")),
            entry("bowl", translations("ko", "\uADF8\uB987", "ja", "\u30DC\u30A6\u30EB", "vi", "b\u00E1t",
                    "zh", "\u7897", "es", "cuenco", "fr", "bol")),
            entry("car", translations("ko", "\uC790\uB3D9\uCC28", "ja", "\u8ECA", "vi", "xe h\u01A1i",
                    "zh", "\u6C7D\u8F66", "es", "coche", "fr", "voiture")),
            entry("cat", translations("ko", "\uACE0\uC591\uC774", "ja", "\u732B", "vi", "m\u00E8o",
                    "zh", "\u732B", "es", "gato", "fr", "chat")),
            entry("cell phone", translations("ko", "\uD734\uB300\uD3F0", "ja", "\u643A\u5E2F\u96FB\u8A71", "vi", "\u0111i\u1EC7n tho\u1EA1i",
                    "zh", "\u624B\u673A", "es", "telefono movil", "fr", "telephone portable")),
            entry("chair", translations("ko", "\uC758\uC790", "ja", "\u6905\u5B50", "vi", "gh\u1EBF",
                    "zh", "\u6905\u5B50", "es", "silla", "fr", "chaise")),
            entry("cup", translations("ko", "\uCEF5", "ja", "\u30AB\u30C3\u30D7", "vi", "c\u1ED1c",
                    "zh", "\u676F\u5B50", "es", "taza", "fr", "tasse")),
            entry("dog", translations("ko", "\uAC1C", "ja", "\u72AC", "vi", "ch\u00F3",
                    "zh", "\u72D7", "es", "perro", "fr", "chien")),
            entry("keyboard", translations("ko", "\uD0A4\uBCF4\uB4DC", "ja", "\u30AD\u30FC\u30DC\u30FC\u30C9", "vi", "b\u00E0n ph\u00EDm",
                    "zh", "\u952E\u76D8", "es", "teclado", "fr", "clavier")),
            entry("laptop", translations("ko", "\uB178\uD2B8\uBD81", "ja", "\u30CE\u30FC\u30C8\u30D1\u30BD\u30B3\u30F3",
                    "vi", "m\u00E1y t\u00EDnh x\u00E1ch tay", "zh", "\u7B14\u8BB0\u672C\u7535\u8111",
                    "es", "portatil", "fr", "ordinateur portable")),
            entry("mouse", translations("ko", "\uB9C8\uC6B0\uC2A4", "ja", "\u30DE\u30A6\u30B9", "vi", "chu\u1ED9t",
                    "zh", "\u9F20\u6807", "es", "raton", "fr", "souris")),
            entry("person", translations("ko", "\uC0AC\uB78C", "ja", "\u4EBA", "vi", "ng\u01B0\u1EDDi",
                    "zh", "\u4EBA", "es", "persona", "fr", "personne")),
            entry("spoon", translations("ko", "\uC21F\uAC00\uB77D", "ja", "\u30B9\u30D7\u30FC\u30F3", "vi", "th\u00ECa",
                    "zh", "\u52FA\u5B50", "es", "cuchara", "fr", "cuillere")),
            entry("tv", translations("ko", "\uD154\uB808\uBE44\uC804", "ja", "\u30C6\u30EC\u30D3", "vi", "ti vi",
                    "zh", "\u7535\u89C6", "es", "televisor", "fr", "television"))
    );

    private final TranslationClient translationClient;
    private final ScannerTranslationCacheRepository scannerTranslationCacheRepository;

    public VocabularyMatch resolve(String normalizedLabel, String languageCode) {
        if (DEFAULT_LANGUAGE_CODE.equals(languageCode)) {
            return VocabularyMatch.builder()
                    .learningWord(normalizedLabel)
                    .translationSource(ScannerTranslationSource.FALLBACK)
                    .build();
        }

        String dictionaryWord = OBJECT_DICTIONARY
                .getOrDefault(normalizedLabel, Map.of())
                .get(languageCode);

        if (dictionaryWord != null) {
            return VocabularyMatch.builder()
                    .learningWord(dictionaryWord)
                    .translationSource(ScannerTranslationSource.DICTIONARY)
                    .build();
        }

        ScannerTranslationCache cachedTranslation = scannerTranslationCacheRepository
                .findByLabelAndLanguageCode(normalizedLabel, languageCode)
                .orElse(null);

        if (cachedTranslation != null) {
            return VocabularyMatch.builder()
                    .learningWord(cachedTranslation.getTranslatedText())
                    .translationSource(ScannerTranslationSource.TRANSLATION_CACHE)
                    .build();
        }

        try {
            String translatedText = translationClient
                    .translate(normalizedLabel, DEFAULT_LANGUAGE_CODE, languageCode)
                    .getTranslatedText();

            if (translatedText != null && !translatedText.isBlank()) {
                scannerTranslationCacheRepository.save(ScannerTranslationCache.builder()
                        .label(normalizedLabel)
                        .languageCode(languageCode)
                        .translatedText(translatedText)
                        .build());

                return VocabularyMatch.builder()
                        .learningWord(translatedText)
                        .translationSource(ScannerTranslationSource.TRANSLATION_API)
                        .build();
            }
        } catch (Exception e) {
            log.warn("Translation unavailable for scanner label '{}': {}", normalizedLabel, e.getMessage());
        }

        return VocabularyMatch.builder()
                .learningWord(normalizedLabel)
                .translationSource(ScannerTranslationSource.FALLBACK)
                .build();
    }

    private static Map.Entry<String, Map<String, String>> entry(String label, Map<String, String> translations) {
        return Map.entry(label, translations);
    }

    private static Map<String, String> translations(String ko, String koWord, String ja, String jaWord,
                                                    String vi, String viWord, String zh, String zhWord,
                                                    String es, String esWord, String fr, String frWord) {
        return Map.of(
                ko, koWord,
                ja, jaWord,
                vi, viWord,
                zh, zhWord,
                es, esWord,
                fr, frWord);
    }

    @Value
    @Builder
    public static class VocabularyMatch {
        String learningWord;
        ScannerTranslationSource translationSource;
    }
}

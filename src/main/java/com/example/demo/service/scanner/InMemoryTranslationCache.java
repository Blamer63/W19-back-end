package com.example.demo.service.scanner;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryTranslationCache {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public Optional<String> get(String label, String targetLanguage) {
        return Optional.ofNullable(cache.get(key(label, targetLanguage)));
    }

    public void put(String label, String targetLanguage, String translatedLabel) {
        cache.put(key(label, targetLanguage), translatedLabel);
    }

    private String key(String label, String targetLanguage) {
        return (label + "::" + targetLanguage).toLowerCase();
    }
}

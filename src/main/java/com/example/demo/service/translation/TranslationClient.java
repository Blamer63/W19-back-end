package com.example.demo.service.translation;

public interface TranslationClient {

    TranslationResult translate(String text, String sourceLanguage, String targetLanguage);
}

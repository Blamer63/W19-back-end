package com.example.demo.service.translation;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TranslationResult {
    String translatedText;
    String detectedSourceLanguage;
}

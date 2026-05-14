package com.example.demo.controller;

import com.example.demo.dto.TextTranslationRequest;
import com.example.demo.dto.TextTranslationResponse;
import com.example.demo.service.TranslationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/translate")
@RequiredArgsConstructor
public class TranslationController {

    private final TranslationService translationService;

    @PostMapping
    public ResponseEntity<TextTranslationResponse> translate(@RequestBody TextTranslationRequest request) {
        return ResponseEntity.ok(translationService.translateText(
                request.getText(), request.getSourceLanguage(), request.getTargetLanguage()
        ));
    }
}

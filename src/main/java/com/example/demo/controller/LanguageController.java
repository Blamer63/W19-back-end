package com.example.demo.controller;

import com.example.demo.entity.Language;
import com.example.demo.repository.LanguageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/languages")
@RequiredArgsConstructor
public class LanguageController {

    private final LanguageRepository languageRepository;

    @GetMapping
    public ResponseEntity<Map<String, List<Language>>> getAllLanguages() {
        return ResponseEntity.ok(Map.of("languages", languageRepository.findAll()));
    }
}

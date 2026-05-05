package com.example.demo.controller;

import com.example.demo.dto.CreateWordRequest;
import com.example.demo.dto.SavedWordResponse;
import com.example.demo.dto.UpdateWordRequest;
import com.example.demo.service.SavedWordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/words")
@RequiredArgsConstructor
public class SavedWordController {

    private final SavedWordService savedWordService;

    @GetMapping
    public ResponseEntity<Page<SavedWordResponse>> getAllWords(
            Authentication authentication,
            @RequestParam(required = false) String language,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Page<SavedWordResponse> words = savedWordService.getUserWords(
                authentication.getName(),
                language,
                sort,
                page,
                size);

        return ResponseEntity.ok(words);
    }

    @GetMapping("/{wordId}")
    public ResponseEntity<SavedWordResponse> getWord(
            Authentication authentication,
            @PathVariable UUID wordId) {

        SavedWordResponse word = savedWordService.getWord(
                authentication.getName(),
                wordId);

        return ResponseEntity.ok(word);
    }

    @PostMapping
    public ResponseEntity<SavedWordResponse> saveWord(
            Authentication authentication,
            @Valid @RequestBody CreateWordRequest request) {

        SavedWordResponse word = savedWordService.saveWord(
                authentication.getName(),
                request);

        return ResponseEntity.status(HttpStatus.CREATED).body(word);
    }

    @PatchMapping("/{wordId}")
    public ResponseEntity<SavedWordResponse> updateWord(
            Authentication authentication,
            @PathVariable UUID wordId,
            @RequestBody UpdateWordRequest request) {

        SavedWordResponse word = savedWordService.updateWord(
                authentication.getName(),
                wordId,
                request);

        return ResponseEntity.ok(word);
    }

    @DeleteMapping("/{wordId}")
    public ResponseEntity<Void> deleteWord(
            Authentication authentication,
            @PathVariable UUID wordId) {

        savedWordService.deleteWord(authentication.getName(), wordId);

        return ResponseEntity.noContent().build();
    }
}

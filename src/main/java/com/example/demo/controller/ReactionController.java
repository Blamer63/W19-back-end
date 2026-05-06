package com.example.demo.controller;

import com.example.demo.dto.PostReactionRequest;
import com.example.demo.dto.PostReactionResponse;
import com.example.demo.service.ReactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/posts/{postId}/reactions")
@RequiredArgsConstructor
public class ReactionController {

    private final ReactionService reactionService;

    @PostMapping
    public ResponseEntity<PostReactionResponse> reactToPost(
            @PathVariable UUID postId,
            @RequestBody PostReactionRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(reactionService.reactToPost(postId, request, authentication.getName()));
    }

    @DeleteMapping
    public ResponseEntity<PostReactionResponse> removeReaction(
            @PathVariable UUID postId,
            Authentication authentication) {
        return ResponseEntity.ok(reactionService.removeReaction(postId, authentication.getName()));
    }
}

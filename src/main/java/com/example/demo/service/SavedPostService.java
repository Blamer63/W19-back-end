package com.example.demo.service;

import com.example.demo.entity.Post;
import com.example.demo.entity.Profile;
import com.example.demo.entity.SavedPost;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.PostRepository;
import com.example.demo.repository.ProfileRepository;
import com.example.demo.repository.SavedPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SavedPostService {

    private final SavedPostRepository savedPostRepository;
    private final PostRepository postRepository;
    private final ProfileRepository profileRepository;

    @Transactional
    public void savePost(UUID postId, String email) {
        Profile user = profileRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        if (!savedPostRepository.existsByUserIdAndPostId(user.getId(), postId)) {
            savedPostRepository.save(SavedPost.builder().user(user).post(post).build());
        }
    }

    @Transactional
    public void unsavePost(UUID postId, String email) {
        Profile user = profileRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        savedPostRepository.deleteByUserIdAndPostId(user.getId(), postId);
    }
}

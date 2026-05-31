package com.example.demo.service;

import com.example.demo.dto.PostReactionRequest;
import com.example.demo.dto.PostReactionResponse;
import com.example.demo.entity.Post;
import com.example.demo.entity.PostReaction;
import com.example.demo.entity.PostReaction.PostReactionId;
import com.example.demo.entity.Profile;
import com.example.demo.enums.NotificationType;
import com.example.demo.repository.PostCommentRepository;
import com.example.demo.repository.PostReactionRepository;
import com.example.demo.repository.PostRepository;
import com.example.demo.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReactionService {

    private final PostReactionRepository postReactionRepository;
    private final PostRepository postRepository;
    private final ProfileRepository profileRepository;
    private final PostCommentRepository postCommentRepository;
    private final NotificationService notificationService;
    private final ProfileService profileService;

    @Transactional
    public PostReactionResponse reactToPost(UUID postId, PostReactionRequest request, String currentUserEmail) {
        Profile currentUser = profileRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        PostReactionId reactionId = new PostReactionId(postId, currentUser.getId());

        boolean isNewReaction = postReactionRepository.findById(reactionId).isEmpty();

        PostReaction reaction = postReactionRepository.findById(reactionId)
                .orElse(PostReaction.builder()
                        .id(reactionId)
                        .post(post)
                        .profile(currentUser)
                        .build());

        reaction.setType(request.getReaction());
        postReactionRepository.save(reaction);

        if (isNewReaction) {
            notificationService.createNotification(
                    post.getAuthor().getId(),
                    currentUser.getId(),
                    NotificationType.POST_REACTION,
                    "New reaction",
                    profileService.displayName(currentUser) + " reacted to your post.",
                    "/posts/" + post.getId());
        }

        return buildResponse(postId, currentUser.getId());
    }

    @Transactional
    public PostReactionResponse removeReaction(UUID postId, String currentUserEmail) {
        Profile currentUser = profileRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        PostReactionId reactionId = new PostReactionId(postId, currentUser.getId());
        postReactionRepository.deleteById(reactionId);

        return buildResponse(postId, currentUser.getId());
    }

    private PostReactionResponse buildResponse(UUID postId, UUID profileId) {
        return PostReactionResponse.builder()
                .likes(postReactionRepository.countByPostId(postId))
                .comments(postCommentRepository.countByPostId(postId))
                .userReaction(postReactionRepository.findByPostIdAndProfileId(postId, profileId)
                        .map(PostReaction::getType)
                        .orElse(null))
                .build();
    }

}

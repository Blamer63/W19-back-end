package com.example.demo.service;

import com.example.demo.dto.CommentResponse;
import com.example.demo.dto.CreateCommentRequest;
import com.example.demo.entity.Post;
import com.example.demo.entity.PostComment;
import com.example.demo.entity.Profile;
import com.example.demo.enums.NotificationType;
import com.example.demo.repository.PostCommentRepository;
import com.example.demo.repository.PostRepository;
import com.example.demo.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final PostCommentRepository postCommentRepository;
    private final PostRepository postRepository;
    private final ProfileRepository profileRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public Page<CommentResponse> getComments(UUID postId, Pageable pageable) {
        return postCommentRepository.findByPostId(postId, pageable)
                .map(this::mapToResponse);
    }

    @Transactional
    public CommentResponse addComment(UUID postId, CreateCommentRequest request, String currentUserEmail) {
        Profile currentUser = profileRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        PostComment comment = PostComment.builder()
                .post(post)
                .author(currentUser)
                .content(request.getContent())
                .build();

        PostComment saved = postCommentRepository.save(comment);
        notificationService.createNotification(
                post.getAuthor().getId(),
                currentUser.getId(),
                NotificationType.POST_COMMENT,
                "New comment",
                displayName(currentUser) + " commented on your post.",
                "/posts/" + post.getId());

        return mapToResponse(saved);
    }

    @Transactional
    public void deleteComment(UUID postId, UUID commentId, String currentUserEmail) {
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));

        if (!comment.getPost().getId().equals(postId)) {
            throw new RuntimeException("Comment does not belong to this post");
        }

        if (!comment.getAuthor().getEmail().equals(currentUserEmail)) {
            throw new RuntimeException("Unauthorized to delete this comment");
        }

        postCommentRepository.delete(comment);
    }

    private CommentResponse mapToResponse(PostComment comment) {
        return CommentResponse.builder()
                .id(comment.getId())
                .author(CommentResponse.AuthorDto.builder()
                        .id(comment.getAuthor().getId())
                        .username(comment.getAuthor().getUsername())
                        .displayName(comment.getAuthor().getDisplayName())
                        .avatarUrl(comment.getAuthor().getAvatarUrl())
                        .build())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant())
                .build();
    }

    private String displayName(Profile profile) {
        if (profile.getDisplayName() != null && !profile.getDisplayName().isBlank()) {
            return profile.getDisplayName();
        }
        return profile.getUsername();
    }
}

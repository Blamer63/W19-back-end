package com.example.demo.repository;

import com.example.demo.entity.Post;
import com.example.demo.enums.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {
    Page<Post> findByOriginalLanguage(String language, Pageable pageable);

    Page<Post> findByOriginalLanguageAndStatusIn(String language, List<PostStatus> statuses, Pageable pageable);

    Page<Post> findByStatusIn(List<PostStatus> statuses, Pageable pageable);

    long countByAuthorId(UUID authorId);

    long countByAuthorIdAndStatus(UUID authorId, PostStatus status);

    Page<Post> findByAuthorIdAndStatus(UUID authorId, PostStatus status, Pageable pageable);

}

package com.example.demo.repository;

import com.example.demo.entity.PostComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PostCommentRepository extends JpaRepository<PostComment, UUID> {
    Page<PostComment> findByPostId(UUID postId, Pageable pageable);

    long countByPostId(UUID postId);
}

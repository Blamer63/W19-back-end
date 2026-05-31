package com.example.demo.repository;

import com.example.demo.entity.PostTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PostTranslationRepository extends JpaRepository<PostTranslation, UUID> {
    Optional<PostTranslation> findByPostIdAndLanguageCode(UUID postId, String languageCode);
}

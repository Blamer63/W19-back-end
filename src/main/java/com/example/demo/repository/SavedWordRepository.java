package com.example.demo.repository;

import com.example.demo.entity.SavedWord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SavedWordRepository extends JpaRepository<SavedWord, UUID> {

    Page<SavedWord> findByUserId(UUID userId, Pageable pageable);

    Page<SavedWord> findByUserIdAndLanguageCode(UUID userId, String languageCode, Pageable pageable);

    Optional<SavedWord> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndWordAndLanguageCode(UUID userId, String word, String languageCode);

    long countByUserIdAndLanguageCode(UUID userId, String languageCode);
}

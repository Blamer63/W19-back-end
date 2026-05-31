package com.example.demo.repository;

import com.example.demo.entity.ScannerTranslationCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScannerTranslationCacheRepository extends JpaRepository<ScannerTranslationCache, UUID> {

    Optional<ScannerTranslationCache> findByLabelAndLanguageCode(String label, String languageCode);
}

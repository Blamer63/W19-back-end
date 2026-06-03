package com.example.demo.repository;

import com.example.demo.entity.UserLanguage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface UserLanguageRepository extends JpaRepository<UserLanguage, UUID> {
    @Modifying
    @Transactional
    @Query("DELETE FROM UserLanguage ul WHERE ul.profile.id = :profileId")
    void deleteByProfileId(UUID profileId);

    @Query("""
            SELECT CASE WHEN COUNT(ul) > 0 THEN true ELSE false END
            FROM UserLanguage ul
            WHERE ul.profile.id = :profileId
              AND ul.language.code = :languageCode
              AND ul.isLearning = true
            """)
    boolean existsLearningLanguage(UUID profileId, String languageCode);
}

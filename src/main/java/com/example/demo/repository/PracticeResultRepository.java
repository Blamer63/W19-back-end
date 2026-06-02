package com.example.demo.repository;

import com.example.demo.entity.PracticeResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PracticeResultRepository extends JpaRepository<PracticeResult, UUID> {

    boolean existsBySessionIdAndWordId(UUID sessionId, UUID wordId);

    Optional<PracticeResult> findBySessionIdAndWordId(UUID sessionId, UUID wordId);

    @Modifying
    @Query(value = """
            delete from practice_results
            where word_id in (
                select id from saved_words where user_id = :userId
            )
            """, nativeQuery = true)
    void deleteBySavedWordUserId(@Param("userId") UUID userId);
}

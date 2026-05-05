package com.example.demo.repository;

import com.example.demo.entity.PracticeResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PracticeResultRepository extends JpaRepository<PracticeResult, UUID> {

    boolean existsBySessionIdAndWordId(UUID sessionId, UUID wordId);

    Optional<PracticeResult> findBySessionIdAndWordId(UUID sessionId, UUID wordId);
}

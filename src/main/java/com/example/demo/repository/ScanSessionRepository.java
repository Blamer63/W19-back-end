package com.example.demo.repository;

import com.example.demo.entity.ScanSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScanSessionRepository extends JpaRepository<ScanSession, UUID> {

    Page<ScanSession> findByUserId(UUID userId, Pageable pageable);

    Optional<ScanSession> findByIdAndUserId(UUID id, UUID userId);
}

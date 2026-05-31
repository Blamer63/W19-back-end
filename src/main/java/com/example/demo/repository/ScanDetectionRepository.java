package com.example.demo.repository;

import com.example.demo.entity.ScanDetection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScanDetectionRepository extends JpaRepository<ScanDetection, UUID> {

    Optional<ScanDetection> findByIdAndScanSessionUserId(UUID id, UUID userId);
}

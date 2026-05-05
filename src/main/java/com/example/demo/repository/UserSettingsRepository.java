package com.example.demo.repository;

import com.example.demo.entity.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserSettingsRepository extends JpaRepository<UserSettings, UUID> {
    Optional<UserSettings> findByProfileId(UUID profileId);
}

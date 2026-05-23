package com.example.demo.repository;

import com.example.demo.entity.UserSettings;
import com.example.demo.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserSettingsRepository extends JpaRepository<UserSettings, UUID> {
    Optional<UserSettings> findByProfile(Profile profile);

    Optional<UserSettings> findByProfileId(UUID profileId);
}

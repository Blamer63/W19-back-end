package com.example.demo.repository;

import com.example.demo.entity.UserSettings;
import com.example.demo.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface UserSettingsRepository extends JpaRepository<UserSettings, UUID> {
    Optional<UserSettings> findByProfile(Profile profile);

    @Query(value = "select * from user_settings where profile_id = :profileId", nativeQuery = true)
    Optional<UserSettings> findByProfileId(@Param("profileId") UUID profileId);
}

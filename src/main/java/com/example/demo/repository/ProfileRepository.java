package com.example.demo.repository;

import com.example.demo.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, java.util.UUID> {
        Optional<Profile> findByUsername(String username);

        Optional<Profile> findByEmail(String email);

        boolean existsByUsername(String username);

        boolean existsByEmail(String email);

        @org.springframework.data.jpa.repository.Query("SELECT DISTINCT p FROM Profile p JOIN p.languages l " +
                        "WHERE (:nativeLanguage IS NULL OR (l.language.code = :nativeLanguage AND l.isLearning = false)) "
                        +
                        "AND (:learningLanguage IS NULL OR (l.language.code = :learningLanguage AND l.isLearning = true))")
        java.util.List<Profile> searchProfiles(
                        @org.springframework.data.repository.query.Param("nativeLanguage") String nativeLanguage,
                        @org.springframework.data.repository.query.Param("learningLanguage") String learningLanguage);

        @org.springframework.data.jpa.repository.Query("SELECT p FROM Profile p WHERE " +
                        "p.id != :excludedId AND " +
                        "(p.showLocation IS NULL OR p.showLocation = true) AND " +
                        "p.latitude IS NOT NULL AND p.longitude IS NOT NULL AND " +
                        "(6371 * acos(cos(radians(:latitude)) * cos(radians(p.latitude)) * " +
                        "cos(radians(p.longitude) - radians(:longitude)) + " +
                        "sin(radians(:latitude)) * sin(radians(p.latitude)))) < :radiusKm " +
                        "ORDER BY (6371 * acos(cos(radians(:latitude)) * cos(radians(p.latitude)) * " +
                        "cos(radians(p.longitude) - radians(:longitude)) + " +
                        "sin(radians(:latitude)) * sin(radians(p.latitude)))) ASC")
        java.util.List<Profile> findNearbyProfiles(
                        @org.springframework.data.repository.query.Param("excludedId") java.util.UUID excludedId,
                        @org.springframework.data.repository.query.Param("latitude") Double latitude,
                        @org.springframework.data.repository.query.Param("longitude") Double longitude,
                        @org.springframework.data.repository.query.Param("radiusKm") Double radiusKm);

        @org.springframework.data.jpa.repository.Query("SELECT p FROM Profile p WHERE p.id != :excludedId ORDER BY p.createdAt DESC")
        java.util.List<Profile> findAllLearnersExcept(
                        @org.springframework.data.repository.query.Param("excludedId") java.util.UUID excludedId);
}

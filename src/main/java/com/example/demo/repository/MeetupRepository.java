package com.example.demo.repository;

import com.example.demo.entity.Meetup;
import com.example.demo.enums.MeetupStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface MeetupRepository extends JpaRepository<Meetup, UUID> {

    // Find upcoming meetups
    Page<Meetup> findByStatusAndMeetupDateAfter(
            MeetupStatus status,
            LocalDateTime date,
            Pageable pageable);

    // Find by language and upcoming
    Page<Meetup> findByLanguageCodeAndStatusAndMeetupDateAfter(
            String languageCode,
            MeetupStatus status,
            LocalDateTime date,
            Pageable pageable);

    // Find nearby meetups using Haversine formula
    @Query("SELECT m FROM Meetup m WHERE " +
            "m.latitude IS NOT NULL AND m.longitude IS NOT NULL AND " +
            "m.status = :status AND m.meetupDate > :currentDate AND " +
            "(6371 * acos(cos(radians(:latitude)) * cos(radians(m.latitude)) * " +
            "cos(radians(m.longitude) - radians(:longitude)) + " +
            "sin(radians(:latitude)) * sin(radians(m.latitude)))) < :radiusKm")
    Page<Meetup> findNearbyMeetups(
            @Param("latitude") Double latitude,
            @Param("longitude") Double longitude,
            @Param("radiusKm") Double radiusKm,
            @Param("status") MeetupStatus status,
            @Param("currentDate") LocalDateTime currentDate,
            Pageable pageable);
}

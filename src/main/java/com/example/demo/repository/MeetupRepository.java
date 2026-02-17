package com.example.demo.repository;

import com.example.demo.entity.Meetup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface MeetupRepository extends JpaRepository<Meetup, UUID> {

    @Query(value = """
            SELECT m.* FROM meetups m
            WHERE m.status = 'UPCOMING'
            AND m.meetup_date >= :now
            AND (6371 * acos(cos(radians(:latitude)) * cos(radians(m.latitude))
                * cos(radians(m.longitude) - radians(:longitude))
                + sin(radians(:latitude)) * sin(radians(m.latitude)))) <= :radiusKm
            ORDER BY m.meetup_date ASC
            """, nativeQuery = true)
    List<Meetup> findUpcomingNearbyMeetups(
            @Param("latitude") Double latitude,
            @Param("longitude") Double longitude,
            @Param("radiusKm") Double radiusKm,
            @Param("now") Instant now);

    List<Meetup> findByStatusOrderByMeetupDateAsc(String status);
}

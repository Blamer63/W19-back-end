package com.example.demo.repository;

import com.example.demo.entity.MeetupAttendee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MeetupAttendeeRepository extends JpaRepository<MeetupAttendee, UUID> {

    List<MeetupAttendee> findByMeetupId(UUID meetupId);

    boolean existsByMeetupIdAndAttendeeId(UUID meetupId, UUID attendeeId);

    Optional<MeetupAttendee> findByMeetupIdAndAttendeeId(UUID meetupId, UUID attendeeId);

    void deleteByMeetupIdAndAttendeeId(UUID meetupId, UUID attendeeId);

    long countByMeetupId(UUID meetupId);
}

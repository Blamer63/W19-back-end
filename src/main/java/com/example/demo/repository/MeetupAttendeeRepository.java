package com.example.demo.repository;

import com.example.demo.entity.MeetupAttendee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MeetupAttendeeRepository extends JpaRepository<MeetupAttendee, UUID> {
}

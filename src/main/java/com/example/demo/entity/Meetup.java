package com.example.demo.entity;

import com.example.demo.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "meetups")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Meetup extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "meetup_date", nullable = false)
    private Instant meetupDate;

    private String location;

    private Double latitude;
    private Double longitude;

    @Column(name = "language_code")
    private String languageCode;

    @Column(name = "max_attendees")
    private Integer maxAttendees;

    private String status; // UPCOMING, COMPLETED, CANCELLED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id", nullable = false)
    private Profile organizer;

    @OneToMany(mappedBy = "meetup", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MeetupAttendee> attendees = new ArrayList<>();
}

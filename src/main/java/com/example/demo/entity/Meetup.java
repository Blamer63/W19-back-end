package com.example.demo.entity;

import com.example.demo.common.BaseEntity;
import com.example.demo.enums.MeetupStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "meetups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Meetup extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id", nullable = false)
    private Profile organizer;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "language_code")
    private Language language;

    @Column(name = "meetup_date", nullable = false)
    private LocalDateTime meetupDate;

    @Column(nullable = false)
    private String location;

    private Double latitude;
    private Double longitude;

    @Column(name = "max_attendees")
    private Integer maxAttendees;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MeetupStatus status = MeetupStatus.UPCOMING;

    @OneToMany(mappedBy = "meetup", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MeetupAttendee> attendees = new ArrayList<>();
}

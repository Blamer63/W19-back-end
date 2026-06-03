package com.example.demo.entity;

import com.example.demo.enums.SourceType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saved_words")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedWord {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Profile user;

    @Column(nullable = false)
    private String word;

    private String translation;

    @Column(name = "language_code")
    private String languageCode;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SourceType source = SourceType.MANUAL;

    @Column(name = "source_id")
    private UUID sourceId;

    private String context;

    private String topic;

    @Column(name = "mastery_level")
    @Builder.Default
    private Integer masteryLevel = 0;

    @Column(name = "next_review")
    private Instant nextReview;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (nextReview == null) {
            nextReview = Instant.now();
        }
    }
}

package com.example.demo.entity;

import com.example.demo.enums.ProficiencyLevel;
import com.example.demo.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "user_languages",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_languages_profile_language",
                columnNames = {"profile_id", "language_code"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserLanguage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "language_code", nullable = false)
    private Language language;

    @Enumerated(EnumType.STRING)
    @Column(name = "proficiency")
    private ProficiencyLevel proficiency;

    @Column(name = "is_learning")
    private boolean isLearning;
}

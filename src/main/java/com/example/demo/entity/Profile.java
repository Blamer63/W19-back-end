package com.example.demo.entity;

import com.example.demo.common.BaseEntity;
import com.example.demo.enums.AppRole;
import com.example.demo.enums.LocationVisibility;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "profiles")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Profile extends BaseEntity {

    @Column(unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(columnDefinition = "TEXT")
    private String bio;

    private Double latitude;
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_visibility")
    @Builder.Default
    private LocationVisibility locationVisibility = LocationVisibility.PUBLIC;

    @Column(name = "location")
    private String location;

    @Column(name = "show_activity")
    @Builder.Default
    private boolean showActivity = true;

    @Column(name = "show_saved_words")
    @Builder.Default
    private boolean showSavedWords = false;

    @Column(name = "followers_count")
    @Builder.Default
    private Integer followersCount = 0;

    @Column(name = "following_count")
    @Builder.Default
    private Integer followingCount = 0;

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<UserLanguage> languages = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<UserRole> roles = new ArrayList<>();

    @OneToOne(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    private RefreshToken refreshToken;

    @OneToOne(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    private UserSettings settings;

    @OneToMany(mappedBy = "author", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Post> posts = new ArrayList<>();

    @OneToMany(mappedBy = "author", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PostComment> profileComments = new ArrayList<>();

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PostReaction> profileReactions = new ArrayList<>();

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<WebPushSubscription> pushSubscriptions = new ArrayList<>();

    public void addRole(AppRole role) {
        UserRole userRole = new UserRole();
        userRole.setUser(this);
        userRole.setRole(role);
        this.roles.add(userRole);
    }
}

package com.example.demo.entity;

import com.example.demo.common.BaseEntity;
import com.example.demo.entity.Message;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation extends BaseEntity {

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "conversation_participants", joinColumns = @JoinColumn(name = "conversation_id"), inverseJoinColumns = @JoinColumn(name = "profile_id"))
    @Builder.Default
    private List<Profile> participants = new ArrayList<>();

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt DESC")
    @Builder.Default
    private List<Message> messages = new ArrayList<>();

    @Column(name = "is_group", nullable = false)
    @Builder.Default
    private boolean isGroup = false;

    @Column(name = "group_name")
    private String groupName;

    @Column(name = "group_avatar")
    private String groupAvatar;

    @Column(name = "last_message_preview")
    private String lastMessagePreview;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    public void addParticipant(Profile profile) {
        this.participants.add(profile);
    }

    public void removeParticipant(Profile profile) {
        this.participants.remove(profile);
    }
}

package com.example.demo.dto;

import com.example.demo.enums.FriendStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendRequestResponse {
    private UUID id;
    private FriendStatus status;

    @JsonProperty("is_sent_by_me")
    private boolean isSentByMe;

    @JsonProperty("other_user")
    private ProfileResponse otherUser;

    @JsonProperty("created_at")
    private Instant createdAt;
}

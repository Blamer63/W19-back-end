package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSummaryResponse {

    @JsonProperty("unread_messages")
    private long unreadMessages;

    @JsonProperty("incoming_friend_requests")
    private long incomingFriendRequests;

    @JsonProperty("total")
    private long total;
}

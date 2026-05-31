package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.example.demo.enums.ReactionType;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostReactionResponse {
    private long likes;
    private long comments;

    @JsonProperty("user_reaction")
    private ReactionType userReaction;
}

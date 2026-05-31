package com.example.demo.dto;

import com.example.demo.enums.ReactionType;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostReactionRequest {
    private ReactionType reaction;
}

package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupCreateRequest {

    @NotBlank(message = "Group name must not be blank")
    private String groupName;

    @NotNull(message = "Participant IDs must not be null")
    @Size(min = 2, message = "A group must have at least 2 other participants")
    private List<UUID> participantIds;

    private String groupAvatar;
}

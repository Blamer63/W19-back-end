package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PushSubscriptionRequest {
    @NotBlank
    private String endpoint;

    @JsonProperty("expiration_time")
    private Long expirationTime;

    @Valid
    @NotNull
    private Keys keys;

    @Data
    public static class Keys {
        @NotBlank
        private String p256dh;

        @NotBlank
        private String auth;
    }
}

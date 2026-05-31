package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PushVapidPublicKeyResponse {
    private boolean enabled;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("public_key")
    private String publicKey;
}

package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class ScanResponse {

    @JsonProperty("scan_session_id")
    private UUID scanSessionId;

    @JsonProperty("detected_objects")
    private List<DetectedObjectDTO> detectedObjects;
}

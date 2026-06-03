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
public class ScanPostImageRequest {

    @JsonProperty("image_url")
    private String imageUrl;

    @JsonProperty("image_index")
    private Integer imageIndex;
}

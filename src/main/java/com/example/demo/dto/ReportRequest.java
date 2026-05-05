package com.example.demo.dto;

import com.example.demo.enums.ReportReason;
import lombok.Data;
import java.util.UUID;

@Data
public class ReportRequest {
    @com.fasterxml.jackson.annotation.JsonProperty("post_id")
    private UUID postId;

    @com.fasterxml.jackson.annotation.JsonProperty("comment_id")
    private UUID commentId;

    private ReportReason reason;
    private String description;
}

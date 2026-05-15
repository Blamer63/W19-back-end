package com.example.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Scanner tuning defaults — loaded from application.yml under app.scanner.
 * VISION_API_URL is injected directly via @Value in VisionServiceClient.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.scanner")
public class ScannerProperties {
    private double defaultConfidenceThreshold = 0.4d;
    private int defaultMaxResults = 3;
}


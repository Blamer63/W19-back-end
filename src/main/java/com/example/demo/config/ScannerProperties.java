package com.example.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.scanner")
public class ScannerProperties {
    private String yoloUrl;
    private String clipUrl;
    private String translationUrl;
    private double defaultConfidenceThreshold = 0.4d;
    private int defaultMaxResults = 3;
}

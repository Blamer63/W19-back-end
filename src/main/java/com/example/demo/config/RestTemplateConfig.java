package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Bean
    @Primary
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public RestTemplate yoloRestTemplate(
            RestTemplateBuilder builder,
            @Value("${app.yolo.timeout-ms:5000}") long timeoutMs) {
        Duration timeout = Duration.ofMillis(timeoutMs);
        return builder
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .build();
    }
}

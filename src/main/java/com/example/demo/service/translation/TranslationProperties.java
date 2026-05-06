package com.example.demo.service.translation;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.translation")
@Getter
@Setter
public class TranslationProperties {

    private String provider = "google";
    private String apiKey = "";
    private String baseUrl = "https://translation.googleapis.com/language/translate/v2";
    private int timeoutMs = 5000;
}

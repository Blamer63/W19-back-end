package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "app.web-push")
public class WebPushProperties {
    private boolean enabled;
    private String vapidPublicKey;
    private String vapidPrivateKey;
    private String vapidSubject;
    private int ttlSeconds = 2_419_200;

    public boolean isConfigured() {
        return enabled
                && hasText(vapidPublicKey)
                && hasText(vapidPrivateKey)
                && hasText(vapidSubject);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

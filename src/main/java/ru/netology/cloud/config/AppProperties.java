package ru.netology.cloud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Все настройки приложения берутся из application.yml (префикс "app").
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String storageDir, List<String> allowedOrigins, List<SeedUser> users,
                            Duration tokenTtl, int maxLoginAttempts, Duration lockDuration) {

    public record SeedUser(String login, String password) {
    }
}

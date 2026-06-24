package com.iramil73.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code app.rate-limit.*}. Fixed-window throttling for auth endpoints.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        int capacity,
        long windowSeconds
) {
}

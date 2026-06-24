package com.iramil73.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code app.admin.*} properties used to seed the initial admin user.
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(
        String email,
        String password
) {
}

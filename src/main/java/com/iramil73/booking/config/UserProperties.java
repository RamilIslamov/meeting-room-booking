package com.iramil73.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Binds {@code app.users.*}. Balance credited to a newly registered user.
 */
@ConfigurationProperties(prefix = "app.users")
public record UserProperties(
        BigDecimal startingBalance
) {
}

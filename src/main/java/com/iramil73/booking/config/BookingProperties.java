package com.iramil73.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalTime;

/**
 * Binds {@code app.booking.*}. Business rules applied when creating bookings.
 */
@ConfigurationProperties(prefix = "app.booking")
public record BookingProperties(
        LocalTime openingTime,
        LocalTime closingTime,
        long maxDurationHours,
        long maxAdvanceDays
) {
}

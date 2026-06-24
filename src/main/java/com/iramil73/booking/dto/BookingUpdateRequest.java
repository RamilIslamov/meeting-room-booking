package com.iramil73.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** Edit payload for a booking (the room cannot be changed). */
public record BookingUpdateRequest(
        @NotBlank @Size(max = 255) String title,
        @NotNull LocalDateTime startTime,
        @NotNull LocalDateTime endTime
) {
}

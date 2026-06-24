package com.iramil73.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record BookingRequest(
        @NotNull Long roomId,
        @NotBlank @Size(max = 255) String title,
        @NotNull LocalDateTime startTime,
        @NotNull LocalDateTime endTime
) {
}

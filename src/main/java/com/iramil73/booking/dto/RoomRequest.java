package com.iramil73.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RoomRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull @Positive Integer capacity,
        @Size(max = 255) String location,
        @Size(max = 1000) String description
) {
}

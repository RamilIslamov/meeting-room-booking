package com.iramil73.booking.dto;

public record CurrentUserResponse(
        Long id,
        String email,
        String fullName,
        String role
) {
}

package com.iramil73.booking.dto;

public record AuthResponse(
        String token,
        String tokenType,
        String email,
        String fullName,
        String role
) {
}

package com.iramil73.booking.dto;

import com.iramil73.booking.entity.User;

import java.math.BigDecimal;

/** Admin-facing view of a user, including wallet balance. */
public record UserResponse(
        Long id,
        String email,
        String fullName,
        String role,
        BigDecimal balance
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getBalance());
    }
}

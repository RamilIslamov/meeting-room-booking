package com.iramil73.booking.dto;

import com.iramil73.booking.entity.Booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

public record BookingResponse(
        Long id,
        Long roomId,
        String roomName,
        Long userId,
        String userEmail,
        String title,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status,
        BigDecimal cost,
        Instant createdAt,
        Instant cancelledAt
) {
    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getRoom().getId(),
                booking.getRoom().getName(),
                booking.getUser().getId(),
                booking.getUser().getEmail(),
                booking.getTitle(),
                booking.getStartTime(),
                booking.getEndTime(),
                booking.getStatus().name(),
                booking.getCost(),
                booking.getCreatedAt(),
                booking.getCancelledAt());
    }
}

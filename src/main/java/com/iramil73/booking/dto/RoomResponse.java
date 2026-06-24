package com.iramil73.booking.dto;

import com.iramil73.booking.entity.Room;

import java.math.BigDecimal;

public record RoomResponse(
        Long id,
        String name,
        Integer capacity,
        String location,
        String description,
        BigDecimal pricePerHour,
        boolean active
) {
    public static RoomResponse from(Room room) {
        return new RoomResponse(
                room.getId(),
                room.getName(),
                room.getCapacity(),
                room.getLocation(),
                room.getDescription(),
                room.getPricePerHour(),
                room.isActive());
    }
}

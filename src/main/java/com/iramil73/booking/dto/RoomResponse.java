package com.iramil73.booking.dto;

import com.iramil73.booking.entity.Room;

public record RoomResponse(
        Long id,
        String name,
        Integer capacity,
        String location,
        String description,
        boolean active
) {
    public static RoomResponse from(Room room) {
        return new RoomResponse(
                room.getId(),
                room.getName(),
                room.getCapacity(),
                room.getLocation(),
                room.getDescription(),
                room.isActive());
    }
}

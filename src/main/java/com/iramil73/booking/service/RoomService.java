package com.iramil73.booking.service;

import com.iramil73.booking.dto.RoomRequest;
import com.iramil73.booking.dto.RoomResponse;
import com.iramil73.booking.entity.Room;
import com.iramil73.booking.exception.ConflictException;
import com.iramil73.booking.exception.NotFoundException;
import com.iramil73.booking.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;

    public List<RoomResponse> listActive() {
        return roomRepository.findByActiveTrue().stream()
                .map(RoomResponse::from)
                .toList();
    }

    public RoomResponse get(Long id) {
        return RoomResponse.from(findOrThrow(id));
    }

    @Transactional
    public RoomResponse create(RoomRequest request) {
        if (roomRepository.existsByName(request.name())) {
            throw new ConflictException("Room name already in use: " + request.name());
        }
        Room room = Room.builder()
                .name(request.name())
                .capacity(request.capacity())
                .location(request.location())
                .floor(request.floor())
                .description(request.description())
                .pricePerHour(request.pricePerHour())
                .active(true)
                .build();
        return RoomResponse.from(roomRepository.save(room));
    }

    @Transactional
    public RoomResponse update(Long id, RoomRequest request) {
        Room room = findOrThrow(id);
        if (!room.getName().equals(request.name()) && roomRepository.existsByName(request.name())) {
            throw new ConflictException("Room name already in use: " + request.name());
        }
        room.setName(request.name());
        room.setCapacity(request.capacity());
        room.setLocation(request.location());
        room.setFloor(request.floor());
        room.setDescription(request.description());
        room.setPricePerHour(request.pricePerHour());
        return RoomResponse.from(room); // flushed on transaction commit (dirty checking)
    }

    /** Soft delete: keeps the row so existing bookings stay referentially valid. */
    @Transactional
    public void deactivate(Long id) {
        findOrThrow(id).setActive(false);
    }

    private Room findOrThrow(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Room not found: " + id));
    }
}

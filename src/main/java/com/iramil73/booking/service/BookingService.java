package com.iramil73.booking.service;

import com.iramil73.booking.dto.BookingRequest;
import com.iramil73.booking.dto.BookingResponse;
import com.iramil73.booking.entity.Booking;
import com.iramil73.booking.entity.BookingStatus;
import com.iramil73.booking.entity.Room;
import com.iramil73.booking.entity.User;
import com.iramil73.booking.exception.BadRequestException;
import com.iramil73.booking.exception.ConflictException;
import com.iramil73.booking.exception.NotFoundException;
import com.iramil73.booking.repository.BookingRepository;
import com.iramil73.booking.repository.RoomRepository;
import com.iramil73.booking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    @Transactional
    public BookingResponse create(BookingRequest request, String userEmail) {
        if (!request.startTime().isBefore(request.endTime())) {
            throw new BadRequestException("startTime must be before endTime");
        }
        if (request.startTime().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Cannot book a slot in the past");
        }

        Room room = roomRepository.findById(request.roomId())
                .orElseThrow(() -> new NotFoundException("Room not found: " + request.roomId()));
        if (!room.isActive()) {
            throw new BadRequestException("Room is not active: " + room.getId());
        }

        // Fast path: friendly 409 in the common (non-concurrent) case.
        boolean overlaps = bookingRepository
                .existsByRoomIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(
                        room.getId(), BookingStatus.ACTIVE, request.endTime(), request.startTime());
        if (overlaps) {
            throw new ConflictException("Time slot already booked for this room");
        }

        User user = currentUser(userEmail);
        Booking booking = Booking.builder()
                .room(room)
                .user(user)
                .title(request.title())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .status(BookingStatus.ACTIVE)
                .build();
        try {
            // saveAndFlush surfaces the exclusion-constraint violation here (not at
            // commit), so a concurrent double-booking that slips past the check above
            // is still rejected by the database and reported as 409.
            return BookingResponse.from(bookingRepository.saveAndFlush(booking));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Time slot already booked for this room");
        }
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> listMy(String userEmail) {
        User user = currentUser(userEmail);
        return bookingRepository.findByUserIdOrderByStartTimeDesc(user.getId()).stream()
                .map(BookingResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> listByRoomAndDate(Long roomId, LocalDate date) {
        if (!roomRepository.existsById(roomId)) {
            throw new NotFoundException("Room not found: " + roomId);
        }
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        return bookingRepository.findForRoomOnDay(roomId, BookingStatus.ACTIVE, dayStart, dayEnd).stream()
                .map(BookingResponse::from)
                .toList();
    }

    @Transactional
    public void cancel(Long bookingId, String userEmail, boolean isAdmin) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found: " + bookingId));

        if (!isAdmin && !booking.getUser().getEmail().equals(userEmail)) {
            throw new AccessDeniedException("Cannot cancel another user's booking");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ConflictException("Booking is already cancelled");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(Instant.now());
    }

    private User currentUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found: " + email));
    }
}

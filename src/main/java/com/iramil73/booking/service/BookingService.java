package com.iramil73.booking.service;

import com.iramil73.booking.config.BookingProperties;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
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
    private final BookingProperties bookingProperties;

    @Transactional
    public BookingResponse create(BookingRequest request, String userEmail, boolean isAdmin) {
        validateTimes(request, isAdmin);

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

        // Admins book for free; regular users pay and must have the balance.
        BigDecimal cost = isAdmin ? zero() : computeCost(room, request.startTime(), request.endTime());
        User user = chargeIfNeeded(userEmail, cost, isAdmin);

        Booking booking = Booking.builder()
                .room(room)
                .user(user)
                .title(request.title())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .status(BookingStatus.ACTIVE)
                .cost(cost)
                .build();
        try {
            // saveAndFlush surfaces the exclusion-constraint violation here (not at
            // commit), so a concurrent double-booking that slips past the check above
            // is still rejected by the database and reported as 409 (rolling back the charge).
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

        // Refund the booking owner if the slot hasn't started yet.
        if (booking.getCost().compareTo(BigDecimal.ZERO) > 0
                && booking.getStartTime().isAfter(LocalDateTime.now())) {
            User owner = booking.getUser();
            owner.setBalance(owner.getBalance().add(booking.getCost()));
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(Instant.now());
    }

    /**
     * Loads the booking user and, for non-admins, atomically checks and deducts the
     * cost from their (row-locked) balance.
     */
    private User chargeIfNeeded(String userEmail, BigDecimal cost, boolean isAdmin) {
        if (isAdmin) {
            return currentUser(userEmail);
        }
        User user = userRepository.findByEmailForUpdate(userEmail)
                .orElseThrow(() -> new NotFoundException("User not found: " + userEmail));
        if (user.getBalance().compareTo(cost) < 0) {
            throw new BadRequestException(
                    "Insufficient balance: booking costs " + cost + " but balance is " + user.getBalance());
        }
        user.setBalance(user.getBalance().subtract(cost));
        return user;
    }

    private BigDecimal computeCost(Room room, LocalDateTime start, LocalDateTime end) {
        long minutes = Duration.between(start, end).toMinutes();
        BigDecimal hours = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
        return room.getPricePerHour().multiply(hours).setScale(2, RoundingMode.HALF_UP);
    }

    private void validateTimes(BookingRequest request, boolean isAdmin) {
        LocalDateTime start = request.startTime();
        LocalDateTime end = request.endTime();

        if (!start.isBefore(end)) {
            throw new BadRequestException("startTime must be before endTime");
        }
        // Admins are unrestricted on timing (e.g. may book in the past / backdate).
        if (isAdmin) {
            return;
        }
        if (start.isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Cannot book a slot in the past");
        }
        if (start.isAfter(LocalDateTime.now().plusDays(bookingProperties.maxAdvanceDays()))) {
            throw new BadRequestException(
                    "Cannot book more than " + bookingProperties.maxAdvanceDays() + " days in advance");
        }
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            throw new BadRequestException("Booking must start and end on the same day");
        }
        if (Duration.between(start, end).toMinutes() > bookingProperties.maxDurationHours() * 60) {
            throw new BadRequestException(
                    "Booking cannot exceed " + bookingProperties.maxDurationHours() + " hours");
        }
        if (start.toLocalTime().isBefore(bookingProperties.openingTime())
                || end.toLocalTime().isAfter(bookingProperties.closingTime())) {
            throw new BadRequestException("Booking must be within working hours "
                    + bookingProperties.openingTime() + "–" + bookingProperties.closingTime());
        }
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
    }

    private User currentUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found: " + email));
    }
}

package com.iramil73.booking;

import com.iramil73.booking.entity.Booking;
import com.iramil73.booking.entity.BookingStatus;
import com.iramil73.booking.entity.Role;
import com.iramil73.booking.entity.Room;
import com.iramil73.booking.entity.User;
import com.iramil73.booking.repository.BookingRepository;
import com.iramil73.booking.repository.RoomRepository;
import com.iramil73.booking.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the database-level exclusion constraint (migration 004) holds even when
 * the application's pre-check is bypassed — this is the real guard against the
 * concurrent double-booking race.
 */
class BookingOverlapConstraintIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void databaseRejectsOverlappingActiveBookingsInSameRoom() {
        Room room = roomRepository.saveAndFlush(
                Room.builder().name("Constraint Room").capacity(4).pricePerHour(BigDecimal.ZERO).active(true).build());
        User user = persistUser("constraint.user@example.com");

        bookingRepository.saveAndFlush(
                booking(room, user, BookingStatus.ACTIVE, "2031-01-01T10:00:00", "2031-01-01T11:00:00"));

        Booking overlapping =
                booking(room, user, BookingStatus.ACTIVE, "2031-01-01T10:30:00", "2031-01-01T11:30:00");
        assertThatThrownBy(() -> bookingRepository.saveAndFlush(overlapping))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cancelledBookingDoesNotBlockTheSlot() {
        Room room = roomRepository.saveAndFlush(
                Room.builder().name("Constraint Room 2").capacity(4).pricePerHour(BigDecimal.ZERO).active(true).build());
        User user = persistUser("constraint.user2@example.com");

        bookingRepository.saveAndFlush(
                booking(room, user, BookingStatus.CANCELLED, "2031-02-01T10:00:00", "2031-02-01T11:00:00"));

        Booking active =
                booking(room, user, BookingStatus.ACTIVE, "2031-02-01T10:00:00", "2031-02-01T11:00:00");
        assertThat(bookingRepository.saveAndFlush(active).getId()).isNotNull();
    }

    private User persistUser(String email) {
        return userRepository.saveAndFlush(User.builder()
                .email(email).passwordHash("x").fullName("Constraint User").role(Role.USER)
                .balance(BigDecimal.ZERO).build());
    }

    private Booking booking(Room room, User user, BookingStatus status, String start, String end) {
        return Booking.builder()
                .room(room).user(user).title("slot").status(status)
                .startTime(LocalDateTime.parse(start)).endTime(LocalDateTime.parse(end))
                .cost(BigDecimal.ZERO)
                .build();
    }
}

package com.iramil73.booking.service;

import com.iramil73.booking.config.BookingProperties;
import com.iramil73.booking.dto.BookingRequest;
import com.iramil73.booking.dto.BookingResponse;
import com.iramil73.booking.entity.Booking;
import com.iramil73.booking.entity.BookingStatus;
import com.iramil73.booking.entity.Role;
import com.iramil73.booking.entity.Room;
import com.iramil73.booking.entity.User;
import com.iramil73.booking.exception.BadRequestException;
import com.iramil73.booking.exception.ConflictException;
import com.iramil73.booking.exception.NotFoundException;
import com.iramil73.booking.repository.BookingRepository;
import com.iramil73.booking.repository.RoomRepository;
import com.iramil73.booking.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private UserRepository userRepository;

    private BookingService bookingService;

    private static final String EMAIL = "owner@example.com";
    private static final LocalDateTime START = LocalDate.now().plusDays(1).atTime(10, 0);
    private static final LocalDateTime END = START.plusHours(1);

    private static BookingProperties permissive() {
        return new BookingProperties(LocalTime.MIN, LocalTime.MAX, 24, 3650);
    }

    private static BookingProperties strict() {
        return new BookingProperties(LocalTime.of(8, 0), LocalTime.of(20, 0), 4, 30);
    }

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(bookingRepository, roomRepository, userRepository, permissive());
    }

    private Room activeRoom() {
        return Room.builder().id(1L).name("Room A").capacity(4)
                .pricePerHour(new BigDecimal("10.00")).active(true).build();
    }

    private User owner(String balance) {
        return User.builder().id(7L).email(EMAIL).fullName("Owner").role(Role.USER)
                .balance(new BigDecimal(balance)).build();
    }

    private BookingRequest request(LocalDateTime start, LocalDateTime end) {
        return new BookingRequest(1L, "Sync", start, end);
    }

    @Test
    void create_rejectsWhenStartNotBeforeEnd() {
        assertThatThrownBy(() -> bookingService.create(request(START, START), EMAIL, false))
                .isInstanceOf(BadRequestException.class);
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_rejectsBookingInThePast() {
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        assertThatThrownBy(() -> bookingService.create(request(start, start.plusHours(1)), EMAIL, false))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_rejectsWhenRoomMissing() {
        when(roomRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> bookingService.create(request(START, END), EMAIL, false))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_rejectsWhenRoomInactive() {
        Room room = activeRoom();
        room.setActive(false);
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        assertThatThrownBy(() -> bookingService.create(request(START, END), EMAIL, false))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_rejectsOverlappingSlot() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(activeRoom()));
        when(bookingRepository.existsByRoomIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(
                eq(1L), eq(BookingStatus.ACTIVE), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> bookingService.create(request(START, END), EMAIL, false))
                .isInstanceOf(ConflictException.class);
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_rejectsWhenInsufficientBalance() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(activeRoom())); // 10.00 / hour, 1h => 10.00
        when(bookingRepository.existsByRoomIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(
                anyLong(), any(), any(), any())).thenReturn(false);
        when(userRepository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(owner("5.00")));
        assertThatThrownBy(() -> bookingService.create(request(START, END), EMAIL, false))
                .isInstanceOf(BadRequestException.class);
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_chargesBalanceAndSavesWhenValid() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(activeRoom()));
        when(bookingRepository.existsByRoomIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(
                anyLong(), any(), any(), any())).thenReturn(false);
        User user = owner("100.00");
        when(userRepository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(user));
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingResponse response = bookingService.create(request(START, END), EMAIL, false);

        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.cost()).isEqualByComparingTo("10.00");
        assertThat(user.getBalance()).isEqualByComparingTo("90.00"); // 100 - 10
    }

    @Test
    void create_adminBooksForFreeIgnoringBalance() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(activeRoom()));
        when(bookingRepository.existsByRoomIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(
                anyLong(), any(), any(), any())).thenReturn(false);
        User admin = owner("0.00");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(admin));
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingResponse response = bookingService.create(request(START, END), EMAIL, true);

        assertThat(response.cost()).isEqualByComparingTo("0.00");
        assertThat(admin.getBalance()).isEqualByComparingTo("0.00"); // untouched
    }

    @Test
    void create_rejectsBookingExceedingMaxDuration() {
        BookingService strictService =
                new BookingService(bookingRepository, roomRepository, userRepository, strict());
        LocalDateTime start = LocalDate.now().plusDays(1).atTime(10, 0);
        assertThatThrownBy(() -> strictService.create(request(start, start.plusHours(6)), EMAIL, false))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_rejectsBookingOutsideWorkingHours() {
        BookingService strictService =
                new BookingService(bookingRepository, roomRepository, userRepository, strict());
        LocalDateTime start = LocalDate.now().plusDays(1).atTime(7, 0);
        assertThatThrownBy(() -> strictService.create(request(start, start.plusHours(1)), EMAIL, false))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_rejectsBookingBeyondAdvanceHorizon() {
        BookingService strictService =
                new BookingService(bookingRepository, roomRepository, userRepository, strict());
        LocalDateTime start = LocalDate.now().plusDays(40).atTime(10, 0);
        assertThatThrownBy(() -> strictService.create(request(start, start.plusHours(1)), EMAIL, false))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_allowsAdminToBackdate() {
        // Past start would be rejected for a user, but an admin may backdate.
        BookingService strictService =
                new BookingService(bookingRepository, roomRepository, userRepository, strict());
        LocalDateTime start = LocalDateTime.now().minusDays(1).withHour(10).withMinute(0);
        when(roomRepository.findById(1L)).thenReturn(Optional.of(activeRoom()));
        when(bookingRepository.existsByRoomIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(
                anyLong(), any(), any(), any())).thenReturn(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(owner("0.00")));
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingResponse response = strictService.create(request(start, start.plusHours(1)), EMAIL, true);
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void cancel_rejectsNonOwnerNonAdmin() {
        Booking booking = activeBooking();
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking));
        assertThatThrownBy(() -> bookingService.cancel(5L, "intruder@example.com", false))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.ACTIVE);
    }

    @Test
    void cancel_rejectsAlreadyCancelled() {
        Booking booking = activeBooking();
        booking.setStatus(BookingStatus.CANCELLED);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking));
        assertThatThrownBy(() -> bookingService.cancel(5L, EMAIL, false))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cancel_refundsOwnerForFutureBooking() {
        User user = owner("90.00");
        Booking booking = Booking.builder()
                .id(5L).room(activeRoom()).user(user).status(BookingStatus.ACTIVE)
                .startTime(START).endTime(END).cost(new BigDecimal("10.00")).build();
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking));

        bookingService.cancel(5L, EMAIL, false);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancelledAt()).isNotNull();
        assertThat(user.getBalance()).isEqualByComparingTo("100.00"); // 90 + 10 refund
    }

    @Test
    void cancel_succeedsForAdminOnOthersBooking() {
        Booking booking = activeBooking();
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking));

        bookingService.cancel(5L, "admin@booking.local", true);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    private Booking activeBooking() {
        return Booking.builder()
                .id(5L).room(activeRoom()).user(owner("100.00")).status(BookingStatus.ACTIVE)
                .startTime(START).endTime(END).cost(BigDecimal.ZERO).build();
    }
}

package com.iramil73.booking.service;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
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
    @InjectMocks
    private BookingService bookingService;

    private static final String EMAIL = "owner@example.com";

    private Room activeRoom() {
        return Room.builder().id(1L).name("Room A").capacity(4).active(true).build();
    }

    private User owner() {
        return User.builder().id(7L).email(EMAIL).fullName("Owner").role(Role.USER).build();
    }

    private BookingRequest request(LocalDateTime start, LocalDateTime end) {
        return new BookingRequest(1L, "Sync", start, end);
    }

    @Test
    void create_rejectsWhenStartNotBeforeEnd() {
        LocalDateTime t = LocalDateTime.now().plusDays(1);
        assertThatThrownBy(() -> bookingService.create(request(t, t), EMAIL))
                .isInstanceOf(BadRequestException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void create_rejectsBookingInThePast() {
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(1);
        assertThatThrownBy(() -> bookingService.create(request(start, end), EMAIL))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_rejectsWhenRoomMissing() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        when(roomRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> bookingService.create(request(start, start.plusHours(1)), EMAIL))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_rejectsWhenRoomInactive() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        Room room = activeRoom();
        room.setActive(false);
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        assertThatThrownBy(() -> bookingService.create(request(start, start.plusHours(1)), EMAIL))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_rejectsOverlappingSlot() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        when(roomRepository.findById(1L)).thenReturn(Optional.of(activeRoom()));
        when(bookingRepository.existsByRoomIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(
                eq(1L), eq(BookingStatus.ACTIVE), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> bookingService.create(request(start, start.plusHours(1)), EMAIL))
                .isInstanceOf(ConflictException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void create_savesActiveBookingWhenValid() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        when(roomRepository.findById(1L)).thenReturn(Optional.of(activeRoom()));
        when(bookingRepository.existsByRoomIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(
                anyLong(), any(), any(), any())).thenReturn(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(owner()));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingResponse response = bookingService.create(request(start, start.plusHours(1)), EMAIL);

        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.roomId()).isEqualTo(1L);
        assertThat(response.userEmail()).isEqualTo(EMAIL);
    }

    @Test
    void cancel_rejectsNonOwnerNonAdmin() {
        Booking booking = Booking.builder()
                .id(5L).room(activeRoom()).user(owner()).status(BookingStatus.ACTIVE).build();
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking));
        assertThatThrownBy(() -> bookingService.cancel(5L, "intruder@example.com", false))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.ACTIVE);
    }

    @Test
    void cancel_rejectsAlreadyCancelled() {
        Booking booking = Booking.builder()
                .id(5L).room(activeRoom()).user(owner()).status(BookingStatus.CANCELLED).build();
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking));
        assertThatThrownBy(() -> bookingService.cancel(5L, EMAIL, false))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cancel_succeedsForOwner() {
        Booking booking = Booking.builder()
                .id(5L).room(activeRoom()).user(owner()).status(BookingStatus.ACTIVE).build();
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking));

        bookingService.cancel(5L, EMAIL, false);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancelledAt()).isNotNull();
    }

    @Test
    void cancel_succeedsForAdminOnOthersBooking() {
        Booking booking = Booking.builder()
                .id(5L).room(activeRoom()).user(owner()).status(BookingStatus.ACTIVE).build();
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking));

        bookingService.cancel(5L, "admin@booking.local", true);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }
}

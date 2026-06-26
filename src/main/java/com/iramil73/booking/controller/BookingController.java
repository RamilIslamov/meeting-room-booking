package com.iramil73.booking.controller;

import com.iramil73.booking.dto.BookingRequest;
import com.iramil73.booking.dto.BookingResponse;
import com.iramil73.booking.dto.BookingUpdateRequest;
import com.iramil73.booking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(@Valid @RequestBody BookingRequest request, Authentication authentication) {
        return bookingService.create(request, authentication.getName(), isAdmin(authentication));
    }

    @GetMapping("/my")
    public List<BookingResponse> my(Authentication authentication) {
        return bookingService.listMy(authentication.getName());
    }

    @GetMapping
    public List<BookingResponse> byRoomAndDate(
            @RequestParam Long roomId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return bookingService.listByRoomAndDate(roomId, date);
    }

    /** Admin dashboard feed: all bookings starting within the inclusive [from, to] day range. */
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public List<BookingResponse> adminInRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return bookingService.listInRange(from, to);
    }

    @PutMapping("/{id}")
    public BookingResponse update(@PathVariable Long id,
                                  @Valid @RequestBody BookingUpdateRequest request,
                                  Authentication authentication) {
        return bookingService.update(id, request, authentication.getName(), isAdmin(authentication));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long id, Authentication authentication) {
        bookingService.cancel(id, authentication.getName(), isAdmin(authentication));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}

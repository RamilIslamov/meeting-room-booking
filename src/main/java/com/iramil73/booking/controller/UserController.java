package com.iramil73.booking.controller;

import com.iramil73.booking.dto.CurrentUserResponse;
import com.iramil73.booking.dto.TopUpRequest;
import com.iramil73.booking.dto.UserResponse;
import com.iramil73.booking.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        return userService.current(authentication.getName());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponse> list() {
        return userService.list();
    }

    @PostMapping("/{id}/top-up")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse topUp(@PathVariable Long id, @Valid @RequestBody TopUpRequest request) {
        return userService.topUp(id, request.amount());
    }
}

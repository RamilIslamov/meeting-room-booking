package com.iramil73.booking.controller;

import com.iramil73.booking.dto.CurrentUserResponse;
import com.iramil73.booking.entity.User;
import com.iramil73.booking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        return new CurrentUserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole().name());
    }
}

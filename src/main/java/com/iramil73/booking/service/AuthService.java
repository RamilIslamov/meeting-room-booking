package com.iramil73.booking.service;

import com.iramil73.booking.config.UserProperties;
import com.iramil73.booking.dto.AuthResponse;
import com.iramil73.booking.dto.LoginRequest;
import com.iramil73.booking.dto.RegisterRequest;
import com.iramil73.booking.entity.Role;
import com.iramil73.booking.entity.User;
import com.iramil73.booking.exception.EmailAlreadyUsedException;
import com.iramil73.booking.repository.UserRepository;
import com.iramil73.booking.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserProperties userProperties;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyUsedException(request.email());
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role(Role.USER)
                .balance(userProperties.startingBalance())
                .build();
        userRepository.save(user);

        return toAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        // Throws BadCredentialsException (-> 401) on wrong email/password.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        User user = userRepository.findByEmail(request.email()).orElseThrow();
        return toAuthResponse(user);
    }

    private AuthResponse toAuthResponse(User user) {
        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, "Bearer", user.getEmail(), user.getFullName(), user.getRole().name());
    }
}

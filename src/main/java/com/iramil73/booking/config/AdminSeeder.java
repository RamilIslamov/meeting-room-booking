package com.iramil73.booking.config;

import com.iramil73.booking.entity.Role;
import com.iramil73.booking.entity.User;
import com.iramil73.booking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Ensures an initial ADMIN user exists on startup, created from {@code app.admin.*}.
 * Idempotent: skips if a user with the configured email already exists.
 */
@Component
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties adminProperties;
    private final UserProperties userProperties;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(adminProperties.email())) {
            return;
        }
        User admin = User.builder()
                .email(adminProperties.email())
                .passwordHash(passwordEncoder.encode(adminProperties.password()))
                .fullName("Administrator")
                .role(Role.ADMIN)
                .balance(userProperties.startingBalance())
                .build();
        userRepository.save(admin);
        log.warn("Seeded initial ADMIN user '{}'. Change the default password in production "
                + "(override ADMIN_EMAIL / ADMIN_PASSWORD).", adminProperties.email());
    }
}

package com.iramil73.booking.service;

import com.iramil73.booking.dto.CurrentUserResponse;
import com.iramil73.booking.dto.UserResponse;
import com.iramil73.booking.entity.User;
import com.iramil73.booking.exception.NotFoundException;
import com.iramil73.booking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public CurrentUserResponse current(String email) {
        return CurrentUserResponse.from(byEmail(email));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse topUp(Long id, BigDecimal amount) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
        user.setBalance(user.getBalance().add(amount));
        return UserResponse.from(user);
    }

    private User byEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found: " + email));
    }
}

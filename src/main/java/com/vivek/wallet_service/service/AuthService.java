package com.vivek.wallet_service.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.vivek.wallet_service.entity.User;
import com.vivek.wallet_service.repository.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    public void register(User userDetails) {
        normalizeAndValidate(userDetails);
        if (userRepository.existsByEmail(userDetails.getEmail())) {
            throw new RuntimeException("Email already registered");
        }
        userDetails.setPassword(passwordEncoder.encode(userDetails.getPassword()));
        userRepository.save(userDetails);
    }

    public String login(User userDetails) {
        normalizeAndValidate(userDetails);
        User user = userRepository.findFirstByEmailOrderByIdAsc(userDetails.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(userDetails.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        return jwtService.generateToken(user.getEmail());
    }

    private void normalizeAndValidate(User userDetails) {
        if (userDetails.getEmail() == null || userDetails.getEmail().isBlank()
                || userDetails.getPassword() == null || userDetails.getPassword().isBlank()) {
            throw new RuntimeException("Email and password are required");
        }
        userDetails.setEmail(userDetails.getEmail().trim().toLowerCase());
    }
}

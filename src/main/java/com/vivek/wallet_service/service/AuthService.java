package com.vivek.wallet_service.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.vivek.wallet_service.dto.AuthRequest;
import com.vivek.wallet_service.dto.AuthResponse;
import com.vivek.wallet_service.entity.RefreshToken;
import com.vivek.wallet_service.entity.User;
import com.vivek.wallet_service.repository.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository, 
                       JwtService jwtService, 
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    //registering a particular usr with email and password
    public void register(User userDetails) {
        normalizeAndValidate(userDetails.getEmail(), userDetails.getPassword());
        
        if (userRepository.existsByEmail(userDetails.getEmail().trim().toLowerCase())) {
            throw new RuntimeException("Email already registered");
        }
        
        userDetails.setEmail(userDetails.getEmail().trim().toLowerCase());
        userDetails.setPassword(passwordEncoder.encode(userDetails.getPassword()));
        userRepository.save(userDetails);
    }

    public AuthResponse login(AuthRequest request) {
        String cleanEmail = normalizeAndValidate(request.email(), request.password());

        // Constant-time check mitigation: throw generic credentials error
        User user = userRepository.findFirstByEmailOrderByIdAsc(cleanEmail)
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new RuntimeException("Invalid email or password");
        }

        // 1. Generate short-lived Access Token (15 mins)
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());

        // 2. Generate initial Refresh Token (7 days) with new tokenFamily UUID
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());

        return new AuthResponse(accessToken, refreshToken.getToken());
    }

    public AuthResponse refreshToken(String rawRefreshToken) {
        // 1. Rotate Refresh Token (Handles reuse detection & expiry internally)
        RefreshToken newRefreshToken = refreshTokenService.rotateRefreshToken(rawRefreshToken);

        // 2. Fetch User to embed claims in new Access Token
        User user = userRepository.findById(newRefreshToken.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 3. Issue new Access Token
        String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());

        return new AuthResponse(newAccessToken, newRefreshToken.getToken());
    }

    private String normalizeAndValidate(String email, String password) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new RuntimeException("Email and password are required");
        }
        return email.trim().toLowerCase();
    }
}
package com.vivek.wallet_service.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.vivek.wallet_service.entity.User;
import com.vivek.wallet_service.repository.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    // BCrypt object for hashing + verifying passwords
    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository,JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    // Register new user
    public void register(User userDetails) {

        // convert raw password -> hashed password
        String hashedPassword =
                passwordEncoder.encode(userDetails.getPassword());

        // replace raw password with hashed one
        userDetails.setPassword(hashedPassword);

        // save user
        userRepository.save(userDetails);
    }

    // Login user
    public String login(User userDetails) {

        User user = userRepository.findByEmail(userDetails.getEmail())
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        // compare raw password with hashed password
        boolean passwordMatches =
                passwordEncoder.matches(
                        userDetails.getPassword(),
                        user.getPassword()
                );

        if (!passwordMatches) {
            throw new RuntimeException("Invalid password");
        }

        return jwtService.generateToken(user.getEmail());
    }
}
package com.vivek.wallet_service.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vivek.wallet_service.entity.User;
import com.vivek.wallet_service.service.AuthService;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public String register(@RequestBody User userDetails) {
        authService.register(userDetails);
        return "User registered successfully";
    }

    @PostMapping("/login")
    public String login(@RequestBody User userDetails) {
        return authService.login(userDetails);
    }
}

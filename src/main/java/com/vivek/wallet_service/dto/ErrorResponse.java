package com.vivek.wallet_service.dto;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public class ErrorResponse {

    private final int status;
    private final String error;
    private final String message;
    private final LocalDateTime timestamp;

    public ErrorResponse(HttpStatus status, String message) {
        this.status = status.value();
        this.error = status.getReasonPhrase();
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }
}

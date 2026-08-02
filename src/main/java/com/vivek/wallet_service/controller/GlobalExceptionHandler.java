package com.vivek.wallet_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.vivek.wallet_service.dto.ErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException exception) {
        HttpStatus status = resolveStatus(exception.getMessage());
        return ResponseEntity
                .status(status)
                .body(new ErrorResponse(status, exception.getMessage()));
    }

    private HttpStatus resolveStatus(String message) {
        if (message == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }

        if (message.contains("not found")) {
            return HttpStatus.NOT_FOUND;
        }

        return switch (message) {
            case "Duplicate request" -> HttpStatus.CONFLICT;
            case "Invalid amount", "Cannot transfer to self", "Insufficient balance" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}

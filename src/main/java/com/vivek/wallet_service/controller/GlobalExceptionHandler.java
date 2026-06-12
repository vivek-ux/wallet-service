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

        if (message.equals("Duplicate request")) {
            return HttpStatus.CONFLICT;
        }

        if (message.equals("Invalid amount")
                || message.equals("Cannot transfer to self")
                || message.equals("Insufficient balance")) {
            return HttpStatus.BAD_REQUEST;
        }

        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}

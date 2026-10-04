package com.vivek.wallet_service.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.vivek.wallet_service.dto.ErrorResponse;
import com.vivek.wallet_service.exception.AccountNotFoundException;
import com.vivek.wallet_service.exception.DuplicateRequestException;
import com.vivek.wallet_service.exception.InsufficientBalanceException;
import com.vivek.wallet_service.exception.InvalidTransferException;
import com.vivek.wallet_service.exception.UserNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({UserNotFoundException.class, AccountNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(DuplicateRequestException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateRequestException exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler({InvalidTransferException.class, InsufficientBalanceException.class})
    public ResponseEntity<ErrorResponse> handleInvalidTransfer(RuntimeException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = "Invalid request";
        List<FieldError> fieldErrors = exception.getBindingResult().getFieldErrors();

        if (!fieldErrors.isEmpty()) {
            message = fieldErrors.get(0).getDefaultMessage();
        }

        return response(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException exception) {
        HttpStatus status = resolveStatus(exception.getMessage());
        return response(status, exception.getMessage());
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(status, message));
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
            case "Invalid amount", "Cannot transfer to self", "Insufficient balance", "Email and password are required" -> HttpStatus.BAD_REQUEST;
            case "Email already registered" -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}

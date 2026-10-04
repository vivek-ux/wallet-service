package com.vivek.wallet_service.service;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.vivek.wallet_service.exception.DuplicateRequestException;
import com.vivek.wallet_service.exception.InvalidTransferException;

@Service
public class IdempotencyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdempotencyService.class);
    private static final Duration IDEMPOTENCY_TTL = Duration.ofMinutes(10);
    private static final String PROCESSING = "processing";
    private static final String COMPLETED = "completed";

    private final StringRedisTemplate redisTemplate;

    public IdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void acquireForCurrentTransaction(String idempotencyKey) {
        validateKey(idempotencyKey);

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                idempotencyKey,
                PROCESSING,
                IDEMPOTENCY_TTL
        );

        if (!Boolean.TRUE.equals(acquired)) {
            throw new DuplicateRequestException("Duplicate request");
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            redisTemplate.delete(idempotencyKey);
            throw new IllegalStateException("Idempotency requires an active transaction");
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    redisTemplate.opsForValue().set(idempotencyKey, COMPLETED, IDEMPOTENCY_TTL);
                } catch (RuntimeException exception) {
                    // PostgreSQL has already committed. Keep the processing key until its TTL expires
                    // instead of reporting the completed transfer as failed to the caller.
                    LOGGER.error("Could not mark committed idempotency key as completed", exception);
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    redisTemplate.delete(idempotencyKey);
                }
            }
        });
    }

    private void validateKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidTransferException("Idempotency key is required");
        }
    }
}

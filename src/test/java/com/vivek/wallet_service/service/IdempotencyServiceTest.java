package com.vivek.wallet_service.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.vivek.wallet_service.exception.DuplicateRequestException;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    private static final String KEY = "transfer-key-1";
    private static final Duration TTL = Duration.ofMinutes(10);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void duplicateKeyIsRejected() {
        when(valueOperations.setIfAbsent(KEY, "processing", TTL)).thenReturn(false);

        assertThatThrownBy(() -> idempotencyService.acquireForCurrentTransaction(KEY))
                .isInstanceOf(DuplicateRequestException.class)
                .hasMessage("Duplicate request");
    }

    @Test
    void completedStateIsWrittenOnlyAfterCommit() {
        when(valueOperations.setIfAbsent(KEY, "processing", TTL)).thenReturn(true);

        idempotencyService.acquireForCurrentTransaction(KEY);

        verify(valueOperations, never()).set(KEY, "completed", TTL);
        synchronizations().forEach(TransactionSynchronization::afterCommit);
        synchronizations().forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

        verify(valueOperations).set(KEY, "completed", TTL);
        verify(redisTemplate, never()).delete(KEY);
    }

    @Test
    void rollbackReleasesProcessingKey() {
        when(valueOperations.setIfAbsent(KEY, "processing", TTL)).thenReturn(true);

        idempotencyService.acquireForCurrentTransaction(KEY);
        synchronizations().forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(valueOperations, never()).set(KEY, "completed", TTL);
        verify(redisTemplate).delete(KEY);
    }

    private java.util.List<TransactionSynchronization> synchronizations() {
        return TransactionSynchronizationManager.getSynchronizations();
    }
}

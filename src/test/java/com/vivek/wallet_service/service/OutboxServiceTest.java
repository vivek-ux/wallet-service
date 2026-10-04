package com.vivek.wallet_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vivek.wallet_service.entity.OutboxEvent;
import com.vivek.wallet_service.exception.InvalidTransferException;
import com.vivek.wallet_service.repository.OutboxEventRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Test
    void saveTransferEventSerializesValidJsonAndPersistsOutboxRow() {
        ObjectMapper objectMapper = new ObjectMapper();
        OutboxService outboxService = new OutboxService(outboxEventRepository, objectMapper);

        outboxService.saveTransferEvent(
                "sender+test@example.com",
                "recipient@example.com",
                new BigDecimal("40.00")
        );

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent event = captor.getValue();
        assertThat(event.getTopic()).isEqualTo("money-transfers");
        JsonNode payload = objectMapper.readTree(event.getPayload());
        assertThat(payload.get("fromEmail").asText()).isEqualTo("sender+test@example.com");
        assertThat(payload.get("toEmail").asText()).isEqualTo("recipient@example.com");
        assertThat(payload.get("amount").asText()).isEqualTo("40.00");
    }

    @Test
    void serializationFailureAbortsOutboxPersistenceCleanly() {
        ObjectMapper objectMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any())).thenThrow(new TestJacksonException("boom"));
        OutboxService outboxService = new OutboxService(outboxEventRepository, objectMapper);

        assertThatThrownBy(() -> outboxService.saveTransferEvent(
                "sender@example.com",
                "recipient@example.com",
                new BigDecimal("40.00")
        )).isInstanceOf(InvalidTransferException.class)
                .hasMessage("Could not prepare transfer event");

        verify(outboxEventRepository, never()).save(any());
    }

    private static final class TestJacksonException extends JacksonException {
        private TestJacksonException(String message) {
            super(message);
        }
    }
}

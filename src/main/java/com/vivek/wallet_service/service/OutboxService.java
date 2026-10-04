package com.vivek.wallet_service.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.vivek.wallet_service.dto.TransferEvent;
import com.vivek.wallet_service.entity.OutboxEvent;
import com.vivek.wallet_service.exception.InvalidTransferException;
import com.vivek.wallet_service.repository.OutboxEventRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class OutboxService {

    private static final String MONEY_TRANSFERS_TOPIC = "money-transfers";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    public void saveTransferEvent(String fromEmail, String toEmail, BigDecimal amount) {
        TransferEvent event = new TransferEvent();
        event.setFromEmail(fromEmail);
        event.setToEmail(toEmail);
        event.setAmount(amount.toPlainString());

        try {
            String payload = objectMapper.writeValueAsString(event);
            outboxEventRepository.save(new OutboxEvent(MONEY_TRANSFERS_TOPIC, payload));
        } catch (JacksonException exception) {
            throw new InvalidTransferException("Could not prepare transfer event", exception);
        }
    }
}

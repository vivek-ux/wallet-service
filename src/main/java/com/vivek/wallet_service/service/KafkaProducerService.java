package com.vivek.wallet_service.service;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.vivek.wallet_service.dto.TransferEvent;

@Service
public class KafkaProducerService {

    private static final String MONEY_TRANSFERS_TOPIC = "money-transfers";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendTransferEvent(TransferEvent event) {
        String message = event.getFromEmail()
                + " sent "
                + event.getAmount()
                + " to "
                + event.getToEmail();

        kafkaTemplate.send(MONEY_TRANSFERS_TOPIC, message);
    }
}

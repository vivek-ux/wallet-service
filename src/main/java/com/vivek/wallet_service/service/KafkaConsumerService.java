package com.vivek.wallet_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaConsumerService.class);

    @KafkaListener(
            topics = "money-transfers",
            groupId = "wallet-group"
    )
    public void consumeTransferEvent(String message) {
        LOGGER.info("Kafka Message Received: {}", message);
    }
}

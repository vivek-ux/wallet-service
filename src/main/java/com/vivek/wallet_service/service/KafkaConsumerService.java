package com.vivek.wallet_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.vivek.wallet_service.dto.TransferEvent;

@Service
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaConsumerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaConsumerService.class);

    private final EmailNotificationService emailNotificationService;

    public KafkaConsumerService(EmailNotificationService emailNotificationService) {
        this.emailNotificationService = emailNotificationService;
    }

    @KafkaListener(
            topics = "money-transfers",
            groupId = "wallet-group"
    )
    public void consumeTransferEvent(String message) {
        LOGGER.info("Kafka Message Received: {}", message);
        emailNotificationService.sendTransferEmails(toTransferEvent(message));
    }

    private TransferEvent toTransferEvent(String message) {
        TransferEvent event = new TransferEvent();
        event.setFromEmail(readJsonValue(message, "fromEmail"));
        event.setToEmail(readJsonValue(message, "toEmail"));
        event.setAmount(readJsonValue(message, "amount"));
        return event;
    }

    private String readJsonValue(String json, String fieldName) {
        String key = "\"" + fieldName + "\":\"";
        int valueStart = json.indexOf(key);

        if (valueStart == -1) {
            throw new RuntimeException("Missing Kafka event field: " + fieldName);
        }

        valueStart += key.length();
        int valueEnd = json.indexOf("\"", valueStart);

        if (valueEnd == -1) {
            throw new RuntimeException("Invalid Kafka event payload");
        }

        return json.substring(valueStart, valueEnd)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}

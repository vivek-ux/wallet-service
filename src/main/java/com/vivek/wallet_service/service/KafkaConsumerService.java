package com.vivek.wallet_service.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumerService {

    @KafkaListener(
            topics = "money-transfers",
            groupId = "wallet-group"
    )
    public void consumeTransferEvent(String message) {

        System.out.println( "Kafka Message Received:");

        System.out.println(message);
    }

}
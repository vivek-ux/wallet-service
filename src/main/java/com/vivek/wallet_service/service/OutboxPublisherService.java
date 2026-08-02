package com.vivek.wallet_service.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vivek.wallet_service.entity.OutboxEvent;
import com.vivek.wallet_service.repository.OutboxEventRepository;

@Service
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class OutboxPublisherService {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisherService(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        // Polling keeps the outbox implementation easy to explain and reliable after restarts.
        List<OutboxEvent> pendingEvents =
                outboxEventRepository.findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        OutboxEvent.STATUS_PENDING,
                        LocalDateTime.now()
                );

        for (OutboxEvent event : pendingEvents) {
            publish(event);
        }
    }

    private void publish(OutboxEvent event) {
        try {
            kafkaTemplate.send(event.getTopic(), event.getPayload()).get(5, TimeUnit.SECONDS);
            event.markSent();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            event.markFailed("Kafka publish was interrupted");
        } catch (ExecutionException | TimeoutException exception) {
            event.markFailed("Kafka publish failed");
        } catch (RuntimeException exception) {
            event.markFailed(exception.getMessage());
        }
    }
}

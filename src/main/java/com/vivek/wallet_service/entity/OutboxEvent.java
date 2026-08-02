package com.vivek.wallet_service.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;

@Entity
public class OutboxEvent {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String topic;

    @Column(columnDefinition = "TEXT")
    private String payload;

    private String status = STATUS_PENDING;

    private int attempts;

    private LocalDateTime createdAt;

    private LocalDateTime nextAttemptAt;

    private LocalDateTime sentAt;

    @Column(length = 1000)
    private String errorMessage;

    protected OutboxEvent() {
    }

    public OutboxEvent(String topic, String payload) {
        this.topic = topic;
        this.payload = payload;
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.nextAttemptAt = now;
    }

    public void markSent() {
        this.status = STATUS_SENT;
        this.sentAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.attempts++;
        this.errorMessage = errorMessage;
        this.nextAttemptAt = LocalDateTime.now().plusMinutes(1);
    }

    public Long getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public String getPayload() {
        return payload;
    }

    public String getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}

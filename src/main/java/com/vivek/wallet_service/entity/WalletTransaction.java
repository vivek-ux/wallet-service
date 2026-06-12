package com.vivek.wallet_service.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class WalletTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Account fromAccount;

    @ManyToOne
    private Account toAccount;

    private BigDecimal amount;

    @Column(unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    private LocalDateTime createdAt;

    public WalletTransaction() {
    }

    public WalletTransaction(
            Account fromAccount,
            Account toAccount,
            BigDecimal amount,
            String idempotencyKey,
            TransactionStatus status
    ) {
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
    }

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}

package com.vivek.wallet_service.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.vivek.wallet_service.entity.TransactionStatus;
import com.vivek.wallet_service.entity.WalletTransaction;

import lombok.Getter;

@Getter
public class TransactionResponse {

    private final Long id;
    private final String fromEmail;
    private final String toEmail;
    private final BigDecimal amount;
    private final TransactionStatus status;
    private final LocalDateTime createdAt;

    public TransactionResponse(WalletTransaction walletTransaction) {
        this.id = walletTransaction.getId();
        this.fromEmail = walletTransaction.getFromAccount().getUser().getEmail();
        this.toEmail = walletTransaction.getToAccount().getUser().getEmail();
        this.amount = walletTransaction.getAmount();
        this.status = walletTransaction.getStatus();
        this.createdAt = walletTransaction.getCreatedAt();
    }
}

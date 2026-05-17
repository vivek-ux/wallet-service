package com.vivek.wallet_service.dto;

import java.math.BigDecimal;

public class CreateTransferRequest {

    private String toEmail;
    private BigDecimal amount;

    // setters
    public void setToEmail(String toEmail) {
        this.toEmail = toEmail;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    // getters
    public String getToEmail() {
        return toEmail;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}





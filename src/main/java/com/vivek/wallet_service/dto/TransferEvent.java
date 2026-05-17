package com.vivek.wallet_service.dto;

public class TransferEvent {

    private String fromEmail;
    private String toEmail;
    private String amount;

    // getters
    public String getFromEmail() {
        return fromEmail;
    }

    public String getToEmail() {
        return toEmail;
    }

    public String getAmount() {
        return amount;
    }

    // setters
    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }

    public void setToEmail(String toEmail) {
        this.toEmail = toEmail;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }
}
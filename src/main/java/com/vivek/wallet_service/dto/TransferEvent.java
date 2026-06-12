package com.vivek.wallet_service.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TransferEvent {

    private String fromEmail;
    private String toEmail;
    private String amount;
}

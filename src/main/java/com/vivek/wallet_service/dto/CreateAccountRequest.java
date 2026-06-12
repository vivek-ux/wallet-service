package com.vivek.wallet_service.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateAccountRequest {

    private BigDecimal initialBalance;
}

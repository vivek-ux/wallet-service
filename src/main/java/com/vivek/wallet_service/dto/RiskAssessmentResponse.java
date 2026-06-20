package com.vivek.wallet_service.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RiskAssessmentResponse {

    private final int riskScore;
    private final String riskLevel;
    private final String recommendedAction;
    private final BigDecimal requestedAmount;
    private final BigDecimal currentBalance;
    private final BigDecimal remainingBalance;
    private final long recentTransferCount;
    private final List<String> reasons;
}

package com.vivek.wallet_service.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.vivek.wallet_service.dto.RiskAssessmentResponse;
import com.vivek.wallet_service.entity.Account;
import com.vivek.wallet_service.entity.User;
import com.vivek.wallet_service.repository.AccountRepository;
import com.vivek.wallet_service.repository.UserRepository;
import com.vivek.wallet_service.repository.WalletTransactionRepository;

@Service
public class RiskAssessmentService {

    private static final BigDecimal HIGH_AMOUNT_THRESHOLD = new BigDecimal("1000.00");
    private static final BigDecimal MEDIUM_AMOUNT_THRESHOLD = new BigDecimal("500.00");
    private static final BigDecimal HIGH_BALANCE_RATIO = new BigDecimal("0.80");
    private static final BigDecimal MEDIUM_BALANCE_RATIO = new BigDecimal("0.50");
    private static final BigDecimal LOW_REMAINING_BALANCE = new BigDecimal("100.00");
    private static final int MANY_RECENT_TRANSFERS_THRESHOLD = 5;

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    public RiskAssessmentService(
            UserRepository userRepository,
            AccountRepository accountRepository,
            WalletTransactionRepository walletTransactionRepository
    ) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.walletTransactionRepository = walletTransactionRepository;
    }

    public RiskAssessmentResponse assessTransfer(String fromEmail, String toEmail, BigDecimal amount) {
        validateAmount(amount);

        User fromUser = getUserByEmail(fromEmail, "Sender user not found");
        User toUser = getUserByEmail(toEmail, "Recipient user not found");

        if (fromUser.getEmail().equals(toUser.getEmail())) {
            throw new RuntimeException("Cannot transfer to self");
        }

        Account fromAccount = accountRepository.findByUser(fromUser)
                .orElseThrow(() -> new RuntimeException("Sender account not found"));

        BigDecimal currentBalance = fromAccount.getBalance();
        BigDecimal remainingBalance = currentBalance.subtract(amount);
        long recentTransferCount = walletTransactionRepository.countTransactionHistory(fromUser);

        List<String> reasons = new ArrayList<>();
        int riskScore = 0;

        riskScore += scoreAmount(amount, reasons);
        riskScore += scoreBalanceImpact(amount, currentBalance, reasons);
        riskScore += scoreRemainingBalance(remainingBalance, reasons);
        riskScore += scoreRecentActivity(recentTransferCount, reasons);

        if (reasons.isEmpty()) {
            reasons.add("No major deterministic risk signals were detected.");
        }

        riskScore = Math.min(riskScore, 100);

        return new RiskAssessmentResponse(
                riskScore,
                riskLevel(riskScore),
                recommendedAction(riskScore),
                amount,
                currentBalance,
                remainingBalance,
                recentTransferCount,
                reasons
        );
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Invalid amount");
        }
    }

    private User getUserByEmail(String email, String errorMessage) {
        return userRepository.findFirstByEmailOrderByIdAsc(email.trim().toLowerCase())
                .orElseThrow(() -> new RuntimeException(errorMessage));
    }

    private int scoreAmount(BigDecimal amount, List<String> reasons) {
        if (amount.compareTo(HIGH_AMOUNT_THRESHOLD) >= 0) {
            reasons.add("Transfer amount is at or above the high amount threshold.");
            return 30;
        }

        if (amount.compareTo(MEDIUM_AMOUNT_THRESHOLD) >= 0) {
            reasons.add("Transfer amount is above the medium amount threshold.");
            return 15;
        }

        return 0;
    }

    private int scoreBalanceImpact(BigDecimal amount, BigDecimal currentBalance, List<String> reasons) {
        if (currentBalance.compareTo(BigDecimal.ZERO) <= 0) {
            reasons.add("Sender account has no available balance.");
            return 40;
        }

        BigDecimal balanceRatio = amount.divide(currentBalance, 4, RoundingMode.HALF_UP);

        if (balanceRatio.compareTo(HIGH_BALANCE_RATIO) >= 0) {
            reasons.add("Transfer uses at least 80% of the sender balance.");
            return 30;
        }

        if (balanceRatio.compareTo(MEDIUM_BALANCE_RATIO) >= 0) {
            reasons.add("Transfer uses at least 50% of the sender balance.");
            return 15;
        }

        return 0;
    }

    private int scoreRemainingBalance(BigDecimal remainingBalance, List<String> reasons) {
        if (remainingBalance.compareTo(BigDecimal.ZERO) < 0) {
            reasons.add("Transfer would overdraw the sender account.");
            return 35;
        }

        if (remainingBalance.compareTo(LOW_REMAINING_BALANCE) < 0) {
            reasons.add("Transfer leaves the sender with a low remaining balance.");
            return 10;
        }

        return 0;
    }

    private int scoreRecentActivity(long recentTransferCount, List<String> reasons) {
        if (recentTransferCount >= MANY_RECENT_TRANSFERS_THRESHOLD) {
            reasons.add("Sender has many recent wallet transactions.");
            return 15;
        }

        return 0;
    }

    private String riskLevel(int riskScore) {
        if (riskScore >= 70) {
            return "HIGH";
        }

        if (riskScore >= 35) {
            return "MEDIUM";
        }

        return "LOW";
    }

    private String recommendedAction(int riskScore) {
        if (riskScore >= 70) {
            return "MANUAL_REVIEW";
        }

        if (riskScore >= 35) {
            return "STEP_UP_VERIFICATION";
        }

        return "ALLOW";
    }
}

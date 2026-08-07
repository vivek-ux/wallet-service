package com.vivek.wallet_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vivek.wallet_service.dto.RiskAssessmentResponse;
import com.vivek.wallet_service.entity.Account;
import com.vivek.wallet_service.entity.User;
import com.vivek.wallet_service.repository.AccountRepository;
import com.vivek.wallet_service.repository.UserRepository;
import com.vivek.wallet_service.repository.WalletTransactionRepository;

@ExtendWith(MockitoExtension.class)
class RiskAssessmentServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    private RiskAssessmentService riskAssessmentService;

    @BeforeEach
    void setUp() {
        riskAssessmentService = new RiskAssessmentService(
                userRepository,
                accountRepository,
                walletTransactionRepository
        );
    }

    @Test
    void assessTransferReturnsLowRiskForSmallTransfer() {
        User sender = user(1L, "sender@example.com");
        User recipient = user(2L, "recipient@example.com");
        Account senderAccount = account(sender, "1000.00");

        when(userRepository.findFirstByEmailOrderByIdAsc("sender@example.com")).thenReturn(Optional.of(sender));
        when(userRepository.findFirstByEmailOrderByIdAsc("recipient@example.com")).thenReturn(Optional.of(recipient));
        when(accountRepository.findByUser(sender)).thenReturn(Optional.of(senderAccount));
        when(walletTransactionRepository.countTransactionHistory(sender)).thenReturn(1L);

        RiskAssessmentResponse response = riskAssessmentService.assessTransfer(
                "sender@example.com",
                "recipient@example.com",
                new BigDecimal("100.00")
        );

        assertThat(response.getRiskScore()).isZero();
        assertThat(response.getRiskLevel()).isEqualTo("LOW");
        assertThat(response.getRecommendedAction()).isEqualTo("ALLOW");
        assertThat(response.getRemainingBalance()).isEqualByComparingTo("900.00");
        assertThat(response.getReasons()).contains("No major deterministic risk signals were detected.");
    }

    @Test
    void assessTransferReturnsHighRiskForLargeBalanceDrainingTransfer() {
        User sender = user(1L, "sender@example.com");
        User recipient = user(2L, "recipient@example.com");
        Account senderAccount = account(sender, "1200.00");

        when(userRepository.findFirstByEmailOrderByIdAsc("sender@example.com")).thenReturn(Optional.of(sender));
        when(userRepository.findFirstByEmailOrderByIdAsc("recipient@example.com")).thenReturn(Optional.of(recipient));
        when(accountRepository.findByUser(sender)).thenReturn(Optional.of(senderAccount));
        when(walletTransactionRepository.countTransactionHistory(sender)).thenReturn(7L);

        RiskAssessmentResponse response = riskAssessmentService.assessTransfer(
                "sender@example.com",
                "recipient@example.com",
                new BigDecimal("1100.00")
        );

        assertThat(response.getRiskScore()).isEqualTo(75);
        assertThat(response.getRiskLevel()).isEqualTo("HIGH");
        assertThat(response.getRecommendedAction()).isEqualTo("MANUAL_REVIEW");
        assertThat(response.getReasons()).contains(
                "Transfer amount is at or above the high amount threshold.",
                "Transfer uses at least 80% of the sender balance.",
                "Sender has many recent wallet transactions."
        );
    }

    @Test
    void assessTransferRejectsInvalidAmount() {
        assertThatThrownBy(() ->
                riskAssessmentService.assessTransfer(
                        "sender@example.com",
                        "recipient@example.com",
                        BigDecimal.ZERO
                )
        ).hasMessage("Invalid amount");
    }

    private User user(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        return user;
    }

    private Account account(User user, String balance) {
        return new Account(user, new BigDecimal(balance));
    }
}

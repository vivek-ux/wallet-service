package com.vivek.wallet_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.vivek.wallet_service.dto.TransactionResponse;
import com.vivek.wallet_service.entity.Account;
import com.vivek.wallet_service.entity.OutboxEvent;
import com.vivek.wallet_service.entity.TransactionStatus;
import com.vivek.wallet_service.entity.User;
import com.vivek.wallet_service.entity.WalletTransaction;
import com.vivek.wallet_service.repository.AccountRepository;
import com.vivek.wallet_service.repository.OutboxEventRepository;
import com.vivek.wallet_service.repository.UserRepository;
import com.vivek.wallet_service.repository.WalletTransactionRepository;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofMinutes(10);
    private static final String IDEMPOTENCY_KEY = "transfer-key-1";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(
                accountRepository,
                userRepository,
                walletTransactionRepository,
                outboxEventRepository,
                redisTemplate
        );

        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("sender@example.com", null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void transferMoneyMovesBalanceCreatesLedgerAndPublishesEvent() {
        User sender = user(1L, "sender@example.com");
        User recipient = user(2L, "recipient@example.com");
        Account senderAccount = account(11L, sender, "100.00");
        Account recipientAccount = account(22L, recipient, "25.00");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(IDEMPOTENCY_KEY, "processing", IDEMPOTENCY_TTL)).thenReturn(true);
        when(userRepository.findFirstByEmailOrderByIdAsc("sender@example.com")).thenReturn(Optional.of(sender));
        when(userRepository.findFirstByEmailOrderByIdAsc("recipient@example.com")).thenReturn(Optional.of(recipient));
        when(accountRepository.findByUser(sender)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByUser(recipient)).thenReturn(Optional.of(recipientAccount));
        when(accountRepository.findAllByIdForUpdate(List.of(11L, 22L)))
                .thenReturn(List.of(senderAccount, recipientAccount));

        accountService.transferMoney("recipient@example.com", new BigDecimal("40.00"), IDEMPOTENCY_KEY);

        assertThat(senderAccount.getBalance()).isEqualByComparingTo("60.00");
        assertThat(recipientAccount.getBalance()).isEqualByComparingTo("65.00");

        ArgumentCaptor<WalletTransaction> transactionCaptor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository).save(transactionCaptor.capture());
        WalletTransaction transaction = transactionCaptor.getValue();
        assertThat(transaction.getFromAccount()).isEqualTo(senderAccount);
        assertThat(transaction.getToAccount()).isEqualTo(recipientAccount);
        assertThat(transaction.getAmount()).isEqualByComparingTo("40.00");
        assertThat(transaction.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEvent outboxEvent = outboxCaptor.getValue();
        assertThat(outboxEvent.getTopic()).isEqualTo("money-transfers");
        assertThat(outboxEvent.getPayload()).contains(
                "\"fromEmail\":\"sender@example.com\"",
                "\"toEmail\":\"recipient@example.com\"",
                "\"amount\":\"40.00\""
        );
        verify(valueOperations).set(IDEMPOTENCY_KEY, "completed", IDEMPOTENCY_TTL);
    }

    @Test
    void transferMoneyRejectsDuplicateIdempotencyKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(IDEMPOTENCY_KEY, "processing", IDEMPOTENCY_TTL)).thenReturn(false);

        assertThatThrownBy(() ->
                accountService.transferMoney("recipient@example.com", new BigDecimal("40.00"), IDEMPOTENCY_KEY)
        ).hasMessage("Duplicate request");

        verify(walletTransactionRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void transferMoneyReleasesIdempotencyKeyWhenValidationFails() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(IDEMPOTENCY_KEY, "processing", IDEMPOTENCY_TTL)).thenReturn(true);

        assertThatThrownBy(() ->
                accountService.transferMoney("recipient@example.com", BigDecimal.ZERO, IDEMPOTENCY_KEY)
        ).hasMessage("Invalid amount");

        verify(redisTemplate).delete(IDEMPOTENCY_KEY);
        verify(walletTransactionRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void getMyTransactionsReturnsSafeHistoryResponse() {
        User sender = user(1L, "sender@example.com");
        User recipient = user(2L, "recipient@example.com");
        Account senderAccount = account(11L, sender, "100.00");
        Account recipientAccount = account(22L, recipient, "25.00");
        WalletTransaction walletTransaction = new WalletTransaction(
                senderAccount,
                recipientAccount,
                new BigDecimal("40.00"),
                IDEMPOTENCY_KEY,
                TransactionStatus.COMPLETED
        );
        walletTransaction.setId(101L);

        when(userRepository.findFirstByEmailOrderByIdAsc("sender@example.com")).thenReturn(Optional.of(sender));
        when(walletTransactionRepository.findTransactionHistory(sender)).thenReturn(List.of(walletTransaction));

        List<TransactionResponse> history = accountService.getMyTransactions();

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getId()).isEqualTo(101L);
        assertThat(history.get(0).getFromEmail()).isEqualTo("sender@example.com");
        assertThat(history.get(0).getToEmail()).isEqualTo("recipient@example.com");
        assertThat(history.get(0).getAmount()).isEqualByComparingTo("40.00");
        assertThat(history.get(0).getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    private User user(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setPassword("password");
        return user;
    }

    private Account account(Long id, User user, String balance) {
        Account account = new Account(user, new BigDecimal(balance));
        account.setId(id);
        return account;
    }
}

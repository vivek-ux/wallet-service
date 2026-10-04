package com.vivek.wallet_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.vivek.wallet_service.dto.TransactionResponse;
import com.vivek.wallet_service.entity.Account;
import com.vivek.wallet_service.entity.TransactionStatus;
import com.vivek.wallet_service.entity.User;
import com.vivek.wallet_service.entity.WalletTransaction;
import com.vivek.wallet_service.exception.InsufficientBalanceException;
import com.vivek.wallet_service.exception.InvalidTransferException;
import com.vivek.wallet_service.exception.UserNotFoundException;
import com.vivek.wallet_service.repository.AccountRepository;
import com.vivek.wallet_service.repository.UserRepository;
import com.vivek.wallet_service.repository.WalletTransactionRepository;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String IDEMPOTENCY_KEY = "transfer-key-1";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private OutboxService outboxService;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(
                accountRepository,
                userRepository,
                walletTransactionRepository,
                idempotencyService,
                outboxService
        );

        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("sender@example.com", null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void transferMoneyMovesBalanceCreatesLedgerAndPersistsOutboxEvent() {
        User sender = user(1L, "sender@example.com");
        User recipient = user(2L, "recipient@example.com");
        Account senderAccount = account(11L, sender, "100.00");
        Account recipientAccount = account(22L, recipient, "25.00");
        stubTransferUsersAndAccounts(sender, recipient, senderAccount, recipientAccount);

        accountService.transferMoney("recipient@example.com", new BigDecimal("40.00"), IDEMPOTENCY_KEY);

        assertThat(senderAccount.getBalance()).isEqualByComparingTo("60.00");
        assertThat(recipientAccount.getBalance()).isEqualByComparingTo("65.00");
        verify(idempotencyService).acquireForCurrentTransaction(IDEMPOTENCY_KEY);

        ArgumentCaptor<WalletTransaction> transactionCaptor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository).save(transactionCaptor.capture());
        WalletTransaction transaction = transactionCaptor.getValue();
        assertThat(transaction.getFromAccount()).isEqualTo(senderAccount);
        assertThat(transaction.getToAccount()).isEqualTo(recipientAccount);
        assertThat(transaction.getAmount()).isEqualByComparingTo("40.00");
        assertThat(transaction.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

        verify(outboxService).saveTransferEvent(
                "sender@example.com",
                "recipient@example.com",
                new BigDecimal("40.00")
        );
    }

    @Test
    void transferMoneyRejectsInsufficientBalance() {
        User sender = user(1L, "sender@example.com");
        User recipient = user(2L, "recipient@example.com");
        Account senderAccount = account(11L, sender, "10.00");
        Account recipientAccount = account(22L, recipient, "25.00");
        stubTransferUsersAndAccounts(sender, recipient, senderAccount, recipientAccount);

        assertThatThrownBy(() -> accountService.transferMoney(
                "recipient@example.com", new BigDecimal("40.00"), IDEMPOTENCY_KEY
        )).isInstanceOf(InsufficientBalanceException.class)
                .hasMessage("Insufficient balance");

        verify(walletTransactionRepository, never()).save(any());
        verify(outboxService, never()).saveTransferEvent(any(), any(), any());
    }

    @Test
    void transferMoneyRejectsZeroAmount() {
        assertThatThrownBy(() -> accountService.transferMoney(
                "recipient@example.com", BigDecimal.ZERO, IDEMPOTENCY_KEY
        )).isInstanceOf(InvalidTransferException.class)
                .hasMessage("Invalid amount");
    }

    @Test
    void transferMoneyRejectsNullAmount() {
        assertThatThrownBy(() -> accountService.transferMoney(
                "recipient@example.com", null, IDEMPOTENCY_KEY
        )).isInstanceOf(InvalidTransferException.class)
                .hasMessage("Invalid amount");
    }

    @Test
    void transferMoneyRejectsBlankRecipientBeforeTrimming() {
        assertThatThrownBy(() -> accountService.transferMoney(
                "  ", new BigDecimal("10.00"), IDEMPOTENCY_KEY
        )).isInstanceOf(InvalidTransferException.class)
                .hasMessage("Recipient email is required");
    }

    @Test
    void transferMoneyRejectsSelfTransfer() {
        User sender = user(1L, "sender@example.com");
        when(userRepository.findFirstByEmailOrderByIdAsc("sender@example.com"))
                .thenReturn(Optional.of(sender));

        assertThatThrownBy(() -> accountService.transferMoney(
                "sender@example.com", new BigDecimal("10.00"), IDEMPOTENCY_KEY
        )).isInstanceOf(InvalidTransferException.class)
                .hasMessage("Cannot transfer to self");
    }

    @Test
    void transferMoneyRejectsMissingRecipient() {
        User sender = user(1L, "sender@example.com");
        when(userRepository.findFirstByEmailOrderByIdAsc("sender@example.com"))
                .thenReturn(Optional.of(sender));
        when(userRepository.findFirstByEmailOrderByIdAsc("missing@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.transferMoney(
                "missing@example.com", new BigDecimal("10.00"), IDEMPOTENCY_KEY
        )).isInstanceOf(UserNotFoundException.class)
                .hasMessage("Recipient user not found");
    }

    @Test
    void transferMoneySortsAccountIdsBeforePessimisticLockQuery() {
        User sender = user(1L, "sender@example.com");
        User recipient = user(2L, "recipient@example.com");
        Account senderAccount = account(22L, sender, "100.00");
        Account recipientAccount = account(11L, recipient, "25.00");

        when(userRepository.findFirstByEmailOrderByIdAsc("sender@example.com")).thenReturn(Optional.of(sender));
        when(userRepository.findFirstByEmailOrderByIdAsc("recipient@example.com")).thenReturn(Optional.of(recipient));
        when(accountRepository.findByUser(sender)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByUser(recipient)).thenReturn(Optional.of(recipientAccount));
        when(accountRepository.findAllByIdForUpdate(List.of(11L, 22L)))
                .thenReturn(List.of(recipientAccount, senderAccount));

        accountService.transferMoney("recipient@example.com", new BigDecimal("40.00"), IDEMPOTENCY_KEY);

        verify(accountRepository).findAllByIdForUpdate(List.of(11L, 22L));
        assertThat(senderAccount.getBalance()).isEqualByComparingTo("60.00");
        assertThat(recipientAccount.getBalance()).isEqualByComparingTo("65.00");
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

    private void stubTransferUsersAndAccounts(
            User sender,
            User recipient,
            Account senderAccount,
            Account recipientAccount
    ) {
        when(userRepository.findFirstByEmailOrderByIdAsc("sender@example.com")).thenReturn(Optional.of(sender));
        when(userRepository.findFirstByEmailOrderByIdAsc("recipient@example.com")).thenReturn(Optional.of(recipient));
        when(accountRepository.findByUser(sender)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByUser(recipient)).thenReturn(Optional.of(recipientAccount));
        when(accountRepository.findAllByIdForUpdate(List.of(11L, 22L)))
                .thenReturn(List.of(senderAccount, recipientAccount));
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

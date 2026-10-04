package com.vivek.wallet_service.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vivek.wallet_service.dto.TransactionResponse;
import com.vivek.wallet_service.entity.Account;
import com.vivek.wallet_service.entity.TransactionStatus;
import com.vivek.wallet_service.entity.User;
import com.vivek.wallet_service.entity.WalletTransaction;
import com.vivek.wallet_service.exception.AccountNotFoundException;
import com.vivek.wallet_service.exception.InsufficientBalanceException;
import com.vivek.wallet_service.exception.InvalidTransferException;
import com.vivek.wallet_service.exception.UserNotFoundException;
import com.vivek.wallet_service.repository.AccountRepository;
import com.vivek.wallet_service.repository.UserRepository;
import com.vivek.wallet_service.repository.WalletTransactionRepository;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final IdempotencyService idempotencyService;
    private final OutboxService outboxService;

    public AccountService(
            AccountRepository accountRepository,
            UserRepository userRepository,
            WalletTransactionRepository walletTransactionRepository,
            IdempotencyService idempotencyService,
            OutboxService outboxService
    ) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.idempotencyService = idempotencyService;
        this.outboxService = outboxService;
    }

    public void createAccount(BigDecimal initialBalance) {
        Account account = new Account(getCurrentUser(), initialBalance);
        accountRepository.save(account);
    }

    public BigDecimal getMyBalance() {
        return getAccountForUser(getCurrentUser(), "Account not found").getBalance();
    }

    public List<TransactionResponse> getMyTransactions() {
        List<WalletTransaction> transactions =
                walletTransactionRepository.findTransactionHistory(getCurrentUser());
        List<TransactionResponse> transactionResponses = new ArrayList<>();

        for (WalletTransaction transaction : transactions) {
            TransactionResponse transactionResponse = new TransactionResponse(transaction);
            transactionResponses.add(transactionResponse);
        }

        return transactionResponses;
    }

    @Transactional
    public void transferMoney(String toEmail, BigDecimal amount, String idempotencyKey) {
        idempotencyService.acquireForCurrentTransaction(idempotencyKey);
        validateTransferRequest(toEmail, amount);

        User fromUser = getCurrentUser("Sender user not found");
        User toUser = getUserByEmail(toEmail, "Recipient user not found");
        validateDifferentUsers(fromUser, toUser);

        Account fromAccount = getAccountForUser(fromUser, "Sender account not found");
        Account toAccount = getAccountForUser(toUser, "Recipient account not found");
        LockedAccounts lockedAccounts = lockAccounts(fromAccount, toAccount);

        validateSufficientBalance(lockedAccounts.fromAccount(), amount);
        moveMoney(lockedAccounts.fromAccount(), lockedAccounts.toAccount(), amount);
        saveLedgerEntry(lockedAccounts.fromAccount(), lockedAccounts.toAccount(), amount, idempotencyKey);
        outboxService.saveTransferEvent(fromUser.getEmail(), toUser.getEmail(), amount);
    }

    private void validateTransferRequest(String toEmail, BigDecimal amount) {
        if (toEmail == null || toEmail.isBlank()) {
            throw new InvalidTransferException("Recipient email is required");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferException("Invalid amount");
        }
    }

    private User getCurrentUser() {
        return getCurrentUser("User not found");
    }

    private User getCurrentUser(String errorMessage) {
        return getUserByEmail(getCurrentUserEmail(), errorMessage);
    }

    private String getCurrentUserEmail() {
        return (String) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
    }

    private User getUserByEmail(String email, String errorMessage) {
        return userRepository.findFirstByEmailOrderByIdAsc(email.trim().toLowerCase())
                .orElseThrow(() -> new UserNotFoundException(errorMessage));
    }

    private Account getAccountForUser(User user, String errorMessage) {
        return accountRepository.findByUser(user)
                .orElseThrow(() -> new AccountNotFoundException(errorMessage));
    }

    private LockedAccounts lockAccounts(Account fromAccount, Account toAccount) {
        // Lock both account rows in id order so opposite-direction transfers do not deadlock each other.
        List<Long> accountIds = new ArrayList<>();
        accountIds.add(fromAccount.getId());
        accountIds.add(toAccount.getId());
        accountIds.sort(Long::compareTo);

        List<Account> lockedAccounts = accountRepository.findAllByIdForUpdate(accountIds);

        Account lockedFromAccount = findLockedAccount(
                lockedAccounts,
                fromAccount.getId(),
                "Sender account not found"
        );

        Account lockedToAccount = findLockedAccount(
                lockedAccounts,
                toAccount.getId(),
                "Recipient account not found"
        );

        return new LockedAccounts(lockedFromAccount, lockedToAccount);
    }

    private Account findLockedAccount(List<Account> lockedAccounts, Long accountId, String errorMessage) {
        for (Account lockedAccount : lockedAccounts) {
            if (lockedAccount.getId().equals(accountId)) {
                return lockedAccount;
            }
        }

        throw new AccountNotFoundException(errorMessage);
    }

    private void validateDifferentUsers(User fromUser, User toUser) {
        if (fromUser.getEmail().equals(toUser.getEmail())) {
            throw new InvalidTransferException("Cannot transfer to self");
        }
    }

    private void validateSufficientBalance(Account account, BigDecimal amount) {
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance");
        }
    }

    private void moveMoney(Account fromAccount, Account toAccount, BigDecimal amount) {
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        toAccount.setBalance(toAccount.getBalance().add(amount));

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);
    }

    private void saveLedgerEntry(
            Account fromAccount,
            Account toAccount,
            BigDecimal amount,
            String idempotencyKey
    ) {
        WalletTransaction walletTransaction = new WalletTransaction(
                fromAccount,
                toAccount,
                amount,
                idempotencyKey,
                TransactionStatus.COMPLETED
        );

        walletTransactionRepository.save(walletTransaction);
    }

    private record LockedAccounts(Account fromAccount, Account toAccount) {
    }
}

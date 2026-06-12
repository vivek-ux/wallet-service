package com.vivek.wallet_service.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vivek.wallet_service.dto.TransactionResponse;
import com.vivek.wallet_service.dto.TransferEvent;
import com.vivek.wallet_service.entity.Account;
import com.vivek.wallet_service.entity.TransactionStatus;
import com.vivek.wallet_service.entity.User;
import com.vivek.wallet_service.entity.WalletTransaction;
import com.vivek.wallet_service.repository.AccountRepository;
import com.vivek.wallet_service.repository.UserRepository;
import com.vivek.wallet_service.repository.WalletTransactionRepository;

@Service
public class AccountService {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofMinutes(10);
    private static final String IDEMPOTENCY_PROCESSING_VALUE = "processing";
    private static final String IDEMPOTENCY_COMPLETED_VALUE = "completed";

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final StringRedisTemplate redisTemplate;
    private final KafkaProducerService kafkaProducerService;

    public AccountService(
            AccountRepository accountRepository,
            UserRepository userRepository,
            WalletTransactionRepository walletTransactionRepository,
            StringRedisTemplate redisTemplate,
            KafkaProducerService kafkaProducerService
    ) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.redisTemplate = redisTemplate;
        this.kafkaProducerService = kafkaProducerService;
    }

    public void createAccount(BigDecimal initialBalance) {
        Account account = new Account(getCurrentUser(), initialBalance);
        accountRepository.save(account);
    }

    public BigDecimal getMyBalance() {
        return getAccountForUser(getCurrentUser(), "Account not found").getBalance();
    }

    public List<TransactionResponse> getMyTransactions() {
        return walletTransactionRepository.findTransactionHistory(getCurrentUser())
                .stream()
                .map(TransactionResponse::new)
                .toList();
    }

    @Transactional
    public void transferMoney(String toEmail, BigDecimal amount, String idempotencyKey) {
        acquireIdempotencyLock(idempotencyKey);

        try {
            validateTransferAmount(amount);

            User fromUser = getCurrentUser("Sender user not found");
            User toUser = getUserByEmail(toEmail, "Recipient user not found");
            validateDifferentUsers(fromUser, toUser);

            Account fromAccount = getAccountForUser(fromUser, "Sender account not found");
            Account toAccount = getAccountForUser(toUser, "Recipient account not found");
            LockedAccounts lockedAccounts = lockAccounts(fromAccount, toAccount);

            validateSufficientBalance(lockedAccounts.fromAccount(), amount);
            moveMoney(lockedAccounts.fromAccount(), lockedAccounts.toAccount(), amount);
            saveLedgerEntry(lockedAccounts.fromAccount(), lockedAccounts.toAccount(), amount, idempotencyKey);

            sendTransferEvent(fromUser.getEmail(), toEmail, amount);
            markRequestCompleted(idempotencyKey);
        } catch (RuntimeException exception) {
            redisTemplate.delete(idempotencyKey);
            throw exception;
        }
    }

    private void acquireIdempotencyLock(String idempotencyKey) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                idempotencyKey,
                IDEMPOTENCY_PROCESSING_VALUE,
                IDEMPOTENCY_TTL
        );

        if (!Boolean.TRUE.equals(acquired)) {
            throw new RuntimeException("Duplicate request");
        }
    }

    private void validateTransferAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Invalid amount");
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
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException(errorMessage));
    }

    private Account getAccountForUser(User user, String errorMessage) {
        return accountRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException(errorMessage));
    }

    private LockedAccounts lockAccounts(Account fromAccount, Account toAccount) {
        // Lock both account rows in id order so opposite-direction transfers do not deadlock each other.
        List<Long> accountIds = List.of(fromAccount.getId(), toAccount.getId());
        Map<Long, Account> lockedAccountsById = accountRepository.findAllByIdForUpdate(accountIds)
                .stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        return new LockedAccounts(
                requireLockedAccount(lockedAccountsById, fromAccount.getId(), "Sender account not found"),
                requireLockedAccount(lockedAccountsById, toAccount.getId(), "Recipient account not found")
        );
    }

    private Account requireLockedAccount(Map<Long, Account> lockedAccountsById, Long accountId, String errorMessage) {
        Account account = lockedAccountsById.get(accountId);

        if (account == null) {
            throw new RuntimeException(errorMessage);
        }

        return account;
    }

    private void validateDifferentUsers(User fromUser, User toUser) {
        if (fromUser.getEmail().equals(toUser.getEmail())) {
            throw new RuntimeException("Cannot transfer to self");
        }
    }

    private void validateSufficientBalance(Account account, BigDecimal amount) {
        if (account.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient balance");
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

    private void sendTransferEvent(String fromEmail, String toEmail, BigDecimal amount) {
        TransferEvent event = new TransferEvent();
        event.setFromEmail(fromEmail);
        event.setToEmail(toEmail);
        event.setAmount(amount.toString());
        kafkaProducerService.sendTransferEvent(event);
    }

    private void markRequestCompleted(String idempotencyKey) {
        redisTemplate.opsForValue().set(
                idempotencyKey,
                IDEMPOTENCY_COMPLETED_VALUE,
                IDEMPOTENCY_TTL
        );
    }

    private record LockedAccounts(Account fromAccount, Account toAccount) {
    }
}

package com.vivek.wallet_service.service;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vivek.wallet_service.dto.TransferEvent;
import com.vivek.wallet_service.entity.Account;
import com.vivek.wallet_service.entity.User;
import com.vivek.wallet_service.repository.AccountRepository;
import com.vivek.wallet_service.repository.UserRepository;

@Service
public class AccountService {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofMinutes(10);
    private static final String IDEMPOTENCY_PROCESSED_VALUE = "processed";

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final KafkaProducerService kafkaProducerService;

    public AccountService(
            AccountRepository accountRepository,
            UserRepository userRepository,
            StringRedisTemplate redisTemplate,
            KafkaProducerService kafkaProducerService
    ) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
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

    @Transactional
    public void transferMoney(String toEmail, BigDecimal amount, String idempotencyKey) {
        rejectDuplicateRequest(idempotencyKey);
        validateTransferAmount(amount);

        User fromUser = getCurrentUser("Sender user not found");
        User toUser = getUserByEmail(toEmail, "Recipient user not found");
        validateDifferentUsers(fromUser, toUser);

        Account fromAccount = getAccountForUser(fromUser, "Sender account not found");
        Account toAccount = getAccountForUser(toUser, "Recipient account not found");
        validateSufficientBalance(fromAccount, amount);

        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        toAccount.setBalance(toAccount.getBalance().add(amount));

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        sendTransferEvent(fromUser.getEmail(), toEmail, amount);
        markRequestProcessed(idempotencyKey);
    }

    private void rejectDuplicateRequest(String idempotencyKey) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(idempotencyKey))) {
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

    private void sendTransferEvent(String fromEmail, String toEmail, BigDecimal amount) {
        TransferEvent event = new TransferEvent();
        event.setFromEmail(fromEmail);
        event.setToEmail(toEmail);
        event.setAmount(amount.toString());
        kafkaProducerService.sendTransferEvent(event);
    }

    private void markRequestProcessed(String idempotencyKey) {
        redisTemplate.opsForValue().set(
                idempotencyKey,
                IDEMPOTENCY_PROCESSED_VALUE,
                IDEMPOTENCY_TTL
        );
    }
}

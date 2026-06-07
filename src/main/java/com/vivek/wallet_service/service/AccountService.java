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

//This is going to contain the business logic for the account service
@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final KafkaProducerService kafkaProducerService;

    public AccountService(AccountRepository accountRepository, UserRepository userRepository, StringRedisTemplate redisTemplate,KafkaProducerService  kafkaProducerService ){
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.kafkaProducerService = kafkaProducerService;   
    }


   //This method will create a new account for a user with an initial balance
    public void createAccount(BigDecimal initialBalance) {

    // get logged in user email
        String email = (String)
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getPrincipal();

        // find user
        User user = userRepository
                .findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException(
                                "User not found"
                        ));

        // create account
        Account account =
                new Account(user, initialBalance);

        accountRepository.save(account);
    }

    //public BigDecimal getAccountBalance(Long accountId){
        
    //}

   public BigDecimal getMyBalance() {

        // get current logged in user's email
        String email = (String)
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getPrincipal();

        // find user by email   
        User user = userRepository
                .findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        // find account belonging to user
        Account account =
                accountRepository.findByUser(user)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Account not found"
                                ));

        return account.getBalance();
    }

   @Transactional
   public void transferMoney(String toEmail, BigDecimal amount, String idempotencyKey) {

        Boolean alreadyExists =
        redisTemplate.hasKey(idempotencyKey);

        if (Boolean.TRUE.equals(alreadyExists)) {
            throw new RuntimeException(
                    "Duplicate request"
            );
        }
        
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Invalid amount");
        }
        String fromEmail = (String)
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getPrincipal();

        User fromUser = userRepository.findByEmail(fromEmail)   
                .orElseThrow(() ->
                        new RuntimeException("Sender user not found"));
        
        User toUser = userRepository.findByEmail(toEmail)
                .orElseThrow(() ->
                        new RuntimeException("Recipient user not found"));

        if(fromUser.getEmail().equals(toUser.getEmail())){
            throw new RuntimeException("Cannot transfer to self");
        }

        Account fromAccount = accountRepository.findByUser(fromUser)
                .orElseThrow(() ->
                        new RuntimeException("Sender account not found"));

        Account toAccount = accountRepository.findByUser(toUser)
                .orElseThrow(() ->
                        new RuntimeException("Recipient account not found"));

        if(fromAccount.getBalance().compareTo(amount) < 0){
            throw new RuntimeException("Insufficient balance");
        }
        
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        toAccount.setBalance(toAccount.getBalance().add(amount));       

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        TransferEvent event =
        new TransferEvent();

        event.setFromEmail(fromEmail);
        event.setToEmail(toEmail);
        event.setAmount(amount.toString());

        kafkaProducerService.sendTransferEvent(event);

        redisTemplate.opsForValue().set(idempotencyKey,"processed",Duration.ofMinutes(10));
        

   }
}

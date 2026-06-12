package com.vivek.wallet_service.controller;

import java.math.BigDecimal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vivek.wallet_service.dto.CreateAccountRequest;
import com.vivek.wallet_service.dto.CreateTransferRequest;
import com.vivek.wallet_service.service.AccountService;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/me/balance")
    public BigDecimal getMyBalance() {
        return accountService.getMyBalance();
    }

    @PostMapping("/create")
    public String createAccount(@RequestBody CreateAccountRequest request) {
        accountService.createAccount(request.getInitialBalance());
        return "Account created";
    }

    @PostMapping("/transfer")
    public String transferMoney(
            @RequestBody CreateTransferRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        accountService.transferMoney(
                request.getToEmail(),
                request.getAmount(),
                idempotencyKey
        );

        return "Transfer successful";
    }
}

package com.vivek.wallet_service.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.vivek.wallet_service.security.JwtFilter;
import com.vivek.wallet_service.security.SecurityConfig;
import com.vivek.wallet_service.service.AccountService;
import com.vivek.wallet_service.service.JwtService;

@WebMvcTest(AccountController.class)
@Import({SecurityConfig.class, JwtFilter.class})
class AccountControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AccountService accountService;

    @MockitoBean
    private JwtService jwtService;

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/accounts", "/accounts"})
    void dashboardRoutesAcceptValidBearerToken(String basePath) throws Exception {
        when(jwtService.isTokenValid("test-token")).thenReturn(true);
        when(jwtService.extractEmail("test-token")).thenReturn("test@example.com");
        when(accountService.getMyBalance()).thenReturn(new BigDecimal("1000"));
        when(accountService.getMyTransactions()).thenReturn(List.of());

        mvc.perform(get(basePath + "/me/balance").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("1000"));
        mvc.perform(get(basePath + "/me/transactions").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/accounts", "/accounts"})
    void dashboardRoutesRequireAuthentication(String basePath) throws Exception {
        mvc.perform(get(basePath + "/me/balance")).andExpect(status().isForbidden());
        mvc.perform(get(basePath + "/me/transactions")).andExpect(status().isForbidden());
    }
}

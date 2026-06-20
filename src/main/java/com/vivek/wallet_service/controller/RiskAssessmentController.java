package com.vivek.wallet_service.controller;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vivek.wallet_service.dto.RiskAssessmentRequest;
import com.vivek.wallet_service.dto.RiskAssessmentResponse;
import com.vivek.wallet_service.service.RiskAssessmentService;

@RestController
@RequestMapping("/risk")
public class RiskAssessmentController {

    private final RiskAssessmentService riskAssessmentService;

    public RiskAssessmentController(RiskAssessmentService riskAssessmentService) {
        this.riskAssessmentService = riskAssessmentService;
    }

    @PostMapping("/assess-transfer")
    public RiskAssessmentResponse assessTransfer(@RequestBody RiskAssessmentRequest request) {
        String fromEmail = (String) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        return riskAssessmentService.assessTransfer(
                fromEmail,
                request.getToEmail(),
                request.getAmount()
        );
    }
}

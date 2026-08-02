package com.vivek.wallet_service.controller;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vivek.wallet_service.dto.RiskAssessmentRequest;
import com.vivek.wallet_service.dto.RiskAssessmentResponse;
import com.vivek.wallet_service.dto.AiRiskAssessmentResponse;
import com.vivek.wallet_service.service.AiRiskAssessmentClient;
import com.vivek.wallet_service.service.RiskAssessmentService;

@RestController
@RequestMapping("/risk")
public class RiskAssessmentController {

    private final RiskAssessmentService riskAssessmentService;
    private final AiRiskAssessmentClient aiRiskAssessmentClient;

    public RiskAssessmentController(
            RiskAssessmentService riskAssessmentService,
            AiRiskAssessmentClient aiRiskAssessmentClient
    ) {
        this.riskAssessmentService = riskAssessmentService;
        this.aiRiskAssessmentClient = aiRiskAssessmentClient;
    }

    @PostMapping("/assess-transfer")
    public RiskAssessmentResponse assessTransfer(@RequestBody RiskAssessmentRequest request) {
        return riskAssessmentService.assessTransfer(
                getCurrentUserEmail(),
                request.getToEmail(),
                request.getAmount()
        );
    }

    @PostMapping("/assess-transfer-ai")
    public AiRiskAssessmentResponse assessTransferWithAi(@RequestBody RiskAssessmentRequest request) {
        RiskAssessmentResponse riskAssessment = riskAssessmentService.assessTransfer(
                getCurrentUserEmail(),
                request.getToEmail(),
                request.getAmount()
        );

        return aiRiskAssessmentClient.explainRisk(riskAssessment);
    }

    private String getCurrentUserEmail() {
        return (String) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
    }
}

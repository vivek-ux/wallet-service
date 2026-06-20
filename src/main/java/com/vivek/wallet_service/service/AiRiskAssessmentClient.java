package com.vivek.wallet_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.vivek.wallet_service.dto.AiRiskAssessmentResponse;
import com.vivek.wallet_service.dto.RiskAssessmentResponse;

@Service
public class AiRiskAssessmentClient {

    private final RestClient restClient;

    public AiRiskAssessmentClient(
            RestClient.Builder restClientBuilder,
            @Value("${ai.risk-service.url:http://localhost:8001}") String riskServiceUrl
    ) {
        this.restClient = restClientBuilder
                .baseUrl(riskServiceUrl)
                .build();
    }

    public AiRiskAssessmentResponse explainRisk(RiskAssessmentResponse riskAssessment) {
        return restClient
                .post()
                .uri("/assess-risk")
                .body(riskAssessment)
                .retrieve()
                .body(AiRiskAssessmentResponse.class);
    }
}

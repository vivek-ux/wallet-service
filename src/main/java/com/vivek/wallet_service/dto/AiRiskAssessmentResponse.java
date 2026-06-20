package com.vivek.wallet_service.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiRiskAssessmentResponse {

    private int riskScore;
    private String riskLevel;
    private String recommendedAction;
    private String summary;
    private List<String> policyReasoning;
    private List<PolicyReference> policyReferences;
    private List<String> nextSteps;

    @Getter
    @Setter
    public static class PolicyReference {
        private String title;
        private String excerpt;
    }
}

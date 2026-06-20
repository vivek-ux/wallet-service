from decimal import Decimal

from pydantic import BaseModel, Field


class RiskContext(BaseModel):
    riskScore: int
    riskLevel: str
    recommendedAction: str
    requestedAmount: Decimal
    currentBalance: Decimal
    remainingBalance: Decimal
    recentTransferCount: int
    reasons: list[str]


class PolicyReference(BaseModel):
    title: str = Field(description="Short title for the policy evidence.")
    excerpt: str = Field(description="Relevant policy excerpt or paraphrase.")


class AiRiskAssessmentResponse(BaseModel):
    riskScore: int
    riskLevel: str
    recommendedAction: str
    summary: str
    policyReasoning: list[str]
    policyReferences: list[PolicyReference]
    nextSteps: list[str]

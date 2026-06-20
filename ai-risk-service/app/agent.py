import json
import os

from langchain_core.prompts import ChatPromptTemplate
from langchain_openai import ChatOpenAI

from app.rag import retrieve_policy_context
from app.schemas import AiRiskAssessmentResponse, RiskContext


def assess_risk_with_rag(risk_context: RiskContext) -> AiRiskAssessmentResponse:
    policy_context = retrieve_policy_context(_build_retrieval_query(risk_context))

    llm = ChatOpenAI(
        model=os.getenv("OPENAI_MODEL", "gpt-4o-mini"),
        temperature=0,
    ).with_structured_output(AiRiskAssessmentResponse)

    prompt = ChatPromptTemplate.from_messages(
        [
            (
                "system",
                (
                    "You are a wallet risk analyst. Use the deterministic wallet "
                    "metrics as source of truth and the retrieved policy context "
                    "as supporting guidance. Do not invent facts. Return a concise "
                    "structured risk assessment."
                ),
            ),
            (
                "human",
                (
                    "Deterministic risk context:\n{risk_context}\n\n"
                    "Retrieved policy context:\n{policy_context}"
                ),
            ),
        ]
    )

    messages = prompt.invoke(
        {
            "risk_context": risk_context.model_dump_json(indent=2),
            "policy_context": policy_context,
        }
    )

    return llm.invoke(messages)


def _build_retrieval_query(risk_context: RiskContext) -> str:
    return json.dumps(
        {
            "riskLevel": risk_context.riskLevel,
            "recommendedAction": risk_context.recommendedAction,
            "reasons": risk_context.reasons,
            "requestedAmount": str(risk_context.requestedAmount),
            "remainingBalance": str(risk_context.remainingBalance),
            "recentTransferCount": risk_context.recentTransferCount,
        }
    )

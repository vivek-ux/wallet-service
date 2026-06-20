from dotenv import load_dotenv
from fastapi import FastAPI

from app.agent import assess_risk_with_rag
from app.schemas import AiRiskAssessmentResponse, RiskContext

load_dotenv()

app = FastAPI(title="Wallet AI Risk Service")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/assess-risk", response_model=AiRiskAssessmentResponse)
def assess_risk(risk_context: RiskContext) -> AiRiskAssessmentResponse:
    return assess_risk_with_rag(risk_context)

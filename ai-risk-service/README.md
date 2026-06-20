# Wallet AI Risk Service

Small FastAPI service that uses LangChain + Chroma RAG to explain wallet transfer risk assessments.

## Run locally

```bash
cd ai-risk-service
cp .env.example .env
uv sync
uv run uvicorn app.main:app --reload --port 8001
```

Spring Boot expects this service at `http://localhost:8001` by default.

## Endpoint

```http
POST /assess-risk
```

The Spring Boot wallet service sends deterministic risk metrics. This service retrieves relevant policy snippets and returns structured AI reasoning.

# LexScope Agent Capability Matrix

LexScope Agent is maintained as a focused portfolio project for legal RAG and Agent engineering.

| Capability | What It Shows | Evidence |
|---|---|---|
| Legal RAG | PDF upload, chunking, embedding, vector search, citation-grounded answers | `/ai/pdf/upload/{chatId}`, `/ai/pdf/chat`, ingestion jobs |
| Agent workflow | ReAct-style reasoning, streaming output, workflow trace, branch sessions | Frontend console and workflow APIs |
| Security | API Key to JWT, refresh token, tenant header, RBAC-style permission checks | Auth APIs and Spring Security config |
| Deployment | Docker Compose full stack with backend, frontend, MySQL, Redis, RabbitMQ | `docker-compose.yml`, Windows scripts |
| Observability | Health checks, metrics, logs, tracing configuration | Actuator, Prometheus/Loki/Tempo config |
| Portfolio polish | Legal domain positioning, README, prompts, example questions | README, frontend copy, default prompts |

## Project Positioning

The project demonstrates how a general RAG system can be turned into a vertical legal knowledge product without rebuilding the entire architecture. The first stage keeps the core ingestion, retrieval, security, and model-calling flow stable, while changing the product layer to focus on civil and commercial law case analysis.

## Resume-Friendly Summary

LexScope Agent is a full-stack legal AI platform based on Spring AI and Vue. It supports OpenAI-compatible models, PDF legal knowledge ingestion, embedding generation, vector retrieval, citation-grounded answers, JWT authentication, Docker deployment, and Windows one-click startup scripts.

# LexScope Agent Documentation

LexScope Agent is a civil and commercial law case analysis and regulation retrieval Agent platform. It packages a Spring AI RAG backend, Vue console, PDF ingestion, vector retrieval, citation-grounded answers, JWT/API key security, Docker deployment, and observability into a runnable portfolio-grade system.

![LexScope Agent architecture](assets/architecture-overview.svg)

## Start Here

| Goal | Document |
|---|---|
| Understand the project | [Project README](../README.md) |
| Run it locally | [Getting Started](getting-started.md) |
| Try the demo flow | [Demo Script](demo-script.md) |
| Call APIs manually | [API Recipes](api-recipes.md) |
| Understand architecture | [Enterprise Architecture](architecture-enterprise.md) |
| Operate the stack | [Operations Manual](operations.md) |

## Recommended First Test

1. Prepare `.env.demo` with an OpenAI-compatible chat model and embedding model.
2. Start the stack with Docker Compose or `scripts/start_windows.ps1`.
3. Open `http://localhost:8088`.
4. Exchange the local API key for a JWT.
5. Upload a civil/commercial law PDF.
6. Ask a question and check whether the answer includes citations or evidence snippets.

## Runtime Links

| Surface | URL |
|---|---|
| Frontend console | `http://localhost:8088` |
| Backend API | `http://localhost:8080` |
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| Health | `http://localhost:8080/actuator/health` |
| RabbitMQ console | `http://localhost:15672` |

## Platform Capabilities

| Area | Coverage |
|---|---|
| Legal RAG | PDF upload, async ingestion, vector retrieval, citation-grounded answers |
| Agent workflow | ReAct-style chat, streaming output, branch/session UI |
| Security | API Key, JWT, refresh token, tenant isolation, audit logging |
| Deployment | Docker Compose, Windows PowerShell scripts, local reproducibility |
| Observability | Health checks, metrics, logs, Tempo/Loki/Prometheus configuration |

## Repository

Source repository: [ZephyrWn/lexscope-agent](https://github.com/ZephyrWn/lexscope-agent)

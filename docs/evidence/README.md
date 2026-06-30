# LexScope Agent Evidence Pack

This pack lists the shortest evidence path for reviewing LexScope Agent as a runnable legal RAG/Agent platform.

## Runtime Evidence

- Local startup: `docker compose --env-file .env.demo up --build -d`
- Windows startup: `scripts/start_windows.ps1`
- Frontend console: `http://localhost:8088`
- Backend API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`

## Product Evidence

- Legal console branding: frontend title, sidebar, menu, prompts, example questions.
- PDF/RAG chain: upload PDF, wait for ingestion, ask a document-grounded question.
- Citation behavior: answer should include source, chunk, evidence snippet, or document basis.
- Streaming behavior: `/ai/pdf/chat` keeps `Flux<String>` streaming output.

## Verification Checklist

- The Docker stack starts successfully.
- JWT can be obtained from the local API key.
- Normal chat can call the configured OpenAI-compatible model.
- PDF ingestion finishes successfully.
- Embedding vectors are generated with the configured embedding model.
- RAG answers cite uploaded document content.
- `.env`, `.env.demo`, API keys, tokens, build outputs, and logs are not committed.

## Portfolio Evidence

LexScope Agent demonstrates:

- Spring Boot and Spring Security backend engineering.
- Spring AI model integration and streaming response handling.
- RAG ingestion and retrieval flow.
- Vue 3 frontend productization.
- Docker Compose deployment.
- Windows PowerShell automation.

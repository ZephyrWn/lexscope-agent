# Contributing

Thanks for your interest in improving LexScope Agent.

This repository is currently maintained as a personal portfolio project for a civil and commercial law RAG/Agent platform. Contributions should keep the project focused on legal knowledge retrieval, case analysis, citation-grounded answers, and deployable full-stack engineering.

## Getting Started

### Prerequisites

- JDK 17+
- Maven 3.9+
- Node.js 18+ for the frontend
- Docker and Docker Compose for the full stack

### Backend

```bash
mvn -B -ntp test
mvn -B -ntp compile
```

### Frontend

```bash
cd frontend
npm install
npm run build
npm run lint
```

### Full Stack

```bash
docker compose --env-file .env.demo up --build -d
```

On Windows, prefer:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start_windows.ps1
```

## Commit Guidelines

- Branch from `main`.
- Keep changes small and focused.
- Use conventional commit messages:
  - `feat: ...`
  - `fix: ...`
  - `docs: ...`
  - `refactor: ...`
  - `chore: ...`
  - `test: ...`

## Scope Guidelines

- Do not commit `.env`, `.env.demo`, API keys, JWTs, private URLs, logs, dependency folders, or build outputs.
- Do not upgrade core framework versions unless the task explicitly asks for it.
- Keep RAG, ingestion, security, and model-calling changes minimal and testable.
- Update README or docs whenever setup, API usage, or visible product behavior changes.

## Security

For security-sensitive issues, do not publish secrets in issues, commits, screenshots, or logs. Rotate any exposed API key immediately.

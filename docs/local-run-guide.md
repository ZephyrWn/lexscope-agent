# Local Run Guide

## Requirements

- JDK 17+
- Maven 3.9+
- Docker Desktop
- Docker Compose
- Node.js 18+ if running the frontend outside Docker

## Quick Start

```bash
git clone https://github.com/ZephyrWn/lexscope-agent.git
cd lexscope-agent
docker compose --env-file .env.demo up --build -d
```

On Windows, use:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup_windows.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\start_windows.ps1
```

## Environment

Create `.env.demo` from `.env.example`, then fill:

```env
OPENAI_BASE_URL=your-openai-compatible-base-url
OPENAI_API_KEY=your-api-key
OPENAI_MODEL=your-chat-model
EMBEDDING_MODEL=your-embedding-model
```

Do not commit `.env`, `.env.demo`, tokens, JWTs, or private API endpoints.

## URLs

| Service | URL |
|---|---|
| Frontend console | `http://localhost:8088` |
| Backend API | `http://localhost:8080` |
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` |
| RabbitMQ console | `http://localhost:15672` |

## Manual Backend Run

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## Manual Frontend Run

```bash
cd frontend
npm install
npm run dev
```

## Smoke Test

1. Open the frontend console.
2. Exchange the local API key for a JWT.
3. Send a normal chat question.
4. Upload a legal PDF.
5. Wait for ingestion to finish.
6. Ask a question about the PDF.
7. Confirm the answer includes citation or evidence information.

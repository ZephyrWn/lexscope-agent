# LexScope Agent Demo Paths

This document lists short demo paths for showing LexScope Agent as a civil and commercial law RAG/Agent platform.

## Path 1: Legal Case PDF RAG

**Goal:** prove that a legal PDF can be uploaded, embedded, retrieved, and cited.

1. Start the local stack.
2. Open `http://localhost:8088`.
3. Exchange the local API key for a JWT.
4. Upload a small civil/commercial law PDF.
5. Wait for ingestion to complete.
6. Ask: `这个案件的核心争议焦点是什么？`
7. Confirm the answer includes source, chunk, or evidence references.

## Path 2: Civil And Commercial Law Consultation

**Goal:** show the chat console as a legal analysis workspace.

Example questions:

- `房屋租赁合同解除纠纷中，出租方需要重点证明哪些事实？`
- `担保合同纠纷中，保证期间和诉讼时效有什么区别？`
- `请按争议焦点、裁判规则、风险提示三部分输出分析。`

Expected result:

- The assistant answers in a legal-analysis style.
- It distinguishes document evidence from model reasoning.
- It avoids fabricating statutes, case numbers, or citations.

## Path 3: Agent Trace And Workflow

**Goal:** show that the system is not just a single chat endpoint.

1. Use the ReAct/Workflow chat entry.
2. Ask a question that needs retrieval or structured reasoning.
3. Check the returned trace, branch session, or workflow status.
4. Confirm the frontend can show the analysis process.

## Path 4: Local Deployment Proof

**Goal:** show that the project can be reproduced on a Windows machine.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup_windows.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\start_windows.ps1
```

Check:

- Frontend: `http://localhost:8088`
- Backend: `http://localhost:8080`
- Swagger: `http://localhost:8080/swagger-ui/index.html`
- Docker containers are healthy

# Changelog

All notable changes to this project are documented in this file.

## [Unreleased]

### Added
- 新增项目维护约定：后续每次代码修改完成后，都需要同步输出固定格式的“变更报告”，说明目标、改动文件、具体改动、实现效果、验证方式、面试表达、风险与后续优化。
- 新增 `ARCHITECTURE.md`，用普通语言记录系统整体流程、前后端模块作用、数据流和部署结构，方便后续继续维护项目上下文。
- 新增 `INTERVIEW_NOTES.md`，沉淀项目亮点、难点、优化点、可复述表达和后续改进方向，服务简历和面试讲述。

### Changed
- 将法律智能问答首屏从“三步说明卡片 + 多个能力入口”改成轻量聊天引导：只保留标题、一句短说明、上传法律资料按钮、格式提示、直接输入问题入口和 3 个快捷问题。
- 默认欢迎语缩短为“请上传法律资料，或直接输入你想分析的问题。”；问题概括、争议焦点、相关依据等结果模块不再在初始空页面提前展示，只在真实回答中出现。
- 新增前端上传入口调用封装，首屏“上传法律资料”按钮复用已有 `/ai/pdf/upload/{chatId}` 上传接口；TXT 会读取到输入框辅助提问，Word 文件给出另存 PDF 的友好提示。
- 去掉左侧“我的记录”区域外层容器边框和背景，只保留单条会话卡片边界，让侧栏更轻、更接近聊天产品的历史列表。
- 修复“实时输出”看起来没有流式效果的问题：后端 ReAct 流式接口在已有完整答案时也会按小段切成 SSE token 输出，并调整 Nginx 代理避免关闭 chunked 传输。
- 本地 `.env` 的 RabbitMQ 演示账号从 `guest/guest` 调整为 Docker Compose 默认演示账号，避免 prod profile 启动校验拒绝应用启动。
- 为普通解释类法律问题增加快答路径：不涉及文档、引用、法条、案例、报告时跳过 ReAct 规划和检索，直接用一次模型调用回答，降低简单问题等待时间。
- 放大左侧记录搜索框高度和文字尺寸，统一圆角、边框和聚焦态，使它与“新建问答”按钮和整体视觉风格更一致。
- 移除设置“数据管理”里的“清空当前会话”按钮，避免高风险操作出现在普通用户设置面板中；底层清空函数保留，后续如需可加二次确认后再开放。
- 左侧历史问答卡片隐藏内部会话标识（如 sessionId/chatId/uuid 截断值）和分组字段，只展示标题与“今天 HH:mm”形式的更新时间；置顶、归档、删除按钮改为更轻的文字按钮。
- 重构设置面板为“基础设置 / 数据管理 / 高级设置 / 关于系统”：默认直接展示模型 API Key、保存设置、恢复默认和使用说明；云端同步、历史版本、分组、记录筛选、效果评测和用量统计默认折叠隐藏。
- 将“服务密钥”改为“模型 API Key”，把“保存到云端”调整为“保存至云端”，让设置文案更贴近普通用户理解。
- “清空当前会话”不再显示在设置面板中，避免误触影响当前问答内容。
- 调整左侧栏顺序为“搜索记录标题或 id”在上、“新建问答”在下、“我的记录”列表继续往下；新建问答默认命名为“新对话”，并按最新更新时间显示在记录顶端，交互更接近 ChatGPT。
- 将左侧“历史版本”整块移入设置的“高级设置”；另存为新版本、对比上一版、采用当前版本和版本列表全部保留，但不再占用普通用户侧栏空间。
- 左侧“我的记录”区域只保留“搜索记录标题或 ID”输入框；分组筛选、归档显示、从云端读取和保存至云端统一移入设置折叠区，继续降低侧栏信息密度。
- 移除聊天输入框下方的四个快捷提示按钮（生成报告、概括争议、整理依据、提示风险），减少输入区下方的按钮堆叠，让用户更专注于自己输入问题。
- 将聊天消息里的助手名称从“法律问答助手”简化为“法律助手”，让角色名更短、更自然，也减少和产品标题“法律智能问答”的重复。
- 主内容头部只保留深浅色主题切换；分组切换、新建分组、归档显示等低频管理动作统一移入设置，减少普通问答界面的按钮噪声。
- 基础设置默认使用本地演示模型 API Key；即使浏览器缓存里没有密钥或留有旧登录状态，也会优先使用默认模型 API Key，用户打开页面即可直接提问。
- 将设置里的“保存并连接 / 刷新连接 / 清空”调整为“保存设置 / 恢复默认”，弱化登录和连接概念，保留 API 配置窗口但不把它作为首次使用门槛。
- 移除左侧四宫格主菜单（智能问答、上传资料、我的记录、生成报告），减少重复入口，让侧栏只保留新建问答、记录、同步和历史版本等高频区域。
- 将“历史版本”下的三个操作按钮调整为一行三列等宽排列，避免第三个按钮掉到下一行导致侧栏不整齐。
- 整理设置里的“开发者工具”面板：统一按钮、下拉框、输入框宽度，移除重复的深浅色切换，避免“打开效果评测”等控件超出容器。
- 统一前端主按钮颜色：将 Element Plus 默认亮蓝主按钮改为产品墨蓝主色，使“新建问答”“保存至云端”“发送”等关键按钮在视觉上保持一致。
- 进一步修复“历史版本”区域窄侧栏排版：标题改为独占一行显示“历史版本 · 数量”，操作按钮固定在下一行，避免标题在任何宽度下被拆成“历史版 / 本”。
- 将聊天消息中的英文角色名 `Assistant / You` 改为“法律助手 / 我”，并移除消息左侧头像，让回答区域更像正式法律文本阅读区。
- 将默认欢迎语里的技术化表达进一步改成普通用户能理解的法律问答说明，不再提示 API Key、JWT 等开发者概念。
- 修复左侧“历史版本”区域的标题和操作按钮挤在一起的问题：标题与数量单独成行，另存、对比、采用按钮放到下一行，避免“历史版本”被压成两行。
- 将“回答质量”和“实时输出”从设置/侧栏偏好区移动到聊天输入框内部，改成类似 ChatGPT 网页的底部工具条，用户提问时可以直接切换回答模式和输出方式。
- 前端普通用户可见定位从偏“开发控制台 / Agent 平台”调整为“法律智能问答”，首页围绕上传资料、输入问题、生成法律分析展开。
- 左侧侧栏改为轻量结构，保留新建问答和我的记录，将云端同步、历史版本、效果评测、模型 API Key、使用范围等功能收进设置或折叠区。
- 回答区域统一使用“问题概括、争议焦点、相关依据、参考案例、初步分析、引用来源、风险提示”等更贴合法律场景的中文模块。
- 前端视觉从偏工程控制台的冷色和技术术语，调整为更清晰的现代应用风格：系统无衬线字体、细边框、浅灰背景、低噪声卡片，并保留少量墨蓝强调。

### Notes
- 本轮前端产品化与风格调整未改后端接口、数据库结构或核心业务逻辑。

## [1.0.0] - 2026-04-28

### Added
- Redis Stream ingestion queue with DLQ, retry re-enqueue, and multi-worker concurrency.
- RabbitMQ ingestion queue backend with dedicated queue/DLX/DLQ declarations and concurrent listeners.
- pgvector formal migration and rollback script.
- API key lifecycle (issue/rotate/revoke/expiry) and JWT refresh token flow.
- Permission-granular security routing and audit log retention scheduler.
- RAG chunking, reranking, multi-document fusion, and answer citations.
- Observability stack templates (Prometheus, Loki, Tempo, Alertmanager, Promtail).
- OpenAPI integration, load testing scripts, large nightly evaluation pipeline.
- ReAct agent endpoints (`/ai/react/chat`, `/ai/react/chat/stream`) with trace payload and SSE events.
- Vue3 + TypeScript + Element Plus frontend console with Markdown rendering, dark mode, responsive layout, and ReAct trace view.
- Nginx reverse-proxy web service in Docker Compose for one-command full-stack startup.
- Development demo admin API key seed (`dev-admin-key-2026`) for local authentication walkthrough.
- Fast Maven test lane plus separate `integration-test` profile for container-backed smoke tests.
- Stream-based SHA-256 hashing utility and PDF safety scanner tests.
- Flyway migration `V9` for tenant isolation on `conversation` and `ingestion_job`.
- PostgreSQL pgvector tenant-aware metadata indexes (`tenant_id`, `tenant_id + chat_id`).

### Changed
- PDF ingestion switched from DB polling loop to queue-driven worker model.
- API key rotation now rotates by stable `keyName` (active key semantics) instead of generating ad-hoc names.
- Vector store backend defaults tuned toward pgvector production path.
- Project naming and runtime identifiers aligned to enterprise platform terminology (`knowledgeops-agent`).
- README and docs upgraded to enterprise deployment/architecture focused documentation set.
- Application security now defaults to enabled outside the development profile.
- Automatic ingestion idempotency keys now use file content hash instead of filename and size.
- PDF safety scanning now reads only the file header and validates PDF magic bytes before ingestion.
- Frontend production build now separates Vue, Element Plus, and Markdown/highlight dependencies into vendor chunks.
- Chat history, chat memory, ingestion job APIs, and PDF download/list operations are now tenant-scoped (`tenant_id`) to prevent cross-tenant data bleed.
- RAG retrieval filter is now tenant-aware (`tenant_id && chat_id`) and ingestion metadata includes `tenant_id`.
- ReAct stream endpoint now emits true model token streaming instead of synthetic answer chunk splitting.
- Cost budget update endpoint now falls back to request tenant header when `tenantId` is omitted in payload.
- Ingestion operational metrics now include tenant tags for submitted/finished/duration series.

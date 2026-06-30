package com.enterprise.iqk.agent.workflow;

import com.enterprise.iqk.agent.harness.AgentAction;
import com.enterprise.iqk.agent.harness.AgentHarnessService;
import com.enterprise.iqk.domain.vo.ReactChatRequestVO;
import com.enterprise.iqk.domain.vo.ReactChatResponseVO;
import com.enterprise.iqk.domain.vo.ReactTraceStepVO;
import com.enterprise.iqk.llm.ModelRouter;
import com.enterprise.iqk.security.TenantContext;
import com.enterprise.iqk.service.TenantCostService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class WorkflowReactAgentService {

    private static final int MAX_STEPS = 6;

    private final AgentWorkflowEngine workflowEngine;
    private final AgentHarnessService agentHarnessService;
    private final ChatClient chatClient;
    private final ModelRouter modelRouter;
    private final TenantCostService tenantCostService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public ReactChatResponseVO chat(ReactChatRequestVO request) {
        validateRequest(request);
        String tenantId = currentTenantId();
        long startedNs = System.nanoTime();

        AgentTaskRecord task = workflowEngine.startTask(
                tenantId, "REACT", request.getPrompt(),
                request.getModelProfile(), request.getChatId(), null);

        ModelRouter.ModelRouteDecision routeDecision = resolveRouteDecision(
                request.getModelProfile(), "react", request.getChatId(), tenantId);

        if (shouldUseFastAnswer(request)) {
            try {
                String answer = fastAnswer(request, routeDecision, tenantId);
                workflowEngine.completeTask(task.getTaskId(), WorkflowState.DONE, answer);
                workflowEngine.recordTaskMetrics("REACT", "DONE", elapsedMs(startedNs));
                return success(request.getChatId(), answer, List.of(), routeDecision, task.getTaskId());
            } catch (RuntimeException e) {
                workflowEngine.failTask(task.getTaskId(), e.getMessage());
                workflowEngine.recordTaskMetrics("REACT", "FAILED", elapsedMs(startedNs));
                throw e;
            }
        }

        List<ReactTraceStepVO> trace = new ArrayList<>();
        String rollingContext = "";

        try {
            for (int stepNum = 1; stepNum <= MAX_STEPS; stepNum++) {
                AgentStepRecord stepRecord = workflowEngine.startStep(
                        task.getTaskId(), "planner", stepNum,
                        Map.of("prompt", request.getPrompt(), "rollingContext", rollingContext));

                long stepStartNs = System.nanoTime();
                ReasonDecision decision = reason(request, rollingContext, trace, routeDecision, tenantId);

                if ("finish".equals(decision.action())) {
                    String answer = StringUtils.hasText(decision.answer())
                            ? decision.answer()
                            : summarizeAnswer(request, trace, rollingContext, routeDecision, tenantId);
                    Map<String, Object> obs = new LinkedHashMap<>();
                    obs.put("status", "completed");
                    obs.put("citations", decision.citations() != null ? decision.citations() : List.of());
                    obs.put("evidence", decision.evidence() != null ? decision.evidence() : List.of());

                    trace.add(buildTraceStep(stepNum, decision, obs));
                    workflowEngine.completeStep(stepRecord.getStepId(), "COMPLETED",
                            Map.of("answer", answer), obs,
                            decision.thought(), "finish", decision.actionInput(),
                            0, 0, elapsedMs(stepStartNs), null);
                    workflowEngine.completeTask(task.getTaskId(), WorkflowState.DONE, answer);
                    workflowEngine.recordTaskMetrics("REACT", "DONE", elapsedMs(startedNs));
                    return success(request.getChatId(), answer, trace, routeDecision, task.getTaskId());
                }

                workflowEngine.transitionStatus(task.getTaskId(),
                        mapToWorkflowState(stepNum), mapToWorkflowState(stepNum + 1));

                Object observation = executeAction(request, decision.action(), decision.actionInput(), tenantId,
                        task.getTaskId(), stepRecord.getStepId());
                trace.add(buildTraceStep(stepNum, decision, observation));
                workflowEngine.completeStep(stepRecord.getStepId(), "COMPLETED",
                        null, observation,
                        decision.thought(), decision.action(), decision.actionInput(),
                        0, 0, elapsedMs(stepStartNs), null);

                rollingContext = appendContext(rollingContext, decision.action(), observation);
            }

            String answer = summarizeAnswer(request, trace, rollingContext, routeDecision, tenantId);
            workflowEngine.completeTask(task.getTaskId(), WorkflowState.DONE, answer);
            workflowEngine.recordTaskMetrics("REACT", "DONE", elapsedMs(startedNs));
            return success(request.getChatId(), answer, trace, routeDecision, task.getTaskId());

        } catch (RuntimeException e) {
            workflowEngine.failTask(task.getTaskId(), e.getMessage());
            workflowEngine.recordTaskMetrics("REACT", "FAILED", elapsedMs(startedNs));
            throw e;
        }
    }

    public Flux<String> stream(ReactChatRequestVO request) {
        long startedNs = System.nanoTime();
        AtomicReference<Long> firstTokenMs = new AtomicReference<>(null);
        AtomicReference<String> outcomeRef = new AtomicReference<>("error");

        return Flux.defer(() -> {
            validateRequest(request);
            String tenantId = currentTenantId();

            AgentTaskRecord task = workflowEngine.startTask(
                    tenantId, "REACT_STREAM", request.getPrompt(),
                    request.getModelProfile(), request.getChatId(), null);

            ModelRouter.ModelRouteDecision routeDecision = resolveRouteDecision(
                    request.getModelProfile(), "react", request.getChatId(), tenantId);

            if (shouldUseFastAnswer(request)) {
                return streamFastAnswer(request, routeDecision, tenantId, task.getTaskId(), startedNs, firstTokenMs, outcomeRef);
            }

            List<ReactTraceStepVO> trace = new ArrayList<>();
            String rollingContext = "";
            String directAnswer = "";

            for (int stepNum = 1; stepNum <= MAX_STEPS; stepNum++) {
                AgentStepRecord stepRecord = workflowEngine.startStep(
                        task.getTaskId(), "planner", stepNum,
                        Map.of("prompt", request.getPrompt()));

                long stepStartNs = System.nanoTime();
                ReasonDecision decision = reason(request, rollingContext, trace, routeDecision, tenantId);

                if ("finish".equals(decision.action())) {
                    Map<String, Object> obs = new LinkedHashMap<>();
                    obs.put("status", "completed");
                    trace.add(buildTraceStep(stepNum, decision, obs));
                    directAnswer = emptyIfBlank(decision.answer());
                    workflowEngine.completeStep(stepRecord.getStepId(), "COMPLETED",
                            Map.of("answer", directAnswer), obs,
                            decision.thought(), "finish", decision.actionInput(),
                            0, 0, elapsedMs(stepStartNs), null);
                    break;
                }

                Object observation = executeAction(request, decision.action(), decision.actionInput(), tenantId,
                        task.getTaskId(), stepRecord.getStepId());
                trace.add(buildTraceStep(stepNum, decision, observation));
                workflowEngine.completeStep(stepRecord.getStepId(), "COMPLETED",
                        null, observation,
                        decision.thought(), decision.action(), decision.actionInput(),
                        0, 0, elapsedMs(stepStartNs), null);
                rollingContext = appendContext(rollingContext, decision.action(), observation);
            }

            Flux<String> traceFlux = Flux.fromIterable(trace)
                    .map(step -> formatSse("trace", toJson(step)));

            StringBuilder answerBuilder = new StringBuilder();
            Flux<String> answerFlux = StringUtils.hasText(directAnswer)
                    ? streamAnswerChunks(directAnswer)
                    : callModelStream(
                    "你是 LexScope Agent，请结合研判轨迹和观察信息给出专业、审慎、可追溯的民商法分析。",
                    buildFinalPrompt(request, trace, rollingContext),
                    routeDecision, tenantId, "react_final");

            Flux<String> tokenFlux = answerFlux
                    .map(token -> {
                        if (firstTokenMs.get() == null) {
                            firstTokenMs.set(elapsedMs(startedNs));
                        }
                        answerBuilder.append(token);
                        return formatSse("token", toJson(Map.of("token", token)));
                    });

            String finalTaskId = task.getTaskId();
            return Flux.concat(traceFlux, tokenFlux)
                    .concatWith(Flux.defer(() -> {
                        if (firstTokenMs.get() == null) {
                            firstTokenMs.set(elapsedMs(startedNs));
                        }
                        String answer = answerBuilder.toString();
                        ReactChatResponseVO response = success(
                                request.getChatId(), answer, trace, routeDecision, finalTaskId);
                        workflowEngine.completeTask(finalTaskId, WorkflowState.DONE, answer);
                        outcomeRef.set("success");
                        return Flux.just(formatSse("done", toJson(response)));
                    }));
        })
                .onErrorResume(ex -> {
                    String message = StringUtils.hasText(ex.getMessage())
                            ? ex.getMessage() : "stream failed";
                    return Flux.just(formatSse("error", toJson(Map.of("message", message))));
                })
                .doFinally(signal -> recordStreamMetrics(startedNs, firstTokenMs.get(), outcomeRef.get()));
    }

    // ── Reason / Action / Summarize (same logic, now with engine) ──

    private ReasonDecision reason(ReactChatRequestVO request,
                                  String rollingContext,
                                  List<ReactTraceStepVO> trace,
                                  ModelRouter.ModelRouteDecision routeDecision,
                                  String tenantId) {
        String planningPrompt = "You are a ReAct planner for LexScope Agent, a civil and commercial law case analysis and regulation retrieval assistant.%nPrefer rag_search when the user asks about cases, statutes, contracts, legal rules, citations, PDF documents, or knowledge base evidence.%nYou must choose exactly one action for the next step.%n%nAllowed actions:%n- query_school%n- query_course%n- add_course_reservation%n- rag_search%n- finish%n%nReturn JSON only:%n{%n  \"thought\": \"short reasoning\",%n  \"action\": \"one action from list\",%n  \"action_input\": {\"key\":\"value\"},%n  \"answer\": \"only provide when action is finish\"%n}%n%nUser question:%n%s%n%nRolling context:%n%s%n%nExisting trace:%n%s%n".formatted(request.getPrompt(), emptyIfBlank(rollingContext), toJson(trace));

        try {
            String raw = callModel("You are strict JSON ReAct planner. Return valid JSON only.",
                    planningPrompt, routeDecision, tenantId, "react_planner");
            return parseDecision(raw);
        } catch (RuntimeException ex) {
            return fallbackDecision(request.getPrompt());
        }
    }

    private Map<String, Object> executeAction(ReactChatRequestVO request,
                                              String action,
                                              Map<String, Object> actionInput,
                                              String tenantId,
                                              String taskId,
                                              String stepId) {
        return agentHarnessService.execute(new AgentAction(
                action,
                actionInput,
                request.getPrompt(),
                tenantId,
                request.getChatId(),
                request.getModelProfile(),
                taskId,
                stepId
        )).toMap();
    }

    private boolean shouldUseFastAnswer(ReactChatRequestVO request) {
        String prompt = emptyIfBlank(request == null ? "" : request.getPrompt());
        String normalized = prompt.toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(normalized) || normalized.length() > 100) {
            return false;
        }

        if (containsAny(normalized,
                "上传", "材料", "文档", "pdf", "附件", "检索", "引用", "来源", "出处",
                "法条", "法规", "案例", "判决", "裁判", "类案", "争议焦点", "研判报告",
                "生成报告", "报告", "根据以下", "如下", "证据", "依据", "合同条款")) {
            return false;
        }

        return containsAny(normalized,
                "是什么", "什么意思", "怎么理解", "区别", "能否", "是否", "可以", "如何",
                "怎么", "为什么", "有哪些", "责任", "风险", "期限", "违约", "解除",
                "赔偿", "合同", "租赁", "公司", "股权", "担保", "借款", "买卖");
    }

    private String fastAnswer(ReactChatRequestVO request,
                              ModelRouter.ModelRouteDecision routeDecision,
                              String tenantId) {
        String answer = callModel(
                "你是法律智能问答助手，请用中文给出简明、审慎、可执行的初步法律解释。",
                buildFastAnswerPrompt(request),
                routeDecision,
                tenantId,
                "react_fast"
        );
        return StringUtils.hasText(answer) ? answer : "当前未能生成答案，请稍后重试。";
    }

    private Flux<String> streamFastAnswer(ReactChatRequestVO request,
                                          ModelRouter.ModelRouteDecision routeDecision,
                                          String tenantId,
                                          String taskId,
                                          long startedNs,
                                          AtomicReference<Long> firstTokenMs,
                                          AtomicReference<String> outcomeRef) {
        StringBuilder answerBuilder = new StringBuilder();
        return callModelStream(
                "你是法律智能问答助手，请用中文给出简明、审慎、可执行的初步法律解释。",
                buildFastAnswerPrompt(request),
                routeDecision,
                tenantId,
                "react_fast"
        )
                .map(token -> {
                    if (firstTokenMs.get() == null) {
                        firstTokenMs.set(elapsedMs(startedNs));
                    }
                    answerBuilder.append(token);
                    return formatSse("token", toJson(Map.of("token", token)));
                })
                .concatWith(Flux.defer(() -> {
                    if (firstTokenMs.get() == null) {
                        firstTokenMs.set(elapsedMs(startedNs));
                    }
                    String answer = answerBuilder.toString();
                    ReactChatResponseVO response = success(request.getChatId(), answer, List.of(), routeDecision, taskId);
                    workflowEngine.completeTask(taskId, WorkflowState.DONE, answer);
                    outcomeRef.set("success");
                    return Flux.just(formatSse("done", toJson(response)));
                }));
    }

    private String buildFastAnswerPrompt(ReactChatRequestVO request) {
        return """
                用户问题:
                %s

                请直接回答这个普通法律问题，要求：
                1. 先给出简明结论；
                2. 再说明关键判断因素；
                3. 不要编造具体法条编号、案号或引用来源；
                4. 如果需要结合材料才能判断，请提醒用户补充合同、判决书或其他法律资料。
                """.formatted(emptyIfBlank(request.getPrompt()));
    }

    private String summarizeAnswer(ReactChatRequestVO request, List<ReactTraceStepVO> trace,
                                    String rollingContext, ModelRouter.ModelRouteDecision routeDecision,
                                    String tenantId) {
        String finalPrompt = buildFinalPrompt(request, trace, rollingContext);
        try {
            String answer = callModel("你是 LexScope Agent，请结合研判轨迹和观察信息给出专业、审慎、可追溯的民商法分析。",
                    finalPrompt, routeDecision, tenantId, "react_final");
            if (StringUtils.hasText(answer)) return answer;
        } catch (RuntimeException ignored) {}
        return "当前未能生成最终答案，请稍后重试。";
    }

    private String buildFinalPrompt(ReactChatRequestVO request,
                                     List<ReactTraceStepVO> trace, String rollingContext) {
        return "用户问题:%n%s%n%n研判轨迹:%n%s%n%n观察上下文:%n%s%n%n请输出最终中文答案，要求结构清晰、依据可追溯；必要时按“事实、争议焦点、裁判规则、法律依据、风险提示”组织。%n".formatted(request.getPrompt(), toJson(trace), emptyIfBlank(rollingContext));
    }

    // ── Helpers (delegated from original ReactAgentService) ──────

    private ReactTraceStepVO buildTraceStep(int step, ReasonDecision d, Object obs) {
        return ReactTraceStepVO.builder()
                .step(step).thought(d.thought()).action(d.action())
                .actionInput(d.actionInput()).observation(obs).build();
    }

    private ReactChatResponseVO success(String chatId, String answer,
                                         List<ReactTraceStepVO> trace,
                                         ModelRouter.ModelRouteDecision routeDecision,
                                         String taskId) {
        List<String> citations = extractTraceStrings(trace, "citations");
        List<String> evidence = extractTraceStrings(trace, "evidence");
        return ReactChatResponseVO.builder()
                .ok(1).msg("ok").chatId(chatId)
                .answer(attachCitationFooter(answer, citations))
                .citations(citations).evidence(evidence)
                .routeProfile(routeDecision == null ? "" : routeDecision.profile())
                .routeReason(routeDecision == null ? "" : routeDecision.reason())
                .routeCostTier(routeDecision == null ? "" : routeDecision.costTier())
                .experimentKey(routeDecision == null ? "" : routeDecision.experimentKey())
                .experimentVariant(routeDecision == null ? "" : routeDecision.experimentVariant())
                .experimentBucket(routeDecision == null ? null : routeDecision.experimentBucket())
                .trace(trace)
                .build();
    }

    private ModelRouter.ModelRouteDecision resolveRouteDecision(String profile, String endpoint,
                                                                 String subjectKey, String tenantId) {
        return modelRouter.resolve(profile, endpoint, tenantId, subjectKey);
    }

    private String callModel(String system, String user, ModelRouter.ModelRouteDecision decision,
                              String tenantId, String endpointTag) {
        long inputTokens = tenantCostService.estimateTokens(system)
                + tenantCostService.estimateTokens(user);
        tenantCostService.assertBudget(tenantId, decision.costTier(), inputTokens, 600);
        String output = chatClient.prompt()
                .options(ChatOptions.builder().model(decision.model()).build())
                .system(system).user(user).call().content();
        long outputTokens = tenantCostService.estimateTokens(output);
        tenantCostService.recordUsage(tenantId, decision.costTier(), inputTokens, outputTokens, endpointTag);
        return output;
    }

    private Flux<String> callModelStream(String system, String user,
                                          ModelRouter.ModelRouteDecision decision,
                                          String tenantId, String endpointTag) {
        long inputTokens = tenantCostService.estimateTokens(system)
                + tenantCostService.estimateTokens(user);
        tenantCostService.assertBudget(tenantId, decision.costTier(), inputTokens, 600);
        StringBuilder collector = new StringBuilder();
        AtomicBoolean recorded = new AtomicBoolean(false);
        return chatClient.prompt()
                .options(ChatOptions.builder().model(decision.model()).build())
                .system(system).user(user).stream().content()
                .doOnNext(collector::append)
                .doFinally(sig -> {
                    if (!recorded.compareAndSet(false, true)) return;
                    long out = tenantCostService.estimateTokens(collector.toString());
                    tenantCostService.recordUsage(tenantId, decision.costTier(), inputTokens, out, endpointTag);
                });
    }

    private Flux<String> streamAnswerChunks(String answer) {
        String text = emptyIfBlank(answer);
        if (!StringUtils.hasText(text)) {
            return Flux.empty();
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();
        int codePointCount = 0;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            chunk.appendCodePoint(codePoint);
            offset += Character.charCount(codePoint);
            codePointCount++;

            if (codePointCount >= 3 || isNaturalStreamBreak(codePoint)) {
                chunks.add(chunk.toString());
                chunk.setLength(0);
                codePointCount = 0;
            }
        }
        if (chunk.length() > 0) {
            chunks.add(chunk.toString());
        }
        return Flux.fromIterable(chunks).delayElements(Duration.ofMillis(24));
    }

    private boolean isNaturalStreamBreak(int codePoint) {
        return Character.isWhitespace(codePoint) || "，。；！？、,.!?;:\n".indexOf(codePoint) >= 0;
    }

    private WorkflowState mapToWorkflowState(int step) {
        return switch (step) {
            case 1 -> WorkflowState.SEARCHING;
            case 2 -> WorkflowState.RETRIEVING;
            case 3 -> WorkflowState.JUDGING;
            case 4 -> WorkflowState.REFLECTING;
            default -> WorkflowState.WRITING;
        };
    }

    // ── Delegated helpers (same as original ReactAgentService) ───

    private ReasonDecision parseDecision(String raw) {
        String json = extractJson(raw);
        if (!StringUtils.hasText(json)) {
            return new ReasonDecision("Fallback to finish.", "finish",
                    Collections.emptyMap(), emptyIfBlank(raw), List.of(), List.of());
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            String action = normalizeAction(node.path("action").asText("finish"));
            Map<String, Object> input = objectMapper.convertValue(
                    node.path("action_input"), new TypeReference<Map<String, Object>>() {});
            if (input == null) input = Collections.emptyMap();
            if (!List.of("query_school", "query_course", "add_course_reservation", "rag_search", "finish")
                    .contains(action)) action = "finish";
            return new ReasonDecision(node.path("thought").asText(""),
                    action, input, node.path("answer").asText(""), List.of(), List.of());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return new ReasonDecision("Parse failed.", "finish",
                    Collections.emptyMap(), emptyIfBlank(raw), List.of(), List.of());
        }
    }

    private ReasonDecision fallbackDecision(String prompt) {
        String safe = emptyIfBlank(prompt).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(safe)) {
            return new ReasonDecision("Empty prompt.", "finish", Collections.emptyMap(),
                    "当前请求内容为空，请补充问题后重试。",
                    List.of("source=fallback://input_validation, chunk=1"),
                    List.of("规则兜底：空问题时引导用户补充输入。"));
        }
        if (containsAny(safe, "案情", "争议焦点", "裁判规则", "法规", "法条", "合同", "租赁",
                "民法典", "案例", "判决", "裁判", "case", "statute", "contract", "lease")) {
            return new ReasonDecision("Fallback legal rag.", "rag_search",
                    Map.of("query", prompt), "", List.of(), List.of());
        }
        if (containsAny(safe, "校区", "campus")) {
            return new ReasonDecision("Fallback school query.", "finish", Collections.emptyMap(),
                    "已识别为校区查询请求：可以返回校区列表，并按城市或课程类型做进一步筛选。",
                    List.of("source=fallback://school_query_flow, chunk=1"),
                    List.of("校区查询流程：先列出校区，再按城市/课程类型筛选。"));
        }
        if (containsAny(safe, "课程预约", "预约字段", "预约需要", "联系方式", "姓名")) {
            return new ReasonDecision("Fallback reservation.", "finish", Collections.emptyMap(),
                    "课程预约建议至少包含：课程、姓名、联系方式、校区。",
                    List.of("source=fallback://course_reservation_schema, chunk=1"),
                    List.of("预约字段模板。"));
        }
        if (containsAny(safe, "知识库", "引用", "来源", "pdf", "文档", "source")) {
            return new ReasonDecision("Fallback rag.", "rag_search",
                    Map.of("query", prompt), "", List.of(), List.of());
        }
        return new ReasonDecision("Generic fallback.", "finish", Collections.emptyMap(),
                "当前规划器暂不可用，建议稍后重试或细化问题关键词。",
                List.of("source=fallback://planner_unavailable, chunk=1"),
                List.of("系统兜底。"));
    }

    private String extractJson(String raw) {
        if (!StringUtils.hasText(raw)) return "";
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        return (start < 0 || end <= start) ? "" : raw.substring(start, end + 1);
    }

    private List<String> extractTraceStrings(List<ReactTraceStepVO> trace, String key) {
        if (trace == null || trace.isEmpty()) return List.of();
        Set<String> values = new LinkedHashSet<>();
        for (ReactTraceStepVO step : trace) {
            if (!(step.getObservation() instanceof Map<?, ?> obs)) continue;
            Object raw = obs.get(key);
            if (raw instanceof List<?> list) {
                for (Object item : list) {
                    String s = emptyIfBlank(String.valueOf(item));
                    if (StringUtils.hasText(s)) values.add(s);
                }
            }
        }
        return List.copyOf(values);
    }

    private String attachCitationFooter(String answer, List<String> citations) {
        if (citations == null || citations.isEmpty()) return emptyIfBlank(answer);
        if (emptyIfBlank(answer).contains("引用来源")) return answer;
        StringBuilder sb = new StringBuilder(emptyIfBlank(answer).trim());
        if (sb.length() > 0) sb.append("\n\n");
        sb.append("引用来源:\n");
        for (int i = 0; i < citations.size(); i++) {
            sb.append("[").append(i + 1).append("] ").append(citations.get(i)).append("\n");
        }
        return sb.toString().trim();
    }

    private String formatSse(String event, String data) {
        return "event: " + event + "\ndata: " + data + "\n\n";
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { return "{\"message\":\"serialization_failed\"}"; }
    }

    private String appendContext(String origin, String action, Object observation) {
        StringBuilder sb = new StringBuilder(emptyIfBlank(origin));
        if (sb.length() > 0) sb.append("\n");
        sb.append("action=").append(action).append(", observation=").append(toJson(observation));
        return sb.toString();
    }

    private void recordStreamMetrics(long startedNs, Long firstTokenMs, String outcome) {
        long total = elapsedMs(startedNs);
        Timer.builder("react.stream.total.latency").tag("outcome", outcome)
                .publishPercentileHistogram().register(meterRegistry)
                .record(total, TimeUnit.MILLISECONDS);
        if (firstTokenMs != null) {
            Timer.builder("react.stream.first_token.latency").tag("outcome", outcome)
                    .publishPercentileHistogram().register(meterRegistry)
                    .record(firstTokenMs, TimeUnit.MILLISECONDS);
        }
        Counter.builder("react.stream.requests").tag("outcome", outcome)
                .register(meterRegistry).increment();
    }

    // ── Trivial delegates ──────────────────────────────────────────

    private String normalizeAction(String a) {
        return (!StringUtils.hasText(a)) ? "finish" : a.trim().toLowerCase(Locale.ROOT);
    }
    private long elapsedMs(long startedNs) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs); }
    private String emptyIfBlank(String v) { return StringUtils.hasText(v) ? v : ""; }
    private boolean containsAny(String text, String... keywords) {
        if (!StringUtils.hasText(text) || keywords == null) return false;
        for (String kw : keywords) {
            if (StringUtils.hasText(kw) && text.contains(kw.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }
    private String currentTenantId() { return TenantContext.normalize(MDC.get(TenantContext.TENANT_REQUEST_ATTRIBUTE)); }
    private void validateRequest(ReactChatRequestVO r) {
        if (r == null || !StringUtils.hasText(r.getPrompt())) throw new IllegalArgumentException("prompt is required");
        if (!StringUtils.hasText(r.getChatId())) throw new IllegalArgumentException("chatId is required");
    }

    private record ReasonDecision(String thought, String action,
                                   Map<String, Object> actionInput, String answer,
                                   List<String> citations, List<String> evidence) {}
}

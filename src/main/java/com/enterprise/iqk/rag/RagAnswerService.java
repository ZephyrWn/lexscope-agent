package com.enterprise.iqk.rag;

import com.enterprise.iqk.config.properties.RagProperties;
import com.enterprise.iqk.constants.SystemConstants;
import com.enterprise.iqk.llm.ModelRouter;
import com.enterprise.iqk.security.TenantContext;
import com.enterprise.iqk.service.TenantCostService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;

@Service
@RequiredArgsConstructor
public class RagAnswerService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final ModelRouter modelRouter;
    private final RagProperties ragProperties;
    private final MeterRegistry meterRegistry;
    private final TenantCostService tenantCostService;

    public RagResult answer(String prompt, String tenantId, String chatId, String conversationId, String modelProfile) {
        Timer.Sample pipelineSample = Timer.start(meterRegistry);
        String pipelineOutcome = "error";

        try {
            String normalizedTenantId = TenantContext.normalize(tenantId);
            String filterExpression = "tenant_id == '" + sanitizeFilterValue(normalizedTenantId) + "' && chat_id == '"
                    + sanitizeFilterValue(chatId) + "'";
            SearchRequest request = SearchRequest.builder()
                    .query(prompt)
                    .topK(ragProperties.getRetrieveTopK())
                    .similarityThreshold(ragProperties.getSimilarityThreshold())
                    .filterExpression(filterExpression)
                    .build();

            List<Document> retrieved = similaritySearchWithMetrics(request);
            if (retrieved == null || retrieved.isEmpty()) {
                pipelineOutcome = "empty";
                return RagResult.builder()
                        .answer("没有在当前知识库中检索到可用内容。")
                        .citations(List.of())
                        .evidence(List.of("未检索到匹配文档，请先上传资料或调整检索词。"))
                        .build();
            }

            List<Document> reranked = rerankWithMetrics(prompt, retrieved);
            List<Document> selected = reranked.stream()
                    .limit(Math.max(1, ragProperties.getRerankTopK()))
                    .toList();

            String context = buildContext(selected);
            ModelRouter.ModelRouteDecision decision = modelRouter.resolve(modelProfile, "rag", normalizedTenantId, chatId);
            long inputTokens = tenantCostService.estimateTokens(prompt + "\n" + context);
            tenantCostService.assertBudget(normalizedTenantId, decision.costTier(), inputTokens, 600);
            String answer = chatClient.prompt()
                    .options(ChatOptions.builder().model(decision.model())
                            .temperature(ragProperties.getTemperature()).build())
                    .system(SystemConstants.RAG_ANSWER_SYSTEM)
                    .user("用户问题:%n%s%n%n上下文:%n%s%n".formatted(prompt, context))
                    .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId))
                    .call()
                    .content();
            long outputTokens = tenantCostService.estimateTokens(answer);
            tenantCostService.recordUsage(normalizedTenantId, decision.costTier(), inputTokens, outputTokens, "rag");

            List<CitationReference> references = buildReferences(selected);
            List<String> evidence = selected.stream()
                    .map(this::evidenceText)
                    .toList();

            pipelineOutcome = "success";
            return RagResult.builder()
                    .answer(sanitizeAnswer(answer))
                    .citations(references)
                    .evidence(evidence)
                    .build();
        } finally {
            pipelineSample.stop(Timer.builder("rag.pipeline.latency")
                    .description("Overall latency for RAG answer pipeline")
                    .tag("outcome", pipelineOutcome)
                    .publishPercentileHistogram()
                    .register(meterRegistry));
            Counter.builder("rag.pipeline.requests")
                    .description("Total number of RAG pipeline requests")
                    .tag("outcome", pipelineOutcome)
                    .register(meterRegistry)
                    .increment();
        }
    }

    private List<Document> similaritySearchWithMetrics(SearchRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "error";
        try {
            List<Document> docs = vectorStore.similaritySearch(request);
            outcome = (docs == null || docs.isEmpty()) ? "empty" : "success";
            return docs;
        } finally {
            sample.stop(Timer.builder("rag.retrieval.latency")
                    .description("Latency of vector similarity search")
                    .tag("outcome", outcome)
                    .publishPercentileHistogram()
                    .register(meterRegistry));
            Counter.builder("rag.retrieval.requests")
                    .description("Number of vector retrieval requests")
                    .tag("outcome", outcome)
                    .register(meterRegistry)
                    .increment();
        }
    }

    private List<Document> rerankWithMetrics(String prompt, List<Document> docs) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "error";
        try {
            List<Document> reranked = rerank(prompt, docs);
            outcome = reranked.isEmpty() ? "empty" : "success";
            return reranked;
        } finally {
            sample.stop(Timer.builder("rag.rerank.latency")
                    .description("Latency of local rerank stage")
                    .tag("outcome", outcome)
                    .publishPercentileHistogram()
                    .register(meterRegistry));
        }
    }

    private List<Document> rerank(String prompt, List<Document> docs) {
        Set<String> promptTokens = tokenize(prompt);
        return docs.stream()
                .sorted((a, b) -> Double.compare(scoreDoc(promptTokens, b), scoreDoc(promptTokens, a)))
                .collect(Collectors.toList());
    }

    private double scoreDoc(Set<String> promptTokens, Document doc) {
        Set<String> docTokens = tokenize(documentText(doc));
        if (docTokens.isEmpty()) {
            return 0.0;
        }
        long overlap = promptTokens.stream().filter(docTokens::contains).count();
        return (double) overlap / docTokens.size();
    }

    private Set<String> tokenize(String text) {
        if (!StringUtils.hasText(text)) {
            return Set.of();
        }
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{Nd}]+"))
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    private String buildContext(List<Document> docs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            Document d = docs.get(i);
            sb.append("[").append(i + 1).append("] ")
                    .append(contextCitationText(d))
                    .append("\n")
                    .append(documentText(d))
                    .append("\n\n");
        }
        return sb.toString();
    }

    private String contextCitationText(Document d) {
        String fileName = metadataString(d, "file_name", "未知文件");
        Integer pageNumber = pageNumber(d);
        StringBuilder builder = new StringBuilder("文件: ").append(fileName);
        if (pageNumber != null && pageNumber > 0) {
            builder.append("，页码: 第 ").append(pageNumber).append(" 页");
        }
        return builder.toString();
    }

    private List<CitationReference> buildReferences(List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }
        return java.util.stream.IntStream.range(0, docs.size())
                .mapToObj(index -> toReference(docs.get(index), index + 1))
                .toList();
    }

    private CitationReference toReference(Document doc, int index) {
        Map<String, Object> metadata = doc.getMetadata();
        String fileName = metadataString(doc, "file_name", "未知文件");
        Integer pageNumber = pageNumber(doc);
        String snippet = truncate(evidenceText(doc), 80);
        Map<String, Object> debug = new LinkedHashMap<>();
        putDebug(debug, "tenant_id", metadata.get("tenant_id"));
        putDebug(debug, "chat_id", metadata.get("chat_id"));
        putDebug(debug, "job_id", metadata.get("job_id"));
        putDebug(debug, "source_type", metadata.get("source_type"));
        putDebug(debug, "chunk_index", metadata.get("chunk_index"));
        putDebug(debug, "distance", metadata.get("distance"));
        return CitationReference.builder()
                .index(index)
                .fileName(fileName)
                .pageNumber(pageNumber)
                .snippet(snippet)
                .debug(debug)
                .build();
    }

    private void putDebug(Map<String, Object> debug, String key, Object value) {
        if (value != null) {
            debug.put(key, value);
        }
    }

    private String metadataString(Document doc, String key, String fallback) {
        Object value = doc.getMetadata().get(key);
        String text = value == null ? "" : String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : fallback;
    }

    private Integer pageNumber(Document doc) {
        for (String key : List.of("page_number", "pageNumber", "page", "page_index")) {
            Object value = doc.getMetadata().get(key);
            Integer parsed = parseInteger(value);
            if (parsed != null && "page_index".equals(key) && parsed >= 0) {
                return parsed + 1;
            }
            if (parsed != null && parsed > 0) {
                return parsed;
            }
        }
        return null;
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            String digits = String.valueOf(value).replaceAll("[^0-9]", "");
            if (!StringUtils.hasText(digits)) {
                return null;
            }
            return Integer.parseInt(digits);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String evidenceText(Document d) {
        String raw = documentText(d);
        if (raw.length() <= 180) {
            return raw;
        }
        return raw.substring(0, 180) + "...";
    }

    private String documentText(Document d) {
        String raw = emptyIfBlank(d.getFormattedContent()).replaceAll("\\s+", " ").trim();
        return cleanMetadataText(raw);
    }

    private String cleanMetadataText(String value) {
        return emptyIfBlank(value)
                .replaceAll("(?i)\\b(?:tenant_id|chat_id|job_id|source|source_type|file_name|filename|fileName|chunk_index|page_number|pageNumber|page_index|distance)\\s*[:=]\\s*[^\\s,，;；]+", " ")
                .replaceAll("(?i)\\b(?:source|chunk|chunk_index)\\s*=\\s*[^\\s,，;；]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String truncate(String value, int maxChars) {
        String raw = emptyIfBlank(value).replaceAll("\\s+", " ").trim();
        if (raw.length() <= maxChars) {
            return raw;
        }
        return raw.substring(0, maxChars) + "...";
    }

    private String sanitizeAnswer(String answer) {
        String cleaned = emptyIfBlank(answer)
                .replaceAll("(?im)^\\s*(?:tenant_id|chat_id|job_id|source|source_type|file_name|filename|fileName|chunk_index|page_number|pageNumber|page_index|distance)\\s*[:=].*$", "")
                .replaceAll("(?im)^\\s*\\[?\\d*\\]?\\s*source\\s*=.*(?:chunk|chunk_index)\\s*=.*$", "")
                .replaceAll("(?i)source\\s*=\\s*[^,，\\n]+[,，]\\s*(?:chunk|chunk_index)\\s*=\\s*[^\\s，,。；;]+", "")
                .trim();
        return cleaned.replaceAll("(?is)\\n{2,}(?:---\\s*)?(引用来源|参考来源)[:：].*$", "").trim();
    }

    private String emptyIfBlank(String value) {
        return StringUtils.hasText(value) ? value : "";
    }

    private String sanitizeFilterValue(String value) {
        return emptyIfBlank(value).replace("'", "");
    }

    @Data
    @Builder
    public static class CitationReference {
        private Integer index;
        private String fileName;
        private Integer pageNumber;
        private String snippet;
        private Map<String, Object> debug;
    }

    @Data
    @Builder
    public static class RagResult {
        private String answer;
        private List<CitationReference> citations;
        private List<String> evidence;
    }
}

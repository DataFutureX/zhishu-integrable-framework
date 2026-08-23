package cn.datafuturex.zhishu.ai.knowledge.service.impl;

import cn.datafuturex.zhishu.ai.platform.ai.AiConfig;
import cn.datafuturex.zhishu.ai.shared.exception.BusinessException;
import cn.datafuturex.zhishu.ai.modelconfig.api.ModelConfigPort;
import cn.datafuturex.zhishu.ai.knowledge.service.EmbeddingService;
import cn.datafuturex.zhishu.ai.knowledge.service.QuestionAnsweringService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 文档问答：向量检索 + 无 Tools 的流式 ChatClient（规避 Spring AI 2.0.0 流式 Tool 缺陷）
 */
@Service
@Slf4j
public class QuestionAnsweringServiceImpl implements QuestionAnsweringService {

    private final ChatClient streamChatClient;
    private final EmbeddingService embeddingService;
    private final ModelConfigPort aiModelConfigService;

    public QuestionAnsweringServiceImpl(
            @Qualifier(AiConfig.STREAM_CHAT_CLIENT) ChatClient streamChatClient,
            EmbeddingService embeddingService,
            ModelConfigPort aiModelConfigService) {
        this.streamChatClient = streamChatClient;
        this.embeddingService = embeddingService;
        this.aiModelConfigService = aiModelConfigService;
    }

    @Override
    public String answerFromAllDocuments(String question, Integer topK, String conversationId) {
        log.info("开始基于所有文档回答问题: {}", question);
        List<Document> relevantDocs = embeddingService.similaritySearch(question, topK != null ? topK : 5);
        if (relevantDocs.isEmpty()) {
            return "抱歉，我在文档库中未找到与您的问题相关的内容。";
        }
        return generateAnswer(question, buildContext(relevantDocs), conversationId);
    }

    @Override
    public String answerFromDocument(String question, Long documentId, Integer topK, String conversationId) {
        log.info("开始基于指定文档回答问题: 文档ID={}, 问题={}", documentId, question);
        String filterExpression = "documentId == '" + documentId + "'";
        List<Document> relevantDocs = embeddingService.similaritySearchWithFilter(
                question, filterExpression, topK != null ? topK : 5);
        if (relevantDocs.isEmpty()) {
            return "抱歉，在指定的文档中未找到与您的问题相关的内容。";
        }
        return generateAnswer(question, buildContext(relevantDocs), conversationId);
    }

    @Override
    public String answerFromDocuments(String question, List<Long> documentIds, Integer topK, String conversationId) {
        if (documentIds == null || documentIds.isEmpty()) {
            return "该知识库暂无已向量化的文档，请先上传并处理文档。";
        }
        if (documentIds.size() == 1) {
            return answerFromDocument(question, documentIds.get(0), topK, conversationId);
        }
        String filterExpression = buildDocumentIdFilter(documentIds);
        log.info("开始基于知识库文档集回答问题: 文档数={}, 问题={}", documentIds.size(), question);
        List<Document> relevantDocs = embeddingService.similaritySearchWithFilter(
                question, filterExpression, topK != null ? topK : 5);
        if (relevantDocs.isEmpty()) {
            return "抱歉，在所选知识库中未找到与您的问题相关的内容。";
        }
        return generateAnswer(question, buildContext(relevantDocs), conversationId);
    }

    @Override
    public Flux<ServerSentEvent<String>> streamFromAllDocuments(String question, Integer topK,
                                                                String conversationId) {
        String cid = resolveConversationId(conversationId);
        return Flux.defer(() -> {
                    List<Document> relevantDocs = embeddingService.similaritySearch(
                            question, topK != null ? topK : 5);
                    if (relevantDocs.isEmpty()) {
                        return Flux.just(
                                ServerSentEvent.<String>builder()
                                        .event("message")
                                        .data("抱歉，我在文档库中未找到与您的问题相关的内容。")
                                        .build(),
                                doneEvent(cid));
                    }
                    return streamGenerateAnswer(question, buildContext(relevantDocs), cid)
                            .concatWith(Flux.just(doneEvent(cid)));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<ServerSentEvent<String>> streamFromDocument(String question, Long documentId, Integer topK,
                                                            String conversationId) {
        String cid = resolveConversationId(conversationId);
        return Flux.defer(() -> {
                    String filterExpression = "documentId == '" + documentId + "'";
                    List<Document> relevantDocs = embeddingService.similaritySearchWithFilter(
                            question, filterExpression, topK != null ? topK : 5);
                    if (relevantDocs.isEmpty()) {
                        return Flux.just(
                                ServerSentEvent.<String>builder()
                                        .event("message")
                                        .data("抱歉，在指定的文档中未找到与您的问题相关的内容。")
                                        .build(),
                                doneEvent(cid));
                    }
                    return streamGenerateAnswer(question, buildContext(relevantDocs), cid)
                            .concatWith(Flux.just(doneEvent(cid)));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<ServerSentEvent<String>> streamFromDocuments(String question, List<Long> documentIds, Integer topK,
                                                             String conversationId) {
        if (documentIds == null || documentIds.isEmpty()) {
            String cid = resolveConversationId(conversationId);
            return Flux.just(
                    ServerSentEvent.<String>builder()
                            .event("message")
                            .data("该知识库暂无已向量化的文档，请先上传并处理文档。")
                            .build(),
                    doneEvent(cid));
        }
        if (documentIds.size() == 1) {
            return streamFromDocument(question, documentIds.get(0), topK, conversationId);
        }
        String cid = resolveConversationId(conversationId);
        String filterExpression = buildDocumentIdFilter(documentIds);
        return Flux.defer(() -> {
                    List<Document> relevantDocs = embeddingService.similaritySearchWithFilter(
                            question, filterExpression, topK != null ? topK : 5);
                    if (relevantDocs.isEmpty()) {
                        return Flux.just(
                                ServerSentEvent.<String>builder()
                                        .event("message")
                                        .data("抱歉，在所选知识库中未找到与您的问题相关的内容。")
                                        .build(),
                                doneEvent(cid));
                    }
                    return streamGenerateAnswer(question, buildContext(relevantDocs), cid)
                            .concatWith(Flux.just(doneEvent(cid)));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String buildDocumentIdFilter(List<Long> documentIds) {
        return documentIds.stream()
                .filter(Objects::nonNull)
                .map(id -> "documentId == '" + id + "'")
                .collect(Collectors.joining(" || "));
    }

    private String buildContext(List<Document> documents) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            String fileName = (String) doc.getMetadata().getOrDefault("fileName", "未知文档");
            Integer chunkIndex = (Integer) doc.getMetadata().getOrDefault("chunkIndex", 0);
            context.append("--- 片段 ").append(i + 1).append(" ---\n");
            context.append("来源: ").append(fileName).append(" (第").append(chunkIndex + 1).append("部分)\n");
            context.append(doc.getText()).append("\n\n");
        }
        return context.toString();
    }

    private String generateAnswer(String question, String context, String conversationId) {
        try {
            String cid = resolveConversationId(conversationId);
            return streamChatClient.prompt()
                    .system(systemPrompt(context))
                    .user(question)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, cid))
                    .options(runtimeOptions())
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("生成答案失败: {}", e.getMessage(), e);
            throw new BusinessException("AI问答失败: " + e.getMessage());
        }
    }

    private Flux<ServerSentEvent<String>> streamGenerateAnswer(String question, String context,
                                                               String conversationId) {
        return streamChatClient.prompt()
                .system(systemPrompt(context))
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .options(runtimeOptions())
                .stream()
                .content()
                .map(chunk -> ServerSentEvent.<String>builder().event("message").data(chunk).build())
                .doOnComplete(() -> log.info("流式文档问答完成"))
                .doOnError(error -> log.error("流式文档问答失败: {}", error.getMessage(), error));
    }

    private OpenAiChatOptions.Builder runtimeOptions() {
        return OpenAiChatOptions.builder()
                .model(aiModelConfigService.currentChatModel())
                .temperature(aiModelConfigService.currentTemperature())
                .maxTokens(aiModelConfigService.currentMaxTokens());
    }

    private static String systemPrompt(String context) {
        return """
                你是一位专业的文档问答助手。请基于提供的上下文信息回答用户的问题。

                回答规则：
                1. 严格基于上下文信息回答问题，不要编造内容
                2. 如果上下文中没有足够信息，请明确说明"根据现有文档，无法找到相关答案"
                3. 回答要准确、简洁、专业
                4. 引用相关信息时，可以提及来源文档
                5. 使用中文回复

                %s
                %s
                """.formatted(
                cn.datafuturex.zhishu.ai.knowledge.api.HybridRetrievalPort.KNOWLEDGE_CONTEXT_HEADER,
                context);
    }

    private static ServerSentEvent<String> doneEvent(String conversationId) {
        return ServerSentEvent.<String>builder().event("done").data(conversationId).build();
    }

    private static String resolveConversationId(String conversationId) {
        return StringUtils.hasText(conversationId) ? conversationId.trim() : UUID.randomUUID().toString();
    }
}

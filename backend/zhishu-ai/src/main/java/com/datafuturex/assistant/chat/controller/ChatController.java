package com.datafuturex.assistant.chat.controller;

import com.datafuturex.assistant.agent.api.AgentChatPort;
import com.datafuturex.assistant.shared.Result;
import com.datafuturex.assistant.shared.UserContext;
import com.datafuturex.assistant.shared.dto.ChatRequestDTO;
import com.datafuturex.assistant.shared.dto.ChatStructuredRequestDTO;
import com.datafuturex.assistant.shared.vo.ChatResponseVO;
import com.datafuturex.assistant.chat.service.AiChatService;
import com.datafuturex.assistant.modelconfig.api.ModelConfigPort;
import com.datafuturex.assistant.chat.service.ChatObservationService;
import com.datafuturex.assistant.chat.service.QaHistoryService;
import com.datafuturex.assistant.shared.chat.ChatScenes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * AI 聊天控制器（Memory / RAG / 结构化）
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI 聊天", description = "多轮对话、流式、结构化输出")
public class ChatController {

    private final AiChatService aiChatService;
    private final QaHistoryService qaHistoryService;
    private final ChatObservationService chatObservationService;
    private final ModelConfigPort aiModelConfigService;
    private final AgentChatPort agentChatPort;

    @PostMapping
    @Operation(summary = "发送聊天消息")
    public Result<ChatResponseVO> chat(@Valid @RequestBody ChatRequestDTO request) {
        long start = System.currentTimeMillis();
        boolean ok = false;
        try {
            ChatResponseVO vo = aiChatService.chat(request);
            qaHistoryService.save(
                    UserContext.getUserId(),
                    ChatScenes.CHAT,
                    request.message(),
                    vo.content(),
                    vo.model(),
                    null,
                    vo.conversationId(),
                    vo.agentId());
            ok = true;
            return Result.success(vo);
        } finally {
            chatObservationService.recordChat("sync", ok, System.currentTimeMillis() - start);
        }
    }

    @PostMapping("/cached")
    @Operation(summary = "发送聊天消息（缓存）")
    public Result<ChatResponseVO> cachedChat(@Valid @RequestBody ChatRequestDTO request) {
        long start = System.currentTimeMillis();
        boolean ok = false;
        try {
            ChatResponseVO vo = aiChatService.cachedChat(request);
            qaHistoryService.save(
                    UserContext.getUserId(),
                    ChatScenes.CHAT,
                    request.message(),
                    vo.content(),
                    vo.model(),
                    null,
                    vo.conversationId(),
                    vo.agentId());
            ok = true;
            return Result.success(vo);
        } finally {
            chatObservationService.recordChat("cached", ok, System.currentTimeMillis() - start);
        }
    }

    @PostMapping("/structured")
    @Operation(summary = "结构化聊天（对比/趋势/告警）")
    public Result<ChatResponseVO> structured(@Valid @RequestBody ChatStructuredRequestDTO request) {
        long start = System.currentTimeMillis();
        boolean ok = false;
        try {
            ChatResponseVO vo = aiChatService.structuredChat(request);
            qaHistoryService.save(
                    UserContext.getUserId(),
                    ChatScenes.CHAT,
                    request.message(),
                    vo.content(),
                    vo.model(),
                    null,
                    vo.conversationId(),
                    vo.agentId());
            ok = true;
            return Result.success(vo);
        } finally {
            chatObservationService.recordChat("structured", ok, System.currentTimeMillis() - start);
        }
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式聊天（SSE：progress|trace|message|done）")
    public Flux<ServerSentEvent<String>> streamChat(@Valid @RequestBody ChatRequestDTO request) {
        String userId = UserContext.getUserId();
        String question = request.message();
        StringBuilder answer = new StringBuilder();
        String[] conversationIdHolder = {request.conversationId()};
        Long agentId = request.agentId() != null
                ? request.agentId()
                : agentChatPort.resolveAgentId(null);

        return aiChatService.streamChat(request)
                .doOnNext(event -> {
                    if ("done".equals(event.event()) && event.data() != null) {
                        conversationIdHolder[0] = event.data();
                    } else if ("message".equals(event.event()) && event.data() != null) {
                        answer.append(event.data());
                    }
                })
                .doOnComplete(() -> Mono.fromRunnable(() -> {
                            try {
                                qaHistoryService.save(
                                        userId,
                                        ChatScenes.CHAT,
                                        question,
                                        answer.toString(),
                                        aiModelConfigService.currentChatModel(),
                                        null,
                                        conversationIdHolder[0],
                                        agentId);
                            } catch (Exception e) {
                                log.warn("保存流式问答历史失败: {}", e.getMessage(), e);
                            }
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe());
    }

    @GetMapping("/health")
    @Operation(summary = "健康检查")
    public Result<String> health() {
        return Result.success("AI Chat Service is running");
    }

    @GetMapping("/diagnose")
    @Operation(summary = "诊断 AI 服务")
    public Result<String> diagnose() {
        try {
            String testResponse = aiChatService.ping();
            return Result.success("AI 服务连接正常。响应: " + testResponse
                    + " | capabilities=memory,rag-advisor,tools(station/alert/project/online),structured,hybrid,metrics");
        } catch (Exception e) {
            log.error("AI 服务诊断失败", e);
            return Result.fail("AI 服务连接失败: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    @GetMapping("/metrics")
    @Operation(summary = "对话运行指标快照")
    public Result<?> metrics() {
        return Result.success(chatObservationService.metricsSnapshot());
    }

    @GetMapping("/eval")
    @Operation(summary = "冒烟评测（Memory / Vector / Hybrid）")
    public Result<?> eval() {
        return Result.success(chatObservationService.runSmokeEval());
    }
}

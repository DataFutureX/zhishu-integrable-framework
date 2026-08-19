package com.datafuturex.assistant.openapi.controller;

import com.datafuturex.assistant.agent.api.AgentChatPort;
import com.datafuturex.assistant.chat.service.AiChatService;
import com.datafuturex.assistant.chat.service.QaHistoryService;
import com.datafuturex.assistant.modelconfig.api.ModelConfigPort;
import com.datafuturex.assistant.shared.UserContext;
import com.datafuturex.assistant.shared.chat.ChatScenes;
import com.datafuturex.assistant.shared.dto.ChatRequestDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/open/v1/chat")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "开放 API · 对话")
public class OpenChatController {

    private final AiChatService aiChatService;
    private final QaHistoryService qaHistoryService;
    private final ModelConfigPort aiModelConfigService;
    private final AgentChatPort agentChatPort;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式聊天")
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
                                log.warn("开放 API 保存流式问答历史失败: {}", e.getMessage());
                            }
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe());
    }
}

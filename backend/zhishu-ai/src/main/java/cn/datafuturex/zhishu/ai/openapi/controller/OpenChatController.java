package cn.datafuturex.zhishu.ai.openapi.controller;

import cn.datafuturex.zhishu.ai.agent.api.AgentChatPort;
import cn.datafuturex.zhishu.ai.chat.service.AiChatService;
import cn.datafuturex.zhishu.ai.chat.service.QaHistoryService;
import cn.datafuturex.zhishu.ai.modelconfig.api.ModelConfigPort;
import cn.datafuturex.zhishu.ai.shared.Result;
import cn.datafuturex.zhishu.ai.shared.UserContext;
import cn.datafuturex.zhishu.ai.shared.chat.ChatScenes;
import cn.datafuturex.zhishu.ai.shared.dto.ChatRequestDTO;
import cn.datafuturex.zhishu.ai.shared.vo.ChatResponseVO;
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

    @PostMapping
    @Operation(summary = "同步对话（外部自建简报等后台任务请调此接口，勿再调 /open/v1/briefings）")
    public Result<ChatResponseVO> chat(@Valid @RequestBody ChatRequestDTO request) {
        ChatResponseVO vo = aiChatService.chat(request);
        if (!Boolean.FALSE.equals(request.enableMemory())) {
            try {
                qaHistoryService.save(
                        UserContext.getUserId(),
                        ChatScenes.CHAT,
                        request.message(),
                        vo.content(),
                        vo.model(),
                        null,
                        vo.conversationId(),
                        vo.agentId());
            } catch (Exception e) {
                log.warn("开放 API 保存同步问答历史失败: {}", e.getMessage());
            }
        }
        return Result.success(vo);
    }

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

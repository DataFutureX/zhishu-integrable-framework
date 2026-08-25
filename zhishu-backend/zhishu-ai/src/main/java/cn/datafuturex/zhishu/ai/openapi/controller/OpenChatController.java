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
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
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
    @Operation(summary = "流式聊天（SSE：progress|trace|message|done）",
            description = "progress 事件随工作流节点/工具的开始与结束即时逐条推送，不做缓存；"
                    + "trace 为结束前下发的完整轨迹（兼容旧客户端）；message 为正文增量；done 携带 conversationId")
    public Flux<ServerSentEvent<String>> streamChat(
            @Valid @RequestBody ChatRequestDTO request, HttpServletResponse response) {
        // SSE 直出：禁用代理/网关缓冲与响应缓存，保证节点进度事件产生即对外输出（X-Accel-Buffering 针对 Nginx 类网关）
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        response.setHeader("X-Accel-Buffering", "no");

        String userId = UserContext.getUserId();
        String question = request.message();
        StringBuilder answer = new StringBuilder();
        String[] conversationIdHolder = {request.conversationId()};
        Long agentId = request.agentId() != null
                ? request.agentId()
                : agentChatPort.resolveAgentId(null);

        // progress（工作流节点/工具进度）与 trace 事件由服务端产生即透传下发，全程不累积；
        // 仅 message 分片在此汇总，供流结束后异步落问答历史
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

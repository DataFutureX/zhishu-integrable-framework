package cn.datafuturex.zhishu.ai.openapi.controller;

import cn.datafuturex.zhishu.ai.knowledge.dto.DocumentQueryDTO;
import cn.datafuturex.zhishu.ai.knowledge.service.DocumentQaService;
import cn.datafuturex.zhishu.ai.modelconfig.api.ModelConfigPort;
import cn.datafuturex.zhishu.ai.shared.UserContext;
import cn.datafuturex.zhishu.ai.shared.chat.ChatScenes;
import cn.datafuturex.zhishu.ai.shared.port.QaHistoryPort;
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
@RequestMapping("/open/v1/knowledges/qa")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "开放 API · 知识问答")
public class OpenKnowledgeQaController {

    private final DocumentQaService documentQaService;
    private final QaHistoryPort qaHistoryService;
    private final ModelConfigPort aiModelConfigService;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "文档智能问答（SSE）")
    public Flux<ServerSentEvent<String>> streamAskQuestion(@Valid @RequestBody DocumentQueryDTO queryDTO) {
        String userId = UserContext.getUserId();
        String question = queryDTO.question();
        Long documentId = queryDTO.documentId();
        StringBuilder answer = new StringBuilder();
        String[] conversationIdHolder = {queryDTO.conversationId()};

        return documentQaService.streamAnswerQuestion(queryDTO)
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
                                        ChatScenes.DOCUMENT_QA,
                                        question,
                                        answer.toString(),
                                        aiModelConfigService.currentChatModel() + " + RAG",
                                        documentId,
                                        conversationIdHolder[0]);
                            } catch (Exception e) {
                                log.warn("开放 API 保存文档问答历史失败: {}", e.getMessage());
                            }
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe());
    }
}

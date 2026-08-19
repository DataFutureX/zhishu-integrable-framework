package com.datafuturex.assistant.knowledge.controller;

import com.datafuturex.assistant.shared.Result;
import com.datafuturex.assistant.shared.UserContext;
import com.datafuturex.assistant.knowledge.dto.DocumentQueryDTO;
import com.datafuturex.assistant.shared.vo.ChatResponseVO;
import com.datafuturex.assistant.knowledge.service.DocumentQaService;
import com.datafuturex.assistant.modelconfig.api.ModelConfigPort;
import com.datafuturex.assistant.shared.chat.ChatScenes;
import com.datafuturex.assistant.shared.port.QaHistoryPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 文档问答控制器
 */
@RestController
@RequestMapping("/api/v1/knowledges/qa")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "文档问答", description = "基于上传文档的智能问答接口（RAG）")
public class DocumentQaController {

    private final DocumentQaService documentQaService;
    private final QaHistoryPort qaHistoryService;
    private final ModelConfigPort aiModelConfigService;

    @PostMapping
    @Operation(summary = "文档智能问答", description = "基于已上传的文档进行智能问答（RAG）")
    public Result<ChatResponseVO> askQuestion(@Valid @RequestBody DocumentQueryDTO queryDTO) {
        log.info("收到文档问答请求: {}", queryDTO.question());
        String[] pair = documentQaService.answerQuestion(queryDTO);
        String response = pair[0];
        String conversationId = pair[1];
        ChatResponseVO vo = ChatResponseVO.of(response, aiModelConfigService.currentChatModel() + " + RAG", conversationId);
        qaHistoryService.save(
                ChatScenes.DOCUMENT_QA,
                queryDTO.question(),
                response,
                vo.model(),
                queryDTO.documentId(),
                conversationId);
        return Result.success(vo);
    }

    @GetMapping("/ask-all")
    @Operation(summary = "查询所有文档", description = "在所有已上传文档中检索并回答问题")
    public Result<ChatResponseVO> askAllDocuments(
            @Parameter(description = "用户问题", required = true) @RequestParam String question,
            @Parameter(description = "返回最相关的片段数量") @RequestParam(defaultValue = "5") Integer topK) {
        String response = documentQaService.answerFromAllDocuments(question, topK);
        ChatResponseVO vo = ChatResponseVO.of(response, aiModelConfigService.currentChatModel() + " + RAG", null);
        qaHistoryService.save(ChatScenes.DOCUMENT_QA, question, response, vo.model(), null);
        return Result.success(vo);
    }

    @GetMapping("/ask-document/{documentId}")
    @Operation(summary = "查询指定文档", description = "在指定文档中检索并回答问题")
    public Result<ChatResponseVO> askSpecificDocument(
            @PathVariable Long documentId,
            @RequestParam String question,
            @RequestParam(defaultValue = "5") Integer topK) {
        String response = documentQaService.answerFromDocument(question, documentId, topK);
        ChatResponseVO vo = ChatResponseVO.of(response, aiModelConfigService.currentChatModel() + " + RAG", null);
        qaHistoryService.save(ChatScenes.DOCUMENT_QA, question, response, vo.model(), documentId);
        return Result.success(vo);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "文档智能问答（SSE）", description = "event=message|done")
    public Flux<ServerSentEvent<String>> streamAskQuestion(@Valid @RequestBody DocumentQueryDTO queryDTO) {
        log.info("收到流式文档问答请求: {}", queryDTO.question());
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
                                log.warn("保存流式文档问答历史失败: {}", e.getMessage(), e);
                            }
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe());
    }
}

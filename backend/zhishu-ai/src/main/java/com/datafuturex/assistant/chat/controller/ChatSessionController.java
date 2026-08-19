package com.datafuturex.assistant.chat.controller;

import com.datafuturex.assistant.shared.chat.ChatScenes;
import com.datafuturex.assistant.shared.Result;
import com.datafuturex.assistant.chat.dto.ChatSessionCreateDTO;
import com.datafuturex.assistant.chat.dto.ChatSessionTitleDTO;
import com.datafuturex.assistant.chat.dto.ChatSessionTruncateDTO;
import com.datafuturex.assistant.chat.vo.ChatSessionVO;
import com.datafuturex.assistant.chat.vo.QaHistoryVO;
import com.datafuturex.assistant.chat.service.ChatSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat-sessions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Agent 会话", description = "会话列表、新建、重命名、按会话加载消息")
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    @GetMapping
    @Operation(summary = "会话列表", description = "当前用户指定场景下的会话，按最近更新倒序")
    public Result<List<ChatSessionVO>> list(
            @Parameter(description = "场景：CHAT / DOCUMENT_QA")
            @RequestParam(defaultValue = ChatScenes.CHAT) String scene) {
        return Result.success(chatSessionService.listCurrentUser(scene));
    }

    @PostMapping
    @Operation(summary = "新建会话")
    public Result<ChatSessionVO> create(@RequestBody(required = false) ChatSessionCreateDTO dto) {
        try {
            return Result.success(chatSessionService.create(dto));
        } catch (Exception e) {
            log.error("新建会话失败", e);
            return Result.fail(e.getMessage());
        }
    }

    @PutMapping("/{conversationId}/title")
    @Operation(summary = "修改会话标题")
    public Result<ChatSessionVO> rename(
            @PathVariable String conversationId,
            @Valid @RequestBody ChatSessionTitleDTO dto) {
        try {
            return Result.success(chatSessionService.rename(conversationId, dto));
        } catch (Exception e) {
            log.error("修改会话标题失败: {}", conversationId, e);
            return Result.fail(e.getMessage());
        }
    }

    @DeleteMapping("/{conversationId}")
    @Operation(summary = "删除会话", description = "同时删除该会话下问答历史")
    public Result<Void> delete(@PathVariable String conversationId) {
        try {
            chatSessionService.delete(conversationId);
            return Result.success();
        } catch (Exception e) {
            log.error("删除会话失败: {}", conversationId, e);
            return Result.fail(e.getMessage());
        }
    }

    @PostMapping("/{conversationId}/truncate")
    @Operation(summary = "裁剪后续轮次", description = "保留前 N 轮提问，用于编辑问题再发送")
    public Result<Void> truncate(
            @PathVariable String conversationId,
            @Valid @RequestBody ChatSessionTruncateDTO dto) {
        try {
            chatSessionService.truncateAfterTurns(conversationId, dto.keepUserTurns());
            return Result.success();
        } catch (Exception e) {
            log.error("裁剪会话失败: {}", conversationId, e);
            return Result.fail(e.getMessage());
        }
    }

    @GetMapping("/{conversationId}/messages")
    @Operation(summary = "会话消息", description = "按时间升序返回该会话问答记录")
    public Result<List<QaHistoryVO>> messages(
            @PathVariable String conversationId,
            @RequestParam(required = false) Integer limit) {
        try {
            return Result.success(chatSessionService.listMessages(conversationId, limit));
        } catch (Exception e) {
            log.error("加载会话消息失败: {}", conversationId, e);
            return Result.fail(e.getMessage());
        }
    }
}

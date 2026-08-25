package cn.datafuturex.zhishu.ai.chat.controller;

import cn.datafuturex.zhishu.ai.shared.chat.ChatScenes;
import cn.datafuturex.zhishu.ai.shared.Result;
import cn.datafuturex.zhishu.ai.chat.vo.QaHistoryVO;
import cn.datafuturex.zhishu.ai.chat.service.QaHistoryService;
import cn.datafuturex.zhishu.ai.chat.service.impl.QaHistoryServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 问答历史接口
 */
@RestController
@RequestMapping("/api/v1/qa-history")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "问答历史", description = "记录并查询用户的智能问答 / 知识问答历史")
public class QaHistoryController {

    private final QaHistoryService qaHistoryService;

    @GetMapping
    @Operation(summary = "查询问答历史", description = "按当前用户（X-User-Id）与场景加载历史记录，按时间升序返回")
    public Result<List<QaHistoryVO>> list(
            @Parameter(description = "场景：CHAT / DOCUMENT_QA", example = "CHAT")
            @RequestParam(defaultValue = ChatScenes.CHAT) String scene,
            @Parameter(description = "最多返回条数", example = "200")
            @RequestParam(required = false) Integer limit) {
        return Result.success(qaHistoryService.listCurrentUser(scene, limit));
    }

    @GetMapping("/portal-demo")
    @Operation(summary = "门户对话演示", description = "公开接口：返回智能问答最近 N 条历史（默认 2），供 Portal 原样演示")
    public Result<List<QaHistoryVO>> portalDemo(
            @Parameter(description = "场景：CHAT / DOCUMENT_QA", example = "CHAT")
            @RequestParam(defaultValue = ChatScenes.CHAT) String scene,
            @Parameter(description = "条数，默认 2，最大 10", example = "2")
            @RequestParam(required = false, defaultValue = "2") Integer limit) {
        return Result.success(qaHistoryService.listLatestForPortal(scene, limit));
    }

    @DeleteMapping
    @Operation(summary = "清空问答历史", description = "清空当前用户指定场景的全部历史记录")
    public Result<Void> clear(
            @Parameter(description = "场景：CHAT / DOCUMENT_QA", example = "CHAT")
            @RequestParam(defaultValue = ChatScenes.CHAT) String scene) {
        qaHistoryService.clearCurrentUser(scene);
        return Result.success();
    }
}

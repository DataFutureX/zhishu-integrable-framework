package cn.datafuturex.zhishu.ai.openapi.controller;

import cn.datafuturex.zhishu.ai.agent.domain.vo.AgentVO;
import cn.datafuturex.zhishu.ai.agent.service.AgentDefinitionService;
import cn.datafuturex.zhishu.ai.shared.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 开放 Agent 目录。外部自建简报时先选 Agent，再调 {@code POST /open/v1/chat}。
 */
@RestController
@RequestMapping("/open/v1")
@RequiredArgsConstructor
@Tag(name = "开放 API · Agent")
public class OpenAgentController {

    private final AgentDefinitionService agentDefinitionService;

    @GetMapping("/agents")
    @Operation(summary = "智能体列表（需 chat 权限；外部简报请选 Agent 后调对话接口）")
    public Result<List<AgentVO>> agents(@RequestParam(required = false) String status) {
        return Result.success(agentDefinitionService.list(status));
    }
}

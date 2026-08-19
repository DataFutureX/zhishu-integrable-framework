package com.datafuturex.assistant.agent.controller;

import com.datafuturex.assistant.agent.domain.dto.AgentCreateDTO;
import com.datafuturex.assistant.agent.domain.dto.AgentPromptDraftDTO;
import com.datafuturex.assistant.agent.domain.dto.AgentTrialDTO;
import com.datafuturex.assistant.agent.domain.dto.AgentUpdateDTO;
import com.datafuturex.assistant.agent.domain.dto.GraphSaveDTO;
import com.datafuturex.assistant.agent.domain.entity.AiAgentRunEntity;
import com.datafuturex.assistant.agent.domain.vo.AgentPromptDraftVO;
import com.datafuturex.assistant.agent.domain.vo.AgentRunVO;
import com.datafuturex.assistant.agent.domain.vo.AgentVO;
import com.datafuturex.assistant.agent.domain.vo.CapabilityVO;
import com.datafuturex.assistant.agent.domain.vo.WorkflowTemplateVO;
import com.datafuturex.assistant.agent.graph.GraphValidationResult;
import com.datafuturex.assistant.agent.graph.WorkflowGraph;
import com.datafuturex.assistant.agent.runtime.AgentEngineSelector;
import com.datafuturex.assistant.shared.trace.AgentTraceEvent;
import com.datafuturex.assistant.agent.service.AgentDefinitionService;
import com.datafuturex.assistant.agent.service.AgentGraphService;
import com.datafuturex.assistant.agent.service.AgentPromptDraftService;
import com.datafuturex.assistant.agent.service.AgentRunService;
import com.datafuturex.assistant.agent.service.AgentRuntimeService;
import com.datafuturex.assistant.shared.Result;
import com.datafuturex.assistant.shared.UserContext;
import com.datafuturex.assistant.shared.vo.ChatResponseVO;
import com.datafuturex.assistant.shared.sse.ChatSseSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
@Tag(name = "智能体管理", description = "Agent 定义、能力编排、Graph 与试运行")
public class AgentController {

    private static final ObjectMapper PROGRESS_MAPPER = new ObjectMapper();

    private final AgentDefinitionService agentDefinitionService;
    private final AgentRuntimeService agentRuntimeService;
    private final AgentGraphService agentGraphService;
    private final AgentRunService agentRunService;
    private final AgentEngineSelector agentEngineSelector;
    private final AgentPromptDraftService agentPromptDraftService;

    @GetMapping
    @Operation(summary = "智能体列表")
    public Result<List<AgentVO>> list(@RequestParam(required = false) String status) {
        return Result.success(agentDefinitionService.list(status));
    }

    @GetMapping("/capabilities")
    @Operation(summary = "能力目录")
    public Result<List<CapabilityVO>> capabilities() {
        return Result.success(agentDefinitionService.listCapabilities());
    }

    @GetMapping("/workflow-templates")
    @Operation(summary = "工作流模板说明")
    public Result<List<WorkflowTemplateVO>> workflowTemplates() {
        return Result.success(agentDefinitionService.listWorkflowTemplates());
    }

    @PostMapping("/workflow-templates/{type}/compile")
    @Operation(summary = "将预设模板编译为 Graph JSON")
    public Result<WorkflowGraph> compileTemplate(
            @PathVariable String type,
            @RequestParam(required = false) Long agentId) {
        return Result.success(agentGraphService.compileTemplate(type, agentId));
    }

    @GetMapping("/runtime-health")
    @Operation(summary = "运行时引擎健康探测")
    public Result<Map<String, Object>> runtimeHealth() {
        return Result.success(agentEngineSelector.health());
    }

    @PostMapping("/system-prompt-draft")
    @Operation(summary = "根据表单字段生成系统提示词初稿")
    public Result<AgentPromptDraftVO> systemPromptDraft(@Valid @RequestBody AgentPromptDraftDTO dto) {
        return Result.success(agentPromptDraftService.generate(dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "智能体详情")
    public Result<AgentVO> detail(@PathVariable Long id) {
        return Result.success(agentDefinitionService.get(id));
    }

    @PostMapping
    @Operation(summary = "创建智能体")
    public Result<AgentVO> create(@Valid @RequestBody AgentCreateDTO dto) {
        return Result.success(agentDefinitionService.create(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新智能体")
    public Result<AgentVO> update(@PathVariable Long id, @Valid @RequestBody AgentUpdateDTO dto) {
        return Result.success(agentDefinitionService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除智能体")
    public Result<Void> delete(@PathVariable Long id) {
        agentDefinitionService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/default")
    @Operation(summary = "设为默认智能体")
    public Result<Void> setDefault(@PathVariable Long id) {
        agentDefinitionService.setDefault(id);
        return Result.success();
    }

    @GetMapping("/{id}/graph")
    @Operation(summary = "获取规范化 Graph")
    public Result<WorkflowGraph> getGraph(@PathVariable Long id) {
        return Result.success(agentGraphService.getGraph(id));
    }

    @PutMapping("/{id}/graph")
    @Operation(summary = "保存 Graph（workflow_type=GRAPH）")
    public Result<WorkflowGraph> saveGraph(@PathVariable Long id, @Valid @RequestBody GraphSaveDTO dto) {
        return Result.success(agentGraphService.saveGraph(id, dto));
    }

    @PostMapping("/{id}/graph/validate")
    @Operation(summary = "校验 Graph 结构")
    public Result<GraphValidationResult> validateGraph(
            @PathVariable Long id,
            @RequestBody WorkflowGraph graph) {
        return Result.success(agentGraphService.validate(graph));
    }

    @GetMapping("/{id}/runs")
    @Operation(summary = "最近运行记录")
    public Result<List<AgentRunVO>> recentRuns(
            @PathVariable Long id,
            @RequestParam(defaultValue = "10") int limit) {
        List<AgentRunVO> list = agentRunService.recent(id, limit).stream().map(this::toRunVo).toList();
        return Result.success(list);
    }

    @PostMapping("/{id}/trial")
    @Operation(summary = "试运行（支持多轮 conversationId）")
    public Result<ChatResponseVO> trial(@PathVariable Long id, @Valid @RequestBody AgentTrialDTO dto) {
        return Result.success(agentRuntimeService.trial(
                id, dto.message(), dto.enableRag(), dto.conversationId(), dto.enableMemory()));
    }

    @PostMapping(value = "/{id}/trial/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "试运行流式（SSE：progress|trace|message|done；无 Tools 阶段为 token 真流式）")
    public Flux<ServerSentEvent<String>> trialStream(
            @PathVariable Long id,
            @Valid @RequestBody AgentTrialDTO dto) {
        UserContext.Snapshot userSnapshot = UserContext.snapshot();
        return Flux.<ServerSentEvent<String>>create(sink -> {
                    UserContext.restore(userSnapshot);
                    try {
                        java.util.concurrent.atomic.AtomicBoolean tokenStreamed =
                                new java.util.concurrent.atomic.AtomicBoolean(false);
                        ChatResponseVO vo = agentRuntimeService.trial(
                                id,
                                dto.message(),
                                dto.enableRag(),
                                dto.conversationId(),
                                dto.enableMemory(),
                                event -> {
                                    try {
                                        sink.next(ServerSentEvent.<String>builder()
                                                .event("progress")
                                                .data(PROGRESS_MAPPER.writeValueAsString(event))
                                                .build());
                                    } catch (Exception ignored) {
                                        // ignore
                                    }
                                },
                                chunk -> {
                                    tokenStreamed.set(true);
                                    sink.next(ServerSentEvent.<String>builder()
                                            .event("message")
                                            .data(chunk)
                                            .build());
                                });
                        if (vo.traces() != null && !vo.traces().isEmpty()) {
                            try {
                                sink.next(ServerSentEvent.<String>builder()
                                        .event("trace")
                                        .data(PROGRESS_MAPPER.writeValueAsString(vo.traces()))
                                        .build());
                            } catch (Exception ignored) {
                                // ignore
                            }
                        }
                        if (!tokenStreamed.get()) {
                            ChatSseSupport
                                    .toSseFluxWithTraces(vo.content(), vo.conversationId(), null)
                                    .filter(e -> !"done".equals(e.event()))
                                    .doOnNext(sink::next)
                                    .blockLast();
                        }
                        sink.next(ServerSentEvent.<String>builder()
                                .event("done")
                                .data(vo.conversationId())
                                .build());
                        sink.complete();
                    } catch (Exception e) {
                        sink.error(e);
                    } finally {
                        UserContext.clear();
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private AgentRunVO toRunVo(AiAgentRunEntity e) {
        return new AgentRunVO(
                e.getId(), e.getAgentId(), e.getConversationId(), e.getStatus(),
                e.getCurrentNode(), e.getStateJson(), e.getCreateTime(), e.getUpdateTime());
    }
}

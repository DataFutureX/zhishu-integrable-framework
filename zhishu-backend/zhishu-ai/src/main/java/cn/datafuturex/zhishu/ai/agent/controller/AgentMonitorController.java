package cn.datafuturex.zhishu.ai.agent.controller;

import cn.datafuturex.zhishu.ai.agent.domain.entity.AiAgentEntity;
import cn.datafuturex.zhishu.ai.agent.domain.entity.AiAgentRunEntity;
import cn.datafuturex.zhishu.ai.agent.domain.vo.AgentExecutionDetailVO;
import cn.datafuturex.zhishu.ai.agent.domain.vo.AgentExecutionVO;
import cn.datafuturex.zhishu.ai.agent.domain.vo.AgentMonitorStatsVO;
import cn.datafuturex.zhishu.ai.agent.mapper.AiAgentRunMapper;
import cn.datafuturex.zhishu.ai.agent.service.AgentDefinitionService;
import cn.datafuturex.zhishu.ai.shared.trace.AgentTraceEvent;
import cn.datafuturex.zhishu.ai.shared.Result;
import cn.datafuturex.zhishu.common.PageResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/agent-monitor")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Agent 执行监控", description = "Agent 执行历史、详情、统计")
public class AgentMonitorController {

    private final AiAgentRunMapper aiAgentRunMapper;
    private final AgentDefinitionService agentDefinitionService;

    /**
     * 执行历史列表（分页 + 筛选）。
     *
     * @param agentId   智能体 ID（可选）
     * @param status    状态筛选（可选）
     * @param runType   执行类型（可选）
     * @param keyword   关键词模糊匹配（可选）
     * @param startTime 时间范围起始（可选）
     * @param endTime   时间范围截止（可选）
     * @param page      页码
     * @param size      每页大小
     * @return 分页执行记录
     */
    @GetMapping("/executions")
    @Operation(summary = "执行历史列表")
    public Result<PageResult<AgentExecutionVO>> executions(
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String runType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        LambdaQueryWrapper<AiAgentRunEntity> wrapper = new LambdaQueryWrapper<>();
        if (agentId != null) {
            wrapper.eq(AiAgentRunEntity::getAgentId, agentId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(AiAgentRunEntity::getStatus, status);
        }
        if (StringUtils.hasText(runType)) {
            wrapper.eq(AiAgentRunEntity::getRunType, runType);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(AiAgentRunEntity::getUserMessage, keyword);
        }
        if (StringUtils.hasText(startTime)) {
            wrapper.ge(AiAgentRunEntity::getCreateTime, LocalDateTime.parse(startTime));
        }
        if (StringUtils.hasText(endTime)) {
            wrapper.le(AiAgentRunEntity::getCreateTime, LocalDateTime.parse(endTime));
        }
        wrapper.orderByDesc(AiAgentRunEntity::getCreateTime);

        Page<AiAgentRunEntity> pageObj = aiAgentRunMapper.selectPage(
                new Page<>(page, Math.min(size, 100)), wrapper);

        // 批量解析 agentName
        Map<Long, String> agentNameMap = resolveAgentNames(pageObj.getRecords());

        PageResult<AgentExecutionVO> voPage = new PageResult<>();
        voPage.setCurrent(pageObj.getCurrent());
        voPage.setSize(pageObj.getSize());
        voPage.setTotal(pageObj.getTotal());
        voPage.setPages(pageObj.getPages());
        voPage.setRecords(pageObj.getRecords().stream()
                .map(e -> toListVO(e, agentNameMap))
                .toList());
        return Result.success(voPage);
    }

    /**
     * 执行详情（含完整轨迹）。
     *
     * @param id 执行记录 ID
     * @return 执行详情
     */
    @GetMapping("/executions/{id}")
    @Operation(summary = "执行详情")
    public Result<AgentExecutionDetailVO> executionDetail(@PathVariable Long id) {
        AiAgentRunEntity entity = aiAgentRunMapper.selectById(id);
        if (entity == null) {
            return Result.success(null);
        }
        String agentName = resolveAgentName(entity.getAgentId());
        List<AgentTraceEvent> traces = parseTraces(entity.getStateJson());
        return Result.success(toDetailVO(entity, agentName, traces));
    }

    /**
     * 统计概览。
     *
     * @param period 时间范围：TODAY / WEEK / MONTH
     * @return 统计数据
     */
    @GetMapping("/stats")
    @Operation(summary = "统计概览")
    public Result<AgentMonitorStatsVO> stats(
            @RequestParam(defaultValue = "TODAY") String period) {

        LocalDateTime since = switch (period.toUpperCase()) {
            case "WEEK" -> LocalDate.now().minusWeeks(1).atStartOfDay();
            case "MONTH" -> LocalDate.now().minusMonths(1).atStartOfDay();
            default -> LocalDate.now().atStartOfDay();
        };

        LambdaQueryWrapper<AiAgentRunEntity> wrapper = new LambdaQueryWrapper<AiAgentRunEntity>()
                .ge(AiAgentRunEntity::getCreateTime, since);
        List<AiAgentRunEntity> all = aiAgentRunMapper.selectList(wrapper);

        int total = all.size();
        int success = (int) all.stream().filter(e -> "SUCCESS".equals(e.getStatus())).count();
        int failed = (int) all.stream().filter(e -> "FAILED".equals(e.getStatus())).count();
        int running = (int) all.stream().filter(e -> "RUNNING".equals(e.getStatus())).count();
        double rate = total > 0 ? Math.round(success * 10000.0 / total) / 100.0 : 0;
        long avgDuration = total > 0
                ? Math.round(all.stream()
                    .filter(e -> e.getDurationMs() != null)
                    .mapToLong(AiAgentRunEntity::getDurationMs)
                    .average().orElse(0))
                : 0;
        int today = (int) all.stream()
                .filter(e -> e.getCreateTime() != null
                        && e.getCreateTime().toLocalDate().equals(LocalDate.now()))
                .count();

        return Result.success(new AgentMonitorStatsVO(
                total, success, failed, running, rate, avgDuration, today));
    }

    /**
     * 按智能体聚合统计。
     *
     * @return 各智能体统计列表
     */
    @GetMapping("/stats/agents")
    @Operation(summary = "按智能体聚合统计")
    public Result<List<Map<String, Object>>> statsByAgent() {
        List<AiAgentRunEntity> all = aiAgentRunMapper.selectList(
                new LambdaQueryWrapper<AiAgentRunEntity>()
                        .orderByDesc(AiAgentRunEntity::getCreateTime));

        Map<Long, List<AiAgentRunEntity>> grouped = all.stream()
                .collect(Collectors.groupingBy(AiAgentRunEntity::getAgentId));

        Map<Long, String> nameMap = resolveAgentNames(all);

        List<Map<String, Object>> result = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            Long agentId = entry.getKey();
            List<AiAgentRunEntity> runs = entry.getValue();
            int total = runs.size();
            int success = (int) runs.stream().filter(e -> "SUCCESS".equals(e.getStatus())).count();
            double rate = total > 0 ? Math.round(success * 10000.0 / total) / 100.0 : 0;
            double avgDuration = runs.stream()
                    .filter(e -> e.getDurationMs() != null)
                    .mapToLong(AiAgentRunEntity::getDurationMs)
                    .average().orElse(0);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("agentId", agentId);
            row.put("agentName", nameMap.getOrDefault(agentId, "未知"));
            row.put("totalCount", total);
            row.put("successCount", success);
            row.put("successRate", rate);
            row.put("avgDurationMs", Math.round(avgDuration));
            result.add(row);
        }
        // 按执行次数降序
        result.sort((a, b) -> Integer.compare((int) b.get("totalCount"), (int) a.get("totalCount")));
        return Result.success(result);
    }

    /**
     * 当前运行中的执行列表。
     *
     * @return 运行中的执行记录
     */
    @GetMapping("/running")
    @Operation(summary = "当前运行中的执行")
    public Result<List<AgentExecutionVO>> running() {
        List<AiAgentRunEntity> list = aiAgentRunMapper.selectList(
                new LambdaQueryWrapper<AiAgentRunEntity>()
                        .eq(AiAgentRunEntity::getStatus, "RUNNING")
                        .orderByAsc(AiAgentRunEntity::getCreateTime));
        Map<Long, String> nameMap = resolveAgentNames(list);
        return Result.success(list.stream().map(e -> toListVO(e, nameMap)).toList());
    }

    // ---- 内部转换方法 ----

    private AgentExecutionVO toListVO(AiAgentRunEntity e, Map<Long, String> agentNameMap) {
        return new AgentExecutionVO(
                e.getId(), e.getAgentId(),
                agentNameMap.getOrDefault(e.getAgentId(), "未知"),
                e.getUserMessage(), e.getResponseSummary(),
                e.getStatus(), e.getDurationMs(),
                e.getModelName(), e.getWorkflowType(),
                e.getRunType(), e.getTtftMs(), e.getTpotMs(),
                e.getTokenCount(), e.getUserId(), e.getCreateTime());
    }

    private AgentExecutionDetailVO toDetailVO(AiAgentRunEntity e, String agentName,
                                               List<AgentTraceEvent> traces) {
        return new AgentExecutionDetailVO(
                e.getId(), e.getAgentId(), agentName,
                e.getUserMessage(), e.getResponseSummary(),
                e.getStatus(), e.getDurationMs(),
                e.getModelName(), e.getWorkflowType(),
                e.getRunType(), e.getTtftMs(), e.getTpotMs(),
                e.getTokenCount(), e.getUserId(),
                traces, e.getCreateTime(), e.getUpdateTime());
    }

    private Map<Long, String> resolveAgentNames(List<AiAgentRunEntity> runs) {
        Set<Long> agentIds = runs.stream()
                .map(AiAgentRunEntity::getAgentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> map = new HashMap<>();
        for (Long agentId : agentIds) {
            try {
                var vo = agentDefinitionService.get(agentId);
                if (vo != null) {
                    map.put(agentId, vo.name());
                }
            } catch (Exception ignored) {
                // 智能体可能已删除
            }
        }
        return map;
    }

    private String resolveAgentName(Long agentId) {
        if (agentId == null) return "未知";
        try {
            var vo = agentDefinitionService.get(agentId);
            return vo != null ? vo.name() : "未知";
        } catch (Exception e) {
            return "未知";
        }
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private List<AgentTraceEvent> parseTraces(String stateJson) {
        if (!StringUtils.hasText(stateJson)) {
            return List.of();
        }
        try {
            return JSON_MAPPER.readValue(stateJson,
                    JSON_MAPPER.getTypeFactory().constructCollectionType(List.class, AgentTraceEvent.class));
        } catch (Exception e) {
            log.warn("解析 traces JSON 失败: {}", e.getMessage());
            return List.of();
        }
    }
}

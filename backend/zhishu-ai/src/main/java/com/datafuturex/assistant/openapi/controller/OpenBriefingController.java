package com.datafuturex.assistant.openapi.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.datafuturex.assistant.agent.domain.vo.AgentVO;
import com.datafuturex.assistant.agent.service.AgentDefinitionService;
import com.datafuturex.assistant.briefing.domain.dto.BriefingScheduleUpsertDTO;
import com.datafuturex.assistant.briefing.domain.entity.AiBriefingScheduleEntity;
import com.datafuturex.assistant.briefing.domain.vo.BriefingDeliveryVO;
import com.datafuturex.assistant.briefing.domain.vo.BriefingScheduleVO;
import com.datafuturex.assistant.briefing.service.BriefingDeliveryService;
import com.datafuturex.assistant.briefing.service.BriefingJobService;
import com.datafuturex.assistant.briefing.service.BriefingScheduleService;
import com.datafuturex.assistant.openapi.dto.OpenBriefingGenerateDTO;
import com.datafuturex.assistant.shared.Result;
import com.datafuturex.assistant.shared.UserContext;
import com.datafuturex.assistant.shared.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/open/v1")
@RequiredArgsConstructor
@Tag(name = "开放 API · 简报")
public class OpenBriefingController {

    private final BriefingDeliveryService deliveryService;
    private final BriefingScheduleService scheduleService;
    private final BriefingJobService jobService;
    private final AgentDefinitionService agentDefinitionService;

    @PostMapping("/briefings/generate")
    @Operation(summary = "为代调用户生成简报")
    public Result<Map<String, Object>> generate(@RequestBody(required = false) OpenBriefingGenerateDTO dto) {
        OpenBriefingGenerateDTO body = dto == null ? new OpenBriefingGenerateDTO(null, null) : dto;
        AiBriefingScheduleEntity schedule = resolveSchedule(body.scheduleId());
        String username = requireUsername();
        int count = jobService.runForUsername(schedule, "RUN_NOW", username, body.prompt());
        scheduleService.markRan(schedule, LocalDateTime.now());
        return Result.success(Map.of(
                "scheduleId", schedule.getId(),
                "generated", count));
    }

    @GetMapping("/briefings/deliveries")
    @Operation(summary = "代调用户的简报投递列表")
    public Result<Page<BriefingDeliveryVO>> deliveries(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status) {
        return Result.success(deliveryService.pageByUser(requireUserId(), status, pageNum, pageSize));
    }

    @GetMapping("/briefings/schedules")
    @Operation(summary = "简报调度列表")
    public Result<List<BriefingScheduleVO>> schedules() {
        return Result.success(scheduleService.listAll());
    }

    @PostMapping("/briefings/schedules")
    @Operation(summary = "创建简报调度")
    public Result<BriefingScheduleVO> createSchedule(@Valid @RequestBody BriefingScheduleUpsertDTO dto) {
        return Result.success(scheduleService.create(dto));
    }

    @PutMapping("/briefings/schedules/{id}")
    @Operation(summary = "更新简报调度")
    public Result<BriefingScheduleVO> updateSchedule(
            @PathVariable Long id,
            @Valid @RequestBody BriefingScheduleUpsertDTO dto) {
        return Result.success(scheduleService.update(id, dto));
    }

    @PostMapping("/briefings/schedules/{id}/run-now")
    @Operation(summary = "立即为代调用户生成该调度简报")
    public Result<Map<String, Object>> runNow(@PathVariable Long id) {
        AiBriefingScheduleEntity schedule = scheduleService.requireEntity(id);
        int count = jobService.runForUsername(schedule, "RUN_NOW", requireUsername(), null);
        scheduleService.markRan(schedule, LocalDateTime.now());
        return Result.success(Map.of(
                "scheduleId", id,
                "generated", count));
    }

    @GetMapping("/agents")
    @Operation(summary = "智能体列表（工作台简报选 Agent）")
    public Result<List<AgentVO>> agents(@RequestParam(required = false) String status) {
        return Result.success(agentDefinitionService.list(status));
    }

    private AiBriefingScheduleEntity resolveSchedule(Long scheduleId) {
        if (scheduleId != null) {
            return scheduleService.requireEntity(scheduleId);
        }
        List<BriefingScheduleVO> enabled = scheduleService.listEnabled();
        if (enabled == null || enabled.isEmpty()) {
            throw new BusinessException("未配置启用的简报调度");
        }
        return scheduleService.requireEntity(enabled.get(0).id());
    }

    private String requireUserId() {
        String userId = UserContext.getUserId();
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(401, "缺少用户上下文");
        }
        return userId;
    }

    private String requireUsername() {
        String username = UserContext.getUsername();
        if (!StringUtils.hasText(username)) {
            throw new BusinessException(401, "缺少 X-On-Behalf-Of");
        }
        return username;
    }
}

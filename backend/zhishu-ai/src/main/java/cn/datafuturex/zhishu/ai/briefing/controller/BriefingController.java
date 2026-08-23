package cn.datafuturex.zhishu.ai.briefing.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.datafuturex.zhishu.ai.briefing.domain.dto.BriefingScheduleUpsertDTO;
import cn.datafuturex.zhishu.ai.briefing.domain.entity.AiBriefingScheduleEntity;
import cn.datafuturex.zhishu.ai.briefing.domain.vo.BriefingDeliveryVO;
import cn.datafuturex.zhishu.ai.briefing.domain.vo.BriefingScheduleVO;
import cn.datafuturex.zhishu.ai.briefing.domain.vo.BriefingStatsVO;
import cn.datafuturex.zhishu.ai.briefing.service.BriefingDeliveryService;
import cn.datafuturex.zhishu.ai.briefing.service.BriefingJobService;
import cn.datafuturex.zhishu.ai.briefing.service.BriefingScheduleService;
import cn.datafuturex.zhishu.ai.briefing.service.BriefingSseService;
import cn.datafuturex.zhishu.ai.modelconfig.api.ModelConfigPort;
import cn.datafuturex.zhishu.ai.shared.Result;
import cn.datafuturex.zhishu.ai.shared.UserContext;
import cn.datafuturex.zhishu.ai.shared.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/briefings")
@RequiredArgsConstructor
@Tag(name = "Agent 简报", description = "知枢内部简报投递与调度；外部请调用开放 Agent 对话接口")
public class BriefingController {

    private final BriefingDeliveryService deliveryService;
    private final BriefingScheduleService scheduleService;
    private final BriefingJobService jobService;
    private final BriefingSseService briefingSseService;
    private final ModelConfigPort modelConfigPort;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "订阅简报 SSE 流",
            description = "接收站内铃推送。支持 Authorization Bearer；EventSource 可用 query 参数 token")
    public SseEmitter stream() {
        return briefingSseService.subscribe(requireUserId());
    }

    @GetMapping("/latest")
    @Operation(summary = "当前用户最新成功简报")
    public Result<BriefingDeliveryVO> latest() {
        return Result.success(deliveryService.latestSuccess(requireUserId()));
    }

    @GetMapping
    @Operation(summary = "分页查询当前用户简报")
    public Result<Page<BriefingDeliveryVO>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status) {
        return Result.success(deliveryService.pageByUser(requireUserId(), status, pageNum, pageSize));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "未读简报数量")
    public Result<Map<String, Long>> unreadCount() {
        return Result.success(Map.of("count", deliveryService.unreadCount(requireUserId())));
    }

    @GetMapping("/recent")
    @Operation(summary = "最近简报列表")
    public Result<List<BriefingDeliveryVO>> recent(@RequestParam(defaultValue = "10") int limit) {
        return Result.success(deliveryService.recent(requireUserId(), limit));
    }

    @GetMapping("/deliveries/stats")
    @Operation(summary = "当前用户简报统计")
    public Result<BriefingStatsVO> stats() {
        return Result.success(deliveryService.stats(requireUserId()));
    }

    @GetMapping("/schedules")
    @Operation(summary = "简报调度列表")
    public Result<List<BriefingScheduleVO>> schedules() {
        return Result.success(scheduleService.listAll());
    }

    @PostMapping("/schedules")
    @Operation(summary = "创建简报调度")
    public Result<BriefingScheduleVO> createSchedule(@Valid @RequestBody BriefingScheduleUpsertDTO dto) {
        return Result.success(scheduleService.create(dto));
    }

    @PutMapping("/schedules/{id}")
    @Operation(summary = "更新简报调度")
    public Result<BriefingScheduleVO> updateSchedule(
            @PathVariable Long id,
            @Valid @RequestBody BriefingScheduleUpsertDTO dto) {
        return Result.success(scheduleService.update(id, dto));
    }

    @PostMapping("/schedules/{id}/run-now")
    @Operation(summary = "立即生成简报")
    public Result<Map<String, Object>> runNow(@PathVariable Long id) {
        if (!modelConfigPort.hasApiKey()) {
            throw new BusinessException(400, "未在模型设置中配置 API Key，请在「模型设置」中填写并保存");
        }
        AiBriefingScheduleEntity schedule = scheduleService.requireEntity(id);
        if (schedule.getAgentId() == null) {
            throw new BusinessException(400, "简报计划未关联智能体，请先编辑计划并选择智能体");
        }
        // 先更新 lastRunAt，再提交异步任务——LLM 调用在后台线程执行，HTTP 立即返回
        scheduleService.markRan(schedule, LocalDateTime.now());
        jobService.runNowAsync(schedule);
        return Result.success(Map.of(
                "scheduleId", id,
                "message", "已提交后台生成，完成后可在简报列表中查看"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "简报详情")
    public Result<BriefingDeliveryVO> detail(@PathVariable Long id) {
        return Result.success(deliveryService.getForUser(id, requireUserId()));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "标记已读")
    public Result<Void> markRead(@PathVariable Long id) {
        deliveryService.markRead(id, requireUserId());
        return Result.success();
    }

    private String requireUserId() {
        String userId = UserContext.getUserId();
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(401, "未登录或缺少用户上下文");
        }
        return userId;
    }
}

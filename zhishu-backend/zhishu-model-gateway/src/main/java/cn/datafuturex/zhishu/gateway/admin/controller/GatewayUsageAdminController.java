package cn.datafuturex.zhishu.gateway.admin.controller;

import cn.datafuturex.zhishu.common.PageResult;
import cn.datafuturex.zhishu.common.Result;
import cn.datafuturex.zhishu.gateway.admin.service.GatewayUsageAdminService;
import cn.datafuturex.zhishu.gateway.admin.vo.GatewayUsageSummaryVO;
import cn.datafuturex.zhishu.gateway.admin.vo.GatewayUsageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 模型网关 · 调用台账查询。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/model-gateway/usages")
@RequiredArgsConstructor
@Tag(name = "模型网关 · 调用台账", description = "按用户/模型/通道/状态查询用量与汇总")
public class GatewayUsageAdminController {

    private final GatewayUsageAdminService gatewayUsageAdminService;

    /**
     * 台账分页查询。
     *
     * @param username 用户名（模糊）
     * @param modelId  模型 id
     * @param channel  通道
     * @param status   状态
     * @param from     起始日期
     * @param to       结束日期
     * @param current  页码
     * @param size     每页大小
     * @return 分页结果
     */
    @GetMapping
    @Operation(summary = "调用台账分页")
    public Result<PageResult<GatewayUsageVO>> page(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String modelId,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return Result.success(gatewayUsageAdminService.page(username, modelId, channel, status,
                startOfDay(from), endOfDay(to), current, size));
    }

    /**
     * 汇总统计。
     *
     * @param from 起始日期
     * @param to   结束日期
     * @return 汇总
     */
    @GetMapping("/summary")
    @Operation(summary = "调用汇总", description = "总量 + 按模型/用户/天聚合")
    public Result<GatewayUsageSummaryVO> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.success(gatewayUsageAdminService.summary(startOfDay(from), endOfDay(to)));
    }

    private static LocalDateTime startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    private static LocalDateTime endOfDay(LocalDate date) {
        return date == null ? null : date.atTime(23, 59, 59);
    }
}

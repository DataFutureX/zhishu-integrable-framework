package com.datafuturex.assistant.agent.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 注入服务器当前时间与报表默认周期，避免模型臆造「今日」日期。
 */
public final class AgentTimeContext {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private AgentTimeContext() {
    }

    /**
     * 追加到 system prompt 末尾的时间与报表规则块。
     */
    public static String appendBlock(String systemPrompt) {
        String base = systemPrompt == null ? "" : systemPrompt.trim();
        String block = buildBlock(LocalDateTime.now(ZONE));
        if (base.isEmpty()) {
            return block;
        }
        return base + "\n\n" + block;
    }

    static String buildBlock(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        YearMonth month = YearMonth.from(today);
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        LocalDate yearStart = LocalDate.of(today.getYear(), 1, 1);
        LocalDate yearEnd = LocalDate.of(today.getYear(), 12, 31);

        String nowStr = now.format(DATE_TIME);
        String todayStr = today.format(DATE);
        String dayStart = today.atStartOfDay().format(DATE_TIME);
        String dayEnd = today.atTime(23, 59, 59).format(DATE_TIME);
        String monthStartStr = monthStart.atStartOfDay().format(DATE_TIME);
        String monthEndStr = monthEnd.atTime(23, 59, 59).format(DATE_TIME);
        String yearStartStr = yearStart.atStartOfDay().format(DATE_TIME);
        String yearEndStr = yearEnd.atTime(23, 59, 59).format(DATE_TIME);

        return """
                【系统时间 · Asia/Shanghai】
                当前时间：%s
                今日：%s
                本月：%s
                本年：%d

                【报表默认周期 · 用户未指定日期时必须遵守，禁止臆造年份/日期】
                - 日报 / 日报告 / 日汇总 → 当日 %s ～ %s（历史查询可用此区间；若只需最新值可调最新要素工具）
                - 月报 / 月报告 / 月汇总 → 当月 %s ～ %s
                - 年报 / 年报告 / 年汇总 → 当年 %s ～ %s
                - 「今日 / 昨天 / 本周 / 上周 / 本月 / 过去一个月」等相对时间一律按上述系统时间换算
                - 调用 queryStationHistoryElements 时必须使用换算后的 yyyy-MM-dd HH:mm:ss，不得使用训练知识中的旧日期
                """.formatted(
                nowStr,
                todayStr,
                month.toString(),
                today.getYear(),
                dayStart, dayEnd,
                monthStartStr, monthEndStr,
                yearStartStr, yearEndStr
        ).trim();
    }
}

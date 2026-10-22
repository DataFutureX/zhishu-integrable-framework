package cn.datafuturex.zhishu.gateway.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 额度策略视图。
 *
 * @param id                主键
 * @param subjectType       主体类型
 * @param subjectKey        主体键
 * @param periodKind        周期类型
 * @param tokenLimit        周期上限（null=不限量）
 * @param usedTokens        当前周期已用
 * @param remaining         当前周期剩余（不限量时 -1）
 * @param periodStart       周期起点
 * @param periodEnd         周期终点
 * @param status            状态
 * @param remark            备注
 * @param createTime        创建时间
 * @param updateTime        更新时间
 */
@Schema(description = "额度策略")
public record GatewayQuotaVO(
        Long id,
        String subjectType,
        String subjectKey,
        String periodKind,
        Long tokenLimit,
        Long usedTokens,
        Long remaining,
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        String status,
        String remark,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}

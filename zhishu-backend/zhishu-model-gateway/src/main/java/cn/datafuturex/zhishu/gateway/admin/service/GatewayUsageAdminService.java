package cn.datafuturex.zhishu.gateway.admin.service;

import cn.datafuturex.zhishu.common.PageResult;
import cn.datafuturex.zhishu.gateway.admin.vo.GatewayUsageSummaryVO;
import cn.datafuturex.zhishu.gateway.admin.vo.GatewayUsageVO;

import java.time.LocalDateTime;

/**
 * 调用台账查询服务。
 */
public interface GatewayUsageAdminService {

    /**
     * 台账分页查询。
     *
     * @param username 用户名（模糊）
     * @param modelId  模型 id
     * @param channel  通道
     * @param status   状态
     * @param from     起始时间
     * @param to       结束时间
     * @param current  页码（从 1 开始）
     * @param size     每页大小
     * @return 分页结果
     */
    PageResult<GatewayUsageVO> page(String username, String modelId, String channel, String status,
                                    LocalDateTime from, LocalDateTime to, long current, long size);

    /**
     * 汇总统计。
     *
     * @param from 起始时间
     * @param to   结束时间
     * @return 汇总
     */
    GatewayUsageSummaryVO summary(LocalDateTime from, LocalDateTime to);
}

package cn.datafuturex.yunqi.biz.systemmonitor.service;

import cn.datafuturex.yunqi.biz.systemmonitor.dto.SystemHealthDTO;
import cn.datafuturex.yunqi.biz.systemmonitor.dto.SystemStatusDTO;

/**
 * 系统监控服务
 */
public interface SystemMonitorService {

    /**
     * 获取系统综合运行状态
     */
    SystemStatusDTO getSystemStatus();

    /**
     * 获取轻量健康检查结果（适用于探活）
     */
    SystemHealthDTO getSystemHealth();
}

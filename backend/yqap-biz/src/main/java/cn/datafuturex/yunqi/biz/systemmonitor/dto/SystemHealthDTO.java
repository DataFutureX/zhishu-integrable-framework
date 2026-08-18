package cn.datafuturex.yunqi.biz.systemmonitor.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统健康检查 DTO（轻量，适用于探活与 K8s 探针）
 */
public record SystemHealthDTO(
        String status,
        LocalDateTime timestamp,
        List<ComponentHealthDTO> components
) {
}

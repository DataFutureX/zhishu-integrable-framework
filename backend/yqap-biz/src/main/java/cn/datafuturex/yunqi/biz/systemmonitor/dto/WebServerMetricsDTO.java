package cn.datafuturex.yunqi.biz.systemmonitor.dto;

/**
 * Spring Boot 内嵌 Web 服务指标
 */
public record WebServerMetricsDTO(
        String status,
        Integer port,
        String servletContainer
) {
}

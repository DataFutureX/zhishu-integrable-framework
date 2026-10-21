package cn.datafuturex.zhishu.desktop.dto;

import java.util.List;
import java.util.Map;

/**
 * 桌面端服务信息响应体。
 *
 * @param name        服务名称
 * @param apiVersion  桌面 API 版本
 * @param features    已启用的功能能力列表
 * @param endpoints   主要接口端点描述（key=路径，value=说明）
 */
public record DesktopInfoDTO(
        String name,
        String apiVersion,
        List<String> features,
        Map<String, String> endpoints
) {
}

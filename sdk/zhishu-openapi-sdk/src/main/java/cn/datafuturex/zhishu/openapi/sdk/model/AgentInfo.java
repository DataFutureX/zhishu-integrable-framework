package cn.datafuturex.zhishu.openapi.sdk.model;

/**
 * 智能体信息。
 *
 * @param id     智能体 ID
 * @param name   名称
 * @param status 状态（ENABLED / DISABLED）
 */
public record AgentInfo(
        Long id,
        String name,
        String status
) {
}

package cn.datafuturex.zhishu.mcp.common.audit;

import org.springframework.util.StringUtils;

/**
 * MCP 调用审计方向。
 * <p>
 * 历史取值为 {@code OUT}（对外平面被调用）/ {@code IN}（调用他方上游），语义反直觉，
 * 迁移后统一为按平面命名；读侧仍兼容旧值，历史数据由
 * {@code db/patch_mcp_call_log_direction.sql} 人工归一。
 */
public enum McpCallDirection {

    /** provide 平面：外部 Client 调用本平台 Tool */
    SERVE,

    /** consume 平面：本平台（Agent）调用上游 Tool */
    CONSUME;

    /**
     * 归一查询参数：接受 {@code SERVE/CONSUME} 与历史值 {@code OUT/IN}。
     *
     * @param raw 原始值，可为空
     * @return 对应枚举；无法识别时返回 {@code null}（表示不过滤）
     */
    public static McpCallDirection fromFilter(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim().toUpperCase();
        return switch (value) {
            case "SERVE", "OUT", "OUTBOUND" -> SERVE;
            case "CONSUME", "IN", "INBOUND" -> CONSUME;
            default -> null;
        };
    }
}

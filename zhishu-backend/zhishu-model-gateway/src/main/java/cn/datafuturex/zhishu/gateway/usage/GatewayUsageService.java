package cn.datafuturex.zhishu.gateway.usage;

import cn.datafuturex.zhishu.gateway.auth.GatewayPrincipal;
import cn.datafuturex.zhishu.gateway.catalog.GatewayModelView;

/**
 * 调用台账服务：请求开始记 RESERVED，结束后按实际用量收敛。
 */
public interface GatewayUsageService {

    /**
     * 记录请求开始（状态 RESERVED）。
     *
     * @param command 开始命令
     * @return 台账行 ID
     */
    long begin(BeginCommand command);

    /**
     * 收敛台账（成功 / 失败 / 中断）。
     *
     * @param usageId 台账行 ID
     * @param outcome 结果
     */
    void finish(long usageId, Outcome outcome);

    /**
     * 客户端元信息。
     *
     * @param ip            来源 IP
     * @param userAgent     User-Agent
     * @param clientName    客户端名（如 LetsWork）
     * @param clientVersion 客户端版本
     */
    record ClientMeta(String ip, String userAgent, String clientName, String clientVersion) {
    }

    /**
     * 台账开始命令。
     *
     * @param requestId      请求唯一标识
     * @param channel        通道：DESKTOP / CONSOLE / AGENT / BRIEFING / OPENAPI / TEST
     * @param principal      调用主体
     * @param view           模型视图
     * @param stream         是否流式
     * @param reservedTokens 预留 token
     * @param meta           客户端元信息
     */
    record BeginCommand(
            String requestId,
            String channel,
            GatewayPrincipal principal,
            GatewayModelView view,
            boolean stream,
            long reservedTokens,
            ClientMeta meta
    ) {
    }

    /**
     * 台账结果。
     *
     * @param status           状态：SUCCESS / ERROR / ABORTED / TIMEOUT / REJECTED_QUOTA
     * @param httpStatus       返回给客户端的 HTTP 状态
     * @param promptTokens     输入 token
     * @param completionTokens 输出 token
     * @param totalTokens      总 token
     * @param cachedTokens     命中缓存 token
     * @param reasoningTokens  推理 token
     * @param estimated        是否估算
     * @param latencyMs        总耗时（毫秒）
     * @param ttftMs           首 token 耗时（毫秒）
     * @param errorCode        错误码
     * @param errorMessage     错误说明
     */
    record Outcome(
            String status,
            Integer httpStatus,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            Integer cachedTokens,
            Integer reasoningTokens,
            boolean estimated,
            Long latencyMs,
            Long ttftMs,
            String errorCode,
            String errorMessage
    ) {
        /**
         * 构造成功结果。
         *
         * @param prompt      输入 token
         * @param completion  输出 token
         * @param total       总 token
         * @param cached      缓存 token
         * @param reasoning   推理 token
         * @param estimated   是否估算
         * @param httpStatus  HTTP 状态
         * @param latencyMs   总耗时
         * @param ttftMs      首 token 耗时
         * @return 结果
         */
        public static Outcome success(Integer prompt, Integer completion, Integer total, Integer cached,
                                      Integer reasoning, boolean estimated, int httpStatus,
                                      long latencyMs, Long ttftMs) {
            return new Outcome("SUCCESS", httpStatus, prompt, completion, total, cached, reasoning,
                    estimated, latencyMs, ttftMs, null, null);
        }
    }
}

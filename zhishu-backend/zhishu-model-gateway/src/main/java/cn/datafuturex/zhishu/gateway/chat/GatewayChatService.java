package cn.datafuturex.zhishu.gateway.chat;

import cn.datafuturex.zhishu.gateway.auth.GatewayPrincipal;
import cn.datafuturex.zhishu.gateway.usage.GatewayUsageService;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 推理端点服务：校验 → 选模型 → 预留额度 → 转发上游 → 结算并写台账。
 */
public interface GatewayChatService {

    /**
     * 处理一次 Chat Completions 请求并直接写响应。
     * <p>
     * 流式与非流式共用同一入口：由请求体的 {@code stream} 决定响应形态。
     * 尚未写出任何字节时抛出的 {@link cn.datafuturex.zhishu.gateway.compat.GatewayException}
     * 会由网关异常处理器序列化为 OpenAI 错误体；已开始流式输出后只能记录并收敛台账。
     *
     * @param principal 调用主体
     * @param channel   通道标识
     * @param rawBody   原始请求体
     * @param meta      客户端元信息
     * @param response  响应
     * @throws IOException 响应写出失败
     */
    void handle(GatewayPrincipal principal, String channel, String rawBody,
                GatewayUsageService.ClientMeta meta, HttpServletResponse response) throws IOException;
}

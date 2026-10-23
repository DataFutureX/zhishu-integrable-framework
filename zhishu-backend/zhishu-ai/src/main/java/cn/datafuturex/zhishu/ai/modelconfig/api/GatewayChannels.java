package cn.datafuturex.zhishu.ai.modelconfig.api;

/**
 * 模型网关台账通道与内部转发的通道声明头。
 * <p>
 * 平台内部（Agent 运行时、控制台对话）经 HTTP 回环调用网关时，用 {@link #HEADER} 声明来源，
 * 使 {@code ai_gateway_usage.channel} 能区分「Agent 推理」与「桌面客户端 / 控制台直连」；
 * 未带该头时由网关按转发头与本机回环做启发式判定。
 */
public final class GatewayChannels {

    /** 通道声明请求头名（网关侧解析） */
    public static final String HEADER = "X-Zhishu-Channel";

    /** 桌面客户端 */
    public static final String DESKTOP = "DESKTOP";

    /** 控制台 / 平台对话（内部调用的默认通道） */
    public static final String CONSOLE = "CONSOLE";

    /** Agent 运行时推理 */
    public static final String AGENT = "AGENT";

    /** 开放 API 调用 */
    public static final String OPENAPI = "OPENAPI";

    private GatewayChannels() {
    }
}

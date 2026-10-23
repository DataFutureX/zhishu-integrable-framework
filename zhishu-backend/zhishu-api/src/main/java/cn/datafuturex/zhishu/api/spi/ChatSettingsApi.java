package cn.datafuturex.zhishu.api.spi;

/**
 * 对话行为设置端口（由 zhishu-biz 的系统配置实现）。
 * <p>
 * 供 zhishu-ai 读取控制台可配的运行期开关，避免 AI 模块直接依赖系统配置表。
 */
public interface ChatSettingsApi {

    /**
     * 对话默认是否开启知识库 RAG。
     *
     * @return true=默认开启；未配置时为 false
     */
    boolean defaultRagEnabled();
}

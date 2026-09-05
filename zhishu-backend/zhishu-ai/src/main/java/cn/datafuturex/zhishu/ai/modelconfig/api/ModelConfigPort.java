package cn.datafuturex.zhishu.ai.modelconfig.api;

import cn.datafuturex.zhishu.ai.modelconfig.dto.AiModelConfigUpdateDTO;
import cn.datafuturex.zhishu.ai.modelconfig.vo.AiModelConfigVO;

/**
 * AI 模型运行时配置
 * @deprecated 已被 {@link ModelProviderPort} 替代，保留仅为向后兼容
 */
@Deprecated
public interface ModelConfigPort {

    AiModelConfigVO getConfig();

    AiModelConfigVO updateConfig(AiModelConfigUpdateDTO dto);

    /** 当前对话模型名（带缓存） */
    String currentChatModel();

    /** 默认温度 */
    Double currentTemperature();

    /** 默认 maxTokens */
    Integer currentMaxTokens();

    /** 对话默认是否开启 RAG */
    boolean currentEnableRagDefault();

    /** 当前向量模型名 */
    String currentEmbeddingModel();

    /** 当前 OpenAI 兼容 Base URL */
    String currentBaseUrl();

    /** 解密后的模型 API Key（仅供运行时调用，禁止写入日志/接口） */
    String currentApiKey();

    /** 是否已配置可用的模型 API Key（库内加密密钥或环境变量回退） */
    boolean hasApiKey();
}

package com.datafuturex.assistant.modelconfig.api;

import com.datafuturex.assistant.modelconfig.dto.AiModelConfigUpdateDTO;
import com.datafuturex.assistant.modelconfig.vo.AiModelConfigVO;

/**
 * AI 模型运行时配置
 */
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
}

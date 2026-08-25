package cn.datafuturex.zhishu.ai.modelconfig.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 模型配置本地参数。crypto-key 只用于加密入库的 API Key，本身不是模型密钥。
 */
@Data
@ConfigurationProperties(prefix = "zhishu.ai")
public class ModelConfigProperties {

    /**
     * AES 包装密钥。生产请用环境变量 ZHISHU_AI_CRYPTO_KEY，勿把模型 API Key 写入配置文件。
     */
    private String cryptoKey = "zhishu-dev-model-key-wrap";

    private String defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
}

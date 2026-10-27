package cn.datafuturex.zhishu.common.crypto;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 凭证包装密钥参数。{@code cryptoKey} 只用于加密入库的 API Key / AK-SK，本身不是模型密钥。
 * <p>
 * 配置前缀保留历史值 {@code zhishu.ai}（对应环境变量 {@code ZHISHU_AI_CRYPTO_KEY}），
 * 以免改动各环境既有配置；更换该值会使存量密文无法解出，共库的多套环境必须使用同一把。
 */
@Data
@ConfigurationProperties(prefix = "zhishu.ai")
public class CredentialProperties {

    /**
     * AES 包装密钥。生产请用环境变量 ZHISHU_AI_CRYPTO_KEY，勿把模型 API Key 写入配置文件。
     */
    private String cryptoKey = "zhishu-dev-model-key-wrap";
}

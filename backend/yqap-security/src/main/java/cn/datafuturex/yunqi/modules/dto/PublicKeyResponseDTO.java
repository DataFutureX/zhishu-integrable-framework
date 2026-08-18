package cn.datafuturex.yunqi.modules.dto;

/**
 * 登录 RSA 公钥响应 DTO
 */
public record PublicKeyResponseDTO(
        /**
         * 密钥唯一标识，登录时需回传
         */
        String keyId,

        /**
         * PEM 格式公钥
         */
        String publicKey,

        /**
         * 加密算法
         */
        String algorithm,

        /**
         * 公钥有效期（秒）
         */
        Integer expireSeconds
) {
}

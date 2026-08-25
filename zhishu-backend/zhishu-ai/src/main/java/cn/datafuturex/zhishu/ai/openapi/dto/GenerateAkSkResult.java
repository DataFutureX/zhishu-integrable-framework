package cn.datafuturex.zhishu.ai.openapi.dto;

/**
 * 生成 AK/SK 的结果。Secret Key 明文仅在此处返回一次。
 */
public record GenerateAkSkResult(
        String accessKey,
        String secretKey
) {
}

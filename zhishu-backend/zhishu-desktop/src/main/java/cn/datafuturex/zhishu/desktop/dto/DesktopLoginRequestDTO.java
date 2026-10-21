package cn.datafuturex.zhishu.desktop.dto;

/**
 * 桌面端登录请求 DTO（无滑动验证码）。
 * <p>
 * 当 {@code yunqi.login-crypto.enabled=true} 时，
 * {@code username} 与 {@code password} 为 RSA 加密后的 Base64 字符串，{@code keyId} 必填；
 * 当加密关闭时，直接传递明文。
 *
 * @param username 用户名（明文或 RSA 加密 Base64）
 * @param password 密码（明文或 RSA 加密 Base64）
 * @param keyId    RSA 公钥标识（启用加密时由 /desktop/v1/auth/public-key 返回）
 */
public record DesktopLoginRequestDTO(
        String username,
        String password,
        String keyId
) {
}

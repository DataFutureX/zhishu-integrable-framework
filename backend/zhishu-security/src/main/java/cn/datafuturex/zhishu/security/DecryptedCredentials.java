package cn.datafuturex.zhishu.security;

/**
 * 解密后的登录凭证
 */
public record DecryptedCredentials(String username, String password) {
}

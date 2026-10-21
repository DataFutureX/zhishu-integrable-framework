package cn.datafuturex.zhishu.desktop.dto;

/**
 * 桌面端登录成功响应 DTO。
 *
 * @param token      JWT Token
 * @param expiration Token 过期时间戳（毫秒）
 * @param userId     用户 ID
 * @param username   用户名
 * @param realName   真实姓名
 * @param role       角色编码
 */
public record DesktopLoginResultDTO(
        String token,
        Long expiration,
        Long userId,
        String username,
        String realName,
        String role
) {
}

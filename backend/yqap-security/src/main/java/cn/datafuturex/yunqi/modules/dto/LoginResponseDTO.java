package cn.datafuturex.yunqi.modules.dto;

/**
 * 登录响应 DTO
 *
 * @author YunQi Application Platform Team
 */
public record LoginResponseDTO(
        /**
         * JWT Token
         */
        String token,
        
        /**
         * Token 过期时间戳
         */
        Long expiration
) {
}

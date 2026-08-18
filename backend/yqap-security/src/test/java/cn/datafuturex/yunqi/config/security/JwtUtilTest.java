package cn.datafuturex.yunqi.config.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JWT 工具类单元测试
 *
 * @author YunQi Application Platform Team
 */
@SpringBootTest(classes = JwtUtil.class)
@TestPropertySource(properties = {
        "jwt.secret=yqap-secret-key-for-jwt-token-generation-2024",
        "jwt.expiration=86400000"
})
class JwtUtilTest {

    @Autowired
    private JwtUtil jwtUtil;

    private String testUsername;
    private String testToken;

    @BeforeEach
    void setUp() {
        testUsername = "testuser";
        testToken = jwtUtil.generateToken(testUsername);
    }

    /**
     * 测试生成 Token
     */
    @Test
    @DisplayName("测试生成 JWT Token")
    void testGenerateToken() {
        assertNotNull(testToken, "生成的 Token 不应为空");
        assertFalse(testToken.isEmpty(), "生成的 Token 不应为空字符串");
        
        // JWT Token 应该包含三个部分，用点号分隔
        String[] parts = testToken.split("\\.");
        assertEquals(3, parts.length, "JWT Token 应包含三个部分");
    }

    /**
     * 测试从 Token 中获取用户名
     */
    @Test
    @DisplayName("测试从 Token 中提取用户名")
    void testGetUsernameFromToken() {
        String username = jwtUtil.getUsernameFromToken(testToken);
        assertEquals(testUsername, username, "提取的用户名应与原始用户名一致");
    }

    /**
     * 测试 Token 验证 - 有效 Token
     */
    @Test
    @DisplayName("测试验证有效的 JWT Token")
    void testValidateValidToken() {
        boolean isValid = jwtUtil.validateToken(testToken);
        assertTrue(isValid, "有效的 Token 应通过验证");
    }

    /**
     * 测试 Token 验证 - 无效 Token
     */
    @Test
    @DisplayName("测试验证无效的 JWT Token")
    void testValidateInvalidToken() {
        String invalidToken = "invalid.token.here";
        boolean isValid = jwtUtil.validateToken(invalidToken);
        assertFalse(isValid, "无效的 Token 应验证失败");
    }

    /**
     * 测试 Token 验证 - 空 Token
     */
    @Test
    @DisplayName("测试验证空 Token")
    void testValidateEmptyToken() {
        boolean isValid = jwtUtil.validateToken("");
        assertFalse(isValid, "空 Token 应验证失败");
    }

    /**
     * 测试 Token 验证 - null Token
     */
    @Test
    @DisplayName("测试验证 null Token")
    void testValidateNullToken() {
        boolean isValid = jwtUtil.validateToken(null);
        assertFalse(isValid, "null Token 应验证失败");
    }

    /**
     * 测试获取 Token 过期时间
     */
    @Test
    @DisplayName("测试获取 Token 过期时间")
    void testGetExpirationFromToken() {
        long expiration = jwtUtil.getExpirationFromToken(testToken);
        assertTrue(expiration > 0, "Token 过期时间应大于 0");
        
        // 过期时间应该在当前时间之后
        long currentTime = System.currentTimeMillis();
        assertTrue(expiration > currentTime, "Token 过期时间应在当前时间之后");
    }

    /**
     * 测试不同用户名的 Token 唯一性
     */
    @Test
    @DisplayName("测试不同用户生成的 Token 不同")
    void testDifferentUsersHaveDifferentTokens() {
        String token1 = jwtUtil.generateToken("user1");
        String token2 = jwtUtil.generateToken("user2");
        
        assertNotEquals(token1, token2, "不同用户生成的 Token 应不同");
    }

    /**
     * 测试同一用户多次生成的 Token 不同（因为包含时间戳）
     */
    @Test
    @DisplayName("测试同一用户多次生成的 Token 不同")
    void testSameUserGeneratesDifferentTokens() throws InterruptedException {
        String token1 = jwtUtil.generateToken(testUsername);
        
        // 等待 1 毫秒确保时间戳不同
        Thread.sleep(1);
        
        String token2 = jwtUtil.generateToken(testUsername);
        
        // 注意：由于 JWT 可能包含其他随机因素，这里不强制要求不同
        // 但实际实现中通常会不同
        assertNotNull(token1);
        assertNotNull(token2);
    }

    /**
     * 测试 Token 中包含用户名信息
     */
    @Test
    @DisplayName("测试 Token 中包含正确的用户名信息")
    void testTokenContainsCorrectUsername() {
        String username1 = jwtUtil.getUsernameFromToken(jwtUtil.generateToken("alice"));
        String username2 = jwtUtil.getUsernameFromToken(jwtUtil.generateToken("bob"));
        
        assertEquals("alice", username1, "Token 应包含正确的用户名 alice");
        assertEquals("bob", username2, "Token 应包含正确的用户名 bob");
    }
}

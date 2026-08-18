package cn.datafuturex.yunqi.common;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 密码生成器测试
 *
 * @author YunQi Application Platform Team
 */
class PasswordGeneratorTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void testAdminPassword() {
        String rawPassword = "admin123";
        // 数据库中存储的哈希值（由 testGenerateNewHash 生成）
        String encodedPassword = "$2a$10$672KsXLZg0JGRwUiwcZdA.gdJXI05j9aemXqj0x18o6pixlWn9fZm";
        
        boolean matches = encoder.matches(rawPassword, encodedPassword);
        
        System.out.println("========================================");
        System.out.println("明文密码: " + rawPassword);
        System.out.println("数据库哈希: " + encodedPassword);
        System.out.println("匹配结果: " + matches);
        System.out.println("========================================");
        
        assertTrue(matches, "密码 admin123 应该匹配数据库中的哈希值");
    }

    @Test
    void testGenerateNewHash() {
        String rawPassword = "admin123";
        String newHash = encoder.encode(rawPassword);
        
        System.out.println("新生成的哈希: " + newHash);
        System.out.println("验证新哈希: " + encoder.matches(rawPassword, newHash));
        
        assertTrue(encoder.matches(rawPassword, newHash));
    }
}

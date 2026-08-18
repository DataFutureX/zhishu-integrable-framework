package cn.datafuturex.yunqi.common;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BCrypt 密码加密工具类
 * <p>
 * 用于生成 BCrypt 加密的密码哈希值
 *
 * @author YunQi Application Platform Team
 */
public class PasswordGenerator {

    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /**
     * 生成 BCrypt 密码哈希
     *
     * @param rawPassword 明文密码
     * @return BCrypt 哈希值
     */
    public static String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    /**
     * 验证密码是否匹配
     *
     * @param rawPassword     明文密码
     * @param encodedPassword BCrypt 哈希值
     * @return 是否匹配
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }

    /**
     * 主方法 - 用于生成密码哈希
     *
     * @param args 命令行参数（可选，传入明文密码）
     */
    public static void main(String[] args) {
        String password = args.length > 0 ? args[0] : "admin123";
        String encoded = encode(password);
        
        System.out.println("========================================");
        System.out.println("明文密码: " + password);
        System.out.println("BCrypt哈希: " + encoded);
        System.out.println("========================================");
        System.out.println("SQL插入语句:");
        System.out.println("INSERT INTO sys_user (id, username, password, role) VALUES (1, 'admin', '" + encoded + "', 'ADMIN');");
        System.out.println("========================================");
    }
}

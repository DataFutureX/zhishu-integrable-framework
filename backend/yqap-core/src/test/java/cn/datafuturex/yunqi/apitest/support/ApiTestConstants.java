package cn.datafuturex.yunqi.apitest.support;

/**
 * API 集成测试常量
 */
public final class ApiTestConstants {

    public static final String PREFIX = "apitest_";
    public static final String ADMIN_USERNAME = "admin";
    public static final String ADMIN_PASSWORD = "admin123";
    /** 种子角色 ADMIN 的固定 ID（init.sql） */
    public static final long ADMIN_ROLE_ID = 1L;

    private ApiTestConstants() {
    }

    public static String unique(String hint) {
        String raw = PREFIX + hint + "_" + Long.toString(System.nanoTime(), 36);
        return raw.length() > 45 ? raw.substring(0, 45) : raw;
    }
}
